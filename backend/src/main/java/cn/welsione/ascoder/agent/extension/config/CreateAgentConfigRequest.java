package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建 Agent 配置的请求体，更新时复用（{@link UpdateAgentConfigRequest} 继承本类）。
 *
 * <p>JSON 数组字段（roleKeys / questionKeywords / toolGroupKeys / skillNames / mcpServerNames）
 * 由 Service 序列化为 JSON 字符串存入 AgentConfig 对应的 Json 列。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAgentConfigRequest {

    @NotBlank @Size(max = 120)
    private String agentId;

    @NotBlank @Size(max = 120)
    private String displayName;

    private String description;

    private AgentRole agentRole;

    private SpecialistTaskKind taskKind;

    @NotBlank
    private String systemPrompt;

    private String taskTemplate;

    @Positive
    private int maxIters = 12;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    @Size(max = 120)
    private String modelId;

    private Long llmProviderId;

    private List<String> roleKeys;
    private List<String> questionKeywords;
    private List<String> toolGroupKeys;
    private List<String> skillNames;
    private List<String> mcpServerNames;

    private boolean required;
    private boolean enabled = true;

    @Size(max = 120)
    private String handoffTitle;
    private String handoffDescription;
    @Size(max = 120)
    private String returnTitle;
    private String returnDescription;

    private int sortOrder = 0;
}
