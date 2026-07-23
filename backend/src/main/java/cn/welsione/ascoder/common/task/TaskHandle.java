package cn.welsione.ascoder.common.task;

import java.util.Date;

/**
 * 异步任务句柄，提交任务后返回此接口供调用方查询进度、取消任务。
 *
 * <p>线程安全：所有方法可从任意线程调用。</p>
 */
public interface TaskHandle {

    /** 任务唯一 ID（对应 asyncTasks 表主键）。 */
    Long getTaskId();

    /** 任务类型。 */
    TaskKind getKind();

    /** 当前状态。 */
    TaskStatus getStatus();

    /** 业务关联 ID（如 repositoryId / projectSpaceId），可为 null。 */
    Long getBusinessId();

    /** 百分比进度（0-100），-1 表示不支持或未知。 */
    int getProgress();

    /** 文本状态描述（如"正在克隆仓库..."）。 */
    String getStatusMessage();

    /** 提交时间。 */
    Date getQueuedAt();

    /** 开始执行时间，未开始为 null。 */
    Date getStartedAt();

    /** 结束时间，未结束为 null。 */
    Date getFinishedAt();

    /** 错误消息，仅 FAILED 状态有值。 */
    String getErrorMessage();

    /** 请求取消。若任务尚未运行则直接标记 CANCELLED；若正在运行则发送中断信号。 */
    void cancel();

    /** 任务是否已进入终态（SUCCEEDED / FAILED / CANCELLED）。 */
    boolean isTerminal();
}
