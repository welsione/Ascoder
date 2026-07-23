package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.persistence.ConversationJpaRepository;
import cn.welsione.ascoder.question.persistence.QueryPlanJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationService 集成测试：验证会话删除级联清理、运行中问题保护与问题列表查询。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。</p>
 */
@Transactional
class ConversationServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private ConversationService service;

    @Autowired
    private ConversationJpaRepository conversationRepository;

    @Autowired
    private QuestionJpaRepository questionRepository;

    @Autowired
    private QueryPlanJpaRepository queryPlanRepository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deleteCascadesToQuestionsAndQueryPlans() {
        Conversation conversation = dataFactory.createConversation("测试级联删除会话");
        Question q1 = dataFactory.createQuestion(conversation, "问题一");
        Question q2 = dataFactory.createQuestion(conversation, "问题二");
        QueryPlan plan1 = dataFactory.createQueryPlan(q1);
        QueryPlan plan2 = dataFactory.createQueryPlan(q2);

        service.delete(conversation.getId());

        entityManager.flush();
        entityManager.clear();

        assertTrue(conversationRepository.findById(conversation.getId()).isEmpty(),
                "会话应被删除");
        assertTrue(questionRepository.findById(q1.getId()).isEmpty(),
                "问题一应被删除");
        assertTrue(questionRepository.findById(q2.getId()).isEmpty(),
                "问题二应被删除");
        assertTrue(queryPlanRepository.findById(plan1.getId()).isEmpty(),
                "查询规划一应被删除");
        assertTrue(queryPlanRepository.findById(plan2.getId()).isEmpty(),
                "查询规划二应被删除");
    }

    @Test
    void deleteThrowsWhenConversationNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.delete(999999L));
    }

    @Test
    void deleteThrowsWhenQuestionIsRunning() {
        Conversation conversation = dataFactory.createConversation("测试运行中保护会话");
        dataFactory.createQuestion(conversation, "运行中问题", QuestionStatus.RUNNING);

        assertThrows(InvalidStateException.class, () -> service.delete(conversation.getId()));

        entityManager.flush();
        entityManager.clear();

        assertTrue(conversationRepository.findById(conversation.getId()).isPresent(),
                "会话不应被删除");
    }

    @Test
    void deleteSucceedsWhenQuestionsAreSucceeded() {
        Conversation conversation = dataFactory.createConversation("测试已完成问题会话");
        dataFactory.createQuestion(conversation, "已完成问题", QuestionStatus.SUCCEEDED);

        service.delete(conversation.getId());

        entityManager.flush();
        entityManager.clear();

        assertTrue(conversationRepository.findById(conversation.getId()).isEmpty(),
                "会话应被删除");
    }

    @Test
    void listQuestionsByConversationReturnsCorrectOrder() {
        Conversation conversation = dataFactory.createConversation("测试问题列表会话");
        Question q1 = dataFactory.createQuestion(conversation, "第一个问题");
        Question q2 = dataFactory.createQuestion(conversation, "第二个问题");
        Question q3 = dataFactory.createQuestion(conversation, "第三个问题");

        List<Question> questions = questionRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        assertEquals(3, questions.size());
        assertEquals("第一个问题", questions.get(0).getText());
        assertEquals("第二个问题", questions.get(1).getText());
        assertEquals("第三个问题", questions.get(2).getText());
    }

    @Test
    void deleteConversationWithNoQuestionsSucceeds() {
        Conversation conversation = dataFactory.createConversation("空会话");

        service.delete(conversation.getId());

        entityManager.flush();
        entityManager.clear();

        assertTrue(conversationRepository.findById(conversation.getId()).isEmpty(),
                "空会话应被删除");
    }
}
