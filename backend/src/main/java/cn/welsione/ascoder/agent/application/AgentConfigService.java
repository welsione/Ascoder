package cn.welsione.ascoder.agent.application;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentConfigReferenceKind;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.LlmProvider;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import cn.welsione.ascoder.agent.extension.config.CreateAgentConfigRequest;
import cn.welsione.ascoder.agent.extension.config.TestRenderResponse;
import cn.welsione.ascoder.agent.extension.config.UpdateAgentConfigRequest;
import cn.welsione.ascoder.agent.extension.mcp.McpServerJpaRepository;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillJpaRepository;
import cn.welsione.ascoder.agent.extension.tool.AgentToolService;
import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import cn.welsione.ascoder.agent.persistence.AgentConfigJpaRepository;
import cn.welsione.ascoder.agent.persistence.LlmProviderJpaRepository;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.welsione.jprompt.JPrompt;
import cn.welsione.jprompt.TemplateException;
import cn.welsione.ascoder.agent.port.AgentConfigPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 配置管理服务，提供 CRUD、3.6 节校验、模板渲染预览与反向引用检查。
 *
 * <p>{@link #listEnabled()} 通过 {@link AgentConfigCache} 缓存（TTL 30s），
 * 配置变更时发布 {@link AgentConfigChangedEvent}，由 {@link AgentConfigCacheListener}
 * 在事务提交后失效缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService implements AgentConfigPort {

    private final AgentConfigJpaRepository repository;
    private final AgentToolService toolService;
    private final AgentSkillJpaRepository skillRepository;
    private final McpServerJpaRepository mcpServerRepository;
    private final LlmProviderJpaRepository llmProviderRepository;
    private final ObjectMapper objectMapper;
    private final AgentConfigCache cache;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AgentConfig> list() {
        return repository.findAll(org.springframework.data.domain.Sort.by("sortOrder").ascending());
    }

    @Transactional(readOnly = true)
    public AgentConfig get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", id));
    }

    /**
     * 按 agentId 加载 AgentConfig，供自学习 Agent 等按业务标识读取配置的场景使用。
     *
     * <p>返回 Optional 而非抛 404，由调用方决定缺失时的兜底策略（如回退硬编码默认值）。</p>
     */
    @Transactional(readOnly = true)
    public Optional<AgentConfig> getByAgentId(String agentId) {
        return repository.findByAgentId(agentId);
    }

    @Transactional
    public AgentConfig create(CreateAgentConfigRequest request) {
        validateAgentIdUnique(request.getAgentId());
        validateRoleAndTaskKind(request.getAgentRole(), request.getTaskKind());
        validatePrompts(request);
        validateTemplateSyntax(request.getTaskTemplate());
        validateReferences(request);
        AgentConfig config = new AgentConfig();
        applyRequest(config, request);
        log.info("创建 Agent 配置，agentId={}", request.getAgentId());
        AgentConfig saved = repository.save(config);
        publishChanged(saved.getAgentId(), "create");
        return saved;
    }

    @Transactional
    public AgentConfig update(Long id, UpdateAgentConfigRequest request) {
        AgentConfig config = get(id);
        if (config.isBuiltin()) {
            if (!config.getAgentId().equals(request.getAgentId())) {
                throw new ValidationException("agentId", "内置 Agent 的 agentId 不可修改");
            }
            if (config.getAgentRole() != request.getAgentRole()) {
                throw new ValidationException("agentRole", "内置 Agent 的角色不可修改");
            }
        } else {
            if (!config.getAgentId().equals(request.getAgentId()) && repository.existsByAgentId(request.getAgentId())) {
                throw new ValidationException("agentId", "agentId 已存在");
            }
        }
        validateRoleAndTaskKind(request.getAgentRole(), request.getTaskKind());
        validatePrompts(request);
        validateTemplateSyntax(request.getTaskTemplate());
        validateReferences(request);
        applyRequest(config, request);
        log.info("更新 Agent 配置，id={}，agentId={}", id, config.getAgentId());
        AgentConfig saved = repository.save(config);
        publishChanged(saved.getAgentId(), "update");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AgentConfig config = get(id);
        if (config.isBuiltin()) {
            throw new InvalidStateException("内置 Agent 禁止删除");
        }
        repository.delete(config);
        log.info("删除 Agent 配置，id={}，agentId={}", id, config.getAgentId());
        publishChanged(config.getAgentId(), "delete");
    }

    @Transactional
    public AgentConfig updateEnabled(Long id, boolean enabled) {
        AgentConfig config = get(id);
        if (config.getAgentRole() == AgentRole.ORCHESTRATOR) {
            long enabledCount = repository.countByAgentRoleAndEnabledTrue(AgentRole.ORCHESTRATOR);
            if (enabled && enabledCount >= 1 && !config.isEnabled()) {
                throw new InvalidStateException("已存在启用的 ORCHESTRATOR Agent，只能启用一个");
            }
            if (!enabled && enabledCount <= 1 && config.isEnabled()) {
                throw new InvalidStateException("必须至少启用一个 ORCHESTRATOR Agent");
            }
        }
        config.setEnabled(enabled);
        log.info("更新 Agent 启停，id={}，agentId={}，enabled={}", id, config.getAgentId(), enabled);
        AgentConfig saved = repository.save(config);
        publishChanged(saved.getAgentId(), "enabled");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AgentConfig> listEnabled() {
        return cache.get(repository::findByEnabledTrueOrderBySortOrderAsc);
    }

    private void publishChanged(String agentId, String action) {
        eventPublisher.publishEvent(new AgentConfigChangedEvent(agentId, action));
    }

    /**
     * 渲染任务模板预览，返回渲染文本与未解析占位符告警。
     */
    public TestRenderResponse testRender(Long id, TaskPromptContext sampleContext) {
        AgentConfig config = get(id);
        if (config.getTaskTemplate() == null || config.getTaskTemplate().isBlank()) {
            return new TestRenderResponse("", List.of("该 Agent 未配置任务模板"));
        }
        TaskPromptContext context = sampleContext != null ? sampleContext : TaskPromptContext.empty();
        String rendered = JPrompt.templateInline(config.getTaskTemplate(), TaskPromptContext.class).build(context);
        List<String> warnings = new ArrayList<>();
        if (rendered.contains("{{")) {
            warnings.add("渲染结果含未解析占位符 {{...}}，请检查模板变量名是否在 TaskPromptContext 中存在");
        }
        return new TestRenderResponse(rendered, warnings);
    }

    /**
     * 反向引用校验：删除工具/技能/MCP 前调用，若被任一 AgentConfig 引用则抛 InvalidStateException。
     */
    @Transactional(readOnly = true)
    public void assertNotReferenced(String name, AgentConfigReferenceKind kind) {
        for (AgentConfig config : repository.findAll()) {
            if (references(config, name, kind)) {
                throw new InvalidStateException("被 Agent " + config.getAgentId() + " 引用，禁止删除");
            }
        }
    }

    private boolean references(AgentConfig config, String name, AgentConfigReferenceKind kind) {
        List<String> keys = switch (kind) {
            case TOOL -> config.getToolGroupKeys();
            case SKILL -> config.getSkillNames();
            case MCP -> config.getMcpServerNames();
        };
        return keys.contains(name);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByLlmProviderId(Long llmProviderId) {
        return repository.countByLlmProviderId(llmProviderId);
    }

    private void validateAgentIdUnique(String agentId) {
        if (repository.existsByAgentId(agentId)) {
            throw new ValidationException("agentId", "agentId 已存在");
        }
    }

    private void validateRoleAndTaskKind(AgentRole role, SpecialistTaskKind taskKind) {
        if (role == null) {
            throw new ValidationException("agentRole", "agentRole 不能为空");
        }
        if (role == AgentRole.PLANNER) {
            throw new ValidationException("agentRole", "一期暂不支持 PLANNER 角色配置");
        }
        if (role == AgentRole.ORCHESTRATOR && taskKind != null) {
            throw new ValidationException("taskKind", "ORCHESTRATOR 角色不应配置 taskKind");
        }
        if (role == AgentRole.SPECIALIST && taskKind == null) {
            throw new ValidationException("taskKind", "SPECIALIST 角色必须配置 taskKind");
        }
        if (role == AgentRole.SELF_LEARNING && taskKind != null) {
            throw new ValidationException("taskKind", "SELF_LEARNING 角色不应配置 taskKind");
        }
    }

    private void validatePrompts(CreateAgentConfigRequest request) {
        if (request.getSystemPrompt() == null || request.getSystemPrompt().isBlank()) {
            throw new ValidationException("systemPrompt", "系统提示词不能为空");
        }
        if (request.getAgentRole() == AgentRole.SPECIALIST
                && (request.getTaskTemplate() == null || request.getTaskTemplate().isBlank())) {
            throw new ValidationException("taskTemplate", "SPECIALIST 角色的任务模板不能为空");
        }
    }

    private void validateTemplateSyntax(String taskTemplate) {
        if (taskTemplate == null || taskTemplate.isBlank()) {
            return;
        }
        try {
            JPrompt.templateInline(taskTemplate, TaskPromptContext.class).build(TaskPromptContext.empty());
        } catch (TemplateException ex) {
            throw new ValidationException("taskTemplate", "任务模板语法错误: " + ex.getMessage());
        }
    }

    private void validateReferences(CreateAgentConfigRequest request) {
        if (request.getLlmProviderId() != null) {
            LlmProvider provider = llmProviderRepository.findById(request.getLlmProviderId())
                    .orElseThrow(() -> new ValidationException("llmProviderId", "指定的 LLM 供应商不存在"));
            if (!provider.isEnabled()) {
                throw new ValidationException("llmProviderId", "指定的 LLM 供应商已禁用");
            }
        }
        Set<String> validToolKeys = toolService.list().stream()
                .map(config -> config.getToolKey())
                .collect(Collectors.toSet());
        for (String key : nullSafe(request.getToolGroupKeys())) {
            if (!validToolKeys.contains(key)) {
                throw new ValidationException("toolGroupKeys", "工具组 key 不存在: " + key);
            }
        }
        for (String name : nullSafe(request.getSkillNames())) {
            if (skillRepository.findByName(name).isEmpty()) {
                throw new ValidationException("skillNames", "Skill 不存在: " + name);
            }
        }
        for (String name : nullSafe(request.getMcpServerNames())) {
            if (mcpServerRepository.findByName(name).isEmpty()) {
                throw new ValidationException("mcpServerNames", "MCP 服务器不存在: " + name);
            }
        }
    }

    private void applyRequest(AgentConfig config, CreateAgentConfigRequest request) {
        config.setAgentId(request.getAgentId());
        config.setDisplayName(request.getDisplayName());
        config.setDescription(request.getDescription());
        config.setAgentRole(request.getAgentRole());
        config.setTaskKind(request.getTaskKind());
        config.setSystemPrompt(request.getSystemPrompt());
        config.setTaskTemplate(request.getTaskTemplate());
        config.setMaxIters(request.getMaxIters());
        config.setMaxTokens(request.getMaxTokens());
        config.setTimeoutSeconds(request.getTimeoutSeconds());
        config.setModelId(request.getModelId());
        config.setLlmProviderId(request.getLlmProviderId());
        config.setRoleKeysJson(toJson(request.getRoleKeys()));
        config.setQuestionKeywordsJson(toJson(request.getQuestionKeywords()));
        config.setToolGroupKeysJson(toJson(request.getToolGroupKeys()));
        config.setSkillNamesJson(toJson(request.getSkillNames()));
        config.setMcpServerNamesJson(toJson(request.getMcpServerNames()));
        config.setRequired(request.isRequired());
        config.setEnabled(request.isEnabled());
        config.setHandoffTitle(request.getHandoffTitle());
        config.setHandoffDescription(request.getHandoffDescription());
        config.setReturnTitle(request.getReturnTitle());
        config.setReturnDescription(request.getReturnDescription());
        config.setSortOrder(request.getSortOrder());
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<String> nullSafe(List<String> list) {
        return list != null ? list : List.of();
    }
}
