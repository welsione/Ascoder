package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 正式知识响应，展示审核后可召回的项目空间知识。
 */
@Value
public class LearningKnowledgeItemResponse {
    Long id;
    Long projectSpaceId;
    Long repositoryId;
    String sourceInsightIdsJson;
    String sourceRawEventIdsJson;
    LearningKnowledgeType type;
    LearningKnowledgeStatus status;
    String title;
    String content;
    String summary;
    String applicableScope;
    String evidenceJson;
    String gitProvenanceJson;
    String tags;
    double confidence;
    int usageCount;
    int acceptedCount;
    int rejectedCount;
    Date lastUsedAt;
    String staleReason;
    Long reviewerId;
    Date createdAt;
    Date updatedAt;

    public static LearningKnowledgeItemResponse from(LearningKnowledgeItem item) {
        return new LearningKnowledgeItemResponse(
                item.getId(),
                item.getProjectSpace().getId(),
                item.getRepository() == null ? null : item.getRepository().getId(),
                item.getSourceInsightIdsJson(),
                item.getSourceRawEventIdsJson(),
                item.getType(),
                item.getStatus(),
                item.getTitle(),
                item.getContent(),
                item.getSummary(),
                item.getApplicableScope(),
                item.getEvidenceJson(),
                item.getGitProvenanceJson(),
                item.getTags(),
                item.getConfidence(),
                item.getUsageCount(),
                item.getAcceptedCount(),
                item.getRejectedCount(),
                item.getLastUsedAt(),
                item.getStaleReason(),
                item.getReviewerId(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
