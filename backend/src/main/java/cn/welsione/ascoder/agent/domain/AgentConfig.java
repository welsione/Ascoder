package cn.welsione.ascoder.agent.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Agent 配置实体，持久化存储可配置 Agent 的定义、提示词、工具装配、模型参数与触发条件。
 *
 * <p>全局级配置（无 projectSpace 维度），所有项目空间共用同一套 Agent 定义。
 * JSON 字段（roleKeysJson / questionKeywordsJson / toolGroupKeysJson / skillNamesJson / mcpServerNamesJson）
 * 以字符串存储，通过便捷方法解析为 {@code List<String>}。</p>
 */
@Slf4j
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agentConfigs")
public class AgentConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String agentId;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRole agentRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SpecialistTaskKind taskKind;

    @Column(nullable = false, columnDefinition = "longtext")
    private String systemPrompt;

    @Column(columnDefinition = "longtext")
    private String taskTemplate;

    @Column(nullable = false)
    private int maxIters = 12;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    @Column(length = 120)
    private String modelId;

    private Long llmProviderId;

    @Column(columnDefinition = "text")
    private String roleKeysJson;

    @Column(columnDefinition = "text")
    private String questionKeywordsJson;

    @Column(columnDefinition = "text")
    private String toolGroupKeysJson;

    @Column(columnDefinition = "text")
    private String skillNamesJson;

    @Column(columnDefinition = "text")
    private String mcpServerNamesJson;

    @Column(nullable = false)
    private boolean required = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean builtin = false;

    @Column(length = 120)
    private String handoffTitle;

    @Column(columnDefinition = "text")
    private String handoffDescription;

    @Column(length = 120)
    private String returnTitle;

    @Column(columnDefinition = "text")
    private String returnDescription;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Version
    private Long version = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 解析 roleKeysJson 为角色关键词列表，空值返回空列表。
     */
    public List<String> getRoleKeys() {
        return parseStringList(roleKeysJson, "roleKeysJson");
    }

    /**
     * 解析 questionKeywordsJson 为问题触发关键词列表，空值返回空列表。
     */
    public List<String> getQuestionKeywords() {
        return parseStringList(questionKeywordsJson, "questionKeywordsJson");
    }

    /**
     * 解析 toolGroupKeysJson 为可用工具组 key 列表，空值返回空列表。
     */
    public List<String> getToolGroupKeys() {
        return parseStringList(toolGroupKeysJson, "toolGroupKeysJson");
    }

    /**
     * 解析 skillNamesJson 为绑定 Skill 名列表，空值返回空列表。
     */
    public List<String> getSkillNames() {
        return parseStringList(skillNamesJson, "skillNamesJson");
    }

    /**
     * 解析 mcpServerNamesJson 为绑定 MCP 服务器名列表，空值返回空列表。
     */
    public List<String> getMcpServerNames() {
        return parseStringList(mcpServerNamesJson, "mcpServerNamesJson");
    }

    private List<String> parseStringList(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, STRING_LIST);
        } catch (JsonProcessingException ex) {
            log.warn("AgentConfig JSON 解析失败，agentId={}，field={}，rawValue={}",
                    agentId, fieldName, json.substring(0, Math.min(100, json.length())));
            return Collections.emptyList();
        }
    }
}
