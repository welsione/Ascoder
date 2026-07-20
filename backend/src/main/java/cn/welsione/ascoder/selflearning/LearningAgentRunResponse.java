package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * Self Learning Agent 后台整理运行记录响应。
 */
@Value
public class LearningAgentRunResponse {
    Long id;
    Long projectSpaceId;
    LearningAgentRunStatus status;
    int limitCount;
    int createdInsightCount;
    int consumedRawEventCount;
    int skippedRawEventCount;
    int failedConversationCount;
    String currentRawEventIdsJson;
    String failureDetailsJson;
    String message;
    String errorMessage;
    Date startedAt;
    Date finishedAt;
    Date createdAt;
    Date updatedAt;

    public static LearningAgentRunResponse from(LearningAgentRun run) {
        return new LearningAgentRunResponse(
                run.getId(),
                run.getProjectSpace().getId(),
                run.getStatus(),
                run.getLimitCount(),
                run.getCreatedInsightCount(),
                run.getConsumedRawEventCount(),
                run.getSkippedRawEventCount(),
                run.getFailedConversationCount(),
                run.getCurrentRawEventIdsJson(),
                run.getFailureDetailsJson(),
                run.getMessage(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }
}
