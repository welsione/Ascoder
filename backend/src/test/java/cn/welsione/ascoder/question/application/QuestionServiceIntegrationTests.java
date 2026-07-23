package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.AbstractIntegrationTest;
import cn.welsione.ascoder.IntegrationTestDataFactory;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.question.api.CreateQuestionRequest;
import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import cn.welsione.ascoder.repository.project.Project;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuestionService 集成测试：验证问题的创建、列表查询、失败标记与状态流转。
 *
 * <p>{@code @Transactional} 保证每个测试方法结束后自动回滚，不污染数据库。
 * prepareStream/prepareRetryStream 涉及 AgentRequest 构建和日志分析任务启动，不在本测试覆盖范围。</p>
 */
@Transactional
class QuestionServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private QuestionService service;

    @Autowired
    private QuestionJpaRepository questionRepository;

    @Autowired
    private IntegrationTestDataFactory dataFactory;

    @Test
    void getReturnsQuestionResponse() {
        Conversation conversation = dataFactory.createConversation("测试获取问题会话");
        Question question = dataFactory.createQuestion(conversation, "这个问题怎么处理？");

        QuestionResponse response = service.get(question.getId());

        assertEquals(question.getId(), response.getId());
        assertEquals("这个问题怎么处理？", response.getText());
        assertEquals(conversation.getId(), response.getConversationId());
        assertEquals(QuestionStatus.PENDING, response.getStatus());
    }

    @Test
    void getThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(999999L));
    }

    @Test
    void listReturnsRecentQuestions() {
        Conversation conversation = dataFactory.createConversation("测试列表会话");
        Question q1 = dataFactory.createQuestion(conversation, "问题一");
        Question q2 = dataFactory.createQuestion(conversation, "问题二");

        List<QuestionResponse> responses = service.list();

        assertTrue(responses.size() >= 2);
        assertTrue(responses.stream().anyMatch(r -> r.getId().equals(q1.getId())));
        assertTrue(responses.stream().anyMatch(r -> r.getId().equals(q2.getId())));
    }

    @Test
    void listByConversationReturnsOrderedQuestions() {
        Conversation conversation = dataFactory.createConversation("测试会话内问题列表");
        Question q1 = dataFactory.createQuestion(conversation, "第一个问题");
        Question q2 = dataFactory.createQuestion(conversation, "第二个问题");
        Question q3 = dataFactory.createQuestion(conversation, "第三个问题");

        List<QuestionResponse> responses = service.listByConversation(conversation.getId());

        assertEquals(3, responses.size());
        assertEquals("第一个问题", responses.get(0).getText());
        assertEquals("第二个问题", responses.get(1).getText());
        assertEquals("第三个问题", responses.get(2).getText());
    }

    @Test
    void listByConversationReturnsEmptyWhenNoQuestions() {
        Conversation conversation = dataFactory.createConversation("空会话");

        List<QuestionResponse> responses = service.listByConversation(conversation.getId());

        assertTrue(responses.isEmpty());
    }

    @Test
    void failMarksPendingQuestionAsFailed() {
        Conversation conversation = dataFactory.createConversation("测试失败会话");
        Question question = dataFactory.createQuestion(conversation, "将失败的问题");

        QuestionResponse response = service.fail(question.getId(), "Agent 执行超时");

        assertEquals(QuestionStatus.FAILED, response.getStatus());
        assertEquals("Agent 执行超时", response.getErrorMessage());

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals(QuestionStatus.FAILED, persisted.getStatus());
        assertEquals("Agent 执行超时", persisted.getErrorMessage());
        assertNotNull(persisted.getCompletedAt());
    }

    @Test
    void failMarksRunningQuestionAsFailed() {
        Conversation conversation = dataFactory.createConversation("测试运行中失败会话");
        Question question = dataFactory.createQuestion(conversation, "运行中的问题", QuestionStatus.RUNNING);

        QuestionResponse response = service.fail(question.getId(), "运行中出错");

        assertEquals(QuestionStatus.FAILED, response.getStatus());
        assertEquals("运行中出错", response.getErrorMessage());

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals(QuestionStatus.FAILED, persisted.getStatus());
    }

    @Test
    void failWithPartialAnswerPreservesPartialContent() {
        Conversation conversation = dataFactory.createConversation("测试部分回答会话");
        Question question = dataFactory.createQuestion(conversation, "问题", QuestionStatus.RUNNING);

        QuestionResponse response = service.fail(
                question.getId(), "Agent 中断", "code context", "部分回答内容", "分析过程");

        assertEquals(QuestionStatus.FAILED, response.getStatus());
        assertEquals("Agent 中断", response.getErrorMessage());

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals("部分回答内容", persisted.getAnswer());
        assertEquals("分析过程", persisted.getAnalysisProcess());
    }

    @Test
    void failThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.fail(999999L, "不存在"));
    }

    @Test
    void failDoesNotChangeAlreadySucceededQuestion() {
        Conversation conversation = dataFactory.createConversation("测试已完成问题会话");
        Question question = dataFactory.createQuestion(conversation, "已完成问题", QuestionStatus.SUCCEEDED);

        QuestionResponse response = service.fail(question.getId(), "不应再失败");

        assertEquals(QuestionStatus.SUCCEEDED, response.getStatus());
        assertNull(response.getErrorMessage());

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals(QuestionStatus.SUCCEEDED, persisted.getStatus());
    }

    @Test
    void succeedMarksRunningQuestionAsSucceeded() {
        Conversation conversation = dataFactory.createConversation("测试成功会话");
        Question question = dataFactory.createQuestion(conversation, "运行中的问题", QuestionStatus.RUNNING);

        QuestionResponse response = service.succeed(question.getId(), "这是完整回答");

        assertEquals(QuestionStatus.SUCCEEDED, response.getStatus());
        assertEquals("这是完整回答", response.getAnswer());

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals(QuestionStatus.SUCCEEDED, persisted.getStatus());
        assertEquals("这是完整回答", persisted.getAnswer());
        assertNotNull(persisted.getCompletedAt());
    }

    @Test
    void succeedThrowsWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.succeed(999999L, "回答"));
    }

    @Test
    void cancelStreamMarksRunningQuestionAsFailed() {
        Conversation conversation = dataFactory.createConversation("测试取消会话");
        Question question = dataFactory.createQuestion(conversation, "运行中的问题", QuestionStatus.RUNNING);

        QuestionResponse response = service.cancelStream(question.getId());

        assertEquals(QuestionStatus.FAILED, response.getStatus());
        assertEquals("用户已停止回答", response.getErrorMessage());

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals(QuestionStatus.FAILED, persisted.getStatus());
    }

    @Test
    void updateProgressUpdatesRunningQuestion() {
        Conversation conversation = dataFactory.createConversation("测试进度会话");
        Question question = dataFactory.createQuestion(conversation, "运行中的问题", QuestionStatus.RUNNING);

        service.updateProgress(question.getId(), "code context", "部分回答", "分析中");

        Question persisted = questionRepository.findById(question.getId()).orElseThrow();
        assertEquals("code context", persisted.getCodegraphContext());
        assertEquals("部分回答", persisted.getAnswer());
        assertEquals("分析中", persisted.getAnalysisProcess());
        assertEquals(QuestionStatus.RUNNING, persisted.getStatus());
    }

    @Test
    void prepareRetryStreamThrowsWhenQuestionNotFailed() {
        Conversation conversation = dataFactory.createConversation("测试重试非失败会话");
        Question question = dataFactory.createQuestion(conversation, "非失败问题", QuestionStatus.PENDING);

        assertThrows(InvalidStateException.class, () ->
                service.prepareRetryStream(question.getId()));
    }

    @Test
    void prepareResumeStreamThrowsWhenQuestionNotRunning() {
        Conversation conversation = dataFactory.createConversation("测试恢复非运行会话");
        Question question = dataFactory.createQuestion(conversation, "非运行问题", QuestionStatus.PENDING);

        assertThrows(InvalidStateException.class, () ->
                service.prepareResumeStream(question.getId()));
    }
}
