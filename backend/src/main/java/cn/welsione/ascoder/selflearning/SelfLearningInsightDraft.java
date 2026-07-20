package cn.welsione.ascoder.selflearning;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Self Learning Agent 从原始会话记录中整理出的候选洞察草稿。
 */
@Data
@NoArgsConstructor
public class SelfLearningInsightDraft {
    private LearningKnowledgeType type;
    private String title;
    private String summary;
    private String conclusion;
    private String businessContext;
    private String glossaryMappingsJson;
    private String codeSymbolsJson;
    private String warnings;
    private String applicableScope;
    private String evidenceJson;
    private String gitProvenanceJson;
    private String tags;
    private Double confidence;
}
