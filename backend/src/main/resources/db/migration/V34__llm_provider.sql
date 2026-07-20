-- LLM 供应商配置表
CREATE TABLE llmProvider (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    providerType    VARCHAR(30)  NOT NULL DEFAULT 'ANTHROPIC_COMPATIBLE',
    apiKey          VARCHAR(500) NOT NULL,
    baseUrl         VARCHAR(500) NOT NULL,
    modelId         VARCHAR(100) NOT NULL,
    maxTokens       INT,
    timeoutSeconds  BIGINT,
    isDefault       BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    builtin         BOOLEAN      NOT NULL DEFAULT FALSE,
    sortOrder       INT          NOT NULL DEFAULT 0,
    createdAt       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_llmProvider_name UNIQUE (name)
);

-- AgentConfig 关联 LLM 供应商
ALTER TABLE agentConfigs ADD COLUMN llmProviderId BIGINT DEFAULT NULL;
ALTER TABLE agentConfigs ADD CONSTRAINT fk_agentConfigs_llmProvider
    FOREIGN KEY (llmProviderId) REFERENCES llmProvider(id);
