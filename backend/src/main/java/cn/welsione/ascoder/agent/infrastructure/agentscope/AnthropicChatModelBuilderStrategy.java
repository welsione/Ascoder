package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.GenerateOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Anthropic 兼容协议的 ChatModel 构建策略。
 *
 * <p>支持 MiniMax、Claude 等使用 Anthropic 兼容 API 的供应商。</p>
 */
@Slf4j
@Component
public class AnthropicChatModelBuilderStrategy implements ChatModelBuilderStrategy {

    @Override
    public boolean supports(String providerType) {
        return LlmProviderType.ANTHROPIC_COMPATIBLE.name().equals(providerType);
    }

    @Override
    public AnthropicChatModel build(ResolvedModelConfig config) {
        log.debug("创建 Anthropic 兼容模型，modelId={}，baseUrl={}", config.getModelId(), config.getBaseUrl());
        return AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelId())
                .stream(true)
                .defaultOptions(GenerateOptions.builder()
                        .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : 4000)
                        .build())
                .build();
    }
}
