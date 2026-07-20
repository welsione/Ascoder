package cn.welsione.ascoder.agent.domain;

/**
 * Agent 流式事件类型，隔离具体 Agent 运行时的事件枚举。
 */
public enum AgentStreamEventType {
    HANDOFF,
    REASONING,
    TOOL_RESULT,
    HINT,
    SUMMARY,
    AGENT_RESULT,
    EVENT
}
