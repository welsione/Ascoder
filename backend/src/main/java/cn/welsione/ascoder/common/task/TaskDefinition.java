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
     * 解析业务 ID 为可读标签，供前端展示。
     *
     * <p>默认返回 null，表示无法解析。各实现类可覆盖此方法，
     * 根据 businessId 查询关联实体名称（如仓库名、项目空间名）。</p>
     *
     * @param businessId 业务关联 ID
     * @return 可读标签，如 "ascoder (仓库)"，无法解析时返回 null
     */
    default String resolveBusinessLabel(Long businessId) {
        return null;
    }

    /**
     * 该类型任务的默认超时（毫秒）。
     *
     * <p>返回 0 表示不超时，适用于：</p>
     * <ul>
     *   <li>任务本身时长不可预估（如大型代码库全量索引）</li>
     *   <li>任务内部已有可靠的超时机制（如 CLI 进程级超时）</li>
     * </ul>
     * <p>不超时的任务依赖以下兜底机制：</p>
     * <ul>
     *   <li>任务内部超时（如 SafeCommandRunner 的进程超时）</li>
     *   <li>{@code ensureTerminal} 兜底（进程崩溃时强制标记终态）</li>
     *   <li>手动清理僵尸任务（前端"清理僵尸任务"按钮）</li>
     *   <li>重启恢复（{@code recoverTasks}）</li>
     * </ul>
     *
     * @return 默认超时毫秒数，0 表示不超时
     */
    default long defaultTimeoutMs() {
        return 0;
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
