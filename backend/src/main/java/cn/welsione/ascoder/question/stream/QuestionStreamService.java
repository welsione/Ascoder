package cn.welsione.ascoder.question.stream;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.port.CodeAnswerAgent;
import cn.welsione.ascoder.question.api.CreateQuestionRequest;
import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.application.AgentEventService;
import cn.welsione.ascoder.question.application.QuestionAnsweredEvent;
import cn.welsione.ascoder.question.application.QuestionService;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 问题流式服务，协调 SSE 连接、Agent 事件分发和回答持久化。
 *
 * <p>事务边界：Agent 调用发生在异步线程，无法使用声明式 {@code @Transactional}，
 * 因此通过 {@link TransactionTemplate} 显式开启事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionStreamService {

    private static final int PROGRESS_SAVE_EVENT_INTERVAL = 8;
    private static final long PROGRESS_SAVE_INTERVAL_MILLIS = 2_000L;

    private final QuestionService questionService;
    private final AgentEventService agentEventService;
    private final CodeAnswerAgent codeAnswerAgent;
    private final TransactionTemplate txTemplate;
    private final SseConnectionManager connectionManager;
    private final AgentEventDispatcher eventDispatcher;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<Long, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<Long, Subscription> runningSubscriptions = new ConcurrentHashMap<>();
    private final Set<Long> cancelledQuestions = ConcurrentHashMap.newKeySet();

    public SseEmitter createEmitter(Long questionId) {
        return connectionManager.createEmitter(questionId);
    }

    public void askAsync(Long questionId, CreateQuestionRequest request, AgentRequest agentRequest) {
        startAsync(questionId, agentRequest);
    }

    public void resumeAsync(Long questionId) {
        try {
            QuestionResponse latest = txTemplate.execute(status -> questionService.get(questionId));
            Future<?> existing = runningTasks.get(questionId);
            if (latest != null && latest.getStatus() == QuestionStatus.RUNNING && existing != null && !existing.isDone()) {
                log.info("复用正在执行的流式任务，questionId={}", questionId);
                connectionManager.sendEvent(questionId, "status", Map.of("status", "RUNNING", "message", "已重新连接到正在执行的任务"));
                return;
            }
            QuestionService.PendingQuestion pending = txTemplate.execute(status -> questionService.prepareResumeStream(questionId));
            if (pending != null) {
                connectionManager.sendEvent(questionId, "created", pending.getQuestion());
                agentEventService.resumeSequence(questionId);
                doStartAsync(questionId, pending.getAgentRequest());
            }
        } catch (Exception ex) {
            log.warn("恢复流式任务失败，questionId={}，错误={}", questionId, ex.getMessage());
            handleError(questionId, ex, null, null, null);
        }
    }

    public void retryAsync(Long questionId) {
        try {
            Future<?> existing = runningTasks.get(questionId);
            if (existing != null && !existing.isDone()) {
                throw new IllegalStateException("问题仍在运行中，不能重试");
            }
            QuestionService.PendingQuestion pending = txTemplate.execute(status -> questionService.prepareRetryStream(questionId));
            if (pending != null) {
                connectionManager.sendEvent(questionId, "created", pending.getQuestion());
                startAsync(questionId, pending.getAgentRequest());
            }
        } catch (Exception ex) {
            log.warn("重试流式任务失败，questionId={}，错误={}", questionId, ex.getMessage());
            handleError(questionId, ex, null, null, null);
        }
    }

    /**
     * 用户主动停止流式回答，取消后台任务并将问题状态持久化为失败。
     */
    public QuestionResponse cancel(Long questionId) {
        log.info("取消流式回答，questionId={}", questionId);
        cancelledQuestions.add(questionId);
        Subscription subscription = runningSubscriptions.remove(questionId);
        if (subscription != null) {
            subscription.cancel();
        }
        Future<?> future = runningTasks.remove(questionId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        QuestionResponse response = txTemplate.execute(status -> questionService.cancelStream(questionId));
        connectionManager.closeEmitter(questionId);
        return response;
    }

    private void startAsync(Long questionId, AgentRequest agentRequest) {
        try {
            cancelledQuestions.remove(questionId);
            agentEventService.initSequence(questionId);
            doStartAsync(questionId, agentRequest);
        } catch (RejectedExecutionException ex) {
            log.warn("Agent 流式执行队列已满，questionId={}", questionId);
            handleError(questionId, new IllegalStateException("Agent 执行队列已满，请稍后重试", ex), null, null, null);
        }
    }

    private void doStartAsync(Long questionId, AgentRequest agentRequest) {
        synchronized (runningTasks) {
            Future<?> existing = runningTasks.get(questionId);
            if (existing != null && !existing.isDone()) {
                log.info("问题已有运行中的流式任务，复用当前任务，questionId={}", questionId);
                connectionManager.sendEvent(questionId, "status", Map.of("status", "RUNNING", "message", "已连接到正在执行的任务"));
                return;
            }
            Future<?> future = connectionManager.agentExecutor.submit(() -> runStream(questionId, agentRequest));
            runningTasks.put(questionId, future);
        }
    }

    private void runStream(Long questionId, AgentRequest agentRequest) {
        AtomicReference<String> codeContextRef = new AtomicReference<>(agentRequest.getCodeContext());
        StringBuilder summaryBuffer = new StringBuilder();
        AnalysisProcessCollector processCollector = new AnalysisProcessCollector();
        AtomicReference<String> finalAnswerRef = new AtomicReference<>("");
        AtomicBoolean completed = new AtomicBoolean(false);
        int[] eventsSinceProgressSave = {0};
        long[] lastProgressSavedAt = {System.currentTimeMillis()};

        try {
            log.info("开始流式回答问题，questionId={}", questionId);
            ensureNotCancelled(questionId);
            QuestionResponse initial = questionService.get(questionId);
            if (initial != null) {
                connectionManager.sendEvent(questionId, "created", initial);
            }
            ensureNotCancelled(questionId);
            connectionManager.sendEvent(questionId, "status", Map.of("status", "RUNNING", "message", "正在分析问题..."));

            codeAnswerAgent.streamAnswer(agentRequest)
                    .doOnSubscribe(subscription -> runningSubscriptions.put(questionId, subscription))
                    .doOnNext(event -> {
                        ensureNotCancelled(questionId);
                        processCollector.append(event);
                        eventDispatcher.dispatch(questionId, event, summaryBuffer, finalAnswerRef, codeContextRef);
                        eventsSinceProgressSave[0]++;
                        if (shouldSaveProgress(eventsSinceProgressSave[0], lastProgressSavedAt[0])) {
                            saveProgress(
                                    questionId,
                                    codeContextRef.get(),
                                    currentPartialAnswer(finalAnswerRef.get(), summaryBuffer),
                                    processCollector.toMarkdown()
                            );
                            eventsSinceProgressSave[0] = 0;
                            lastProgressSavedAt[0] = System.currentTimeMillis();
                        }
                    })
                    .doOnComplete(() -> completed.set(true))
                    .blockLast();

            ensureNotCancelled(questionId);
            if (completed.get()) {
                String finalAnswer = finalAnswerRef.get();
                handleComplete(questionId, finalAnswer == null || finalAnswer.isBlank()
                        ? summaryBuffer.toString()
                        : finalAnswer, codeContextRef.get(), processCollector.toMarkdown());
            } else {
                handleError(questionId, new RuntimeException("流式回答异常终止"),
                        codeContextRef.get(), finalAnswerRef.get(), processCollector.toMarkdown());
            }
        } catch (CancellationException ex) {
            log.info("流式回答已取消，questionId={}", questionId);
        } catch (Exception ex) {
            if (cancelledQuestions.contains(questionId) || isInterrupted(ex)) {
                log.info("流式回答已中断，questionId={}", questionId);
                return;
            }
            log.error("流式回答失败，questionId={}，错误={}", questionId, ex.getMessage(), ex);
            handleError(questionId, ex, codeContextRef.get(), finalAnswerRef.get(), processCollector.toMarkdown());
        } finally {
            runningTasks.remove(questionId);
            runningSubscriptions.remove(questionId);
            agentEventService.flushBuffer();
            agentEventService.removeSequence(questionId);
        }
    }

    private void handleComplete(Long questionId, String fullAnswer, String codeContext, String analysisProcess) {
        try {
            ensureNotCancelled(questionId);
            if (fullAnswer == null || fullAnswer.isBlank()) {
                throw new IllegalStateException("AgentScope 未返回有效回答");
            }
            if (StreamErrorClassifier.isAgentFailure(fullAnswer)) {
                throw new IllegalStateException(fullAnswer);
            }
            QuestionResponse response = txTemplate.execute(status ->
                    questionService.succeed(questionId, fullAnswer, codeContext, analysisProcess)
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "SUCCEEDED");
            payload.put("questionId", questionId);
            payload.put("answer", fullAnswer);
            if (response != null) {
                payload.put("answerSummary", response.getAnswerSummary());
                payload.put("answerEvidence", response.getAnswerEvidence());
                payload.put("uncertainty", response.getUncertainty());
                payload.put("nextStep", response.getNextStep());
            }
            createSelfLearningCandidate(questionId, response, fullAnswer);
            if (analysisProcess != null) {
                payload.put("analysisProcess", analysisProcess);
            }
            if (codeContext != null) {
                payload.put("codeContext", codeContext);
            }
            connectionManager.sendEvent(questionId, "complete", payload);
            log.info("流式回答完成，questionId={}", questionId);
        } catch (Exception ex) {
            log.error("保存流式回答结果失败，questionId={}，错误={}", questionId, ex.getMessage(), ex);
            handleError(questionId, ex, codeContext, fullAnswer, analysisProcess);
        } finally {
            connectionManager.closeEmitter(questionId);
        }
    }

    private boolean shouldSaveProgress(int eventsSinceProgressSave, long lastProgressSavedAt) {
        return eventsSinceProgressSave >= PROGRESS_SAVE_EVENT_INTERVAL
                || System.currentTimeMillis() - lastProgressSavedAt >= PROGRESS_SAVE_INTERVAL_MILLIS;
    }

    private void saveProgress(Long questionId, String codeContext, String partialAnswer, String analysisProcess) {
        try {
            txTemplate.executeWithoutResult(status ->
                    questionService.updateProgress(questionId, codeContext, partialAnswer, analysisProcess)
            );
        } catch (Exception ex) {
            log.debug("保存流式进度失败，questionId={}，错误={}", questionId, ex.getMessage());
        }
    }

    private String currentPartialAnswer(String finalAnswer, StringBuilder summaryBuffer) {
        if (finalAnswer != null && !finalAnswer.isBlank()) {
            return finalAnswer;
        }
        return summaryBuffer.isEmpty() ? null : summaryBuffer.toString();
    }

    private void handleError(Long questionId, Throwable ex) {
        handleError(questionId, ex, null, null, null);
    }

    private void createSelfLearningCandidate(Long questionId, QuestionResponse response, String fullAnswer) {
        try {
            eventPublisher.publishEvent(new QuestionAnsweredEvent(questionId, response, fullAnswer));
        } catch (Exception ex) {
            log.warn("发布自学习事件失败，questionId={}，错误={}", questionId, ex.getMessage());
        }
    }

    private void handleError(Long questionId, Throwable ex, String codeContext, String partialAnswer, String analysisProcess) {
        if (cancelledQuestions.contains(questionId)) {
            connectionManager.closeEmitter(questionId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "FAILED");
        payload.put("questionId", questionId);
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (isInterrupted(ex)) {
            message = "任务被中断：" + message;
        }
        payload.put("message", message);
        payload.put("errorCategory", StreamErrorClassifier.classify(message));
        if (partialAnswer != null && !partialAnswer.isBlank()) {
            payload.put("partialAnswer", partialAnswer);
        }
        if (analysisProcess != null && !analysisProcess.isBlank()) {
            payload.put("analysisProcess", analysisProcess);
        }
        connectionManager.sendEvent(questionId, "error", payload);
        try {
            String errorMessage = message;
            txTemplate.executeWithoutResult(status ->
                    questionService.fail(questionId, errorMessage, codeContext, partialAnswer, analysisProcess)
            );
        } catch (Exception e) {
            log.error("标记失败状态异常，questionId={}", questionId, e);
        }
        connectionManager.closeEmitter(questionId);
    }

    private void ensureNotCancelled(Long questionId) {
        if (cancelledQuestions.contains(questionId) || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("问题已取消");
        }
    }

    @PreDestroy
    void markRunningTasksInterrupted() {
        runningTasks.forEach((questionId, future) -> {
            Subscription subscription = runningSubscriptions.remove(questionId);
            if (subscription != null) {
                subscription.cancel();
            }
            if (future != null && !future.isDone()) {
                future.cancel(true);
                handleError(questionId, new IllegalStateException("服务关闭，流式任务已中断"), null, null, null);
            }
        });
        runningTasks.clear();
        runningSubscriptions.clear();
    }

    private static boolean isInterrupted(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return true;
            }
            current = current.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    /** 静态方法保留以保持单元测试兼容性。 */
    static String classifyError(String message) {
        return StreamErrorClassifier.classify(message);
    }

    /** 静态方法保留以保持单元测试兼容性。 */
    static void replaceSummary(StringBuilder summaryBuffer, String content, boolean replace) {
        AgentEventDispatcher.replaceSummary(summaryBuffer, content, replace);
    }
}
