package cn.welsione.ascoder.common.task;

/**
 * 任务取消异常，由引擎映射为 CANCELLED 状态而非 FAILED。
 */
public class TaskCancelledException extends RuntimeException {

    public TaskCancelledException(String message) {
        super(message);
    }

    public TaskCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
