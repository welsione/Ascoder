package cn.welsione.ascoder.agent.extension.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内置 Agent 工具定义，作为工具目录和运行时装配的静态来源。
 */
@Getter
@AllArgsConstructor
public class AgentToolDefinition {

    private String toolKey;
    private String displayName;
    private String groupName;
    private String riskLevel;
    private String description;
    private boolean defaultEnabled;
}
