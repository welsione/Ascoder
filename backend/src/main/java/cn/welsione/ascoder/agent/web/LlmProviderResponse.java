package cn.welsione.ascoder.agent.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * LLM 供应商响应 DTO，apiKey 为脱敏格式。
 */
@Data
public class LlmProviderResponse {
    private Long id;
    private String name;
    private String providerType;
    private String apiKey;
    private String baseUrl;
    private String modelId;
    private Integer maxTokens;
    private Long timeoutSeconds;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private boolean enabled;
    private boolean builtin;
    private int sortOrder;
    private String createdAt;
    private String updatedAt;
}
