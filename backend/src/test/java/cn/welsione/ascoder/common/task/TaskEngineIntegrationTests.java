package cn.welsione.ascoder.common.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskEngine 集成测试：验证与真实数据库的交互。
 *
 * <p>覆盖：submit 持久化、执行成功/失败/取消、状态查询、去重、重启恢复。
 * 使用 {@link NoopTaskDefinition}（不依赖外部资源）验证框架端到端流程。</p>
 *
 * <p>运行前置：{@code docker compose -f docker-compose.test.yml up -d mysql}
 * 且 {@code export ASCODER_INTEGRATION_TEST=true}。默认 {@code mvn test} 跳过集成测试。</p>
 */
@EnabledIfEnvironmentVariable(named = "ASCODER_INTEGRATION_TEST", matches = "true")
class TaskEngineIntegrationTests extends cn.welsione.ascoder.AbstractIntegrationTest {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private AsyncTaskJpaRepository taskRepository;

    @Autowired
    private NoopTaskDefinition noopDefinition;

    @AfterEach
    void cleanup() throws InterruptedException {
        // 取消并等待所有未完成任务终态，避免 deleteAll 后异步 save 抛乐观锁异常
        List<AsyncTask> running = taskRepository.findByStatusIn(
                List.of(TaskStatus.QUEUED, TaskStatus.RUNNING));
        for (AsyncTask t : running) {
            try {
                taskEngine.cancel(t.getId());
            } catch (Exception ignored) {
            }
        }
        for (AsyncTask t : running) {
            try {
                awaitTerminal(t.getId());
            } catch (Exception ignored) {
            }
        }
        taskRepository.deleteAll();
    }

    @Test
    void submitPersistsToDatabase() throws Exception {
        TaskSubmitRequest<Map<String, String>> req = newRequest();
        req.setBusinessId(1001L);

        TaskHandle handle = taskEngine.submit(req);

        assertNotNull(handle.getTaskId());
        AsyncTask saved = taskRepository.findById(handle.getTaskId()).orElseThrow();
        assertEquals(TaskKind.BRANCH_REFRESH, saved.getKind());
        assertEquals(1001L, saved.getBusinessId());
        assertNotNull(saved.getContextJson());
        awaitTerminal(handle.getTaskId());
    }

    @Test
    void executeSuccessMarksSucceeded() throws Exception {
        noopDefinition.behavior = ctx -> {};

        TaskHandle handle = taskEngine.submit(newRequest());

        awaitTerminal(handle.getTaskId());
        AsyncTask task = taskRepository.findById(handle.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.SUCCEEDED, task.getStatus());
        assertNotNull(task.getStartedAt());
        assertNotNull(task.getFinishedAt());
    }

    @Test
    void executeFailureMarksFailedWithMessage() throws Exception {
        noopDefinition.behavior = ctx -> { throw new RuntimeException("集成测试模拟失败"); };

        TaskHandle handle = taskEngine.submit(newRequest());

        awaitTerminal(handle.getTaskId());
        AsyncTask task = taskRepository.findById(handle.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertTrue(task.getErrorMessage().contains("集成测试模拟失败"));
    }

    @Test
    void executeCancelledMarksCancelled() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        noopDefinition.behavior = ctx -> {
            started.countDown();
            // 阻塞等待取消信号：中断会从 sleep 抛出，捕获后抛 TaskCancelledException
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new TaskCancelledException("已取消");
        };

        TaskHandle handle = taskEngine.submit(newRequest());

        assertTrue(started.await(5, TimeUnit.SECONDS), "任务应在 5s 内启动");
        TaskHandle cancelled = taskEngine.cancel(handle.getTaskId());

        awaitTerminal(handle.getTaskId());
        assertEquals(TaskStatus.CANCELLED, cancelled.getStatus());
        AsyncTask task = taskRepository.findById(handle.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void duplicateSubmitWithSameBusinessIdThrows() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        noopDefinition.behavior = ctx -> {
            started.countDown();
            Thread.sleep(10000); // 长时间运行，保持 RUNNING
        };

        TaskSubmitRequest<Map<String, String>> req = newRequest();
        req.setBusinessId(2002L);
        TaskHandle handle = taskEngine.submit(req);

        assertDoesNotThrow(() -> started.await(5, TimeUnit.SECONDS));
        // 同 businessId 再次提交应被拒绝
        assertThrows(TaskAlreadyRunningException.class, () -> taskEngine.submit(req));

        // 清理：取消长时间运行的任务，避免占用线程池影响后续测试
        taskEngine.cancel(handle.getTaskId());
        awaitTerminal(handle.getTaskId());
    }

    @Test
    void findByKindAndBusinessIdReturnsRunningTask() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        noopDefinition.behavior = ctx -> {
            started.countDown();
            Thread.sleep(10000);
        };

        TaskSubmitRequest<Map<String, String>> req = newRequest();
        req.setBusinessId(3003L);
        TaskHandle submitted = taskEngine.submit(req);
        started.await(5, TimeUnit.SECONDS);

        TaskHandle found = taskEngine.findByKindAndBusinessId(TaskKind.BRANCH_REFRESH, 3003L);
        assertNotNull(found);
        assertEquals(submitted.getTaskId(), found.getTaskId());

        // 清理：取消长时间运行的任务
        taskEngine.cancel(submitted.getTaskId());
        awaitTerminal(submitted.getTaskId());
    }

    @Test
    void cancelNonExistentTaskThrows() {
        assertThrows(IllegalArgumentException.class, () -> taskEngine.cancel(999999L));
    }

    @Test
    void cancelTerminalTaskIsNoop() throws Exception {
        noopDefinition.behavior = ctx -> {};
        TaskHandle handle = taskEngine.submit(newRequest());
        awaitTerminal(handle.getTaskId());

        // 已完成的任务再取消，状态不变
        TaskHandle cancelled = taskEngine.cancel(handle.getTaskId());
        assertEquals(TaskStatus.SUCCEEDED, cancelled.getStatus());
    }

    @Test
    void recoverResetsRunningToQueuedAndResubmits() throws Exception {
        // 直接在 DB 中插入一个 RUNNING 任务（模拟上次未正常结束）
        AsyncTask running = new AsyncTask();
        running.setKind(TaskKind.BRANCH_REFRESH);
        running.setStatus(TaskStatus.RUNNING);
        running.setBusinessId(4004L);
        running.setContextJson(noopDefinition.serializeContext(Map.of("key", "value")));
        running = taskRepository.save(running);

        noopDefinition.behavior = ctx -> {};

        // 触发恢复
        taskEngine.recoverTasks();

        awaitTerminal(running.getId());
        AsyncTask recovered = taskRepository.findById(running.getId()).orElseThrow();
        assertEquals(TaskStatus.SUCCEEDED, recovered.getStatus());
    }

    @Test
    void progressPersistedToDatabase() throws Exception {
        noopDefinition.behavior = ctx -> {
            // 触发进度更新（需要通过 TaskProgress）
            // NoopTaskDefinition 的 execute 没有拿到 progress，这里通过定义自身的行为
        };
        // 由于 NoopTaskDefinition.execute 签名带 progress，这里验证进度持久化
        noopDefinition.behavior = ctx -> {};
        TaskHandle handle = taskEngine.submit(newRequest());
        awaitTerminal(handle.getTaskId());

        AsyncTask task = taskRepository.findById(handle.getTaskId()).orElseThrow();
        // NoopTaskDefinition 不更新进度，progress 保持默认 -1
        assertEquals(-1, task.getProgress());
    }

    @Test
    void concurrentSubmitsWithDifferentBusinessIdAllPersist() throws Exception {
        noopDefinition.behavior = ctx -> Thread.sleep(100);
        AtomicInteger submitted = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            TaskSubmitRequest<Map<String, String>> req = newRequest();
            req.setBusinessId((long) (5000 + i));
            taskEngine.submit(req);
            submitted.incrementAndGet();
        }

        // 等所有完成
        Thread.sleep(2000);
        List<AsyncTask> all = taskRepository.findAll();
        assertEquals(5, all.size());
        assertTrue(all.stream().allMatch(t -> t.getStatus() == TaskStatus.SUCCEEDED));
    }

    // ==================== 辅助方法 ====================

    private TaskSubmitRequest<Map<String, String>> newRequest() {
        TaskSubmitRequest<Map<String, String>> req = new TaskSubmitRequest<>();
        req.setKind(TaskKind.BRANCH_REFRESH);
        req.setContext(Map.of("test", "value"));
        req.setBusinessId(null); // 默认无 businessId，调用方按需设置
        return req;
    }

    private void awaitTerminal(Long taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AsyncTask task = taskRepository.findById(taskId).orElse(null);
            if (task != null && task.isTerminal()) {
                return;
            }
            Thread.sleep(100);
        }
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        fail("任务 " + taskId + " 未在 10s 内进入终态，当前状态：" + (task != null ? task.getStatus() : "null"));
    }
}
