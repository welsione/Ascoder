package cn.welsione.ascoder.selflearning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存正式知识请求，供管理员维护审核后的知识内容。
 */
@Data
public class SaveLearningKnowledgeItemRequest {
    Long repositoryId;
    String sourceInsightIdsJson;
    String sourceRawEventIdsJson;
    LearningKnowledgeType type;
    LearningKnowledgeStatus status;
    @NotBlank
    @Size(max = 200)
    String title;
    @NotBlank
    @Size(max = 6000)
    String content;
    String summary;
    String applicableScope;
    String evidenceJson;
    String gitProvenanceJson;
    @Size(max = 500)
    String tags;
    Double confidence;
}
