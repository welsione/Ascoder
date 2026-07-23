package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SelfLearningService 集成测试：验证自学习摘要查询与设置更新。
 *
 * <p>该服务为纯 DB 服务，不触发 LLM Agent 调用。
 * {@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。</p>
 */
@Transactional
class SelfLearningServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private SelfLearningService service;

    @Autowired
    private SelfLearningSettingsJpaRepository settingsRepository;

    @Autowired
    private LearningTermJpaRepository termRepository;

    @Autowired
    private LearningKnowledgeItemJpaRepository knowledgeItemRepository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    @Test
    void summaryReturnsDefaultSettingsAndZeroCountsForNewProjectSpace() {
        Project project = dataFactory.createProject("sp-summary");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-summary");

        SelfLearningSummaryResponse summary = service.summary(space.getId());

        assertNotNull(summary.getSettings());
        assertEquals(space.getId(), summary.getSettings().getProjectSpaceId());
        // 新项目空间应自动创建默认设置
        assertFalse(summary.getSettings().isEnabled());
        assertTrue(summary.getSettings().isRawEventCaptureEnabled());
        assertTrue(summary.getSettings().isSourceVisibleEnabled());
        assertTrue(summary.getSettings().isAdminReviewRequired());
        // 新项目空间各计数应为 0
        assertEquals(0, summary.getRawEventCount());
        assertEquals(0, summary.getInsightCount());
        assertEquals(0, summary.getPendingInsightCount());
        assertEquals(0, summary.getKnowledgeItemCount());
        assertEquals(0, summary.getExperienceCount());
        assertEquals(0, summary.getCandidateCount());
        assertEquals(0, summary.getCorrectionCount());
        assertEquals(0, summary.getTermCount());
    }

    @Test
    void getSettingsReturnsDefaultSettingsForNewProjectSpace() {
        Project project = dataFactory.createProject("sp-settings-default");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-settings-default");

        SelfLearningSettingsResponse settings = service.getSettings(space.getId());

        assertNotNull(settings.getId());
        assertEquals(space.getId(), settings.getProjectSpaceId());
        assertFalse(settings.isEnabled());
        assertFalse(settings.isAutoCandidateEnabled());
        assertTrue(settings.isRawEventCaptureEnabled());
        assertFalse(settings.isAutoInsightEnabled());
        assertFalse(settings.isAnswerInjectionEnabled());
        assertTrue(settings.isSourceVisibleEnabled());
        assertTrue(settings.isAdminReviewRequired());
    }

    @Test
    void updateSettingsEnablesSelfLearning() {
        Project project = dataFactory.createProject("sp-enable");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-enable");

        UpdateSelfLearningSettingsRequest request = new UpdateSelfLearningSettingsRequest();
        request.setEnabled(true);
        request.setAutoInsightEnabled(true);

        SelfLearningSettingsResponse updated = service.updateSettings(space.getId(), request);

        assertTrue(updated.isEnabled());
        assertTrue(updated.isAutoInsightEnabled());
        // 未更新的字段保留默认值
        assertTrue(updated.isRawEventCaptureEnabled());
        assertTrue(updated.isAdminReviewRequired());
    }

    @Test
    void updateSettingsDisablesFields() {
        Project project = dataFactory.createProject("sp-disable");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-disable");

        // 先启用
        UpdateSelfLearningSettingsRequest enable = new UpdateSelfLearningSettingsRequest();
        enable.setEnabled(true);
        enable.setRawEventCaptureEnabled(true);
        enable.setAutoInsightEnabled(true);
        enable.setAnswerInjectionEnabled(true);
        service.updateSettings(space.getId(), enable);

        // 再禁用部分字段
        UpdateSelfLearningSettingsRequest disable = new UpdateSelfLearningSettingsRequest();
        disable.setEnabled(false);
        disable.setAutoInsightEnabled(false);
        disable.setAnswerInjectionEnabled(false);
        SelfLearningSettingsResponse updated = service.updateSettings(space.getId(), disable);

        assertFalse(updated.isEnabled());
        assertFalse(updated.isAutoInsightEnabled());
        assertFalse(updated.isAnswerInjectionEnabled());
        // 未更新的字段保留之前的值
        assertTrue(updated.isRawEventCaptureEnabled());
    }

    @Test
    void updateSettingsPersistsToDatabase() {
        Project project = dataFactory.createProject("sp-persist");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-persist");

        UpdateSelfLearningSettingsRequest request = new UpdateSelfLearningSettingsRequest();
        request.setEnabled(true);
        request.setAdminReviewRequired(false);
        service.updateSettings(space.getId(), request);

        SelfLearningSettings persisted = settingsRepository.findByProjectSpace_Id(space.getId()).orElseThrow();
        assertTrue(persisted.isEnabled());
        assertFalse(persisted.isAdminReviewRequired());
    }

    @Test
    void updateSettingsWithNullFieldsPreservesExistingValues() {
        Project project = dataFactory.createProject("sp-null");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-null");

        // 先设置初始值
        UpdateSelfLearningSettingsRequest initial = new UpdateSelfLearningSettingsRequest();
        initial.setEnabled(true);
        initial.setAutoCandidateEnabled(true);
        service.updateSettings(space.getId(), initial);

        // 传入全 null 的请求，所有字段应保留
        UpdateSelfLearningSettingsRequest noChange = new UpdateSelfLearningSettingsRequest();
        SelfLearningSettingsResponse updated = service.updateSettings(space.getId(), noChange);

        assertTrue(updated.isEnabled());
        assertTrue(updated.isAutoCandidateEnabled());
    }

    @Test
    void summaryReflectsCreatedTermsAndKnowledgeItems() {
        Project project = dataFactory.createProject("sp-summary-count");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-summary-count");

        // 先调用一次 summary 触发默认设置创建
        service.summary(space.getId());

        // 通过 JPA 直接插入术语，验证摘要计数
        LearningTerm term = new LearningTerm();
        term.setProjectSpace(space);
        term.setTerm("DDD");
        term.setDefinition("领域驱动设计");
        term.setConfidence(0.9);
        termRepository.save(term);

        // 通过 JPA 直接插入知识项，验证摘要计数
        LearningKnowledgeItem item = new LearningKnowledgeItem();
        item.setProjectSpace(space);
        item.setTitle("知识项");
        item.setContent("内容");
        item.setConfidence(0.8);
        knowledgeItemRepository.save(item);

        SelfLearningSummaryResponse summary = service.summary(space.getId());
        assertEquals(1, summary.getTermCount());
        assertEquals(1, summary.getKnowledgeItemCount());
    }
}
