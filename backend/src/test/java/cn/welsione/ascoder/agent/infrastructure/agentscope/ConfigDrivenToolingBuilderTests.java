package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.extension.mcp.McpServerConfig;
import cn.welsione.ascoder.agent.extension.mcp.McpServerJpaRepository;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillConfig;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillJpaRepository;
import cn.welsione.ascoder.agent.extension.tool.AgentToolService;
import cn.welsione.ascoder.selflearning.SelfLearningAgentTools;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.common.FilePathSanitizer;
import cn.welsione.ascoder.common.SafeCommandRunner;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ConfigDrivenToolingBuilder.registerSelectedByGroup AND 逻辑测试。
 *
 * <p>用 mock Toolkit 验证工具组注册次数，确认"Agent 勾选 ∩ 全局启用"双条件生效。</p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigDrivenToolingBuilderTests {

    @Mock private Toolkit toolkit;
    @Mock private Toolkit.ToolRegistration registration;
    @Mock private CodeGraphClient codeGraphClient;
    @Mock private GitRepositoryService gitRepositoryService;
    @Mock private SafeCommandRunner safeCommandRunner;
    @Mock private AgentToolService toolService;
    @Mock private FilePathSanitizer filePathSanitizer;
    @Mock private SelfLearningAgentToolFactory selfLearningAgentToolFactory;
    @Mock private LogExploreToolRegistrar logExploreToolRegistrar;
    @Mock private AgentSkillJpaRepository skillRepository;
    @Mock private McpServerJpaRepository mcpServerRepository;
    @Mock private McpClientFactory mcpClientFactory;

    @InjectMocks
    private ConfigDrivenToolingBuilder builder;

    @BeforeEach
    void setUp() {
        lenient().when(toolkit.registration()).thenReturn(registration);
        lenient().when(registration.tool(any())).thenReturn(registration);
        lenient().when(selfLearningAgentToolFactory.createTools(anyLong())).thenReturn(mock(SelfLearningAgentTools.class));
    }

    @Test
    void andLogic_bothSelectedRegistersCodegraph() {
        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSelectedByGroup(toolkit, Set.of("codegraph"), Set.of("codegraph"), request(), new AtomicReference<>(""), warnings);

        verify(registration, times(1)).tool(any());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("git") && w.contains("被跳过")));
    }

    @Test
    void andLogic_globalDisabledSkips() {
        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSelectedByGroup(toolkit, Set.of("codegraph"), Set.of(), request(), new AtomicReference<>(""), warnings);

        verify(registration, never()).tool(any());
    }

    @Test
    void andLogic_agentNotSelectedSkips() {
        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSelectedByGroup(toolkit, Set.of(), Set.of("codegraph"), request(), new AtomicReference<>(""), warnings);

        verify(registration, never()).tool(any());
    }

    @Test
    void allGroupsSelectedRegistersAll() {
        Set<String> all = Set.of("codegraph", "git", "file", "text", "command", "self-learning", "log");
        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSelectedByGroup(toolkit, all, all, request(), new AtomicReference<>(""), warnings);

        // codegraph(1) + git(2) + file(1) + text(1) + command(1) + self-learning(1) = 7 次 tool()
        // log 走 logExploreToolRegistrar.register，不调 tool()
        verify(registration, times(7)).tool(any());
        verify(logExploreToolRegistrar).register(eq(toolkit), any(), eq(all));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void emptySelectionRegistersNothing() {
        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSelectedByGroup(toolkit, Set.of(), Set.of("codegraph", "git"), request(), new AtomicReference<>(""), warnings);

        verify(registration, never()).tool(any());
        verify(logExploreToolRegistrar, never()).register(any(), any(), any());
    }

    @Test
    void registerSkillsFiltersBySelectedNames() {
        SkillBox skillBox = mock(SkillBox.class);
        SkillBox.SkillRegistration skillReg = mock(SkillBox.SkillRegistration.class);
        when(skillBox.registration()).thenReturn(skillReg);
        when(skillReg.skill(any())).thenReturn(skillReg);
        when(skillRepository.findByEnabledTrueOrderByCreatedAtDesc()).thenReturn(List.of(skill("spring"), skill("vue")));

        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSkills(skillBox, List.of("spring"), warnings);

        verify(skillReg, times(1)).skill(any());
    }

    @Test
    void registerSkillsEmptyNamesSkips() {
        SkillBox skillBox = mock(SkillBox.class);

        List<String> warnings = new java.util.ArrayList<>();
        builder.registerSkills(skillBox, List.of(), warnings);

        verify(skillBox, never()).registration();
        verify(skillRepository, never()).findByEnabledTrueOrderByCreatedAtDesc();
    }

    @Test
    void registerMcpServersFiltersBySelectedNames() {
        McpServerConfig serverA = mcpServer("web");
        McpServerConfig serverB = mcpServer("db");
        when(mcpServerRepository.findByEnabledTrueOrderByCreatedAtDesc()).thenReturn(List.of(serverA, serverB));
        when(mcpClientFactory.createClient(any())).thenThrow(new RuntimeException("skip"));

        builder.registerMcpServers(toolkit, List.of("web"), new java.util.ArrayList<>());

        // 仅对选中的 server-a 调用 createClient
        verify(mcpClientFactory, times(1)).createClient(serverA);
        verify(mcpClientFactory, never()).createClient(serverB);
    }

    @Test
    void registerMcpServersEmptyNamesSkips() {
        builder.registerMcpServers(toolkit, List.of(), new java.util.ArrayList<>());

        verify(mcpServerRepository, never()).findByEnabledTrueOrderByCreatedAtDesc();
    }

    private AgentSkillConfig skill(String name) {
        AgentSkillConfig config = new AgentSkillConfig();
        config.setName(name);
        config.setDescription("d");
        config.setSkillContent("c");
        config.setSource("manual");
        return config;
    }

    private McpServerConfig mcpServer(String name) {
        McpServerConfig config = new McpServerConfig();
        config.setName(name);
        return config;
    }

    private AgentRequest request() {
        return new AgentRequest(
                1L, null, 100L, "demo", "/tmp/demo", "/tmp/demo/.codegraph",
                List.of(), "问题", "developer", null, null, null, null, null);
    }
}
