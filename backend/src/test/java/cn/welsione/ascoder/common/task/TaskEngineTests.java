package cn.welsione.ascoder.common.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TaskEngine 核心流程测试：提交、去重、队列满、取消、执行成功/失败/取消、恢复。
 *
 * <p>使用同步执行的 ExecutorService mock，确保测试可重入、无时序竞态。</p>
 */
class TaskEngineTests {

    private AsyncTaskJpaRepository taskRepository;
    private TaskExecutorRegistry executorRegistry;
    private TaskProgressPublisher progressPublisher;
    private TransactionTemplate txTemplate;
    private ObjectMapper objectMapper;
    private SyncExecutor syncExecutor;
    private TestTaskDefinition definition;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        taskRepository = mock(AsyncTaskJpaRepository.class);
        executorRegistry = mock(TaskExecutorRegistry.class);
        progressPublisher = mock(TaskProgressPublisher.class);
        objectMapper = new ObjectMapper();
        definition = spy(new TestTaskDefinition());

        // 同步执行器：任务在 submit 时立即执行，便于测试
        syncExecutor = new SyncExecutor();
        when(executorRegistry.getExecutor(any(TaskKind.class))).thenReturn(syncExecutor);

        // TransactionTemplate：直接执行 callback
        txTemplate = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        doAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        }).when(txTemplate).execute(any(TransactionCallback.class));
    }

    private TaskEngine newEngine() {
        return new TaskEngine(taskRepository, executorRegistry, progressPublisher,
                txTemplate, objectMapper, List.of(definition));
    }

    private TaskSubmitRequest<Map<String, String>> submitRequest() {
        TaskSubmitRequest<Map<String, String>> req = new TaskSubmitRequest<>();
        req.setKind(TestTaskDefinition.KIND);
        req.setContext(Map.of("key", "value"));
        req.setBusinessId(100L);
        return req;
    }

    /** 模拟 save 时给实体分配 ID 并保留同一引用（保留 contextJson 等字段）。 */
    private final java.util.concurrent.atomic.AtomicReference<AsyncTask> savedRef =
            new java.util.concurrent.atomic.AtomicReference<>();

    private void mockSaveAndFindById() {
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> {
            AsyncTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            savedRef.set(t);
            return t;
        });
        when(taskRepository.findById(1L)).thenAnswer(inv ->
                java.util.Optional.ofNullable(savedRef.get()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitExecutesAndSucceeds() throws Exception {
        TaskEngine engine = newEngine();
        mockSaveAndFindById();

        // 让 execute 成功
        definition.behavior = ctx -> { /* 正常完成 */ };

        TaskHandle handle = engine.submit(submitRequest());

        assertNotNull(handle);
        assertEquals(TestTaskDefinition.KIND, handle.getKind());
        // execute 在同步执行器中已跑完 -> 状态应为 SUCCEEDED
        assertEquals(TaskStatus.SUCCEEDED, savedRef.get().getStatus());
        verify(definition).execute(any(), any());
    }

    @Test
    void submitWithRunningDuplicateThrows() {
        TaskEngine engine = newEngine();
        AsyncTask existing = new AsyncTask();
        existing.setStatus(TaskStatus.RUNNING);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TestTaskDefinition.KIND), eq(100L), anyList()))
                .thenReturn(List.of(existing));

        TaskAlreadyRunningException ex = assertThrows(TaskAlreadyRunningException.class,
                () -> engine.submit(submitRequest()));
        assertTrue(ex.getMessage().contains("BRANCH_REFRESH"));
    }

    @Test
    void submitNoBusinessIdSkipsDedup() {
        TaskEngine engine = newEngine();
        AsyncTask savedTask = new AsyncTask();
        savedTask.setId(1L);
        savedTask.setKind(TestTaskDefinition.KIND);
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(savedTask);
        when(taskRepository.findById(1L)).thenReturn(java.util.Optional.of(savedTask));

        TaskSubmitRequest<Map<String, String>> req = submitRequest();
        req.setBusinessId(null);
        definition.behavior = ctx -> {};

        TaskHandle handle = engine.submit(req);
        assertNotNull(handle);
        // businessId 为 null 不查重
        verify(taskRepository, never()).findByKindAndBusinessIdAndStatusIn(any(), any(), anyList());
    }

    @Test
    void submitQueueFullMarksFailedAndThrows() {
        TaskEngine engine = newEngine();
        AsyncTask savedTask = new AsyncTask();
        savedTask.setId(1L);
        savedTask.setKind(TestTaskDefinition.KIND);
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(savedTask);

        // 线程池拒绝
        when(executorRegistry.getExecutor(any(TaskKind.class)))
                .thenReturn(new RejectingExecutor());

        assertThrows(TaskQueueFullException.class, () -> engine.submit(submitRequest()));
        assertEquals(TaskStatus.FAILED, savedTask.getStatus());
    }

    @Test
    void executeFailureMarksFailed() {
        TaskEngine engine = newEngine();
        mockSaveAndFindById();

        definition.behavior = ctx -> { throw new RuntimeException("boom"); };

        engine.submit(submitRequest());
        assertEquals(TaskStatus.FAILED, savedRef.get().getStatus());
        assertNotNull(savedRef.get().getErrorMessage());
        assertTrue(savedRef.get().getErrorMessage().contains("boom"));
    }

    @Test
    void executeCancelledMarksCancelled() {
        TaskEngine engine = newEngine();
        mockSaveAndFindById();

        definition.behavior = ctx -> { throw new TaskCancelledException("取消"); };

        engine.submit(submitRequest());
        assertEquals(TaskStatus.CANCELLED, savedRef.get().getStatus());
    }

    @Test
    void cancelTerminalTaskIsNoop() {
        TaskEngine engine = newEngine();
        AsyncTask task = new AsyncTask();
        task.setId(5L);
        task.succeed(null);

        when(taskRepository.findById(5L)).thenReturn(java.util.Optional.of(task));
        when(taskRepository.save(any(AsyncTask.class))).thenReturn(task);

        TaskHandle handle = engine.cancel(5L);
        assertEquals(TaskStatus.SUCCEEDED, handle.getStatus());
    }

    @Test
    void cancelRunningTaskSetsCancelledAndSignalsContext() throws Exception {
        TaskEngine engine = newEngine();
        // 用异步执行器，让任务在 cancel 时还在 runningTasks 中
        AsyncExecutor asyncExecutor = new AsyncExecutor();
        when(executorRegistry.getExecutor(any(TaskKind.class))).thenReturn(asyncExecutor);
        mockSaveAndFindById();

        definition.behavior = ctx -> {
            // 阻塞等待取消信号（lastProgress 在 execute 开头已设置）
            while (!definition.lastProgress.isCancelled()) {
                try { Thread.sleep(10); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TaskCancelledException("中断取消");
                }
            }
            throw new TaskCancelledException("检测到取消");
        };

        // 提交（异步执行）
        engine.submit(submitRequest());
        // 等 executeTask 开始
        awaitCondition(() -> definition.lastProgress != null, 1000);

        // 取消
        TaskHandle cancelled = engine.cancel(1L);
        assertEquals(TaskStatus.CANCELLED, cancelled.getStatus());

        // 等任务线程结束
        awaitCondition(() -> savedRef.get().isTerminal(), 2000);
        asyncExecutor.shutdown();
    }

    @Test
    void recoverResetsRunningToQueuedAndResubmits() throws Exception {
        TaskEngine engine = newEngine();

        // 给 task 设置 contextJson，避免 executeTask 反序列化 NPE
        String contextJson = new ObjectMapper().writeValueAsString(Map.of("key", "value"));

        AsyncTask running = new AsyncTask();
        running.setId(1L);
        running.setKind(TestTaskDefinition.KIND);
        running.setStatus(TaskStatus.RUNNING);
        running.setContextJson(contextJson);

        AsyncTask queued = new AsyncTask();
        queued.setId(2L);
        queued.setKind(TestTaskDefinition.KIND);
        queued.setStatus(TaskStatus.QUEUED);
        queued.setContextJson(contextJson);

        when(taskRepository.findByStatusIn(anyList())).thenReturn(List.of(running, queued));
        when(taskRepository.save(any(AsyncTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.findById(1L)).thenReturn(java.util.Optional.of(running));
        when(taskRepository.findById(2L)).thenReturn(java.util.Optional.of(queued));

        definition.behavior = ctx -> {};

        engine.recoverTasks();

        // 同步执行器立即执行：两个任务都重新提交并成功完成
        assertEquals(TaskStatus.SUCCEEDED, running.getStatus());
        assertEquals(TaskStatus.SUCCEEDED, queued.getStatus());
        verify(definition, atLeast(2)).execute(any(), any());
    }

    @Test
    void recoverWithNoUnfinishedIsNoop() {
        TaskEngine engine = newEngine();
        when(taskRepository.findByStatusIn(anyList())).thenReturn(Collections.emptyList());
        engine.recoverTasks();
        verify(executorRegistry, never()).getExecutor(any());
    }

    @Test
    void findByKindAndBusinessIdReturnsHandle() {
        TaskEngine engine = newEngine();
        AsyncTask task = new AsyncTask();
        task.setId(7L);
        task.setKind(TestTaskDefinition.KIND);
        task.setStatus(TaskStatus.RUNNING);
        when(taskRepository.findByKindAndBusinessIdAndStatusIn(
                eq(TestTaskDefinition.KIND), eq(100L), anyList()))
                .thenReturn(List.of(task));

        TaskHandle handle = engine.findByKindAndBusinessId(TestTaskDefinition.KIND, 100L);
        assertNotNull(handle);
        assertEquals(7L, handle.getTaskId());
    }

    @Test
    void getHandleReturnsNullWhenNotFound() {
        TaskEngine engine = newEngine();
        when(taskRepository.findById(999L)).thenReturn(java.util.Optional.empty());
        assertNull(engine.getHandle(999L));
    }

    // ==================== 测试辅助 ====================

    /** 同步执行器：submit 时立即运行。 */
    static class SyncExecutor extends AbstractExecutorService {
        @Override
        public void shutdown() {}
        @Override
        public List<Runnable> shutdownNow() { return new ArrayList<>(); }
        @Override
        public boolean isShutdown() { return false; }
        @Override
        public boolean isTerminated() { return false; }
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override
        public void execute(Runnable command) { command.run(); }
        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            task.run();
            return new java.util.concurrent.CompletableFuture<>();
        }
    }

    /** 拒绝执行器：模拟队列满。 */
    static class RejectingExecutor extends AbstractExecutorService {
        @Override
        public void shutdown() {}
        @Override
        public List<Runnable> shutdownNow() { return new ArrayList<>(); }
        @Override
        public boolean isShutdown() { return false; }
        @Override
        public boolean isTerminated() { return false; }
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override
        public void execute(Runnable command) { throw new RejectedExecutionException(); }
        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            throw new RejectedExecutionException();
        }
    }

    /** 异步执行器：真实线程，用于取消测试。 */
    static class AsyncExecutor extends AbstractExecutorService {
        final java.util.concurrent.ExecutorService delegate =
                java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "test-async");
                    t.setDaemon(true);
                    return t;
                });
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
        @Override public void execute(Runnable command) { delegate.execute(command); }
        @Override public java.util.concurrent.Future<?> submit(Runnable task) { return delegate.submit(task); }
    }

    /** 测试用 TaskDefinition，execute 行为可配置。 */
    static class TestTaskDefinition implements TaskDefinition<Map<String, String>> {
        static final TaskKind KIND = TaskKind.BRANCH_REFRESH; // 复用已注册的 kind
        volatile java.util.function.Consumer<Map<String, String>> behavior = ctx -> {};
        volatile TaskProgress lastProgress;

        @Override
        public TaskKind kind() { return KIND; }

        @Override
        public void execute(Map<String, String> context, TaskProgress progress) throws Exception {
            lastProgress = progress;
            behavior.accept(context);
        }

        @Override
        public String serializeContext(Map<String, String> context) {
            try { return new ObjectMapper().writeValueAsString(context); }
            catch (Exception e) { throw new RuntimeException(e); }
        }

        @Override
        public Map<String, String> deserializeContext(String json) {
            try { return new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    /** 轮询等待条件满足，超时抛断言。 */
    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        fail("条件未在 " + timeoutMs + "ms 内满足");
    }
}
