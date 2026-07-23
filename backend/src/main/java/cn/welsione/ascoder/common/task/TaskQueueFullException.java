package cn.welsione.ascoder.common.task;

import cn.welsione.ascoder.common.exception.DomainException;

/**
 * 任务队列已满异常，对应 HTTP 429 Too Many Requests。
 */
public class TaskQueueFullException extends DomainException {

    public TaskQueueFullException(TaskKind kind) {
        super("TASK_QUEUE_FULL", kind.name() + " 任务队列已满，请稍后重试");
    }
}
