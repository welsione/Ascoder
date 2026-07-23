package cn.welsione.ascoder.common.task;

/**
 * 异步任务定义，每种异步操作实现此接口。
 *
 * <p>实现类必须是 Spring Bean，由 {@link TaskEngine} 按类型发现和调度。</p>
 *
 * @param <C> 任务上下文类型，携带业务参数
 */
public interface TaskDefinition<C> {

    /** 本定义支持的任务类型。 */
    TaskKind kind();

    /**
     * 执行任务。
     *
     * @param context  业务上下文（由提交方传入）
     * @param progress 进度回调（实现方通过此接口报告进度）
     * @throws TaskCancelledException 任务被取消时抛出，引擎会将其映射为 CANCELLED 状态
     * @throws Exception 业务异常，引擎会将其映射为 FAILED 状态
     */
    void execute(C context, TaskProgress progress) throws Exception;

    /**
     * 任务取消回调（可选覆盖）。
     *
     * <p>在 {@link TaskHandle#cancel()} 被调用后，引擎会触发此方法。
     * 默认实现仅设置线程中断标志；如需更细粒度的取消（如取消 reactor Subscription、
     * 终止子进程），实现方可覆盖此方法。</p>
     *
     * @param context 业务上下文
     */
    default void onCancel(C context) {
        Thread.currentThread().interrupt();
    }

    /**
     * 序列化业务上下文为 JSON（持久化到 DB）。
     * 重启恢复时由 {@link #deserializeContext(String)} 反序列化。
     */
    String serializeContext(C context);

    /**
     * 反序列化业务上下文。
     */
    C deserializeContext(String json);
}
