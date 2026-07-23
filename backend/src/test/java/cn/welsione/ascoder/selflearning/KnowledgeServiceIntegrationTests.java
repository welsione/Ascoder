package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KnowledgeService 集成测试：验证正式知识条目的 CRUD、状态流转与列表查询。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。</p>
 */
@Transactional
class KnowledgeServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private KnowledgeService service;

    @Autowired
    private LearningKnowledgeItemJpaRepository repository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    private SaveLearningKnowledgeItemRequest buildRequest(String title, String content) {
        SaveLearningKnowledgeItemRequest request = new SaveLearningKnowledgeItemRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setType(LearningKnowledgeType.QUESTION_ANSWER);
        request.setStatus(LearningKnowledgeStatus.VERIFIED);
        request.setConfidence(0.9);
        return request;
    }

    @Test
    void createKnowledgeItemSuccess() {
        Project project = dataFactory.createProject("kp-create");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-create");

        LearningKnowledgeItemResponse created = service.createKnowledgeItem(
                space.getId(), buildRequest("知识项-创建", "这是测试内容"));

        assertNotNull(created.getId());
        assertEquals(space.getId(), created.getProjectSpaceId());
        assertEquals("知识项-创建", created.getTitle());
        assertEquals("这是测试内容", created.getContent());
        assertEquals(LearningKnowledgeType.QUESTION_ANSWER, created.getType());
        assertEquals(LearningKnowledgeStatus.VERIFIED, created.getStatus());
        assertEquals(0.9, created.getConfidence());

        LearningKnowledgeItem persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals("知识项-创建", persisted.getTitle());
    }

    @Test
    void listKnowledgeItemsReturnsItemsForProjectSpace() {
        Project project = dataFactory.createProject("kp-list");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-list");
        Project otherProject = dataFactory.createProject("kp-list-other");
        ProjectSpace otherSpace = dataFactory.createProjectSpace(otherProject, "space-list-other");

        service.createKnowledgeItem(space.getId(), buildRequest("知识-1", "内容-1"));
        service.createKnowledgeItem(space.getId(), buildRequest("知识-2", "内容-2"));
        service.createKnowledgeItem(otherSpace.getId(), buildRequest("知识-其他", "内容-其他"));

        List<LearningKnowledgeItemResponse> items = service.listKnowledgeItems(space.getId(), null);

        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(i -> i.getProjectSpaceId().equals(space.getId())));
    }

    @Test
    void listKnowledgeItemsFiltersByStatus() {
        Project project = dataFactory.createProject("kp-filter");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-filter");

        service.createKnowledgeItem(space.getId(), buildRequest("知识-验证", "内容"));
        LearningKnowledgeItemResponse archived = service.createKnowledgeItem(
                space.getId(), buildRequest("知识-归档", "内容"));
        service.archiveKnowledgeItem(space.getId(), archived.getId());

        List<LearningKnowledgeItemResponse> verifiedItems = service.listKnowledgeItems(
                space.getId(), LearningKnowledgeStatus.VERIFIED);
        List<LearningKnowledgeItemResponse> deprecatedItems = service.listKnowledgeItems(
                space.getId(), LearningKnowledgeStatus.DEPRECATED);

        assertEquals(1, verifiedItems.size());
        assertEquals(LearningKnowledgeStatus.VERIFIED, verifiedItems.get(0).getStatus());
        assertEquals(1, deprecatedItems.size());
        assertEquals(LearningKnowledgeStatus.DEPRECATED, deprecatedItems.get(0).getStatus());
    }

    @Test
    void updateKnowledgeItemSuccess() {
        Project project = dataFactory.createProject("kp-update");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-update");
        LearningKnowledgeItemResponse created = service.createKnowledgeItem(
                space.getId(), buildRequest("原标题", "原内容"));

        SaveLearningKnowledgeItemRequest update = buildRequest("更新标题", "更新内容");
        update.setTags("tag1,tag2");
        LearningKnowledgeItemResponse updated = service.updateKnowledgeItem(
                space.getId(), created.getId(), update);

        assertEquals("更新标题", updated.getTitle());
        assertEquals("更新内容", updated.getContent());
        assertEquals("tag1,tag2", updated.getTags());

        LearningKnowledgeItem persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals("更新标题", persisted.getTitle());
        assertEquals("tag1,tag2", persisted.getTags());
    }

    @Test
    void archiveKnowledgeItemSetsDeprecatedStatus() {
        Project project = dataFactory.createProject("kp-archive");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-archive");
        LearningKnowledgeItemResponse created = service.createKnowledgeItem(
                space.getId(), buildRequest("知识-归档", "内容"));

        LearningKnowledgeItemResponse archived = service.archiveKnowledgeItem(space.getId(), created.getId());

        assertEquals(LearningKnowledgeStatus.DEPRECATED, archived.getStatus());
        LearningKnowledgeItem persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals(LearningKnowledgeStatus.DEPRECATED, persisted.getStatus());
    }

    @Test
    void markKnowledgeItemStaleSetsStaleStatusAndReason() {
        Project project = dataFactory.createProject("kp-stale");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-stale");
        LearningKnowledgeItemResponse created = service.createKnowledgeItem(
                space.getId(), buildRequest("知识-过期", "内容"));

        ReviewLearningInsightRequest review = new ReviewLearningInsightRequest();
        review.setReviewComment("内容已过时");
        LearningKnowledgeItemResponse stale = service.markKnowledgeItemStale(
                space.getId(), created.getId(), review);

        assertEquals(LearningKnowledgeStatus.STALE, stale.getStatus());
        assertEquals("内容已过时", stale.getStaleReason());
    }

    @Test
    void deleteKnowledgeItemRemovesRecord() {
        Project project = dataFactory.createProject("kp-delete");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-delete");
        LearningKnowledgeItemResponse created = service.createKnowledgeItem(
                space.getId(), buildRequest("知识-删除", "内容"));

        service.deleteKnowledgeItem(space.getId(), created.getId());

        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    void updateThrowsWhenNotFound() {
        Project project = dataFactory.createProject("kp-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.updateKnowledgeItem(space.getId(), 999999L, buildRequest("标题", "内容")));
    }

    @Test
    void deleteThrowsWhenNotFound() {
        Project project = dataFactory.createProject("kp-delete-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-delete-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.deleteKnowledgeItem(space.getId(), 999999L));
    }

    @Test
    void archiveThrowsWhenNotFound() {
        Project project = dataFactory.createProject("kp-archive-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-archive-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.archiveKnowledgeItem(space.getId(), 999999L));
    }
}
