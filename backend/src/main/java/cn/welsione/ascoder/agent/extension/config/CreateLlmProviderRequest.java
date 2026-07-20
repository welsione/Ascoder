package cn.welsione.ascoder.agent.extension.config;

import lombok.Data;

/**
 * 创建 LLM 供应商请求。
 */
@Data
public class CreateLlmProviderRequest {
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
