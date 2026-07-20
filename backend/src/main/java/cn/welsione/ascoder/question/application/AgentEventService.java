package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.question.domain.AgentEvent;
import cn.welsione.ascoder.question.persistence.AgentEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 事件持久化与查询服务，按 questionId + attemptNo 隔离事件记录。
 *
 * <p>写入采用内存缓冲 + 定时批量刷盘，避免高频事件阻塞流式分发。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEventService {

    private static final int FLUSH_THRESHOLD = 20;

    private final AgentEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    private final Map<Long, AttemptState> attemptStates = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<AgentEvent> buffer = new ConcurrentLinkedQueue<>();

    /**
     * 追加一条事件记录到内存缓冲，sequenceNo 和 attemptNo 自动获取当前值。
     */
    public void append(Long questionId, String eventType, Map<String, Object> payload) {
        AttemptState state = attemptStates.get(questionId);
        int attemptNo = state != null ? state.attemptNo : 1;
        int sequenceNo = state != null ? state.sequenceCounter.incrementAndGet() : 1;
        if (state == null) {
            state = new AttemptState(1, new AtomicInteger(1));
            attemptStates.put(questionId, state);
        }
        AgentEvent event = new AgentEvent();
        event.setQuestionId(questionId);
        event.setAttemptNo(attemptNo);
        event.setSequenceNo(sequenceNo);
        event.setEventType(eventType);
        event.setPayload(serializePayload(payload));
        buffer.add(event);
        if (buffer.size() >= FLUSH_THRESHOLD) {
            flushBuffer();
        }
    }

    /**
     * 定时刷盘，每 2 秒将缓冲区事件批量写入数据库。
     */
    @Scheduled(fixedDelay = 2000)
    public void flushBuffer() {
        List<AgentEvent> batch = new ArrayList<>();
        AgentEvent event;
        while ((event = buffer.poll()) != null) {
            batch.add(event);
        }
        if (batch.isEmpty()) return;
        try {
            repository.saveAll(batch);
        } catch (Exception ex) {
            buffer.addAll(batch);
            log.warn("批量持久化 Agent 事件失败，已放回缓冲区，数量={}，错误={}", batch.size(), ex.getMessage());
        }
    }

    /**
     * 查询指定问题的所有事件，按 sequenceNo 升序。
     */
    @Transactional(readOnly = true)
    public List<AgentEvent> listEvents(Long questionId, int attemptNo) {
        flushBufferForQuestion(questionId);
        return repository.findByQuestionIdAndAttemptNoOrderBySequenceNoAsc(questionId, attemptNo);
    }

    /**
     * 初始化问题的事件序列计数器，自动计算下一个 attemptNo。
     */
    public void initSequence(Long questionId) {
        int nextAttemptNo = latestAttemptNo(questionId) + 1;
        int existingCount = repository.countByQuestionIdAndAttemptNo(questionId, nextAttemptNo);
        attemptStates.put(questionId, new AttemptState(nextAttemptNo, new AtomicInteger(existingCount)));
    }

    /**
     * 恢复已有问题的序列计数器（用于 resume 场景，继续当前 attempt）。
     */
    public void resumeSequence(Long questionId) {
        int currentAttempt = latestAttemptNo(questionId);
        int existingCount = repository.countByQuestionIdAndAttemptNo(questionId, currentAttempt);
        attemptStates.put(questionId, new AttemptState(currentAttempt, new AtomicInteger(existingCount)));
    }

    /**
     * 清除指定问题的序列计数器。
     */
    public void removeSequence(Long questionId) {
        attemptStates.remove(questionId);
    }

    @PreDestroy
    void shutdown() {
        flushBuffer();
    }

    /**
     * 获取指定问题的最新 attemptNo。
     */
    public int latestAttemptNo(Long questionId) {
        flushBufferForQuestion(questionId);
        return repository.maxAttemptNoByQuestionId(questionId);
    }

    private void flushBufferForQuestion(Long questionId) {
        List<AgentEvent> questionEvents = new ArrayList<>();
        for (AgentEvent event : buffer) {
            if (event.getQuestionId().equals(questionId)) {
                questionEvents.add(event);
            }
        }
        buffer.removeAll(questionEvents);
        if (!questionEvents.isEmpty()) {
            try {
                repository.saveAll(questionEvents);
            } catch (Exception ex) {
                buffer.addAll(questionEvents);
                log.warn("刷盘问题事件失败，已放回缓冲区，questionId={}，错误={}", questionId, ex.getMessage());
            }
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static class AttemptState {
        final int attemptNo;
        final AtomicInteger sequenceCounter;

        AttemptState(int attemptNo, AtomicInteger sequenceCounter) {
            this.attemptNo = attemptNo;
            this.sequenceCounter = sequenceCounter;
        }
    }
}
