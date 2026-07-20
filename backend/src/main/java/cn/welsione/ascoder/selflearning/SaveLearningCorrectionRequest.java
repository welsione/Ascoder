package cn.welsione.ascoder.selflearning;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建或更新纠错记录的请求体。
 */
@Data
public class SaveLearningCorrectionRequest {
    Long sourceQuestionId;
    @NotBlank
    String wrongConclusion;
    @NotBlank
    String correctedConclusion;
    String verificationProcess;
    String evidenceJson;
    LearningCorrectionStatus status;
}
