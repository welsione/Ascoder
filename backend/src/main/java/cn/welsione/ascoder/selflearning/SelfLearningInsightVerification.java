package cn.welsione.ascoder.selflearning;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 候选洞察复核结果，描述 LLM Agent 基于当前代码证据给出的审核建议。
 */
@Data
@NoArgsConstructor
public class SelfLearningInsightVerification {
    private String status;
    private String summary;
    private String codeEvidenceJson;
    private String gitProvenanceJson;
    private String suggestedWarnings;
    private String suggestedChanges;
    private Double confidence;
}
