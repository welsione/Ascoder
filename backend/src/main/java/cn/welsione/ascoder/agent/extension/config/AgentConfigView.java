package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRuntimeStatus;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 配置视图 DTO，合并 AgentConfig 与运行态状态，供前端列表展示。
 *
 * <p>直接暴露 AgentConfig 全部字段 + {@code runtimeStatus}（IDLE / RUNNING），
 * 避免前端二次请求状态快照接口。</p>
 */
@Data
public class AgentConfigView {
    private Long id;
    private String agentId;
    private String displayName;
    private String description;
    private AgentRole agentRole;
    private SpecialistTaskKind taskKind;
    private String systemPrompt;
    private String taskTemplate;
    private int maxIters;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private String modelId;
    private List<String> roleKeys;
    private List<String> questionKeywords;
    private List<String> toolGroupKeys;
    private List<String> skillNames;
    private List<String> mcpServerNames;
    private boolean required;
    private boolean enabled;
    private boolean builtin;
    private String handoffTitle;
    private String handoffDescription;
    private String returnTitle;
    private String returnDescription;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AgentRuntimeStatus runtimeStatus;

    public AgentConfigView(AgentConfig config, AgentRuntimeStatus runtimeStatus) {
        this.id = config.getId();
        this.agentId = config.getAgentId();
        this.displayName = config.getDisplayName();
        this.description = config.getDescription();
        this.agentRole = config.getAgentRole();
        this.taskKind = config.getTaskKind();
        this.systemPrompt = config.getSystemPrompt();
        this.taskTemplate = config.getTaskTemplate();
        this.maxIters = config.getMaxIters();
        this.maxTokens = config.getMaxTokens();
        this.timeoutSeconds = config.getTimeoutSeconds();
        this.modelId = config.getModelId();
        this.roleKeys = config.getRoleKeys();
        this.questionKeywords = config.getQuestionKeywords();
        this.toolGroupKeys = config.getToolGroupKeys();
        this.skillNames = config.getSkillNames();
        this.mcpServerNames = config.getMcpServerNames();
        this.required = config.isRequired();
        this.enabled = config.isEnabled();
        this.builtin = config.isBuiltin();
        this.handoffTitle = config.getHandoffTitle();
        this.handoffDescription = config.getHandoffDescription();
        this.returnTitle = config.getReturnTitle();
        this.returnDescription = config.getReturnDescription();
        this.sortOrder = config.getSortOrder();
        this.createdAt = config.getCreatedAt();
        this.updatedAt = config.getUpdatedAt();
        this.runtimeStatus = runtimeStatus;
    }
}
