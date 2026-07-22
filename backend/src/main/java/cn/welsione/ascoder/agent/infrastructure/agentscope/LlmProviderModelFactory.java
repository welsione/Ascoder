package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.LlmProviderService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import io.agentscope.core.model.AnthropicChatModel;
import lombok.extern.slf4j.Slf4j;
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
    private final RuntimeSettingsService runtimeSettings;

    public LlmProviderModelFactory(
            LlmProviderService llmProviderService,
            List<ChatModelBuilderStrategy> builderStrategies,
            List<ConnectionTestStrategy> connectionTestStrategies,
            RuntimeSettingsService runtimeSettings) {
        this.llmProviderService = llmProviderService;
        this.builderStrategies = builderStrategies;
        this.connectionTestStrategies = connectionTestStrategies;
        this.runtimeSettings = runtimeSettings;
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
        // agent.timeout-seconds 未列入白名单，统一使用 tool-timeout-seconds 作为兜底
        long s = runtimeSettings.readInt("agent.tool-timeout-seconds");
        return Duration.ofSeconds(s);
    }

    public Duration toolTimeout() {
        long s = runtimeSettings.readInt("agent.tool-timeout-seconds");
        return Duration.ofSeconds(s);
    }

    public int maxIters() {
        return runtimeSettings.readInt("agent.max-iters");
    }

    public int codeResearcherMaxIters() {
        return runtimeSettings.readInt("agent.code-researcher-max-iters");
    }

    public int impactAnalyzerMaxIters() {
        return runtimeSettings.readInt("agent.impact-analyzer-max-iters");
    }

    public int roleSpecialistMaxIters() {
        return runtimeSettings.readInt("agent.role-specialist-max-iters");
    }

    public int modelMaxAttempts() {
        return runtimeSettings.readInt("agent.model-max-attempts");
    }

    public int toolMaxAttempts() {
        return runtimeSettings.readInt("agent.tool-max-attempts");
    }

    public boolean planningEnabled() {
        return runtimeSettings.readBoolean("agent.planning-enabled");
    }

    public int planMaxSubtasks() {
        return runtimeSettings.readInt("agent.plan-max-subtasks");
    }

    private ResolvedModelConfig resolveDefaultProvider() {
        LlmProvider provider = llmProviderService.getDefault();
        return merge(provider, null);
    }

    /**
     * 将 LlmProvider 配置与 AgentConfig 覆盖合并为不可变快照。
     *
     * <p>AgentConfig 的 modelId / maxTokens / timeoutSeconds 可覆盖供应商级默认值。
     * timeoutSeconds 和 maxTokens 在数据库中允许为 null，合并时按优先级回退：</p>
     * <ol>
     *   <li>AgentConfig 显式覆盖</li>
     *   <li>LlmProvider 设置值</li>
     *   <li>RuntimeSettings 中的 agent.tool-timeout-seconds（超时）或 null（maxTokens）</li>
     * </ol>
     */
    private ResolvedModelConfig merge(LlmProvider provider, AgentConfig config) {
        String modelId = (config != null && config.getModelId() != null) ? config.getModelId() : provider.getModelId();
        Integer maxTokens = (config != null && config.getMaxTokens() != null) ? config.getMaxTokens() : provider.getMaxTokens();
        Long timeoutSeconds = resolveTimeoutSeconds(provider, config);
        return new ResolvedModelConfig(
                provider.getApiKey(),
                provider.getBaseUrl(),
                modelId,
                maxTokens,
                timeoutSeconds,
                provider.getProviderType()
        );
    }

    /**
     * 解析超时秒数，按优先级回退：AgentConfig → LlmProvider → RuntimeSettings 默认值。
     */
    private Long resolveTimeoutSeconds(LlmProvider provider, AgentConfig config) {
        if (config != null && config.getTimeoutSeconds() != null) {
            return config.getTimeoutSeconds().longValue();
        }
        if (provider.getTimeoutSeconds() != null) {
            return provider.getTimeoutSeconds();
        }
        return runtimeSettings.readLong("agent.tool-timeout-seconds");
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
