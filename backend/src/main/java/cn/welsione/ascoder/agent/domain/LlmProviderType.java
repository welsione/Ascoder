package cn.welsione.ascoder.agent.domain;

/**
 * LLM 供应商协议类型，决定模型创建和连接测试的策略。
 *
 * <ul>
 *   <li>{@link #ANTHROPIC_COMPATIBLE}：Anthropic 兼容协议（MiniMax、Claude 等）。</li>
 *   <li>{@link #OPENAI_COMPATIBLE}：OpenAI 兼容协议（DeepSeek、通义千问等），一期为占位实现。</li>
 * </ul>
 */
public enum LlmProviderType {
    ANTHROPIC_COMPATIBLE,
    OPENAI_COMPATIBLE
}
