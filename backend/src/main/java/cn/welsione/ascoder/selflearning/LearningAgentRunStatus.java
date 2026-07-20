package cn.welsione.ascoder.selflearning;

/**
 * Self Learning Agent 后台整理任务状态。
 */
public enum LearningAgentRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILED,
    SKIPPED,
    FAILED
}
