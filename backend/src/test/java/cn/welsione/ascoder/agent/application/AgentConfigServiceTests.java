package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentConfigReferenceKind;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.LlmProviderType;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import cn.welsione.ascoder.agent.extension.config.CreateAgentConfigRequest;
import cn.welsione.ascoder.agent.extension.config.TestRenderResponse;
import cn.welsione.ascoder.agent.extension.config.UpdateAgentConfigRequest;
import cn.welsione.ascoder.agent.extension.mcp.McpServerConfig;
import cn.welsione.ascoder.agent.extension.mcp.McpServerJpaRepository;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillConfig;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillJpaRepository;
import cn.welsione.ascoder.agent.extension.tool.AgentToolConfig;
import cn.welsione.ascoder.agent.extension.tool.AgentToolService;
import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import cn.welsione.ascoder.agent.persistence.AgentConfigJpaRepository;
import cn.welsione.ascoder.agent.persistence.LlmProviderJpaRepository;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentConfigService 校验与 CRUD 行为测试。
 */
@ExtendWith(MockitoExtension.class)
class AgentConfigServiceTests {

    @Mock private AgentConfigJpaRepository repository;
    @Mock private AgentToolService toolService;
    @Mock private AgentSkillJpaRepository skillRepository;
    @Mock private McpServerJpaRepository mcpServerRepository;
    @Mock private LlmProviderJpaRepository llmProviderRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private AgentConfigCache cache;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentConfigService service;

    @BeforeEach
    void setUp() {
        lenient().when(toolService.list()).thenReturn(List.of(toolConfig("codegraph")));
        lenient().when(skillRepository.findByName("spring-boot")).thenReturn(Optional.of(new AgentSkillConfig()));
        lenient().when(mcpServerRepository.findByName("web")).thenReturn(Optional.of(new McpServerConfig()));
    }

    @Test
    void createSpecialistSucceeds() {
        when(repository.existsByAgentId("my-researcher")).thenReturn(false);
        when(repository.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentConfig saved = service.create(specialistRequest("my-researcher"));

        ArgumentCaptor<AgentConfig> captor = ArgumentCaptor.forClass(AgentConfig.class);
        verify(repository).save(captor.capture());
        assertEquals("my-researcher", captor.getValue().getAgentId());
        assertEquals("[\"codegraph\"]", captor.getValue().getToolGroupKeysJson());
        assertEquals(AgentRole.SPECIALIST, saved.getAgentRole());
    }

    @Test
    void createOrchestratorSucceeds() {
        when(repository.existsByAgentId("my-orch")).thenReturn(false);
        when(repository.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId("my-orch");
        request.setDisplayName("Orch");
        request.setAgentRole(AgentRole.ORCHESTRATOR);
        request.setSystemPrompt("sys");
        request.setTaskTemplate("template");
        request.setMaxIters(10);
        assertDoesNotThrow(() -> service.create(request));
    }

    @Test
    void createPublishesChangedEvent() {
        when(repository.existsByAgentId("evt")).thenReturn(false);
        when(repository.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(specialistRequest("evt"));

        ArgumentCaptor<AgentConfigChangedEvent> captor = ArgumentCaptor.forClass(AgentConfigChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("evt", captor.getValue().getAgentId());
        assertEquals("create", captor.getValue().getAction());
    }

    @Test
    void createDuplicateAgentIdThrows() {
        when(repository.existsByAgentId("code-researcher")).thenReturn(true);
        assertThrows(ValidationException.class, () -> service.create(specialistRequest("code-researcher")));
    }

    @Test
    void createPlannerRoleThrows() {
        when(repository.existsByAgentId("planner")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("planner");
        request.setAgentRole(AgentRole.PLANNER);
        request.setTaskKind(null);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createOrchestratorWithTaskKindThrows() {
        when(repository.existsByAgentId("orch")).thenReturn(false);
        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId("orch");
        request.setDisplayName("Orch");
        request.setAgentRole(AgentRole.ORCHESTRATOR);
        request.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        request.setSystemPrompt("sys");
        request.setMaxIters(10);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createSelfLearningSucceeds() {
        when(repository.existsByAgentId("self-learning-insight")).thenReturn(false);
        when(repository.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId("self-learning-insight");
        request.setDisplayName("Insight");
        request.setAgentRole(AgentRole.SELF_LEARNING);
        request.setTaskKind(null);
        request.setSystemPrompt("sys");
        request.setTaskTemplate("{{projectSpaceName}}");
        request.setMaxIters(1);
        AgentConfig saved = service.create(request);

        assertEquals(AgentRole.SELF_LEARNING, saved.getAgentRole());
    }

    @Test
    void createSelfLearningWithTaskKindThrows() {
        when(repository.existsByAgentId("sl")).thenReturn(false);
        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId("sl");
        request.setDisplayName("SL");
        request.setAgentRole(AgentRole.SELF_LEARNING);
        request.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        request.setSystemPrompt("sys");
        request.setMaxIters(1);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void getByAgentIdReturnsConfig() {
        AgentConfig config = config("self-learning-insight", AgentRole.SELF_LEARNING, true);
        when(repository.findByAgentId("self-learning-insight")).thenReturn(Optional.of(config));

        Optional<AgentConfig> found = service.getByAgentId("self-learning-insight");

        assertTrue(found.isPresent());
        assertEquals("self-learning-insight", found.get().getAgentId());
    }

    @Test
    void getByAgentIdMissingReturnsEmpty() {
        when(repository.findByAgentId("missing")).thenReturn(Optional.empty());
        assertTrue(service.getByAgentId("missing").isEmpty());
    }

    @Test
    void createSpecialistWithoutTaskKindThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setTaskKind(null);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createSpecialistWithoutTaskTemplateThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setTaskTemplate(null);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createInvalidToolKeyThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setToolGroupKeys(List.of("nonexistent"));
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createInvalidSkillNameThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setSkillNames(List.of("missing-skill"));
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createInvalidMcpServerNameThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setMcpServerNames(List.of("missing-mcp"));
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createTemplateSyntaxErrorThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setTaskTemplate("{{#if active}}未闭合");
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void updateBuiltinChangeAgentIdThrows() {
        AgentConfig builtin = config("code-researcher", AgentRole.SPECIALIST, true);
        when(repository.findById(1L)).thenReturn(Optional.of(builtin));

        UpdateAgentConfigRequest request = new UpdateAgentConfigRequest();
        request.setAgentId("renamed");
        request.setDisplayName("CR");
        request.setAgentRole(AgentRole.SPECIALIST);
        request.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        request.setSystemPrompt("sys");
        request.setTaskTemplate("{{question}}");
        request.setMaxIters(10);
        assertThrows(ValidationException.class, () -> service.update(1L, request));
    }

    @Test
    void updateBuiltinChangeRoleThrows() {
        AgentConfig builtin = config("code-researcher", AgentRole.SPECIALIST, true);
        when(repository.findById(1L)).thenReturn(Optional.of(builtin));

        UpdateAgentConfigRequest request = new UpdateAgentConfigRequest();
        request.setAgentId("code-researcher");
        request.setDisplayName("CR");
        request.setAgentRole(AgentRole.ORCHESTRATOR);
        request.setSystemPrompt("sys");
        request.setTaskTemplate("{{question}}");
        request.setMaxIters(10);
        assertThrows(ValidationException.class, () -> service.update(1L, request));
    }

    @Test
    void deleteBuiltinThrows() {
        AgentConfig builtin = config("code-researcher", AgentRole.SPECIALIST, true);
        when(repository.findById(1L)).thenReturn(Optional.of(builtin));
        assertThrows(InvalidStateException.class, () -> service.delete(1L));
    }

    @Test
    void deleteNonBuiltinSucceeds() {
        AgentConfig custom = config("custom", AgentRole.SPECIALIST, false);
        when(repository.findById(1L)).thenReturn(Optional.of(custom));
        service.delete(1L);
        verify(repository).delete(custom);
    }

    @Test
    void updateEnabledSecondOrchestratorThrows() {
        AgentConfig orch = config("orch2", AgentRole.ORCHESTRATOR, false);
        orch.setEnabled(false);
        when(repository.findById(2L)).thenReturn(Optional.of(orch));
        when(repository.countByAgentRoleAndEnabledTrue(AgentRole.ORCHESTRATOR)).thenReturn(1L);
        assertThrows(InvalidStateException.class, () -> service.updateEnabled(2L, true));
    }

    @Test
    void updateEnabledDisableOnlyOrchestratorThrows() {
        AgentConfig orch = config("orch", AgentRole.ORCHESTRATOR, false);
        orch.setEnabled(true);
        when(repository.findById(1L)).thenReturn(Optional.of(orch));
        when(repository.countByAgentRoleAndEnabledTrue(AgentRole.ORCHESTRATOR)).thenReturn(1L);
        assertThrows(InvalidStateException.class, () -> service.updateEnabled(1L, false));
    }

    @Test
    void updateEnabledSpecialistSucceeds() {
        AgentConfig spec = config("spec", AgentRole.SPECIALIST, false);
        spec.setEnabled(true);
        when(repository.findById(1L)).thenReturn(Optional.of(spec));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.updateEnabled(1L, false);
        assertFalse(spec.isEnabled());
    }

    @Test
    void testRenderReturnsRenderedText() {
        AgentConfig config = config("spec", AgentRole.SPECIALIST, false);
        config.setTaskTemplate("Q: {{question}}");
        when(repository.findById(1L)).thenReturn(Optional.of(config));

        TaskPromptContext context = TaskPromptContext.empty();
        context.setQuestion("my question");
        TestRenderResponse response = service.testRender(1L, context);

        assertEquals("Q: my question", response.getRenderedText());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    void testRenderUnresolvedPlaceholderWarning() {
        AgentConfig config = config("spec", AgentRole.SPECIALIST, false);
        config.setTaskTemplate("Q: {{question}} and {{nonExistent}}");
        when(repository.findById(1L)).thenReturn(Optional.of(config));

        TestRenderResponse response = service.testRender(1L, null);
        assertFalse(response.getWarnings().isEmpty());
    }

    @Test
    void assertNotReferencedReferencedThrows() {
        AgentConfig config = config("spec", AgentRole.SPECIALIST, false);
        config.setToolGroupKeysJson("[\"codegraph\"]");
        when(repository.findAll()).thenReturn(List.of(config));

        assertThrows(InvalidStateException.class,
                () -> service.assertNotReferenced("codegraph", AgentConfigReferenceKind.TOOL));
    }

    @Test
    void assertNotReferencedNotReferencedPasses() {
        AgentConfig config = config("spec", AgentRole.SPECIALIST, false);
        config.setToolGroupKeysJson("[]");
        when(repository.findAll()).thenReturn(List.of(config));

        assertDoesNotThrow(() -> service.assertNotReferenced("codegraph", AgentConfigReferenceKind.TOOL));
    }

    @Test
    void createWithNonExistentLlmProviderIdThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        when(llmProviderRepository.findById(999L)).thenReturn(Optional.empty());

        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setLlmProviderId(999L);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createWithDisabledLlmProviderIdThrows() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        LlmProvider disabledProvider = new LlmProvider();
        disabledProvider.setId(1L);
        disabledProvider.setEnabled(false);
        when(llmProviderRepository.findById(1L)).thenReturn(Optional.of(disabledProvider));

        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setLlmProviderId(1L);
        assertThrows(ValidationException.class, () -> service.create(request));
    }

    @Test
    void createWithEnabledLlmProviderIdSucceeds() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        LlmProvider enabledProvider = new LlmProvider();
        enabledProvider.setId(1L);
        enabledProvider.setEnabled(true);
        when(llmProviderRepository.findById(1L)).thenReturn(Optional.of(enabledProvider));
        when(repository.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setLlmProviderId(1L);
        assertDoesNotThrow(() -> service.create(request));
    }

    @Test
    void createWithNullLlmProviderIdSucceeds() {
        when(repository.existsByAgentId("spec")).thenReturn(false);
        when(repository.save(any(AgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAgentConfigRequest request = specialistRequest("spec");
        request.setLlmProviderId(null);
        assertDoesNotThrow(() -> service.create(request));
    }

    @Test
    void getMissingThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99L));
    }

    private CreateAgentConfigRequest specialistRequest(String agentId) {
        CreateAgentConfigRequest request = new CreateAgentConfigRequest();
        request.setAgentId(agentId);
        request.setDisplayName(agentId);
        request.setAgentRole(AgentRole.SPECIALIST);
        request.setTaskKind(SpecialistTaskKind.CODE_RESEARCH);
        request.setSystemPrompt("system prompt");
        request.setTaskTemplate("{{question}}");
        request.setToolGroupKeys(List.of("codegraph"));
        request.setMaxIters(10);
        return request;
    }

    private AgentConfig config(String agentId, AgentRole role, boolean builtin) {
        AgentConfig config = new AgentConfig();
        config.setId(1L);
        config.setAgentId(agentId);
        config.setAgentRole(role);
        config.setBuiltin(builtin);
        config.setSystemPrompt("sys");
        config.setTaskTemplate("{{question}}");
        return config;
    }

    private AgentToolConfig toolConfig(String toolKey) {
        AgentToolConfig tool = new AgentToolConfig();
        tool.setToolKey(toolKey);
        return tool;
    }
}
