package cn.welsione.ascoder.agent.domain;

/**
 * Agent 当前运行态（内存瞬时状态，不持久化）。
 *
 * <p>{@link #IDLE} 表示空闲，{@link #RUNNING} 表示正在处理某个问题。</p>
 */
public enum AgentRuntimeStatus {
    IDLE,
    RUNNING
}
