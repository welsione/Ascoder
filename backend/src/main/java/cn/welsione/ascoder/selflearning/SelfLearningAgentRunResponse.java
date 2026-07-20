package cn.welsione.ascoder.selflearning;

import lombok.Getter;

/**
 * Self Learning Agent 整理原始记录后的运行结果。
 */
@Getter
public class SelfLearningAgentRunResponse {
    private final Long runId;
    private final LearningAgentRunStatus status;
    private final int createdInsightCount;
    private final int consumedRawEventCount;
    private final int skippedRawEventCount;
    private final int failedConversationCount;
    private final String message;

    public SelfLearningAgentRunResponse(int createdInsightCount, int consumedRawEventCount, int skippedRawEventCount, String message) {
        this(null, null, createdInsightCount, consumedRawEventCount, skippedRawEventCount, 0, message);
    }

    public SelfLearningAgentRunResponse(
            Long runId,
            LearningAgentRunStatus status,
            int createdInsightCount,
            int consumedRawEventCount,
            int skippedRawEventCount,
            int failedConversationCount,
            String message
    ) {
        this.runId = runId;
        this.status = status;
        this.createdInsightCount = createdInsightCount;
        this.consumedRawEventCount = consumedRawEventCount;
        this.skippedRawEventCount = skippedRawEventCount;
        this.failedConversationCount = failedConversationCount;
        this.message = message;
    }
}
