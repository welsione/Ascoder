package cn.welsione.ascoder.agent.extension.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建 Agent 技能的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAgentSkillRequest {
    @NotBlank @Size(max = 120)
    String name;
    @NotBlank
    String description;
    @NotBlank
    String skillContent;
    @Size(max = 120)
    String source;
    boolean enabled;
}
