package cn.welsione.ascoder.common.task;

/**
 * 任务线程池参数提供者端口。
 *
 * <p>{@link TaskExecutorRegistry} 依赖此端口获取各 {@link TaskKind} 的线程池参数，
 * 由 runtime 模块基于运行时设置实现，避免 common 模块反向依赖具体配置实现。</p>
 */
public interface TaskPoolConfigPort {

    /**
     * 读取指定任务类型的线程池参数。
     *
     * @param kind 任务类型
     * @return 线程池参数（core / max / queue）
     * @throws IllegalStateException 对应配置缺失或值非法
     */
    TaskPoolParams resolve(TaskKind kind);
}
