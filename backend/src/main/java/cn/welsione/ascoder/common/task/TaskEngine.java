package cn.welsione.ascoder.common.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * 统一异步任务引擎，负责任务的提交、调度、执行、取消和恢复。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>按 TaskKind 路由到对应线程池</li>
 *   <li>任务状态持久化（asyncTasks 表）</li>
 *   <li>进度更新持久化</li>
 *   <li>取消信号传递</li>
 *   <li>重启后恢复 QUEUED/RUNNING 任务</li>
 * </ul>
 */
@Slf4j
@Service
public class TaskEngine implements org.springframework.beans.factory.SmartInitializingSingleton {

    private final AsyncTaskJpaRepository taskRepository;
    private final TaskExecutorRegistry executorRegistry;
    private final TaskProgressPublisher progressPublisher;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final org.springframework.beans.factory.ObjectProvider<java.util.List<TaskDefinition<?>>> definitionsProvider;

    /** 按 kind 索引的 TaskDefinition。 */
    private final Map<TaskKind, TaskDefinition<?>> definitions = new ConcurrentHashMap<>();

    /** 运行中任务的内存上下文。 */
    private final Map<Long, RunningTaskContext> runningTasks = new ConcurrentHashMap<>();

    public TaskEngine(AsyncTaskJpaRepository taskRepository,
                      TaskExecutorRegistry executorRegistry,
                      TaskProgressPublisher progressPublisher,
                      TransactionTemplate txTemplate,
                      ObjectMapper objectMapper,
                      org.springframework.beans.factory.ObjectProvider<java.util.List<TaskDefinition<?>>> definitionsProvider) {
        this.taskRepository = taskRepository;
        this.executorRegistry = executorRegistry;
        this.progressPublisher = progressPublisher;
        this.txTemplate = txTemplate;
        this.objectMapper = objectMapper;
        this.definitionsProvider = definitionsProvider;
    }

    /**
     * 注册所有 TaskDefinition Bean 并恢复未完成任务。
     *
     * 注册所有 TaskDefinition Bean 并恢复未完成任务。
     *
     * <p>用 {@link org.springframework.beans.factory.SmartInitializingSingleton} 在所有单例
     * Bean 创建完成后调用，避免 {@code @PostConstruct} 过早解析 TaskDefinition 列表触发循环依赖
     *（TaskDefinition 依赖链可能回到 TaskEngine）。List 按 {@code @Order} 排序，
     * 后注册的覆盖先注册的（测试可借此覆盖生产实现）。</p>
     */
    @Override
    public void afterSingletonsInstantiated() {
        init();
    }

    void init() {
        java.util.List<TaskDefinition<?>> defList = definitionsProvider.getIfAvailable(java.util.Collections::emptyList);
        for (TaskDefinition<?> def : defList) {
            definitions.put(def.kind(), def);
            log.info("注册任务定义：{}", def.kind());
        }
        recoverTasks();
    }

    /**
     * 提交异步任务。
     *
     * @return 任务句柄
     * @throws TaskAlreadyRunningException 同 kind + businessId 已有运行中任务
     * @throws TaskQueueFullException 对应 kind 的线程池队列已满
     */
    @SuppressWarnings("unchecked")
    public <C> TaskHandle submit(TaskSubmitRequest<C> request) {
        TaskKind kind = request.getKind();
        TaskDefinition<C> definition = (TaskDefinition<C>) definitions.get(kind);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的任务定义: " + kind);
        }

        // 去重检查
        if (request.getBusinessId() != null) {
            List<AsyncTask> active = taskRepository.findByKindAndBusinessIdAndStatusIn(
                    kind, request.getBusinessId(),
                    List.of(TaskStatus.QUEUED, TaskStatus.RUNNING));
            if (!active.isEmpty()) {
                throw new TaskAlreadyRunningException(kind, request.getBusinessId());
            }
        }

        // 序列化上下文
        String contextJson = definition.serializeContext(request.getContext());

        // DB 插入
        AsyncTask task = new AsyncTask();
        task.setKind(kind);
        task.setBusinessId(request.getBusinessId());
        task.setContextJson(contextJson);
        task.setMaxRetries(request.getMaxRetries());
        task.setTimeoutMs(request.getTimeoutMs());
        task = taskRepository.save(task);

        Long taskId = task.getId();

        // 提交线程池
        try {
            java.util.concurrent.atomic.AtomicBoolean cancelledFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
            TaskProgressImpl progress = new TaskProgressImpl(taskId, progressPublisher, cancelledFlag);
            @SuppressWarnings("unchecked")
            TaskDefinition<Object> def = (TaskDefinition<Object>) (TaskDefinition<?>) definition;
            Future<?> future = executorRegistry.getExecutor(kind).submit(() -> executeTask(taskId, def, progress, cancelledFlag));
            // 记录运行时上下文（含 Future 引用，用于取消）
            runningTasks.put(taskId, new RunningTaskContext(future, def, request.getContext(), progress));
        } catch (RejectedExecutionException e) {
            // 队列满，标记失败
            task.fail("任务队列已满");
            taskRepository.save(task);
            throw new TaskQueueFullException(kind);
        }

        log.info("提交异步任务：taskId={}，kind={}，businessId={}", taskId, kind, request.getBusinessId());
        return new TaskHandleImpl(task);
    }

    /**
     * 查询任务句柄。
     */
    public TaskHandle getHandle(Long taskId) {
        return taskRepository.findById(taskId)
                .map(TaskHandleImpl::new)
                .orElse(null);
    }

    /**
     * 按 kind + businessId 查询任务。
     */
    public TaskHandle findByKindAndBusinessId(TaskKind kind, Long businessId) {
        return taskRepository.findByKindAndBusinessIdAndStatusIn(
                        kind, businessId, List.of(TaskStatus.QUEUED, TaskStatus.RUNNING))
                .stream()
                .findFirst()
                .map(TaskHandleImpl::new)
                .orElse(null);
    }

    /**
     * 分页查询任务列表，支持按类型和状态筛选。
     */
    @Transactional(readOnly = true)
    public Page<AsyncTask> list(TaskKind kind, List<TaskStatus> statuses, Pageable pageable) {
        if (kind != null && statuses != null && !statuses.isEmpty()) {
            return taskRepository.findByKindAndStatusIn(kind, statuses, pageable);
        }
        if (kind != null) {
            return taskRepository.findByKind(kind, pageable);
        }
        if (statuses != null && !statuses.isEmpty()) {
            return taskRepository.findByStatusIn(statuses, pageable);
        }
        return taskRepository.findAll(pageable);
    }

    /**
     * 取消任务。
     */
    public TaskHandle cancel(Long taskId) {
        AsyncTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        if (task.isTerminal()) {
            return new TaskHandleImpl(task);
        }

        // DB 标记取消
        task.cancel();
        task = taskRepository.save(task);

        // 内存取消信号
        RunningTaskContext ctx = runningTasks.get(taskId);
        if (ctx != null) {
            try {
                ctx.requestCancel();
            } catch (Exception e) {
                log.warn("取消任务回调异常，taskId={}，错误={}", taskId, e.getMessage());
            }
        }

        log.info("取消任务：taskId={}，kind={}", taskId, task.getKind());
        return new TaskHandleImpl(task);
    }

    /**
     * 启动后恢复未完成的任务：RUNNING → QUEUED 重新提交。
     */
    void recoverTasks() {
        List<AsyncTask> unfinished = taskRepository.findByStatusIn(
                List.of(TaskStatus.QUEUED, TaskStatus.RUNNING));

        if (unfinished.isEmpty()) {
            return;
        }

        log.info("恢复 {} 个未完成的异步任务", unfinished.size());
        for (AsyncTask task : unfinished) {
            if (task.getStatus() == TaskStatus.RUNNING) {
                // 上次未正常结束，重置为 QUEUED
                task.setStatus(TaskStatus.QUEUED);
                task.setStartedAt(null);
                taskRepository.save(task);
            }
            // 重新提交（延迟执行，等所有 Bean 初始化完毕）
            try {
                java.util.concurrent.atomic.AtomicBoolean cancelledFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
                @SuppressWarnings("unchecked")
                TaskDefinition<Object> def = (TaskDefinition<Object>) definitions.get(task.getKind());
                TaskProgressImpl prog = new TaskProgressImpl(task.getId(), progressPublisher, cancelledFlag);
                executorRegistry.getExecutor(task.getKind()).submit(() -> executeTask(task.getId(), def, prog, cancelledFlag));
            } catch (Exception e) {
                log.error("恢复任务失败，taskId={}，错误={}", task.getId(), e.getMessage());
                task.fail("恢复失败: " + e.getMessage());
                taskRepository.save(task);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeTask(Long taskId, TaskDefinition<Object> definition, TaskProgressImpl progress,
                             java.util.concurrent.atomic.AtomicBoolean cancelledFlag) {
        AsyncTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.isTerminal()) {
            return;
        }

        TaskKind kind = task.getKind();
        if (definition == null) {
            task.fail("未注册的任务定义: " + kind);
            taskRepository.save(task);
            return;
        }

        // 反序列化上下文
        Object context;
        try {
            context = definition.deserializeContext(task.getContextJson());
        } catch (Exception e) {
            task.fail("上下文反序列化失败: " + e.getMessage());
            taskRepository.save(task);
            return;
        }

        // 标记 RUNNING
        txTemplate.executeWithoutResult(status -> {
            taskRepository.findById(taskId).ifPresent(t -> {
                t.start();
                taskRepository.save(t);
            });
        });

        // 更新运行时上下文（含反序列化后的 context，用于取消回调）
        RunningTaskContext ctx = runningTasks.get(taskId);
        if (ctx != null) {
            runningTasks.put(taskId, new RunningTaskContext(
                    ctx.getFuture(), definition, context, progress));
        } else {
            // recover 路径没有预建 RunningTaskContext
            runningTasks.put(taskId, new RunningTaskContext(null, definition, context, progress));
        }

        log.info("开始执行任务：taskId={}，kind={}", taskId, kind);

        try {
            definition.execute(context, progress);

            // 成功
            txTemplate.executeWithoutResult(status -> {
                taskRepository.findById(taskId).ifPresent(t -> {
                    t.succeed(null);
                    taskRepository.save(t);
                });
            });
            log.info("任务执行成功：taskId={}，kind={}", taskId, kind);

        } catch (TaskCancelledException e) {
            txTemplate.executeWithoutResult(status -> {
                taskRepository.findById(taskId).ifPresent(t -> {
                    t.cancel();
                    taskRepository.save(t);
                });
            });
            log.info("任务已取消：taskId={}，kind={}", taskId, kind);

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // 提取 cause chain
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                errorMsg += "（" + e.getCause().getMessage() + "）";
            }
            String finalErrorMsg = errorMsg;

            txTemplate.executeWithoutResult(status -> {
                taskRepository.findById(taskId).ifPresent(t -> {
                    t.fail(finalErrorMsg);
                    taskRepository.save(t);
                });
            });
            log.error("任务执行失败：taskId={}，kind={}，错误={}", taskId, kind, errorMsg);
        } finally {
            runningTasks.remove(taskId);
            progressPublisher.clearThrottle(taskId);
        }
    }

    /**
     * TaskHandle 的内部实现，代理到 AsyncTask 实体。
     */
    private class TaskHandleImpl implements TaskHandle {

        private AsyncTask task;

        TaskHandleImpl(AsyncTask task) {
            this.task = task;
        }

        @Override
        public Long getTaskId() {
            return task.getId();
        }

        @Override
        public TaskKind getKind() {
            return task.getKind();
        }

        @Override
        public TaskStatus getStatus() {
            return task.getStatus();
        }

        @Override
        public Long getBusinessId() {
            return task.getBusinessId();
        }

        @Override
        public int getProgress() {
            return task.getProgress();
        }

        @Override
        public String getStatusMessage() {
            return task.getStatusMessage();
        }

        @Override
        public Date getQueuedAt() {
            return task.getQueuedAt();
        }

        @Override
        public Date getStartedAt() {
            return task.getStartedAt();
        }

        @Override
        public Date getFinishedAt() {
            return task.getFinishedAt();
        }

        @Override
        public String getErrorMessage() {
            return task.getErrorMessage();
        }

        @Override
        public void cancel() {
            task = taskRepository.findById(task.getId()).orElse(task);
            TaskEngine.this.cancel(task.getId());
            task = taskRepository.findById(task.getId()).orElse(task);
        }

        @Override
        public boolean isTerminal() {
            return task.isTerminal();
        }
    }
}
