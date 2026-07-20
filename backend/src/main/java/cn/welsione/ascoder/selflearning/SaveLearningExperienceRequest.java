package cn.welsione.ascoder.selflearning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建或更新经验知识的请求体。
 */
@Data
public class SaveLearningExperienceRequest {
    Long repositoryId;
    Long sourceQuestionId;
    LearningExperienceType type;
    LearningExperienceStatus status;
    @NotBlank
    @Size(max = 200)
    String title;
    String problem;
    @NotBlank
    String conclusion;
    String applicableScope;
    String evidenceJson;
    String gitProvenanceJson;
    @Size(max = 500)
    String tags;
    Double confidence;
}
