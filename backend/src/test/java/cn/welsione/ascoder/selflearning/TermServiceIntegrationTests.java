package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TermService 集成测试：验证自学习术语的 CRUD、重复校验与列表查询。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。</p>
 */
@Transactional
class TermServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private TermService service;

    @Autowired
    private LearningTermJpaRepository repository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    private SaveLearningTermRequest buildRequest(String term, String definition) {
        SaveLearningTermRequest request = new SaveLearningTermRequest();
        request.setTerm(term);
        request.setDefinition(definition);
        request.setConfidence(0.8);
        return request;
    }

    @Test
    void createTermSuccess() {
        Project project = dataFactory.createProject("tp-create");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-create");

        LearningTermResponse created = service.createTerm(
                space.getId(), buildRequest("DDD", "领域驱动设计"));

        assertNotNull(created.getId());
        assertEquals(space.getId(), created.getProjectSpaceId());
        assertEquals("DDD", created.getTerm());
        assertEquals("领域驱动设计", created.getDefinition());
        assertEquals("manual", created.getSource());
        assertEquals(0.8, created.getConfidence());

        LearningTerm persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals("DDD", persisted.getTerm());
    }

    @Test
    void createDuplicateTermThrowsDuplicateException() {
        Project project = dataFactory.createProject("tp-dup");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-dup");
        service.createTerm(space.getId(), buildRequest("DDD", "领域驱动设计"));

        assertThrows(DuplicateException.class, () ->
                service.createTerm(space.getId(), buildRequest("DDD", "重复定义")));
    }

    @Test
    void createDuplicateTermInDifferentProjectSpaceSucceeds() {
        Project project = dataFactory.createProject("tp-dup-multi");
        ProjectSpace space1 = dataFactory.createProjectSpace(project, "space-dup-1");
        ProjectSpace space2 = dataFactory.createProjectSpace(project, "space-dup-2");

        service.createTerm(space1.getId(), buildRequest("DDD", "领域驱动设计"));
        assertDoesNotThrow(() ->
                service.createTerm(space2.getId(), buildRequest("DDD", "另一个空间的定义")));
    }

    @Test
    void listTermsReturnsTermsForProjectSpace() {
        Project project = dataFactory.createProject("tp-list");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-list");
        Project otherProject = dataFactory.createProject("tp-list-other");
        ProjectSpace otherSpace = dataFactory.createProjectSpace(otherProject, "space-list-other");

        service.createTerm(space.getId(), buildRequest("术语-1", "定义-1"));
        service.createTerm(space.getId(), buildRequest("术语-2", "定义-2"));
        service.createTerm(otherSpace.getId(), buildRequest("术语-其他", "定义-其他"));

        List<LearningTermResponse> terms = service.listTerms(space.getId());

        assertEquals(2, terms.size());
        assertTrue(terms.stream().allMatch(t -> t.getProjectSpaceId().equals(space.getId())));
    }

    @Test
    void updateTermSuccess() {
        Project project = dataFactory.createProject("tp-update");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-update");
        LearningTermResponse created = service.createTerm(
                space.getId(), buildRequest("DDD", "领域驱动设计"));

        SaveLearningTermRequest update = buildRequest("DDD-updated", "更新后的定义");
        update.setScope("后端");
        update.setExamples("示例内容");
        LearningTermResponse updated = service.updateTerm(space.getId(), created.getId(), update);

        assertEquals("DDD-updated", updated.getTerm());
        assertEquals("更新后的定义", updated.getDefinition());
        assertEquals("后端", updated.getScope());
        assertEquals("示例内容", updated.getExamples());

        LearningTerm persisted = repository.findById(created.getId()).orElseThrow();
        assertEquals("DDD-updated", persisted.getTerm());
        assertEquals("后端", persisted.getScope());
    }

    @Test
    void deleteTermRemovesRecord() {
        Project project = dataFactory.createProject("tp-delete");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-delete");
        LearningTermResponse created = service.createTerm(
                space.getId(), buildRequest("DDD", "领域驱动设计"));

        service.deleteTerm(space.getId(), created.getId());

        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    void updateThrowsWhenNotFound() {
        Project project = dataFactory.createProject("tp-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.updateTerm(space.getId(), 999999L, buildRequest("DDD", "定义")));
    }

    @Test
    void deleteThrowsWhenNotFound() {
        Project project = dataFactory.createProject("tp-delete-notfound");
        ProjectSpace space = dataFactory.createProjectSpace(project, "space-delete-notfound");

        assertThrows(ResourceNotFoundException.class, () ->
                service.deleteTerm(space.getId(), 999999L));
    }
}
