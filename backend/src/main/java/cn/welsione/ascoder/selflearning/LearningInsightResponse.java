package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 候选洞察响应，展示 Self Learning Agent 的待审核整理结果。
 */
@Value
public class LearningInsightResponse {
    Long id;
    Long projectSpaceId;
    Long repositoryId;
    String sourceRawEventIdsJson;
    String sourceQuestionIdsJson;
    LearningKnowledgeType type;
    LearningInsightStatus status;
    String title;
    String summary;
    String conclusion;
    String businessContext;
    String glossaryMappingsJson;
    String codeSymbolsJson;
    String warnings;
    String applicableScope;
    String evidenceJson;
    String gitProvenanceJson;
    String tags;
    double confidence;
    Long reviewerId;
    String reviewComment;
    Date reviewedAt;
    Date createdAt;
    Date updatedAt;

    public static LearningInsightResponse from(LearningInsight insight) {
        return new LearningInsightResponse(
                insight.getId(),
                insight.getProjectSpace().getId(),
                insight.getRepository() == null ? null : insight.getRepository().getId(),
                insight.getSourceRawEventIdsJson(),
                insight.getSourceQuestionIdsJson(),
                insight.getType(),
                insight.getStatus(),
                insight.getTitle(),
                insight.getSummary(),
                insight.getConclusion(),
                insight.getBusinessContext(),
                insight.getGlossaryMappingsJson(),
                insight.getCodeSymbolsJson(),
                insight.getWarnings(),
                insight.getApplicableScope(),
                insight.getEvidenceJson(),
                insight.getGitProvenanceJson(),
                insight.getTags(),
                insight.getConfidence(),
                insight.getReviewerId(),
                insight.getReviewComment(),
                insight.getReviewedAt(),
                insight.getCreatedAt(),
                insight.getUpdatedAt()
        );
    }
}
