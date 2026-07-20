package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 候选洞察复核响应，返回 LLM Agent 的代码复核结论和证据摘要。
 */
@Value
public class LearningInsightVerificationResponse {
    Long insightId;
    String status;
    String summary;
    String codeEvidenceJson;
    String gitProvenanceJson;
    String suggestedWarnings;
    String suggestedChanges;
    Double confidence;
    Date verifiedAt;

    public static LearningInsightVerificationResponse from(Long insightId, SelfLearningInsightVerification verification) {
        return new LearningInsightVerificationResponse(
                insightId,
                verification.getStatus(),
                verification.getSummary(),
                verification.getCodeEvidenceJson(),
                verification.getGitProvenanceJson(),
                verification.getSuggestedWarnings(),
                verification.getSuggestedChanges(),
                verification.getConfidence(),
                new Date()
        );
    }
}
