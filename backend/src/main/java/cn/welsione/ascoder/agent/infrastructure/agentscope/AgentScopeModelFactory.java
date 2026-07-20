package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.GenerateOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AgentScope 模型工厂，创建 Anthropic 兼容的 ChatModel 实例。
 *
 * <p>实现 {@link ChatModelFactory} 端口接口，Agent 类依赖接口而非此类，
 * 符合依赖倒置原则。</p>
 *
 * @deprecated 请使用 {@link LlmProviderModelFactory}，从数据库读取供应商配置。
 *             此保留类仅在 {@code ascoder.llm-provider=agentscope} 时激活，用于向后兼容。
 */
@Deprecated
@Slf4j
@Component
@ConditionalOnProperty(name = "ascoder.llm-provider", havingValue = "agentscope")
public class AgentScopeModelFactory implements ChatModelFactory {

    private final String modelId;
    private final String apiKey;
    private final String baseUrl;
    private final int maxTokens;
    private final Duration timeout;
    private final Duration toolTimeout;
    private final int maxIters;
    private final int codeResearcherMaxIters;
    private final int impactAnalyzerMaxIters;
    private final int roleSpecialistMaxIters;
    private final int modelMaxAttempts;
    private final int toolMaxAttempts;
    private final boolean planningEnabled;
    private final int planMaxSubtasks;

    public AgentScopeModelFactory(
            @Value("${ascoder.agent.model-id}") String modelId,
            @Value("${ascoder.agent.api-key}") String apiKey,
            @Value("${ascoder.agent.base-url}") String baseUrl,
            @Value("${ascoder.agent.max-tokens}") int maxTokens,
            @Value("${ascoder.agent.timeout-seconds}") long timeoutSeconds,
            @Value("${ascoder.agent.tool-timeout-seconds:${ascoder.agent.timeout-seconds}}") long toolTimeoutSeconds,
            @Value("${ascoder.agent.max-iters}") int maxIters,
            @Value("${ascoder.agent.code-researcher-max-iters}") int codeResearcherMaxIters,
            @Value("${ascoder.agent.impact-analyzer-max-iters}") int impactAnalyzerMaxIters,
            @Value("${ascoder.agent.role-specialist-max-iters:8}") int roleSpecialistMaxIters,
            @Value("${ascoder.agent.model-max-attempts}") int modelMaxAttempts,
            @Value("${ascoder.agent.tool-max-attempts}") int toolMaxAttempts,
            @Value("${ascoder.agent.planning-enabled}") boolean planningEnabled,
            @Value("${ascoder.agent.plan-max-subtasks}") int planMaxSubtasks
    ) {
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.maxTokens = maxTokens;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.toolTimeout = Duration.ofSeconds(toolTimeoutSeconds);
        this.maxIters = maxIters;
        this.codeResearcherMaxIters = codeResearcherMaxIters;
        this.impactAnalyzerMaxIters = impactAnalyzerMaxIters;
        this.roleSpecialistMaxIters = roleSpecialistMaxIters;
        this.modelMaxAttempts = modelMaxAttempts;
        this.toolMaxAttempts = toolMaxAttempts;
        this.planningEnabled = planningEnabled;
        this.planMaxSubtasks = planMaxSubtasks;
    }

    @Override
    public AnthropicChatModel createDefaultModel() {
        return createAnthropicCompatibleModel();
    }

    /**
     * 创建 Anthropic 兼容的默认模型，使用全局配置参数。
     */
    public AnthropicChatModel createAnthropicCompatibleModel() {
        return createModel(modelId, maxTokens);
    }

    /**
     * 按 AgentConfig 的模型参数创建模型，任一字段为 NULL 时回退全局默认。
     *
     * @param config Agent 配置，提供 modelId / maxTokens 覆盖
     * @return Anthropic 兼容 ChatModel
     */
    @Override
    public AnthropicChatModel createModel(cn.welsione.ascoder.agent.domain.AgentConfig config) {
        String effectiveModelId = config.getModelId() != null ? config.getModelId() : modelId;
        int effectiveMaxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : maxTokens;
        return createModel(effectiveModelId, effectiveMaxTokens);
    }

    @Override
    public ResolvedModelConfig resolveProvider(cn.welsione.ascoder.agent.domain.AgentConfig config) {
        String effectiveModelId = config.getModelId() != null ? config.getModelId() : modelId;
        Integer effectiveMaxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : maxTokens;
        Long effectiveTimeout = config.getTimeoutSeconds() != null
                ? config.getTimeoutSeconds().longValue() : timeout.getSeconds();
        return new ResolvedModelConfig(apiKey, baseUrl, effectiveModelId, effectiveMaxTokens,
                effectiveTimeout, cn.welsione.ascoder.agent.domain.LlmProviderType.ANTHROPIC_COMPATIBLE);
    }

    private AnthropicChatModel createModel(String effectiveModelId, int effectiveMaxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("缺少 MiniMax API Key，请设置 MINIMAX_API_KEY");
            throw new IllegalStateException("缺少 MiniMax API Key，请设置 MINIMAX_API_KEY");
        }
        log.debug("创建 Anthropic 兼容模型，modelId={}，baseUrl={}", effectiveModelId, baseUrl);
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(effectiveModelId)
                .stream(true)
                .defaultOptions(GenerateOptions.builder()
                        .maxTokens(effectiveMaxTokens)
                        .build())
                .build();
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public Duration toolTimeout() {
        return toolTimeout;
    }

    @Override
    public int maxIters() {
        return maxIters;
    }

    public int codeResearcherMaxIters() {
        return codeResearcherMaxIters;
    }

    public int impactAnalyzerMaxIters() {
        return impactAnalyzerMaxIters;
    }

    public int roleSpecialistMaxIters() {
        return roleSpecialistMaxIters;
    }

    @Override
    public int modelMaxAttempts() {
        return modelMaxAttempts;
    }

    @Override
    public int toolMaxAttempts() {
        return toolMaxAttempts;
    }

    public boolean planningEnabled() {
        return planningEnabled;
    }

    public int planMaxSubtasks() {
        return planMaxSubtasks;
    }
}
