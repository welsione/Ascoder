package cn.welsione.ascoder.common.task;

import java.util.Map;

/**
 * 任务进度回调，由引擎注入给 {@link TaskDefinition#execute}。
 *
 * <p>实现方在执行过程中调用此接口报告进度，引擎负责持久化和推送。</p>
 */
public interface TaskProgress {

    /** 更新百分比进度（0-100）。 */
    void percent(int value);

    /** 更新文本状态描述。 */
    void message(String message);

    /** 更新百分比 + 文本状态。 */
    default void update(int percentValue, String messageText) {
        percent(percentValue);
        message(messageText);
    }

    /**
     * 推送自定义事件到 SSE 流（用于 Question 的 reasoning/tool_call 等细粒度事件）。
     *
     * <p>非 Question 类型的任务可忽略此方法。引擎对不订阅 SSE 的任务类型为空操作。</p>
     */
    void pushEvent(String eventName, Map<String, Object> payload);

    /** 检查任务是否已被取消，实现方应在长操作中定期检查。 */
    boolean isCancelled();

    /**
     * 如果已取消则抛出 {@link TaskCancelledException}。
     * 实现方在关键检查点调用此方法，替代手动 if-check。
     */
    default void checkCancelled() {
        if (isCancelled()) {
            throw new TaskCancelledException("任务已取消");
        }
    }
}
