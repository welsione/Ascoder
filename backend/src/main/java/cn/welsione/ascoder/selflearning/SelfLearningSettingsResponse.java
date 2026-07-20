package cn.welsione.ascoder.selflearning;

import lombok.Value;

import java.util.Date;

/**
 * 自学习设置响应，供前端展示项目空间级策略。
 */
@Value
public class SelfLearningSettingsResponse {
    Long id;
    Long projectSpaceId;
    boolean enabled;
    boolean autoCandidateEnabled;
    boolean rawEventCaptureEnabled;
    boolean autoInsightEnabled;
    boolean answerInjectionEnabled;
    boolean sourceVisibleEnabled;
    boolean adminReviewRequired;
    Date createdAt;
    Date updatedAt;

    public static SelfLearningSettingsResponse from(SelfLearningSettings settings) {
        return new SelfLearningSettingsResponse(
                settings.getId(),
                settings.getProjectSpace().getId(),
                settings.isEnabled(),
                settings.isAutoCandidateEnabled(),
                settings.isRawEventCaptureEnabled(),
                settings.isAutoInsightEnabled(),
                settings.isAnswerInjectionEnabled(),
                settings.isSourceVisibleEnabled(),
                settings.isAdminReviewRequired(),
                settings.getCreatedAt(),
                settings.getUpdatedAt()
        );
    }
}
