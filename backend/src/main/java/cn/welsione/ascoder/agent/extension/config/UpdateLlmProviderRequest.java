package cn.welsione.ascoder.agent.extension.config;

import lombok.Data;

/**
 * 更新 LLM 供应商请求。apiKey 为空时保留原值。
 */
@Data
public class UpdateLlmProviderRequest {
    private String name;
    private String providerType;
    private String apiKey;
    private String baseUrl;
    private String modelId;
    private Integer maxTokens;
    private Long timeoutSeconds;
    private boolean isDefault;
    private boolean enabled;
}
