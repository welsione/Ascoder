package cn.welsione.ascoder.common.task;

import cn.welsione.ascoder.common.exception.DomainException;

/**
 * 任务重复提交异常（同一 businessId + kind 已有运行中任务）。
 */
public class TaskAlreadyRunningException extends DomainException {

    public TaskAlreadyRunningException(TaskKind kind, Long businessId) {
        super("TASK_ALREADY_RUNNING", kind.name() + " 任务已在运行中，businessId=" + businessId);
    }
}
