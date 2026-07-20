package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议的连接测试策略（一期占位实现）。
 */
@Slf4j
@Component
public class OpenAIConnectionTestStrategy implements ConnectionTestStrategy {

    @Override
    public boolean supports(String providerType) {
        return LlmProviderType.OPENAI_COMPATIBLE.name().equals(providerType);
    }

    @Override
    public ConnectionTestResult test(ResolvedModelConfig config) {
        throw new UnsupportedOperationException("OPENAI_COMPATIBLE 供应商暂不支持");
    }
}
