package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import io.agentscope.core.model.AnthropicChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentScopeModelFactory.createModel(AgentConfig) NULL 回退与覆盖测试。
 */
class AgentScopeModelFactoryTests {

    private AgentScopeModelFactory factory() {
        return new AgentScopeModelFactory(
                "global-model", "key", "url", 4000,
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
    }

    @Test
    void nullConfigFieldsFallbackToGlobal() {
        AgentScopeModelFactory factory = factory();
        AgentConfig config = new AgentConfig();

        AnthropicChatModel model = factory.createModel(config);

        assertNotNull(model);
        assertEquals("global-model", model.getModelName());
    }

    @Test
    void configModelIdOverridesGlobal() {
        AgentScopeModelFactory factory = factory();
        AgentConfig config = new AgentConfig();
        config.setModelId("override-model");

        AnthropicChatModel model = factory.createModel(config);

        assertEquals("override-model", model.getModelName());
    }

    @Test
    void allConfigFieldsOverrideGlobal() {
        AgentScopeModelFactory factory = factory();
        AgentConfig config = new AgentConfig();
        config.setModelId("full-override");
        config.setMaxTokens(8000);

        AnthropicChatModel model = factory.createModel(config);

        assertEquals("full-override", model.getModelName());
    }

    @Test
    void missingApiKeyThrows() {
        AgentScopeModelFactory factory = new AgentScopeModelFactory(
                "global-model", "", "url", 4000,
                240L, 300L, 12, 100, 8, 8, 2, 1, true, 10);
        AgentConfig config = new AgentConfig();

        assertThrows(IllegalStateException.class, () -> factory.createModel(config));
    }

    @Test
    void createAnthropicCompatibleModelUnchanged() {
        AgentScopeModelFactory factory = factory();

        AnthropicChatModel model = factory.createAnthropicCompatibleModel();

        assertEquals("global-model", model.getModelName());
    }
}
