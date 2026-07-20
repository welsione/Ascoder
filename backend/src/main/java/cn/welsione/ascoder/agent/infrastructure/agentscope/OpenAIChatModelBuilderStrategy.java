package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import io.agentscope.core.model.AnthropicChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议的 ChatModel 构建策略（一期占位实现）。
 *
 * <p>待实际接入时引入 OpenAI SDK 或通用 HTTP 客户端实现。</p>
 */
@Slf4j
@Component
public class OpenAIChatModelBuilderStrategy implements ChatModelBuilderStrategy {

    @Override
    public boolean supports(String providerType) {
        return LlmProviderType.OPENAI_COMPATIBLE.name().equals(providerType);
    }

    @Override
    public AnthropicChatModel build(ResolvedModelConfig config) {
        throw new UnsupportedOperationException("OPENAI_COMPATIBLE 供应商暂不支持");
    }
}
