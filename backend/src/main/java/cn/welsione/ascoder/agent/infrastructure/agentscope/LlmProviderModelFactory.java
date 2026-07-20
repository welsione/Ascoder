package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.LlmProviderService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import io.agentscope.core.model.AnthropicChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 数据库驱动的 ChatModel 工厂，从 LlmProvider 表读取供应商配置创建模型。
 *
 * <p>实现 {@link ChatModelFactory} 端口接口，按 providerType 从策略列表选择构建策略，
 * 支持多供应商动态切换。Agent 行为参数（toolTimeoutSeconds、maxIters 等）从 application.yml 注入。</p>
 *
 * <p>当 {@code ascoder.llm-provider=database} 时装配（默认值），与 {@link AgentScopeModelFactory} 互斥。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ascoder.llm-provider", havingValue = "database", matchIfMissing = true)
public class LlmProviderModelFactory implements ChatModelFactory {

    private final LlmProviderService llmProviderService;
    private final List<ChatModelBuilderStrategy> builderStrategies;
    private final List<ConnectionTestStrategy> connectionTestStrategies;
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

    public LlmProviderModelFactory(
            LlmProviderService llmProviderService,
            List<ChatModelBuilderStrategy> builderStrategies,
            List<ConnectionTestStrategy> connectionTestStrategies,
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
        this.llmProviderService = llmProviderService;
        this.builderStrategies = builderStrategies;
        this.connectionTestStrategies = connectionTestStrategies;
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
    public AnthropicChatModel createModel(AgentConfig config) {
        ResolvedModelConfig resolved = resolveProvider(config);
        return selectBuilderStrategy(resolved.getProviderType().name()).build(resolved);
    }

    @Override
    public AnthropicChatModel createDefaultModel() {
        ResolvedModelConfig resolved = resolveDefaultProvider();
        return selectBuilderStrategy(resolved.getProviderType().name()).build(resolved);
    }

    @Override
    public ResolvedModelConfig resolveProvider(AgentConfig config) {
        LlmProvider provider;
        if (config.getLlmProviderId() != null) {
            provider = llmProviderService.getDecrypted(config.getLlmProviderId());
        } else {
            provider = llmProviderService.getDefault();
        }
        return merge(provider, config);
    }

    /**
     * 使用指定供应商配置执行连接测试。
     */
    public ConnectionTestResult testConnection(ResolvedModelConfig config) {
        return selectConnectionTestStrategy(config.getProviderType().name()).test(config);
    }

    public Duration timeout() {
        return timeout;
    }

    public Duration toolTimeout() {
        return toolTimeout;
    }

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

    public int modelMaxAttempts() {
        return modelMaxAttempts;
    }

    public int toolMaxAttempts() {
        return toolMaxAttempts;
    }

    public boolean planningEnabled() {
        return planningEnabled;
    }

    public int planMaxSubtasks() {
        return planMaxSubtasks;
    }

    private ResolvedModelConfig resolveDefaultProvider() {
        LlmProvider provider = llmProviderService.getDefault();
        return merge(provider, null);
    }

    /**
     * 将 LlmProvider 配置与 AgentConfig 覆盖合并为不可变快照。
     *
     * <p>AgentConfig 的 modelId / maxTokens / timeoutSeconds 可覆盖供应商级默认值。</p>
     */
    private ResolvedModelConfig merge(LlmProvider provider, AgentConfig config) {
        String modelId = (config != null && config.getModelId() != null) ? config.getModelId() : provider.getModelId();
        Integer maxTokens = (config != null && config.getMaxTokens() != null) ? config.getMaxTokens() : provider.getMaxTokens();
        Long timeoutSeconds = (config != null && config.getTimeoutSeconds() != null)
                ? config.getTimeoutSeconds().longValue()
                : provider.getTimeoutSeconds();
        return new ResolvedModelConfig(
                provider.getApiKey(),
                provider.getBaseUrl(),
                modelId,
                maxTokens,
                timeoutSeconds,
                provider.getProviderType()
        );
    }

    private ChatModelBuilderStrategy selectBuilderStrategy(String providerType) {
        return builderStrategies.stream()
                .filter(s -> s.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new InvalidStateException("不支持的供应商协议类型: " + providerType));
    }

    private ConnectionTestStrategy selectConnectionTestStrategy(String providerType) {
        return connectionTestStrategies.stream()
                .filter(s -> s.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new InvalidStateException("不支持的供应商协议类型: " + providerType));
    }
}
