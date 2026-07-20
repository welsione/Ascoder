package cn.welsione.ascoder.selflearning;

import lombok.Data;

/**
 * 更新自学习设置的请求体，字段为空时保留原值。
 */
@Data
public class UpdateSelfLearningSettingsRequest {
    Boolean enabled;
    Boolean autoCandidateEnabled;
    Boolean rawEventCaptureEnabled;
    Boolean autoInsightEnabled;
    Boolean answerInjectionEnabled;
    Boolean sourceVisibleEnabled;
    Boolean adminReviewRequired;
}
