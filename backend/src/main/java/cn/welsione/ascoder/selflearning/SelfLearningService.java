package cn.welsione.ascoder.selflearning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自学习门面服务，仅保留项目空间级摘要与设置查询/更新。
 * 具体业务已按聚合拆分到 InsightService、KnowledgeService、ExperienceService、
 * TermService、CorrectionService、AgentRunService 与 SelfLearningContextBuilder。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfLearningService {

    private final SelfLearningEntityLoader entityLoader;
    private final SelfLearningSettingsJpaRepository settingsRepository;

    @Transactional
    public SelfLearningSummaryResponse summary(Long projectSpaceId) {
        SelfLearningSettings settings = entityLoader.settings(projectSpaceId);
        return new SelfLearningSummaryResponse(
                SelfLearningSettingsResponse.from(settings),
                entityLoader.countRawEvents(projectSpaceId),
                entityLoader.countInsights(projectSpaceId),
                entityLoader.countPendingInsights(projectSpaceId),
                entityLoader.countKnowledgeItems(projectSpaceId),
                entityLoader.countExperiences(projectSpaceId),
                entityLoader.countCandidateExperiences(projectSpaceId),
                entityLoader.countCorrections(projectSpaceId),
                entityLoader.countTerms(projectSpaceId)
        );
    }

    @Transactional
    public SelfLearningSettingsResponse getSettings(Long projectSpaceId) {
        return SelfLearningSettingsResponse.from(entityLoader.settings(projectSpaceId));
    }

    @Transactional
    public SelfLearningSettingsResponse updateSettings(Long projectSpaceId, UpdateSelfLearningSettingsRequest request) {
        SelfLearningSettings settings = entityLoader.settings(projectSpaceId);
        if (request.getEnabled() != null) {
            settings.setEnabled(request.getEnabled());
        }
        if (request.getAutoCandidateEnabled() != null) {
            settings.setAutoCandidateEnabled(request.getAutoCandidateEnabled());
        }
        if (request.getRawEventCaptureEnabled() != null) {
            settings.setRawEventCaptureEnabled(request.getRawEventCaptureEnabled());
        }
        if (request.getAutoInsightEnabled() != null) {
            settings.setAutoInsightEnabled(request.getAutoInsightEnabled());
        }
        if (request.getAnswerInjectionEnabled() != null) {
            settings.setAnswerInjectionEnabled(request.getAnswerInjectionEnabled());
        }
        if (request.getSourceVisibleEnabled() != null) {
            settings.setSourceVisibleEnabled(request.getSourceVisibleEnabled());
        }
        if (request.getAdminReviewRequired() != null) {
            settings.setAdminReviewRequired(request.getAdminReviewRequired());
        }
        settings.touch();
        log.info("更新自学习设置，projectSpaceId={}，enabled={}", projectSpaceId, settings.isEnabled());
        return SelfLearningSettingsResponse.from(settingsRepository.save(settings));
    }
}
