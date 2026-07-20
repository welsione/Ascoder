package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 自学习原始记录响应，供管理员查看事件留痕。
 */
@Value
public class LearningRawEventResponse {
    Long id;
    Long projectSpaceId;
    Long repositoryId;
    String branchName;
    Long questionId;
    Long conversationId;
    String agentId;
    LearningRawEventType eventType;
    String eventPayloadJson;
    String summary;
    String evidenceJson;
    String gitProvenanceJson;
    String userFeedbackType;
    Date sourceCreatedAt;
    Date createdAt;

    public static LearningRawEventResponse from(LearningRawEvent event) {
        return new LearningRawEventResponse(
                event.getId(),
                event.getProjectSpace().getId(),
                event.getRepository() == null ? null : event.getRepository().getId(),
                event.getBranchName(),
                event.getQuestion() == null ? null : event.getQuestion().getId(),
                event.getConversationId(),
                event.getAgentId(),
                event.getEventType(),
                event.getEventPayloadJson(),
                event.getSummary(),
                event.getEvidenceJson(),
                event.getGitProvenanceJson(),
                event.getUserFeedbackType(),
                event.getSourceCreatedAt(),
                event.getCreatedAt()
        );
    }
}
