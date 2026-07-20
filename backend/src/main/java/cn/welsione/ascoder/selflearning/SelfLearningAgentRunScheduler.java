package cn.welsione.ascoder.selflearning;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Self Learning Agent 后台调度器，避免管理员触发整理时阻塞 HTTP 请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfLearningAgentRunScheduler {

    private final AgentRunService service;
    private final Set<Long> runningProjectSpaceIds = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, new SelfLearningThreadFactory());

    public SelfLearningAgentRunResponse submit(Long projectSpaceId, Integer limit) {
        if (!runningProjectSpaceIds.add(projectSpaceId)) {
            return new SelfLearningAgentRunResponse(
                    null,
                    LearningAgentRunStatus.RUNNING,
                    0,
                    0,
                    0,
                    0,
                    "Self Learning Agent 正在后台整理该项目空间，请稍后刷新待审核洞察。"
            );
        }
        LearningAgentRun run = service.createAgentRun(projectSpaceId, limit);
        executor.submit(() -> run(run.getId(), projectSpaceId, limit));
        return new SelfLearningAgentRunResponse(
                run.getId(),
                LearningAgentRunStatus.QUEUED,
                0,
                0,
                0,
                0,
                "Self Learning Agent 已提交后台整理，runId=" + run.getId() + "。"
        );
    }

    private void run(Long runId, Long projectSpaceId, Integer limit) {
        try {
            log.info("Self Learning Agent 后台整理开始，runId={}，projectSpaceId={}", runId, projectSpaceId);
            service.markAgentRunRunning(runId);
            SelfLearningAgentRunResponse response = service.runSelfLearningAgent(projectSpaceId, limit, runId);
            service.completeAgentRun(runId, response);
            log.info("Self Learning Agent 后台整理完成，runId={}，projectSpaceId={}，message={}", runId, projectSpaceId, response.getMessage());
        } catch (RuntimeException ex) {
            service.failAgentRun(runId, ex.getMessage());
            log.warn("Self Learning Agent 后台整理失败，runId={}，projectSpaceId={}，error={}", runId, projectSpaceId, ex.getMessage());
        } finally {
            runningProjectSpaceIds.remove(projectSpaceId);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static class SelfLearningThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("self-learning-agent-runner");
            thread.setDaemon(true);
            return thread;
        }
    }
}
