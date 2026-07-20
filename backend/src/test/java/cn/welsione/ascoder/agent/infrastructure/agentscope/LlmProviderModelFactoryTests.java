package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.LlmProviderService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.ConnectionTestResult;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.ResolvedModelConfig;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import io.agentscope.core.model.AnthropicChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * LlmProviderModelFactory 核心场景测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmProviderModelFactoryTests {

    @Mock
    private LlmProviderService llmProviderService;

    @Mock
    private ChatModelBuilderStrategy anthropicStrategy;

    @Mock
    private ConnectionTestStrategy connectionTestStrategy;

    @Mock
    private AnthropicChatModel mockModel;

    private LlmProvider defaultProvider() {
        LlmProvider provider = new LlmProvider();
        provider.setId(1L);
        provider.setName("Test Provider");
        provider.setProviderType(LlmProviderType.ANTHROPIC_COMPATIBLE);
        provider.setApiKey("test-api-key");
        provider.setBaseUrl("https://api.example.com/anthropic");
        provider.setModelId("test-model");
        provider.setMaxTokens(4000);
        provider.setTimeoutSeconds(240L);
        provider.setDefault(true);
        provider.setEnabled(true);
        return provider;
    }

    private LlmProviderModelFactory factoryWithAnthropic() {
        when(anthropicStrategy.supports(LlmProviderType.ANTHROPIC_COMPATIBLE.name())).thenReturn(true);
        return new LlmProviderModelFactory(
                llmProviderService,
                List.of(anthropicStrategy),
                List.of(connectionTestStrategy),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10
        );
    }

    @Test
    void createModelWithDefaultProvider() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);
        AgentConfig config = new AgentConfig();

        AnthropicChatModel model = factory.createModel(config);

        assertSame(mockModel, model);
        verify(anthropicStrategy).build(argThat(resolved ->
                resolved.getApiKey().equals("test-api-key")
                        && resolved.getBaseUrl().equals("https://api.example.com/anthropic")
                        && resolved.getModelId().equals("test-model")
                        && resolved.getMaxTokens().equals(4000)
                        && resolved.getProviderType() == LlmProviderType.ANTHROPIC_COMPATIBLE
        ));
    }

    @Test
    void createModelWithSpecificProvider() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        LlmProvider provider = defaultProvider();
        provider.setId(2L);
        provider.setName("Claude");
        provider.setModelId("claude-3-5-sonnet");
        when(llmProviderService.getDecrypted(2L)).thenReturn(provider);
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);
        AgentConfig config = new AgentConfig();
        config.setLlmProviderId(2L);

        AnthropicChatModel model = factory.createModel(config);

        assertSame(mockModel, model);
        verify(llmProviderService).getDecrypted(2L);
        verify(llmProviderService, never()).getDefault();
        verify(anthropicStrategy).build(argThat(resolved ->
                resolved.getModelId().equals("claude-3-5-sonnet")
        ));
    }

    @Test
    void createDefaultModel() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);

        AnthropicChatModel model = factory.createDefaultModel();

        assertSame(mockModel, model);
        verify(llmProviderService).getDefault();
    }

    @Test
    void createDefaultModelNoProviderThrows() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenThrow(new InvalidStateException("未配置默认 LLM 供应商"));

        assertThrows(InvalidStateException.class, () -> factory.createDefaultModel());
    }

    @Test
    void agentConfigOverridesProviderModelId() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);
        AgentConfig config = new AgentConfig();
        config.setModelId("override-model");

        factory.createModel(config);

        verify(anthropicStrategy).build(argThat(resolved ->
                resolved.getModelId().equals("override-model")
        ));
    }

    @Test
    void agentConfigOverridesProviderMaxTokens() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);
        AgentConfig config = new AgentConfig();
        config.setMaxTokens(8000);

        factory.createModel(config);

        verify(anthropicStrategy).build(argThat(resolved ->
                resolved.getMaxTokens().equals(8000)
        ));
    }

    @Test
    void agentConfigOverridesProviderTimeoutSeconds() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);
        AgentConfig config = new AgentConfig();
        config.setTimeoutSeconds(120);

        factory.createModel(config);

        verify(anthropicStrategy).build(argThat(resolved ->
                resolved.getTimeoutSeconds().equals(120L)
        ));
    }

    @Test
    void agentConfigNullFieldsFallbackToProvider() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        when(anthropicStrategy.build(any(ResolvedModelConfig.class))).thenReturn(mockModel);
        AgentConfig config = new AgentConfig();

        factory.createModel(config);

        verify(anthropicStrategy).build(argThat(resolved ->
                resolved.getModelId().equals("test-model")
                        && resolved.getMaxTokens().equals(4000)
                        && resolved.getTimeoutSeconds().equals(240L)
        ));
    }

    @Test
    void unsupportedProviderTypeThrows() {
        when(anthropicStrategy.supports(LlmProviderType.OPENAI_COMPATIBLE.name())).thenReturn(false);
        LlmProviderModelFactory factory = new LlmProviderModelFactory(
                llmProviderService,
                List.of(anthropicStrategy),
                List.of(connectionTestStrategy),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10
        );
        LlmProvider provider = defaultProvider();
        provider.setProviderType(LlmProviderType.OPENAI_COMPATIBLE);
        when(llmProviderService.getDefault()).thenReturn(provider);
        AgentConfig config = new AgentConfig();

        assertThrows(InvalidStateException.class, () -> factory.createModel(config));
    }

    @Test
    void resolveProviderReturnsCorrectSnapshot() {
        LlmProviderModelFactory factory = factoryWithAnthropic();
        when(llmProviderService.getDefault()).thenReturn(defaultProvider());
        AgentConfig config = new AgentConfig();
        config.setModelId("custom-model");

        ResolvedModelConfig resolved = factory.resolveProvider(config);

        assertEquals("test-api-key", resolved.getApiKey());
        assertEquals("https://api.example.com/anthropic", resolved.getBaseUrl());
        assertEquals("custom-model", resolved.getModelId());
        assertEquals(LlmProviderType.ANTHROPIC_COMPATIBLE, resolved.getProviderType());
    }

    @Test
    void testConnectionDelegatesToStrategy() {
        when(connectionTestStrategy.supports(LlmProviderType.ANTHROPIC_COMPATIBLE.name())).thenReturn(true);
        LlmProviderModelFactory factory = factoryWithAnthropic();
        ConnectionTestResult expectedResult = new ConnectionTestResult(true, "连接成功", 150L);
        when(connectionTestStrategy.test(any(ResolvedModelConfig.class))).thenReturn(expectedResult);

        ResolvedModelConfig config = new ResolvedModelConfig(
                "key", "https://api.example.com/anthropic", "model", 4000, 240L, LlmProviderType.ANTHROPIC_COMPATIBLE);
        ConnectionTestResult result = factory.testConnection(config);

        assertTrue(result.isSuccess());
        assertEquals("连接成功", result.getMessage());
        assertEquals(150L, result.getLatencyMs());
    }

    @Test
    void timeoutReturnsConfiguredValue() {
        LlmProviderModelFactory factory = new LlmProviderModelFactory(
                llmProviderService, Collections.emptyList(), Collections.emptyList(),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
        assertEquals(Duration.ofSeconds(240), factory.timeout());
    }

    @Test
    void toolTimeoutReturnsConfiguredValue() {
        LlmProviderModelFactory factory = new LlmProviderModelFactory(
                llmProviderService, Collections.emptyList(), Collections.emptyList(),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
        assertEquals(Duration.ofSeconds(300), factory.toolTimeout());
    }

    @Test
    void maxItersReturnsConfiguredValue() {
        LlmProviderModelFactory factory = new LlmProviderModelFactory(
                llmProviderService, Collections.emptyList(), Collections.emptyList(),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
        assertEquals(12, factory.maxIters());
    }

    @Test
    void modelMaxAttemptsReturnsConfiguredValue() {
        LlmProviderModelFactory factory = new LlmProviderModelFactory(
                llmProviderService, Collections.emptyList(), Collections.emptyList(),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
        assertEquals(2, factory.modelMaxAttempts());
    }

    @Test
    void toolMaxAttemptsReturnsConfiguredValue() {
        LlmProviderModelFactory factory = new LlmProviderModelFactory(
                llmProviderService, Collections.emptyList(), Collections.emptyList(),
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
        assertEquals(1, factory.toolMaxAttempts());
    }
}
