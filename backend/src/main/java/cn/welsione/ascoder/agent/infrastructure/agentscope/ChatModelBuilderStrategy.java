package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import io.agentscope.core.model.AnthropicChatModel;

/**
 * ChatModel 构建策略接口，按供应商协议类型创建不同类型的 ChatModel。
 *
 * <p>每个 providerType 对应一个策略实现，新增供应商类型只需加策略实现，符合 OCP。</p>
 */
public interface ChatModelBuilderStrategy {

    /**
     * 判断此策略是否支持指定的供应商协议类型。
     */
    boolean supports(String providerType);

    /**
     * 按配置快照构建 ChatModel。
     */
    AnthropicChatModel build(ResolvedModelConfig config);
}
