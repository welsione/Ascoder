package cn.welsione.ascoder.common.task;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 任务上下文序列化/反序列化工具，消除各 {@link TaskDefinition} 中重复的
 * try-catch-IllegalStateException 样板。
 *
 * <p>所有 TaskDefinition 的上下文均为可 JSON 序列化的类型化对象，序列化逻辑完全一致，
 * 仅上下文类型和错误消息不同。通过此工具类统一异常包装，各 TaskDefinition 只需一行调用。</p>
 */
public final class TaskContextSerializer {

    private TaskContextSerializer() {
    }

    /**
     * 序列化上下文为 JSON 字符串。
     *
     * @param objectMapper Jackson ObjectMapper
     * @param context       任务上下文
     * @param taskName      任务名称（用于错误消息，如 "Git fetch"）
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败
     */
    public static <C> String serialize(ObjectMapper objectMapper, C context, String taskName) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            throw new IllegalStateException("序列化" + taskName + "任务上下文失败", e);
        }
    }

    /**
     * 反序列化 JSON 为上下文对象。
     *
     * @param objectMapper Jackson ObjectMapper
     * @param json          JSON 字符串
     * @param contextClass  上下文类型
     * @param taskName      任务名称（用于错误消息）
     * @return 反序列化的上下文对象
     * @throws IllegalStateException 反序列化失败
     */
    public static <C> C deserialize(ObjectMapper objectMapper, String json, Class<C> contextClass, String taskName) {
        try {
            return objectMapper.readValue(json, contextClass);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化" + taskName + "任务上下文失败", e);
        }
    }
}
