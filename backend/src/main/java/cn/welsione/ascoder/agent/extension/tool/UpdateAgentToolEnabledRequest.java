package cn.welsione.ascoder.agent.extension.tool;

import lombok.Data;

/**
 * 更新 Agent 工具启停状态的请求。
 */
@Data
public class UpdateAgentToolEnabledRequest {

    private boolean enabled;
}
