package cn.welsione.ascoder.agent.domain;

/**
 * Agent 单次运行的最终状态，持久化在 agentRunRecords.status。
 */
public enum AgentRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    INTERRUPTED
}
