package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * InsightService 集成测试：验证候选洞察的创建、列表查询、状态流转（审核通过/拒绝/合并）、
 * 微调（mock LLM Agent）以及不存在时抛出领域异常。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。
 * verify/refine 方法调用 {@link SelfLearningInsightReviewAgent}（LLM Agent），通过 {@code @MockBean} 隔离。</p>
 */
@Transactional
class InsightServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private InsightService service;

    @Autowired
    private LearningInsightJpaRepository insightRepository;

    @Autowired
    private LearningKnowledgeItemJpaRepository knowledgeItemRepository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    @MockBean
    private SelfLearningInsightReviewAgent insightReviewAgent;

    private SaveLearningInsightRequest buildRequest(String title, String conclusion) {
        SaveLearningInsightRequest request = new SaveLearningInsightRequest();
        request.setTitle(title);
        request.setConclusion(conclusion);
        request.setType(LearningKnowledgeType.QUESTION_ANSWER);
        request.setConfidence(0.5);
        return request;
    }

    @Test
    void createInsightSuccess() {
        Project project = dataFactory.createProject("insight-create");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-create");

        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("候选洞察-创建", "这是结论内容"));

        assertNotNull(created.getId());
        assertEquals(space.getId(), created.getProjectSpaceId());
        assertEquals("候选洞察-创建", created.getTitle());
        assertEquals("这是结论内容", created.getConclusion());
        assertEquals(LearningInsightStatus.PENDING_REVIEW, created.getStatus());
        assertEquals(LearningKnowledgeType.QUESTION_ANSWER, created.getType());
        assertEquals(0.5, created.getConfidence());

        LearningInsight persisted = insightRepository.findById(created.getId()).orElseThrow();
        assertEquals("候选洞察-创建", persisted.getTitle());
        assertEquals(LearningInsightStatus.PENDING_REVIEW, persisted.getStatus());
    }

    @Test
    void listInsightsReturnsItemsForProjectSpace() {
        Project project = dataFactory.createProject("insight-list");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-list");
        Project otherProject = dataFactory.createProject("insight-list-other");
        ProjectSpace otherSpace = dataFactory.createProjectSpace(otherProject, "space-list-other");

        service.createInsight(space.getId(), buildRequest("洞察-1", "结论-1"));
        service.createInsight(space.getId(), buildRequest("洞察-2", "结论-2"));
        service.createInsight(otherSpace.getId(), buildRequest("洞察-其他", "结论-其他"));

        List<LearningInsightResponse> items = service.listInsights(space.getId(), null);

        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(i -> i.getProjectSpaceId().equals(space.getId())));
    }

    @Test
    void listInsightsFiltersByStatus() {
        Project project = dataFactory.createProject("insight-filter");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-filter");

        service.createInsight(space.getId(), buildRequest("待审核洞察", "结论"));
        LearningInsightResponse toReject = service.createInsight(
                space.getId(), buildRequest("将被拒绝", "结论"));
        ReviewLearningInsightRequest review = new ReviewLearningInsightRequest();
        review.setReviewComment("内容不准确");
        service.rejectInsight(space.getId(), toReject.getId(), review);

        List<LearningInsightResponse> pendingItems = service.listInsights(
                space.getId(), LearningInsightStatus.PENDING_REVIEW);
        List<LearningInsightResponse> rejectedItems = service.listInsights(
                space.getId(), LearningInsightStatus.REJECTED);

        assertEquals(1, pendingItems.size());
        assertEquals(LearningInsightStatus.PENDING_REVIEW, pendingItems.get(0).getStatus());
        assertEquals(1, rejectedItems.size());
        assertEquals(LearningInsightStatus.REJECTED, rejectedItems.get(0).getStatus());
    }

    @Test
    void approveInsightTransitionsToMergedAndCreatesKnowledgeItem() {
        Project project = dataFactory.createProject("insight-approve");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-approve");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("待审核洞察", "这是结论"));

        ReviewLearningInsightRequest review = new ReviewLearningInsightRequest();
        review.setReviewComment("审核通过");
        LearningInsightResponse approved = service.approveInsight(space.getId(), created.getId(), review);

        assertEquals(LearningInsightStatus.MERGED, approved.getStatus());
        assertNotNull(approved.getReviewedAt());
        assertTrue(approved.getReviewComment().startsWith("已审核通过并归纳为正式知识 #"));

        LearningInsight persisted = insightRepository.findById(created.getId()).orElseThrow();
        assertEquals(LearningInsightStatus.MERGED, persisted.getStatus());

        List<LearningKnowledgeItem> knowledgeItems = knowledgeItemRepository
                .findByProjectSpace_IdOrderByUpdatedAtDesc(space.getId());
        assertEquals(1, knowledgeItems.size());
        assertEquals("待审核洞察", knowledgeItems.get(0).getTitle());
        assertEquals(LearningKnowledgeStatus.VERIFIED, knowledgeItems.get(0).getStatus());
    }

    @Test
    void rejectInsightTransitionsToRejected() {
        Project project = dataFactory.createProject("insight-reject");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-reject");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("待审核洞察", "结论"));

        ReviewLearningInsightRequest review = new ReviewLearningInsightRequest();
        review.setReviewComment("结论不准确");
        LearningInsightResponse rejected = service.rejectInsight(space.getId(), created.getId(), review);

        assertEquals(LearningInsightStatus.REJECTED, rejected.getStatus());
        assertEquals("结论不准确", rejected.getReviewComment());
        assertNotNull(rejected.getReviewedAt());

        LearningInsight persisted = insightRepository.findById(created.getId()).orElseThrow();
        assertEquals(LearningInsightStatus.REJECTED, persisted.getStatus());
    }

    @Test
    void updateInsightModifiesFields() {
        Project project = dataFactory.createProject("insight-update");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-update");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("原标题", "原结论"));

        SaveLearningInsightRequest update = buildRequest("更新标题", "更新结论");
        update.setSummary("新增摘要");
        update.setTags("tag1,tag2");
        LearningInsightResponse updated = service.updateInsight(space.getId(), created.getId(), update);

        assertEquals("更新标题", updated.getTitle());
        assertEquals("更新结论", updated.getConclusion());
        assertEquals("新增摘要", updated.getSummary());
        assertEquals("tag1,tag2", updated.getTags());

        LearningInsight persisted = insightRepository.findById(created.getId()).orElseThrow();
        assertEquals("更新标题", persisted.getTitle());
        assertEquals("tag1,tag2", persisted.getTags());
    }

    @Test
    void approveThrowsWhenInsightNotFound() {
        Project project = dataFactory.createProject("insight-approve-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-approve-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.approveInsight(space.getId(), 999999L, new ReviewLearningInsightRequest()));
    }

    @Test
    void rejectThrowsWhenInsightNotFound() {
        Project project = dataFactory.createProject("insight-reject-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-reject-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.rejectInsight(space.getId(), 999999L, new ReviewLearningInsightRequest()));
    }

    @Test
    void approveThrowsWhenStatusNotPendingReview() {
        Project project = dataFactory.createProject("insight-double-approve");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-double-approve");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("洞察", "结论"));

        ReviewLearningInsightRequest review = new ReviewLearningInsightRequest();
        service.approveInsight(space.getId(), created.getId(), review);

        assertThrows(InvalidStateException.class, () ->
                service.approveInsight(space.getId(), created.getId(), review));
    }

    @Test
    void verifyInsightReturnsAgentResult() {
        Project project = dataFactory.createProject("insight-verify");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-verify");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("待复核洞察", "结论"));

        SelfLearningInsightVerification verification = new SelfLearningInsightVerification();
        verification.setStatus("CONFIRMED");
        verification.setSummary("代码证据与结论一致");
        verification.setConfidence(0.9);
        when(insightReviewAgent.verify(any(ProjectSpace.class), any(LearningInsight.class), anyList()))
                .thenReturn(verification);

        LearningInsightVerificationResponse response = service.verifyInsight(space.getId(), created.getId());

        assertEquals(created.getId(), response.getInsightId());
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals("代码证据与结论一致", response.getSummary());
        assertEquals(0.9, response.getConfidence());
        assertNotNull(response.getVerifiedAt());
    }

    @Test
    void refineInsightReturnsSuggestionFromAgent() {
        Project project = dataFactory.createProject("insight-refine");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-refine");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("待微调洞察", "原结论"));

        SelfLearningInsightDraft draft = new SelfLearningInsightDraft();
        draft.setTitle("微调后的标题");
        draft.setConclusion("微调后的结论");
        draft.setConfidence(0.8);
        draft.setType(LearningKnowledgeType.QUESTION_ANSWER);
        when(insightReviewAgent.refine(
                any(ProjectSpace.class), any(LearningInsight.class), anyList(), anyString()))
                .thenReturn(draft);

        RefineLearningInsightRequest request = new RefineLearningInsightRequest();
        request.setInstruction("让结论更简洁");
        RefineLearningInsightResponse response = service.refineInsight(space.getId(), created.getId(), request);

        assertEquals(created.getId(), response.getInsightId());
        assertNotNull(response.getSuggestion());
        assertEquals("微调后的标题", response.getSuggestion().getTitle());
        assertEquals("微调后的结论", response.getSuggestion().getConclusion());
        assertEquals(0.8, response.getSuggestion().getConfidence());
        assertNotNull(response.getAssistantMessage());
    }

    @Test
    void refineThrowsWhenInstructionBlank() {
        Project project = dataFactory.createProject("insight-refine-blank");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-refine-blank");
        LearningInsightResponse created = service.createInsight(
                space.getId(), buildRequest("洞察", "结论"));

        RefineLearningInsightRequest request = new RefineLearningInsightRequest();
        request.setInstruction("  ");

        assertThrows(ValidationException.class, () ->
                service.refineInsight(space.getId(), created.getId(), request));
    }

    @Test
    void cleanupLegacyInsightsRemovesMatchingInsights() {
        Project project = dataFactory.createProject("insight-cleanup");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-cleanup");

        LearningInsight legacyInsight = new LearningInsight();
        legacyInsight.setProjectSpace(space);
        legacyInsight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        legacyInsight.setType(LearningKnowledgeType.QUESTION_ANSWER);
        legacyInsight.setTitle("项目问答洞察：旧规则产物");
        legacyInsight.setConclusion("旧结论");
        legacyInsight.setConfidence(0.3);
        insightRepository.save(legacyInsight);

        service.createInsight(space.getId(), buildRequest("新候选洞察", "新结论"));

        CleanupLegacyInsightsResponse response = service.cleanupLegacyInsights(space.getId());

        assertEquals(1, response.getDeletedInsightCount());
        List<LearningInsight> remaining = insightRepository
                .findByProjectSpace_IdOrderByUpdatedAtDesc(space.getId());
        assertEquals(1, remaining.size());
        assertEquals("新候选洞察", remaining.get(0).getTitle());
    }
}
