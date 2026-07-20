package cn.welsione.ascoder.agent.domain;

import lombok.Value;

/**
 * Agent 工具调用事件中的工具请求。
 */
@Value
public class AgentToolCall {
    String id;
    String name;
    Object input;
}
