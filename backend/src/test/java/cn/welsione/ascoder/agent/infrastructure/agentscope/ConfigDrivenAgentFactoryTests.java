package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ConfigDrivenAgentFactory role 分支构建测试，验证 ORCHESTRATOR / SPECIALIST 两种角色均能构建 HarnessAgent，
 * 且 chatModelFactory.createModel 被调用。
 */
@ExtendWith(MockitoExtension.class)
class ConfigDrivenAgentFactoryTests {

    @Mock private ChatModelFactory chatModelFactory;

    @InjectMocks
    private ConfigDrivenAgentFactory factory;

    @Test
    void buildOrchestratorSucceeds() {
        when(chatModelFactory.createModel(any(AgentConfig.class))).thenReturn(mockModel());
        when(chatModelFactory.timeout()).thenReturn(Duration.ofSeconds(60));
        when(chatModelFactory.toolTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatModelFactory.modelMaxAttempts()).thenReturn(2);
        when(chatModelFactory.toolMaxAttempts()).thenReturn(1);

        HarnessAgent agent = factory.build(orchestratorConfig(), request(), tooling());

        assertNotNull(agent);
        verify(chatModelFactory).createModel(any(AgentConfig.class));
    }

    @Test
    void buildSpecialistSucceeds() {
        when(chatModelFactory.createModel(any(AgentConfig.class))).thenReturn(mockModel());
        when(chatModelFactory.timeout()).thenReturn(Duration.ofSeconds(60));
        when(chatModelFactory.toolTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatModelFactory.modelMaxAttempts()).thenReturn(2);
        when(chatModelFactory.toolMaxAttempts()).thenReturn(1);

        HarnessAgent agent = factory.build(specialistConfig(), request(), tooling());

        assertNotNull(agent);
        verify(chatModelFactory).createModel(any(AgentConfig.class));
    }

    @Test
    void configTimeoutOverridesGlobal() {
        when(chatModelFactory.createModel(any(AgentConfig.class))).thenReturn(mockModel());
        when(chatModelFactory.toolTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatModelFactory.modelMaxAttempts()).thenReturn(2);
        when(chatModelFactory.toolMaxAttempts()).thenReturn(1);

        AgentConfig config = specialistConfig();
        config.setTimeoutSeconds(120);

        assertDoesNotThrow(() -> factory.build(config, request(), tooling()));
        // config 提供了 timeoutSeconds，应使用它而非全局 timeout()
        verify(chatModelFactory, never()).timeout();
    }

    @Test
    void buildPreservesBuiltinAgentParameters() {
        // 验证内置 code-researcher（SPECIALIST）配置能正常构建，maxIters=100 传入
        when(chatModelFactory.createModel(any(AgentConfig.class))).thenReturn(mockModel());
        when(chatModelFactory.timeout()).thenReturn(Duration.ofSeconds(60));
        when(chatModelFactory.toolTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatModelFactory.modelMaxAttempts()).thenReturn(2);
        when(chatModelFactory.toolMaxAttempts()).thenReturn(1);

        AgentConfig researcher = new AgentConfig();
        researcher.setAgentId("code-researcher");
        researcher.setAgentRole(AgentRole.SPECIALIST);
        researcher.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        researcher.setSystemPrompt("sys");
        researcher.setMaxIters(100);

        assertDoesNotThrow(() -> factory.build(researcher, request(), tooling()));
    }

    @Test
    void buildSpecialistSetsWorkspace() {
        when(chatModelFactory.createModel(any(AgentConfig.class))).thenReturn(mockModel());
        when(chatModelFactory.timeout()).thenReturn(Duration.ofSeconds(60));
        when(chatModelFactory.toolTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatModelFactory.modelMaxAttempts()).thenReturn(2);
        when(chatModelFactory.toolMaxAttempts()).thenReturn(1);

        HarnessAgent agent = factory.build(specialistConfig(), request(), tooling());

        assertNotNull(agent.getWorkspaceManager());
        assertNotNull(agent.getWorkspaceManager().getWorkspace());
        assertTrue(agent.getWorkspaceManager().getWorkspace().endsWith(".ascoder-specialist"),
                "SPECIALIST workspace 应以 .ascoder-specialist 结尾，实际: " + agent.getWorkspaceManager().getWorkspace());
    }

    @Test
    void buildOrchestratorWorkspaceUnchanged() {
        when(chatModelFactory.createModel(any(AgentConfig.class))).thenReturn(mockModel());
        when(chatModelFactory.timeout()).thenReturn(Duration.ofSeconds(60));
        when(chatModelFactory.toolTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatModelFactory.modelMaxAttempts()).thenReturn(2);
        when(chatModelFactory.toolMaxAttempts()).thenReturn(1);

        HarnessAgent agent = factory.build(orchestratorConfig(), request(), tooling());

        assertNotNull(agent.getWorkspaceManager());
        assertNotNull(agent.getWorkspaceManager().getWorkspace());
        assertTrue(agent.getWorkspaceManager().getWorkspace().endsWith(".ascoder-harness"),
                "ORCHESTRATOR workspace 应以 .ascoder-harness 结尾，实际: " + agent.getWorkspaceManager().getWorkspace());
    }

    private AnthropicChatModel mockModel() {
        return mock(AnthropicChatModel.class);
    }

    private AgentConfig orchestratorConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("orchestrator");
        config.setAgentRole(AgentRole.ORCHESTRATOR);
        config.setSystemPrompt("sys");
        config.setMaxIters(12);
        return config;
    }

    private AgentConfig specialistConfig() {
        AgentConfig config = new AgentConfig();
        config.setAgentId("code-researcher");
        config.setAgentRole(AgentRole.SPECIALIST);
        config.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        config.setSystemPrompt("sys");
        config.setMaxIters(100);
        return config;
    }

    private AgentRequest request() {
        return new AgentRequest(
                1L, null, 100L, "demo-space", "/tmp/demo-space", "/tmp/demo-space/.codegraph",
                List.of(), "问题", "developer", null, null, null, null, null);
    }

    private AgentTooling tooling() {
        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);
        return new AgentTooling(toolkit, skillBox, List.of());
    }
}
