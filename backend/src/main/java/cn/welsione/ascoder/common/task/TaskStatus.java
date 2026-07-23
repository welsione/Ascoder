package cn.welsione.ascoder.common.task;

/**
 * 统一任务生命周期状态。
 *
 * <p>QUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED</p>
 */
public enum TaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
