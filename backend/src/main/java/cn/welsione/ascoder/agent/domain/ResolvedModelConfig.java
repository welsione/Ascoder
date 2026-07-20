package cn.welsione.ascoder.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 运行时绑定的供应商配置快照，不可变。
 *
 * <p>Agent 创建 ChatModel 时，将 LlmProvider 的关键参数深拷贝为本值对象，
 * 传递给 Model 构建器。Agent 运行期间持有快照，不受后续配置变更影响。</p>
 */
@Getter
@AllArgsConstructor
public class ResolvedModelConfig {

    private final String apiKey;
    private final String baseUrl;
    private final String modelId;
    private final Integer maxTokens;
    private final Long timeoutSeconds;
    private final LlmProviderType providerType;
}
