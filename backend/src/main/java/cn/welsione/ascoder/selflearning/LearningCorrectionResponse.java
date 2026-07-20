package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 纠错记录响应，展示错误结论、正确结论和验证状态。
 */
@Value
public class LearningCorrectionResponse {
    Long id;
    Long projectSpaceId;
    Long sourceQuestionId;
    String wrongConclusion;
    String correctedConclusion;
    String verificationProcess;
    String evidenceJson;
    LearningCorrectionStatus status;
    Date createdAt;
    Date updatedAt;

    public static LearningCorrectionResponse from(LearningCorrection correction) {
        return new LearningCorrectionResponse(
                correction.getId(),
                correction.getProjectSpace().getId(),
                correction.getSourceQuestion() == null ? null : correction.getSourceQuestion().getId(),
                correction.getWrongConclusion(),
                correction.getCorrectedConclusion(),
                correction.getVerificationProcess(),
                correction.getEvidenceJson(),
                correction.getStatus(),
                correction.getCreatedAt(),
                correction.getUpdatedAt()
        );
    }
}
