package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 经验知识响应，包含经验状态、证据和使用统计。
 */
@Value
public class LearningExperienceResponse {
    Long id;
    Long projectSpaceId;
    Long repositoryId;
    Long sourceQuestionId;
    LearningExperienceType type;
    LearningExperienceStatus status;
    String title;
    String problem;
    String conclusion;
    String applicableScope;
    String evidenceJson;
    String gitProvenanceJson;
    String tags;
    double confidence;
    int usageCount;
    int acceptedCount;
    int rejectedCount;
    Date createdAt;
    Date updatedAt;

    public static LearningExperienceResponse from(LearningExperience experience) {
        return new LearningExperienceResponse(
                experience.getId(),
                experience.getProjectSpace().getId(),
                experience.getRepository() == null ? null : experience.getRepository().getId(),
                experience.getSourceQuestion() == null ? null : experience.getSourceQuestion().getId(),
                experience.getType(),
                experience.getStatus(),
                experience.getTitle(),
                experience.getProblem(),
                experience.getConclusion(),
                experience.getApplicableScope(),
                experience.getEvidenceJson(),
                experience.getGitProvenanceJson(),
                experience.getTags(),
                experience.getConfidence(),
                experience.getUsageCount(),
                experience.getAcceptedCount(),
                experience.getRejectedCount(),
                experience.getCreatedAt(),
                experience.getUpdatedAt()
        );
    }
}
