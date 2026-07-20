package cn.welsione.ascoder.agent.extension.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新 Agent 技能启用状态的请求体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgentSkillEnabledRequest {
    boolean enabled;
}
