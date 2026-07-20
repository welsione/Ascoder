package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.loganalysis.application.LogAnalysisService;
import cn.welsione.ascoder.loganalysis.application.LogUploadService;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import cn.welsione.ascoder.loganalysis.domain.LogUploadStatus;
import cn.welsione.ascoder.question.api.CreateQuestionRequest;
import cn.welsione.ascoder.question.domain.Conversation;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.persistence.QueryPlanJpaRepository;
import cn.welsione.ascoder.question.persistence.QuestionJpaRepository;
import cn.welsione.ascoder.question.planning.QuestionPlan;
import cn.welsione.ascoder.question.planning.QuestionPlanner;
import cn.welsione.ascoder.question.planning.QuestionType;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceMember;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceStatus;
import cn.welsione.ascoder.selflearning.AgentRunService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuestionService 单元测试，覆盖流式问题准备过程中的规划复用和日志上传校验。
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceTests {

    @Mock
    private QuestionJpaRepository repository;
    @Mock
    private QueryPlanJpaRepository queryPlanRepository;
    @Mock
    private ProjectSpaceService projectSpaceService;
    @Mock
    private QuestionPlanner questionPlanner;
    @Mock
    private ConversationResolver conversationResolver;
    @Mock
    private AgentRequestBuilder requestBuilder;
    @Mock
    private AnswerWriter answerWriter;
    @Mock
    private LogAnalysisService logAnalysisService;
    @Mock
    private LogUploadService logUploadService;
    @Mock
    private AgentRunService selfLearningService;

    private QuestionService service;
    private ProjectSpace projectSpace;
    private ProjectSpaceMember member;
    private Conversation conversation;
    private QuestionPlan plan;
    private AgentRequest agentRequest;

    @BeforeEach
    void setUp() {
        service = new QuestionService(
                repository,
                queryPlanRepository,
                projectSpaceService,
                questionPlanner,
                conversationResolver,
                requestBuilder,
                answerWriter,
                logAnalysisService,
                logUploadService,
                selfLearningService,
                new ObjectMapper()
        );

        projectSpace = new ProjectSpace();
        ReflectionTestUtils.setField(projectSpace, "id", 7L);
        projectSpace.setName("Ascoder");
        projectSpace.setRootPath("/repo/ascoder");
        projectSpace.setStatus(ProjectSpaceStatus.READY);

        CodeRepository codeRepository = new CodeRepository();
        ReflectionTestUtils.setField(codeRepository, "id", 11L);
        codeRepository.setName("backend");

        member = new ProjectSpaceMember();
        member.setProjectSpace(projectSpace);
        member.setRepository(codeRepository);
        member.setBranchName("main");
        member.setRole("repository");

        conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", 13L);
        conversation.setTitle("入口分析");

        plan = new QuestionPlan(
                QuestionType.ENTRY_POINT,
                List.of("入口"),
                List.of("codegraph_context"),
                List.of(),
                "识别入口",
                0.8,
                List.of("ENTRY_POINT:入口"),
                List.of()
        );
        agentRequest = new AgentRequest(
                7L,
                null,
                13L,
                "Ascoder",
                "/repo/ascoder",
                null,
                List.of(),
                "入口在哪里",
                "developer",
                plan.toPromptText(),
                "",
                null,
                plan,
                null
        );
    }

    @Test
    void prepareStreamBuildsAgentRequestWithSinglePlannedResult() {
        CreateQuestionRequest request = new CreateQuestionRequest(7L, null, "入口在哪里", "developer", List.of());
        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(projectSpaceService.members(7L)).thenReturn(List.of(member));
        when(conversationResolver.resolve(request, projectSpace, member)).thenReturn(conversation);
        when(questionPlanner.plan("入口在哪里", "developer")).thenReturn(plan);
        when(repository.saveAndFlush(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            ReflectionTestUtils.setField(question, "id", 17L);
            return question;
        });
        when(queryPlanRepository.save(any(QueryPlan.class))).thenAnswer(invocation -> {
            QueryPlan queryPlan = invocation.getArgument(0);
            ReflectionTestUtils.setField(queryPlan, "id", 19L);
            return queryPlan;
        });
        when(requestBuilder.build(17L, projectSpace, 13L, List.of(member),
                "入口在哪里", "developer", plan.toPromptText(), plan, List.of()))
                .thenReturn(agentRequest);

        QuestionService.PendingQuestion pending = service.prepareStream(request);

        assertThat(pending.getQuestion().getId()).isEqualTo(17L);
        assertThat(pending.getAgentRequest()).isSameAs(agentRequest);
        verify(questionPlanner).plan("入口在哪里", "developer");
        verify(questionPlanner, never()).planForType(any(), any(), any());
    }

    @Test
    void prepareResumeStreamReusesPersistedQueryPlan() {
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.setProjectSpaceId(7L);
        question.setConversation(conversation);
        question.setRepositoryId(11L);
        question.setText("入口在哪里");
        question.setRole("developer");
        question.setBranchName("main");
        question.running();

        QueryPlan queryPlan = new QueryPlan();
        ReflectionTestUtils.setField(queryPlan, "id", 19L);
        queryPlan.setQuestion(question);
        queryPlan.setType(plan.getType());
        queryPlan.setRewrittenQueriesJson("[\"入口\"]");
        queryPlan.setRecommendedToolsJson("[\"codegraph_context\"]");
        queryPlan.setRecommendedSkillsJson("[]");
        queryPlan.setConfidence(0.8);
        queryPlan.setMatchedSignalsJson("[\"ENTRY_POINT:入口\"]");
        queryPlan.setAlternativeTypesJson("[]");
        queryPlan.setReasoning("识别入口");

        when(repository.findById(17L)).thenReturn(Optional.of(question));
        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(projectSpaceService.members(7L)).thenReturn(List.of(member));
        when(queryPlanRepository.findByQuestionId(17L)).thenReturn(Optional.of(queryPlan));
        when(repository.saveAndFlush(question)).thenReturn(question);
        when(requestBuilder.build(17L, projectSpace, 13L, List.of(member),
                "入口在哪里", "developer", plan.toPromptText(), plan, List.of()))
                .thenReturn(agentRequest);

        QuestionService.PendingQuestion pending = service.prepareResumeStream(17L);

        assertThat(pending.getQuestion().getId()).isEqualTo(17L);
        assertThat(pending.getQuestion().getStatus()).isEqualTo(QuestionStatus.RUNNING);
        assertThat(pending.getAgentRequest()).isSameAs(agentRequest);
        verify(questionPlanner, never()).plan(any(), any());
        verify(questionPlanner, never()).planForType(any(), any(), any());
    }

    @Test
    void prepareResumeStreamRejectsFailedQuestion() {
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.fail(null, "boom");

        when(repository.findById(17L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.prepareResumeStream(17L))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("只有运行中的问题可以恢复");
    }

    @Test
    void prepareRetryStreamRequiresFailedQuestion() {
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.running();

        when(repository.findById(17L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.prepareRetryStream(17L))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("只有失败的问题可以重试");
    }

    @Test
    void prepareStreamRejectsLogUploadFromAnotherProjectSpace() {
        CreateQuestionRequest request = new CreateQuestionRequest(7L, null, "分析日志", "developer", List.of(31L));
        ProjectSpace anotherSpace = new ProjectSpace();
        ReflectionTestUtils.setField(anotherSpace, "id", 8L);
        LogUpload upload = new LogUpload();
        upload.setProjectSpace(anotherSpace);
        upload.setStatus(LogUploadStatus.READY);

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(projectSpaceService.members(7L)).thenReturn(List.of(member));
        when(logUploadService.get(31L)).thenReturn(upload);

        assertThatThrownBy(() -> service.prepareStream(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("日志上传不属于当前项目空间");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void prepareStreamRejectsUnreadyLogUpload() {
        CreateQuestionRequest request = new CreateQuestionRequest(7L, null, "分析日志", "developer", List.of(31L));
        LogUpload upload = new LogUpload();
        upload.setProjectSpace(projectSpace);
        upload.setStatus(LogUploadStatus.PARSING);

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(projectSpaceService.members(7L)).thenReturn(List.of(member));
        when(logUploadService.get(31L)).thenReturn(upload);

        assertThatThrownBy(() -> service.prepareStream(request))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("日志上传尚未完成预处理");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void getThrowsDomainNotFoundWhenQuestionMissing() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .hasMessageContaining("问题 不存在: 404");
    }
}
