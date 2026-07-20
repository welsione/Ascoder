package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.application.QuestionAnsweredEvent;
import cn.welsione.ascoder.question.application.QuestionQueryPort;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QuestionStatus;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.question.planning.QuestionType;
import cn.welsione.ascoder.repository.CodeRepository;
import cn.welsione.ascoder.repository.RepositoryQueryPort;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.repository.projectspace.ProjectSpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自学习拆分服务测试，约束正式知识注入和候选洞察生成的安全边界。
 * 原门面 SelfLearningService 已按聚合拆分，本测试分别构造 ContextBuilder、InsightService 与 AgentRunService。
 */
@ExtendWith(MockitoExtension.class)
class SelfLearningServiceTests {

    @Mock
    private ProjectSpaceService projectSpaceService;
    @Mock
    private RepositoryQueryPort repositoryQueryPort;
    @Mock
    private QuestionQueryPort questionQueryPort;
    @Mock
    private SelfLearningSettingsJpaRepository settingsRepository;
    @Mock
    private LearningExperienceJpaRepository experienceRepository;
    @Mock
    private LearningTermJpaRepository termRepository;
    @Mock
    private LearningCorrectionJpaRepository correctionRepository;
    @Mock
    private LearningRawEventJpaRepository rawEventRepository;
    @Mock
    private LearningInsightJpaRepository insightRepository;
    @Mock
    private LearningKnowledgeItemJpaRepository knowledgeRepository;
    @Mock
    private SelfLearningInsightAgent insightAgent;
    @Mock
    private SelfLearningInsightReviewAgent insightReviewAgent;
    @Mock
    private InsightStateMachine insightStateMachine;
    @Mock
    private InsightFieldTruncator insightFieldTruncator;
    @Mock
    private LearningAgentRunJpaRepository agentRunRepository;

    private SelfLearningContextBuilder contextBuilder;
    private InsightService insightService;
    private AgentRunService agentRunService;
    private SelfLearningEventListener eventListener;
    private ProjectSpace projectSpace;

    @BeforeEach
    void setUp() {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        SelfLearningEntityLoader entityLoader = new SelfLearningEntityLoader(
                projectSpaceService,
                settingsRepository,
                repositoryQueryPort,
                questionQueryPort,
                insightRepository,
                knowledgeRepository,
                rawEventRepository,
                agentRunRepository,
                experienceRepository,
                termRepository,
                correctionRepository);
        ConversationRecordHelper conversationRecordHelper = new ConversationRecordHelper(
                rawEventRepository, questionQueryPort, entityLoader, objectMapper);
        contextBuilder = new SelfLearningContextBuilder(
                projectSpaceService, entityLoader, knowledgeRepository);
        insightService = new InsightService(
                entityLoader,
                conversationRecordHelper,
                insightStateMachine,
                insightReviewAgent,
                insightFieldTruncator);
        agentRunService = new AgentRunService(
                entityLoader,
                conversationRecordHelper,
                insightAgent,
                insightFieldTruncator,
                transactionTemplate());
        eventListener = new SelfLearningEventListener(insightService);
        // truncator mock：透传输入值（lenient：部分测试不使用 truncator）
        lenient().when(insightFieldTruncator.truncateTitle(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(insightFieldTruncator.truncateSummary(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(insightFieldTruncator.truncateConclusion(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(insightFieldTruncator.truncateTags(any())).thenAnswer(inv -> inv.getArgument(0));

        projectSpace = new ProjectSpace();
        ReflectionTestUtils.setField(projectSpace, "id", 7L);
        projectSpace.setName("Ascoder");
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    @Test
    void buildContextOnlyIncludesVerifiedOrActiveKnowledge() {
        SelfLearningSettings settings = settings(true, true, false);
        LearningKnowledgeItem verified = knowledge("索引失败排查", "先检查 CodeGraph indexPath", LearningKnowledgeStatus.VERIFIED);
        LearningKnowledgeItem stale = knowledge("过期知识", "这条不能注入", LearningKnowledgeStatus.STALE);

        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings));
        when(knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of(stale, verified));

        String context = contextBuilder.buildContext(7L, "索引失败怎么排查");

        assertThat(context).contains("索引失败排查");
        assertThat(context).contains("先检查 CodeGraph indexPath");
        assertThat(context).doesNotContain("过期知识");
        assertThat(context).doesNotContain("这条不能注入");
    }

    @Test
    void buildContextReturnsBlankWhenAnswerInjectionDisabled() {
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, false)));

        String context = contextBuilder.buildContext(7L, "索引失败怎么排查");

        assertThat(context).isBlank();
        verify(knowledgeRepository, never()).findByProjectSpace_IdOrderByUpdatedAtDesc(7L);
    }

    @Test
    void createCandidateFromAnswerSavesRawEventAndPendingInsightOnlyWhenEnabled() {
        CodeRepository repository = new CodeRepository();
        ReflectionTestUtils.setField(repository, "id", 11L);
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.setProjectSpaceId(7L);
        question.setRepositoryId(11L);
        question.setText("入口在哪里");
        question.setAnswerSummary("入口在 AscoderApplication");
        QuestionResponse response = new QuestionResponse();
        response.setAnswerSummary("入口在 AscoderApplication");

        when(questionQueryPort.findById(17L)).thenReturn(Optional.of(question));
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, true)));
        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(repositoryQueryPort.findById(11L)).thenReturn(Optional.of(repository));
        when(rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(questionQueryPort.findByQuestionIdIn(List.of(17L))).thenReturn(List.of());
        when(rawEventRepository.save(any())).thenAnswer(invocation -> {
            LearningRawEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 31L);
            return event;
        });

        insightService.createCandidateFromAnswer(17L, response, "完整答案");

        ArgumentCaptor<LearningRawEvent> rawCaptor = ArgumentCaptor.forClass(LearningRawEvent.class);
        verify(rawEventRepository).save(rawCaptor.capture());
        assertThat(rawCaptor.getValue().getEventType()).isEqualTo(LearningRawEventType.CONVERSATION_RECORD);
        assertThat(rawCaptor.getValue().getSummary()).contains("入口在哪里", "入口在 AscoderApplication");
        assertThat(rawCaptor.getValue().getEventPayloadJson()).contains("conversation-record");

        ArgumentCaptor<LearningInsight> insightCaptor = ArgumentCaptor.forClass(LearningInsight.class);
        verify(insightRepository).save(insightCaptor.capture());
        LearningInsight saved = insightCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(LearningInsightStatus.PENDING_REVIEW);
        assertThat(saved.getConfidence()).isEqualTo(0.35);
        assertThat(saved.getConclusion()).isEqualTo("入口在 AscoderApplication");
        assertThat(saved.getApplicableScope()).contains("管理员审核");
    }

    @Test
    void createCandidateFromAnswerSkipsWhenRawAndInsightDisabled() {
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.setProjectSpaceId(7L);
        question.setText("入口在哪里");

        when(questionQueryPort.findById(17L)).thenReturn(Optional.of(question));
        SelfLearningSettings settings = settings(true, false, false);
        settings.setRawEventCaptureEnabled(false);
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings));

        insightService.createCandidateFromAnswer(17L, null, "完整答案");

        verify(rawEventRepository, never()).save(any());
        verify(insightRepository, never()).save(any());
    }

    @Test
    void onQuestionAnsweredEventTriggersCreateCandidateFromAnswer() {
        CodeRepository repository = new CodeRepository();
        ReflectionTestUtils.setField(repository, "id", 11L);
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.setProjectSpaceId(7L);
        question.setRepositoryId(11L);
        question.setText("入口在哪里");
        question.setAnswerSummary("入口在 AscoderApplication");
        QuestionResponse response = new QuestionResponse();
        response.setAnswerSummary("入口在 AscoderApplication");

        when(questionQueryPort.findById(17L)).thenReturn(Optional.of(question));
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, true)));
        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(repositoryQueryPort.findById(11L)).thenReturn(Optional.of(repository));
        when(rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(questionQueryPort.findByQuestionIdIn(List.of(17L))).thenReturn(List.of());
        when(rawEventRepository.save(any())).thenAnswer(invocation -> {
            LearningRawEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 31L);
            return event;
        });

        eventListener.onQuestionAnswered(new QuestionAnsweredEvent(17L, response, "完整答案"));

        verify(insightRepository).save(any());
    }

    @Test
    void onQuestionAnsweredEventSwallowsCandidateCreationFailure() {
        when(questionQueryPort.findById(17L))
                .thenThrow(new RuntimeException("boom"));

        eventListener.onQuestionAnswered(new QuestionAnsweredEvent(17L, null, "完整答案"));

        verify(insightRepository, never()).save(any());
    }

    @Test
    void runSelfLearningAgentCreatesPendingInsightAndSkipsUsedRawEvents() {
        LearningRawEvent rawEvent = new LearningRawEvent();
        ReflectionTestUtils.setField(rawEvent, "id", 41L);
        rawEvent.setProjectSpace(projectSpace);
        rawEvent.setEventType(LearningRawEventType.USER_QUESTION);
        rawEvent.setSummary("用户询问支付入口 PaymentService.pay 和 OrderController 的业务语境");
        List<LearningInsight> savedInsights = new ArrayList<>();

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, false)));
        when(rawEventRepository.findTop50ByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(rawEvent));
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenAnswer(invocation -> savedInsights);
        when(insightAgent.summarize(projectSpace, List.of(rawEvent))).thenReturn(Optional.of(agentDraft()));
        when(insightRepository.save(any())).thenAnswer(invocation -> {
            LearningInsight insight = invocation.getArgument(0);
            ReflectionTestUtils.setField(insight, "id", 91L);
            savedInsights.add(insight);
            return insight;
        });

        SelfLearningAgentRunResponse firstRun = agentRunService.runSelfLearningAgent(7L, 10);
        SelfLearningAgentRunResponse secondRun = agentRunService.runSelfLearningAgent(7L, 10);

        assertThat(firstRun.getCreatedInsightCount()).isEqualTo(1);
        assertThat(firstRun.getConsumedRawEventCount()).isEqualTo(1);
        assertThat(secondRun.getCreatedInsightCount()).isZero();
        assertThat(savedInsights).hasSize(1);
        LearningInsight insight = savedInsights.get(0);
        assertThat(insight.getStatus()).isEqualTo(LearningInsightStatus.PENDING_REVIEW);
        assertThat(insight.getSourceRawEventIdsJson()).isEqualTo("[41]");
        assertThat(insight.getConclusion()).contains("PaymentService.pay");
        assertThat(insight.getGlossaryMappingsJson()).contains("PaymentService.pay");
        assertThat(insight.getCodeSymbolsJson()).contains("PaymentService.pay");
        assertThat(insight.getWarnings()).contains("管理员审核");
    }

    @Test
    void runSelfLearningAgentUsesLlmAgentDraftWhenAvailable() {
        LearningRawEvent rawEvent = new LearningRawEvent();
        ReflectionTestUtils.setField(rawEvent, "id", 41L);
        rawEvent.setProjectSpace(projectSpace);
        rawEvent.setEventType(LearningRawEventType.CONVERSATION_RECORD);
        rawEvent.setSummary("完整会话：用户询问支付入口 PaymentService.pay");

        SelfLearningInsightDraft draft = new SelfLearningInsightDraft();
        draft.setType(LearningKnowledgeType.GLOSSARY);
        draft.setTitle("支付入口术语映射审核");
        draft.setSummary("用户多次用支付入口描述订单支付编排入口。");
        draft.setConclusion("在该会话语境中，支付入口指 PaymentService.pay。");
        draft.setBusinessContext("用户想用业务语言定位代码入口。");
        draft.setGlossaryMappingsJson("[{\"term\":\"支付入口\",\"meaning\":\"PaymentService.pay\"}]");
        draft.setCodeSymbolsJson("[\"PaymentService.pay\"]");
        draft.setWarnings("需要核对当前代码。");
        draft.setApplicableScope("支付模块问答。");
        draft.setTags("llm-agent,glossary");
        draft.setConfidence(0.61);

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, false)));
        when(rawEventRepository.findTop50ByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(rawEvent));
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());
        when(insightAgent.summarize(projectSpace, List.of(rawEvent))).thenReturn(Optional.of(draft));
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        agentRunService.runSelfLearningAgent(7L, 10);

        ArgumentCaptor<LearningInsight> insightCaptor = ArgumentCaptor.forClass(LearningInsight.class);
        verify(insightRepository).save(insightCaptor.capture());
        LearningInsight insight = insightCaptor.getValue();
        assertThat(insight.getTitle()).isEqualTo("支付入口术语映射审核");
        assertThat(insight.getType()).isEqualTo(LearningKnowledgeType.GLOSSARY);
        assertThat(insight.getConclusion()).contains("PaymentService.pay");
        assertThat(insight.getBusinessContext()).contains("业务语言");
        assertThat(insight.getTags()).isEqualTo("llm-agent,glossary");
        assertThat(insight.getConfidence()).isEqualTo(0.61);
        assertThat(insight.getWarnings()).contains("当前代码").contains("Git");
    }

    @Test
    void runSelfLearningAgentUpdatesRunProgressAfterEachSavedInsight() {
        LearningRawEvent rawEvent = new LearningRawEvent();
        ReflectionTestUtils.setField(rawEvent, "id", 41L);
        rawEvent.setProjectSpace(projectSpace);
        rawEvent.setEventType(LearningRawEventType.CONVERSATION_RECORD);
        rawEvent.setSummary("完整会话：用户询问支付入口 PaymentService.pay");

        LearningAgentRun run = new LearningAgentRun();
        ReflectionTestUtils.setField(run, "id", 501L);
        run.setProjectSpace(projectSpace);
        run.start();

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, false)));
        when(rawEventRepository.findTop50ByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(rawEvent));
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());
        when(insightAgent.summarize(projectSpace, List.of(rawEvent))).thenReturn(Optional.of(agentDraft()));
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRunRepository.findById(501L)).thenReturn(Optional.of(run));
        when(agentRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        agentRunService.runSelfLearningAgent(7L, 10, 501L);

        assertThat(run.getCreatedInsightCount()).isEqualTo(1);
        assertThat(run.getConsumedRawEventCount()).isEqualTo(1);
        assertThat(run.getCurrentRawEventIdsJson()).isEqualTo("[41]");
        verify(agentRunRepository).save(run);
    }

    @Test
    void runSelfLearningAgentRecordsConversationFailureWithoutSavingFallbackInsight() {
        LearningRawEvent rawEvent = new LearningRawEvent();
        ReflectionTestUtils.setField(rawEvent, "id", 41L);
        rawEvent.setProjectSpace(projectSpace);
        rawEvent.setEventType(LearningRawEventType.CONVERSATION_RECORD);
        rawEvent.setSummary("完整会话：用户询问支付入口 PaymentService.pay");

        LearningAgentRun run = new LearningAgentRun();
        ReflectionTestUtils.setField(run, "id", 501L);
        run.setProjectSpace(projectSpace);
        run.start();

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, false)));
        when(rawEventRepository.findTop50ByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(rawEvent));
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());
        when(insightAgent.summarize(projectSpace, List.of(rawEvent)))
                .thenThrow(new SelfLearningInsightException("模型超时"));
        when(agentRunRepository.findById(501L)).thenReturn(Optional.of(run));
        when(agentRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SelfLearningAgentRunResponse response = agentRunService.runSelfLearningAgent(7L, 10, 501L);

        assertThat(response.getCreatedInsightCount()).isZero();
        assertThat(response.getFailedConversationCount()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(LearningAgentRunStatus.FAILED);
        assertThat(run.getFailedConversationCount()).isEqualTo(1);
        assertThat(run.getFailureDetailsJson()).contains("模型超时", "[41]");
        verify(insightRepository, never()).save(any());
    }

    @Test
    void importHistoryRawEventsCreatesOneConversationRecordWithoutDuplicates() {
        CodeRepository repository = new CodeRepository();
        ReflectionTestUtils.setField(repository, "id", 11L);

        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", 17L);
        question.setProjectSpaceId(7L);
        question.setRepositoryId(11L);
        question.setText("历史问题：支付入口在哪里");
        question.setStatus(QuestionStatus.SUCCEEDED);
        question.setAnswerSummary("支付入口在 PaymentService.pay。");
        question.setAnswerEvidenceJson("{\"file\":\"PaymentService.java\"}");
        question.setBranchName("main");
        question.setCommitSha("abc123");
        ReflectionTestUtils.setField(question, "createdAt", new Date());
        ReflectionTestUtils.setField(question, "completedAt", new Date());

        QueryPlan queryPlan = new QueryPlan();
        queryPlan.setQuestion(question);
        queryPlan.setType(QuestionType.ENTRY_POINT);
        queryPlan.setRewrittenQueriesJson("[\"PaymentService.pay\"]");
        queryPlan.setRecommendedToolsJson("[\"codegraph_context\"]");
        queryPlan.setConfidence(0.8);
        queryPlan.setReasoning("历史规划");

        List<LearningRawEvent> savedEvents = new ArrayList<>();
        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(repositoryQueryPort.findById(11L)).thenReturn(Optional.of(repository));
        when(settingsRepository.findByProjectSpace_Id(7L)).thenReturn(Optional.of(settings(true, false, false)));
        when(questionQueryPort.findByProjectSpaceId(7L)).thenReturn(List.of(question));
        when(questionQueryPort.findByQuestionIdIn(List.of(17L))).thenReturn(List.of(queryPlan));
        when(rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenAnswer(invocation -> savedEvents);
        when(rawEventRepository.save(any())).thenAnswer(invocation -> {
            LearningRawEvent event = invocation.getArgument(0);
            savedEvents.add(event);
            return event;
        });

        ImportHistoryRawEventsResponse first = agentRunService.importHistoryRawEvents(7L);
        ImportHistoryRawEventsResponse second = agentRunService.importHistoryRawEvents(7L);

        assertThat(first.getImportedConversationCount()).isEqualTo(1);
        assertThat(first.getImportedRawEventCount()).isEqualTo(1);
        assertThat(second.getImportedRawEventCount()).isZero();
        assertThat(savedEvents).extracting(LearningRawEvent::getEventType)
                .containsExactly(LearningRawEventType.CONVERSATION_RECORD);
        assertThat(savedEvents.get(0).getSummary()).contains("历史问题：支付入口在哪里", "PaymentService.pay");
        assertThat(savedEvents.get(0).getEventPayloadJson()).contains("历史规划", "PaymentService.pay");
        assertThat(savedEvents.get(0).getEvidenceJson()).contains("PaymentService.java");
        assertThat(savedEvents.get(0).getGitProvenanceJson()).contains("abc123");
        verify(rawEventRepository).save(any());
    }

    @Test
    void cleanupLegacyRawEventsDeletesSplitEventsAndMarksApprovedKnowledgeStale() {
        LearningRawEvent legacy = new LearningRawEvent();
        ReflectionTestUtils.setField(legacy, "id", 41L);
        legacy.setEventType(LearningRawEventType.ASSISTANT_ANSWER);

        LearningRawEvent conversation = new LearningRawEvent();
        ReflectionTestUtils.setField(conversation, "id", 42L);
        conversation.setEventType(LearningRawEventType.CONVERSATION_RECORD);

        LearningInsight pending = new LearningInsight();
        pending.setStatus(LearningInsightStatus.PENDING_REVIEW);
        pending.setSourceRawEventIdsJson("[41]");

        LearningInsight approved = new LearningInsight();
        approved.setStatus(LearningInsightStatus.APPROVED);
        approved.setSourceRawEventIdsJson("[41]");

        LearningKnowledgeItem knowledge = new LearningKnowledgeItem();
        knowledge.setStatus(LearningKnowledgeStatus.VERIFIED);
        knowledge.setSourceRawEventIdsJson("[41]");

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(legacy, conversation));
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of(pending, approved));
        when(knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of(knowledge));

        CleanupLegacyRawEventsResponse result = agentRunService.cleanupLegacyRawEvents(7L);

        assertThat(result.getDeletedRawEventCount()).isEqualTo(1);
        assertThat(result.getDeletedInsightCount()).isEqualTo(1);
        assertThat(result.getStaleKnowledgeItemCount()).isEqualTo(1);
        assertThat(knowledge.getStatus()).isEqualTo(LearningKnowledgeStatus.STALE);
        verify(insightRepository).deleteAll(List.of(pending));
        verify(rawEventRepository).deleteAll(List.of(legacy));
        verify(knowledgeRepository).saveAll(List.of(knowledge));
    }

    @Test
    void cleanupLegacyInsightsDeletesOnlyOldAgentDraftsAndMarksKnowledgeStale() {
        LearningInsight oldFallback = new LearningInsight();
        ReflectionTestUtils.setField(oldFallback, "id", 61L);
        oldFallback.setStatus(LearningInsightStatus.PENDING_REVIEW);
        oldFallback.setTitle("项目问答洞察：旧流水账标题");
        oldFallback.setTags("rule-fallback,conversation-record");

        LearningInsight oldPrompt = new LearningInsight();
        ReflectionTestUtils.setField(oldPrompt, "id", 62L);
        oldPrompt.setStatus(LearningInsightStatus.REJECTED);
        oldPrompt.setTitle("项目问答洞察：旧提示词标题");
        oldPrompt.setTags("self-learning-agent,raw-events");

        LearningInsight approvedOld = new LearningInsight();
        ReflectionTestUtils.setField(approvedOld, "id", 63L);
        approvedOld.setStatus(LearningInsightStatus.APPROVED);
        approvedOld.setTitle("项目问答洞察：已审核的不直接删除");
        approvedOld.setTags("self-learning-agent,raw-events");

        LearningInsight newInsight = new LearningInsight();
        ReflectionTestUtils.setField(newInsight, "id", 64L);
        newInsight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        newInsight.setTitle("开放平台自动签权限口径差异");
        newInsight.setTags("llm-agent,contract-sign");

        LearningKnowledgeItem knowledge = new LearningKnowledgeItem();
        knowledge.setStatus(LearningKnowledgeStatus.VERIFIED);
        knowledge.setSourceInsightIdsJson("[61]");

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L))
                .thenReturn(List.of(oldFallback, oldPrompt, approvedOld, newInsight));
        when(knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of(knowledge));

        CleanupLegacyInsightsResponse result = insightService.cleanupLegacyInsights(7L);

        assertThat(result.getDeletedInsightCount()).isEqualTo(2);
        assertThat(result.getStaleKnowledgeItemCount()).isEqualTo(1);
        assertThat(knowledge.getStatus()).isEqualTo(LearningKnowledgeStatus.STALE);
        verify(insightRepository).deleteAll(List.of(oldFallback, oldPrompt));
        verify(knowledgeRepository).saveAll(List.of(knowledge));
    }

    @Test
    void approveInsightPreservesStructuredFieldsInKnowledgeContent() {
        LearningInsight insight = new LearningInsight();
        ReflectionTestUtils.setField(insight, "id", 51L);
        insight.setProjectSpace(projectSpace);
        insight.setType(LearningKnowledgeType.GLOSSARY);
        insight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        insight.setTitle("支付术语映射");
        insight.setSummary("支付入口经验");
        insight.setConclusion("支付入口由 PaymentService.pay 负责。");
        insight.setBusinessContext("用户说支付入口时，通常指订单支付编排。");
        insight.setGlossaryMappingsJson("[{\"term\":\"PaymentService.pay\",\"meaning\":\"支付入口\"}]");
        insight.setCodeSymbolsJson("[\"PaymentService.pay\"]");
        insight.setWarnings("必须结合当前代码和 Git 证据复核。");
        insight.setConfidence(0.6);

        when(insightRepository.findByIdAndProjectSpace_Id(51L, 7L)).thenReturn(Optional.of(insight));
        when(knowledgeRepository.save(any())).thenAnswer(invocation -> {
            LearningKnowledgeItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 88L);
            return item;
        });
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        insightService.approveInsight(7L, 51L, new ReviewLearningInsightRequest());

        ArgumentCaptor<LearningKnowledgeItem> knowledgeCaptor = ArgumentCaptor.forClass(LearningKnowledgeItem.class);
        verify(knowledgeRepository).save(knowledgeCaptor.capture());
        LearningKnowledgeItem item = knowledgeCaptor.getValue();
        assertThat(item.getContent())
                .contains("## 结论")
                .contains("PaymentService.pay")
                .contains("## 业务语境")
                .contains("订单支付编排")
                .contains("## 代码名词映射")
                .contains("## 代码符号")
                .contains("## 注意事项");
        assertThat(item.getStatus()).isEqualTo(LearningKnowledgeStatus.VERIFIED);
    }

    @Test
    void verifyInsightDelegatesToReviewAgentWithSourceConversation() {
        LearningInsight insight = pendingInsight();
        insight.setSourceRawEventIdsJson("[41]");
        LearningRawEvent rawEvent = new LearningRawEvent();
        ReflectionTestUtils.setField(rawEvent, "id", 41L);
        rawEvent.setProjectSpace(projectSpace);
        rawEvent.setEventType(LearningRawEventType.CONVERSATION_RECORD);
        rawEvent.setSummary("用户确认 PaymentService.pay 是支付入口");

        SelfLearningInsightVerification verification = new SelfLearningInsightVerification();
        verification.setStatus("VERIFIED");
        verification.setSummary("当前代码证据支持该洞察。");
        verification.setCodeEvidenceJson("[{\"symbol\":\"PaymentService.pay\"}]");
        verification.setConfidence(0.82);

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(insightRepository.findByIdAndProjectSpace_Id(51L, 7L)).thenReturn(Optional.of(insight));
        when(rawEventRepository.findAllById(List.of(41L))).thenReturn(List.of(rawEvent));
        when(insightReviewAgent.verify(projectSpace, insight, List.of(rawEvent))).thenReturn(verification);

        LearningInsightVerificationResponse response = insightService.verifyInsight(7L, 51L);

        assertThat(response.getStatus()).isEqualTo("VERIFIED");
        assertThat(response.getSummary()).contains("支持");
        assertThat(response.getCodeEvidenceJson()).contains("PaymentService.pay");
    }

    @Test
    void refineInsightReturnsSaveSuggestionWithoutPersisting() {
        LearningInsight insight = pendingInsight();
        insight.setSourceRawEventIdsJson("[41]");
        LearningRawEvent rawEvent = new LearningRawEvent();
        ReflectionTestUtils.setField(rawEvent, "id", 41L);
        rawEvent.setProjectSpace(projectSpace);

        SelfLearningInsightDraft draft = new SelfLearningInsightDraft();
        draft.setType(LearningKnowledgeType.GLOSSARY);
        draft.setTitle("支付入口术语映射");
        draft.setConclusion("在该会话语境中，支付入口指 PaymentService.pay，仍需管理员核对当前代码。");
        draft.setWarnings("需要核对当前代码和 Git 证据。");
        draft.setTags("llm-agent,review-refined,payment");
        draft.setConfidence(0.62);

        RefineLearningInsightRequest request = new RefineLearningInsightRequest();
        request.setInstruction("把表述改得更适合审核通过");

        when(projectSpaceService.getEntity(7L)).thenReturn(projectSpace);
        when(insightRepository.findByIdAndProjectSpace_Id(51L, 7L)).thenReturn(Optional.of(insight));
        when(rawEventRepository.findAllById(List.of(41L))).thenReturn(List.of(rawEvent));
        when(insightReviewAgent.refine(projectSpace, insight, List.of(rawEvent), request.getInstruction())).thenReturn(draft);

        RefineLearningInsightResponse response = insightService.refineInsight(7L, 51L, request);

        assertThat(response.getSuggestion().getTitle()).isEqualTo("支付入口术语映射");
        assertThat(response.getSuggestion().getConclusion()).contains("PaymentService.pay");
        assertThat(response.getSuggestion().getWarnings()).contains("当前代码").contains("Git");
        assertThat(response.getSuggestion().getTags()).contains("review-refined");
        verify(insightRepository, never()).save(any());
    }

    private SelfLearningSettings settings(boolean enabled, boolean answerInjectionEnabled, boolean autoCandidateEnabled) {
        SelfLearningSettings settings = new SelfLearningSettings();
        settings.setProjectSpace(projectSpace);
        settings.setEnabled(enabled);
        settings.setAnswerInjectionEnabled(answerInjectionEnabled);
        settings.setAutoCandidateEnabled(autoCandidateEnabled);
        settings.setRawEventCaptureEnabled(true);
        settings.setAutoInsightEnabled(autoCandidateEnabled);
        return settings;
    }

    private LearningInsight pendingInsight() {
        LearningInsight insight = new LearningInsight();
        ReflectionTestUtils.setField(insight, "id", 51L);
        insight.setProjectSpace(projectSpace);
        insight.setType(LearningKnowledgeType.QUESTION_ANSWER);
        insight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        insight.setTitle("支付入口洞察");
        insight.setSummary("支付入口经验");
        insight.setConclusion("支付入口由 PaymentService.pay 负责。");
        insight.setWarnings("必须结合当前代码和 Git 证据复核。");
        insight.setConfidence(0.6);
        return insight;
    }

    private LearningKnowledgeItem knowledge(String title, String content, LearningKnowledgeStatus status) {
        LearningKnowledgeItem item = new LearningKnowledgeItem();
        item.setProjectSpace(projectSpace);
        item.setType(LearningKnowledgeType.QUESTION_ANSWER);
        item.setStatus(status);
        item.setTitle(title);
        item.setContent(content);
        item.setConfidence(0.8);
        return item;
    }

    private SelfLearningInsightDraft agentDraft() {
        SelfLearningInsightDraft draft = new SelfLearningInsightDraft();
        draft.setType(LearningKnowledgeType.GLOSSARY);
        draft.setTitle("支付入口术语映射审核");
        draft.setSummary("用户用支付入口描述订单支付编排入口。");
        draft.setConclusion("在该会话语境中，支付入口指 PaymentService.pay。");
        draft.setBusinessContext("用户想用业务语言定位代码入口。");
        draft.setGlossaryMappingsJson("[{\"term\":\"支付入口\",\"meaning\":\"PaymentService.pay\"}]");
        draft.setCodeSymbolsJson("[\"PaymentService.pay\"]");
        draft.setWarnings("需要核对当前代码。");
        draft.setApplicableScope("支付模块问答。");
        draft.setTags("llm-agent,glossary");
        draft.setConfidence(0.61);
        return draft;
    }
}
