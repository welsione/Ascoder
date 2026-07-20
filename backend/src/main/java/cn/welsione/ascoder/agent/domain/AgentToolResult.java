package cn.welsione.ascoder.agent.domain;

import lombok.Value;

/**
 * Agent 工具结果事件中的工具输出。
 */
@Value
public class AgentToolResult {
    String id;
    String name;
    String output;
    boolean suspended;
}
