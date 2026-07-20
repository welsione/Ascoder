package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.extension.mcp.McpServerConfig;
import cn.welsione.ascoder.agent.extension.mcp.McpServerJpaRepository;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillConfig;
import cn.welsione.ascoder.agent.extension.skill.AgentSkillJpaRepository;
import cn.welsione.ascoder.agent.extension.tool.AgentToolService;
import cn.welsione.ascoder.analysis.CodeAnalysisTools;
import cn.welsione.ascoder.analysis.CodeGraphWorkspaceContext;
import cn.welsione.ascoder.analysis.FileInspectionTools;
import cn.welsione.ascoder.analysis.GitProvenanceTools;
import cn.welsione.ascoder.analysis.RepositoryInspectionTools;
import cn.welsione.ascoder.analysis.RestrictedCommandTools;
import cn.welsione.ascoder.analysis.TextSearchTools;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.common.FilePathSanitizer;
import cn.welsione.ascoder.common.SafeCommandRunner;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import cn.welsione.ascoder.repository.git.GitRepositoryService;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 配置驱动的工具装配器，按 {@link AgentConfig} 的工具组 / Skill / MCP 引用动态组装 Toolkit。
 *
 * <p>替代原 {@code ResearcherToolingBuilder} + {@code OrchestratorToolingBuilder} 两个硬编码装配器。
 * 工具组注册采用 AND 逻辑：组 key 需同时被 Agent 勾选（{@code config.toolGroupKeys}）且在全局启用
 * （{@code toolService.enabledToolKeys()}）。Skill / MCP 按 {@code config.skillNames} /
 * {@code config.mcpServerNames} 过滤注册，空列表不注册。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigDrivenToolingBuilder {

    private final CodeGraphClient codeGraphClient;
    private final GitRepositoryService gitRepositoryService;
    private final SafeCommandRunner safeCommandRunner;
    private final AgentToolService toolService;
    private final FilePathSanitizer filePathSanitizer;
    private final SelfLearningAgentToolFactory selfLearningAgentToolFactory;
    private final LogExploreToolRegistrar logExploreToolRegistrar;
    private final AgentSkillJpaRepository skillRepository;
    private final McpServerJpaRepository mcpServerRepository;
    private final McpClientFactory mcpClientFactory;
    private final PromptManager promptManager;

    public AgentTooling buildFor(AgentConfig config, AgentRequest request) {
        validateProjectSpace(request);
        log.info("构建配置驱动工具集，agentId={}，toolGroups={}", config.getAgentId(), config.getToolGroupKeys());
        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);
        skillBox.setExposeAllSkillMetadata(false);

        AtomicReference<String> codeContext = new AtomicReference<>(request.getCodeContext());
        Set<String> agentSelectedGroups = new HashSet<>(config.getToolGroupKeys());
        Set<String> globallyEnabledGroups = toolService.enabledToolKeys();
        List<String> warnings = new ArrayList<>();
        registerSelectedByGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, request, codeContext, warnings);

        registerSkills(skillBox, config.getSkillNames(), warnings);
        registerMcpServers(toolkit, config.getMcpServerNames(), warnings);

        return new AgentTooling(toolkit, skillBox, warnings);
    }

    /**
     * AND 逻辑：仅当组 key 被 Agent 勾选且在全局启用时注册该工具组。
     */
    void registerSelectedByGroup(Toolkit toolkit, Set<String> agentSelectedGroups, Set<String> globallyEnabledGroups,
                                 AgentRequest request, AtomicReference<String> codeContext, List<String> warnings) {
        CodeGraphWorkspaceContext workspaceContext = workspaceContext(request);
        String codeGraphMetadata = buildCodeGraphMetadata(request);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "codegraph",
                () -> new CodeAnalysisTools(codeGraphClient, workspaceContext, request.getText(), codeContext, codeGraphMetadata), warnings);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "git",
                () -> new RepositoryInspectionTools(gitRepositoryService, workspaceContext, filePathSanitizer, codeContext, codeGraphMetadata), warnings);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "git",
                () -> new GitProvenanceTools(gitRepositoryService, workspaceContext, codeContext, codeGraphMetadata), warnings);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "file",
                () -> new FileInspectionTools(workspaceContext, codeContext, codeGraphMetadata, promptManager), warnings);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "text",
                () -> new TextSearchTools(workspaceContext, codeContext, codeGraphMetadata), warnings);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "command",
                () -> new RestrictedCommandTools(safeCommandRunner, workspaceContext, codeContext, codeGraphMetadata), warnings);
        registerGroup(toolkit, agentSelectedGroups, globallyEnabledGroups, "self-learning",
                () -> selfLearningAgentToolFactory.createTools(request.getProjectSpaceId()), warnings);
        if (agentSelectedGroups.contains("log") && globallyEnabledGroups.contains("log")) {
            logExploreToolRegistrar.register(toolkit, request, globallyEnabledGroups);
        }
    }

    private void registerGroup(Toolkit toolkit, Set<String> agentSelectedGroups, Set<String> globallyEnabledGroups,
                               String groupKey, Supplier<Object> toolsSupplier, List<String> warnings) {
        if (!agentSelectedGroups.contains(groupKey) || !globallyEnabledGroups.contains(groupKey)) {
            String reason = !agentSelectedGroups.contains(groupKey) ? "Agent 未勾选" : "全局未启用";
            warnings.add("工具组 " + groupKey + " 被跳过（" + reason + "）");
            log.debug("跳过工具组，groupKey={}（agentSelected={}，globallyEnabled={}）",
                    groupKey, agentSelectedGroups.contains(groupKey), globallyEnabledGroups.contains(groupKey));
            return;
        }
        toolkit.registration().tool(toolsSupplier.get()).apply();
        log.debug("工具组注册完成，groupKey={}", groupKey);
    }

    void registerSkills(SkillBox skillBox, List<String> selectedNames, List<String> warnings) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return;
        }
        Set<String> names = new HashSet<>(selectedNames);
        List<AgentSkillConfig> skills = skillRepository.findByEnabledTrueOrderByCreatedAtDesc().stream()
                .filter(skill -> names.contains(skill.getName()))
                .collect(Collectors.toList());
        log.info("注册技能，agentId 选中 {} 个，命中 {} 个", selectedNames.size(), skills.size());

        Set<String> matchedNames = skills.stream().map(AgentSkillConfig::getName).collect(Collectors.toSet());
        names.stream()
                .filter(name -> !matchedNames.contains(name))
                .forEach(name -> warnings.add("Skill 未命中：" + name));

        for (AgentSkillConfig config : skills) {
            AgentSkill skill = AgentSkill.builder()
                    .name(config.getName())
                    .description(config.getDescription())
                    .skillContent(config.getSkillContent())
                    .source(config.getSource())
                    .build();
            skillBox.registration().skill(skill).apply();
        }
    }

    void registerMcpServers(Toolkit toolkit, List<String> selectedNames, List<String> warnings) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return;
        }
        Set<String> names = new HashSet<>(selectedNames);
        List<McpServerConfig> servers = mcpServerRepository.findByEnabledTrueOrderByCreatedAtDesc().stream()
                .filter(server -> names.contains(server.getName()))
                .collect(Collectors.toList());
        log.info("注册 MCP 服务器，选中 {} 个，命中 {} 个", selectedNames.size(), servers.size());

        Set<String> matchedNames = servers.stream().map(McpServerConfig::getName).collect(Collectors.toSet());
        names.stream()
                .filter(name -> !matchedNames.contains(name))
                .forEach(name -> warnings.add("MCP Server 未命中：" + name));

        for (McpServerConfig server : servers) {
            try {
                McpClientWrapper client = mcpClientFactory.createClient(server);
                Toolkit.ToolRegistration registration = toolkit.registration().mcpClient(client);
                List<String> enabledTools = mcpClientFactory.parseStringList(server.getEnabledToolsJson());
                List<String> disabledTools = new ArrayList<>(mcpClientFactory.parseStringList(server.getDisabledToolsJson()));
                List<McpSchema.Tool> declaredTools = client.listTools().block(Duration.ofSeconds(server.getTimeoutSeconds()));
                List<String> schemaUnsafe = McpToolSchemaValidator.filterUnsafeTools(declaredTools);
                if (!schemaUnsafe.isEmpty()) {
                    disabledTools.addAll(schemaUnsafe);
                    warnings.add("MCP Server " + server.getName() + " 禁用不安全工具：" + schemaUnsafe);
                }
                if (!enabledTools.isEmpty()) {
                    registration.enableTools(enabledTools);
                }
                if (!disabledTools.isEmpty()) {
                    registration.disableTools(disabledTools);
                }
                registration.apply();
                server.setLastError(null);
            } catch (RuntimeException ex) {
                warnings.add("MCP Server " + server.getName() + " 注册失败：" + ex.getMessage());
                server.setLastError(ex.getMessage());
                log.warn("MCP 服务器注册失败，name={}，错误={}", server.getName(), ex.getMessage());
            }
            mcpServerRepository.save(server);
        }
    }

    private void validateProjectSpace(AgentRequest request) {
        if (request.getProjectSpaceRootPath() == null || request.getProjectSpaceRootPath().isBlank()) {
            throw new InvalidStateException("项目空间没有可用代码目录");
        }
    }

    private CodeGraphWorkspaceContext workspaceContext(AgentRequest request) {
        return new CodeGraphWorkspaceContext(Path.of(request.getProjectSpaceRootPath()), request.getRepositories());
    }

    private String buildCodeGraphMetadata(AgentRequest request) {
        return """
                ProjectSpace: %s
                ProjectSpaceRoot: %s
                CodeGraphIndex: %s
                AvailableRepositories:
                %s
                """.formatted(
                request.getProjectSpaceName(),
                request.getProjectSpaceRootPath(),
                request.getProjectSpaceCodegraphIndexPath(),
                repositorySummary(request)
        ).strip();
    }

    private String repositorySummary(AgentRequest request) {
        if (request.getRepositories() == null || request.getRepositories().isEmpty()) {
            return "- 未配置";
        }
        StringBuilder builder = new StringBuilder();
        for (AgentRequest.RepositoryContext repo : request.getRepositories()) {
            builder.append("- ").append(repo.getRepositoryName())
                    .append(" [").append(repo.getRole() != null ? repo.getRole() : "repository").append("]")
                    .append(repo.isPrimary() ? " primary" : "")
                    .append(" branch=").append(repo.getBranchName() != null ? repo.getBranchName() : "未记录")
                    .append(" path=").append(repo.getWorkspacePath() != null ? repo.getWorkspacePath() : "未记录")
                    .append('\n');
        }
        return builder.toString().trim();
    }
}
