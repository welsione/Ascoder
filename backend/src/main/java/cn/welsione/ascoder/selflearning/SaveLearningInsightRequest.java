package cn.welsione.ascoder.selflearning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存候选洞察请求，供 Self Learning Agent 或管理员编辑待审核内容。
 */
@Data
public class SaveLearningInsightRequest {
    Long repositoryId;
    String sourceRawEventIdsJson;
    String sourceQuestionIdsJson;
    LearningKnowledgeType type;
    LearningInsightStatus status;
    @NotBlank
    @Size(max = 200)
    String title;
    @Size(max = 6000)
    String summary;
    @NotBlank
    @Size(max = 6000)
    String conclusion;
    String businessContext;
    String glossaryMappingsJson;
    String codeSymbolsJson;
    String warnings;
    String applicableScope;
    String evidenceJson;
    String gitProvenanceJson;
    @Size(max = 500)
    String tags;
    Double confidence;
}
