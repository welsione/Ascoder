package cn.welsione.ascoder.question.application;

import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.loganalysis.application.LogAnalysisService;
import cn.welsione.ascoder.loganalysis.application.LogUploadService;
import cn.welsione.ascoder.loganalysis.domain.LogUpload;
import cn.welsione.ascoder.loganalysis.domain.LogUploadStatus;
import cn.welsione.ascoder.question.api.CreateQuestionRequest;
import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.api.QuestionResponse.LogUploadBriefResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 问题服务，协调问题提交流程：校验→持久化→规划→Agent 调用→回答写回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<QuestionType>> QUESTION_TYPE_LIST = new TypeReference<>() {
    };

    private final QuestionJpaRepository repository;
    private final QueryPlanJpaRepository queryPlanRepository;
    private final ProjectSpaceService projectSpaceService;
    private final QuestionPlanner questionPlanner;
    private final ConversationResolver conversationResolver;
    private final AgentRequestBuilder requestBuilder;
    private final AnswerWriter answerWriter;
    private final LogAnalysisService logAnalysisService;
    private final LogUploadService logUploadService;
    private final AgentRunService selfLearningService;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuestionResponse createPending(CreateQuestionRequest request) {
        return prepareStream(request).getQuestion();
    }

    @Transactional
    public PendingQuestion prepareStream(CreateQuestionRequest request) {
        log.info("准备流式问题，项目空间ID={}，问题={}", request.getProjectSpaceId(), request.getText());
        ProjectSpace projectSpace = projectSpaceService.getEntity(request.getProjectSpaceId());
        List<ProjectSpaceMember> members = validateProjectSpace(projectSpace);
        ProjectSpaceMember primary = members.get(0);
        validateLogUploads(request, projectSpace);

        Question question = createQuestion(request, projectSpace, primary);
        question.running();
        repository.saveAndFlush(question);

        QuestionPlan plan = planFor(request);
        saveQueryPlan(question, plan);
        selfLearningService.recordQuestionPlan(question.getId(), plan.toPromptText());
        List<Long> uploadIds = request.getLogUploadIds() != null ? request.getLogUploadIds() : List.of();
        for (Long uploadId : uploadIds) {
            logAnalysisService.startTask(question, uploadId);
        }

        AgentRequest agentRequest = requestBuilder.build(
                question.getId(), projectSpace, question.getConversation().getId(), members,
                request.getText().trim(), request.getRole(),
                plan.toPromptText(), plan, request.getLogUploadIds()
        );

        return new PendingQuestion(toResponse(question, null), agentRequest);
    }

    public QuestionResponse succeed(Long questionId, String answer) {
        return answerWriter.succeed(questionId, answer);
    }

    public QuestionResponse succeed(Long questionId, String answer, String codeContext, String analysisProcess) {
        return answerWriter.succeed(questionId, answer, codeContext, analysisProcess);
    }

    public QuestionResponse fail(Long questionId, String errorMessage) {
        return answerWriter.fail(questionId, errorMessage);
    }

    public QuestionResponse fail(Long questionId, String errorMessage, String codeContext, String partialAnswer, String analysisProcess) {
        return answerWriter.fail(questionId, errorMessage, codeContext, partialAnswer, analysisProcess);
    }

    /**
     * 用户主动停止流式回答时，将运行中的问题结束为失败状态，避免页面自动恢复再次执行。
     */
    public QuestionResponse cancelStream(Long questionId) {
        return answerWriter.fail(questionId, "用户已停止回答");
    }

    public void updateProgress(Long questionId, String codeContext, String partialAnswer, String analysisProcess) {
        answerWriter.updateProgress(questionId, codeContext, partialAnswer, analysisProcess);
    }

    @Transactional
    public PendingQuestion prepareResumeStream(Long questionId) {
        Question question = repository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("问题", questionId));
        if (question.getStatus() != QuestionStatus.RUNNING) {
            throw new InvalidStateException("只有运行中的问题可以恢复");
        }
        return prepareExistingStream(question);
    }

    @Transactional
    public PendingQuestion prepareRetryStream(Long questionId) {
        Question question = repository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("问题", questionId));
        if (question.getStatus() != QuestionStatus.FAILED) {
            throw new InvalidStateException("只有失败的问题可以重试");
        }
        question.setAnswer(null);
        question.setAnswerSummary(null);
        question.setAnswerEvidenceJson(null);
        question.setAnalysisProcess(null);
        question.setUncertainty(null);
        question.setNextStep(null);
        question.setCodegraphContext(null);
        return prepareExistingStream(question);
    }

    private PendingQuestion prepareExistingStream(Question question) {
        if (question.getProjectSpaceId() == null) {
            throw new InvalidStateException("问题所属项目空间已不存在，无法执行");
        }
        if (question.getConversation() == null) {
            throw new InvalidStateException("问题缺少会话上下文，无法执行");
        }

        Long questionId = question.getId();
        ProjectSpace projectSpace = projectSpaceService.getEntity(question.getProjectSpaceId());
        List<ProjectSpaceMember> members = validateProjectSpace(projectSpace);
        validateResumeLogUpload(question, projectSpace);
        QuestionPlan plan = queryPlanRepository.findByQuestionId(questionId)
                .map(this::toQuestionPlan)
                .orElseGet(() -> planFor(toCreateQuestionRequest(question)));

        question.running();
        repository.saveAndFlush(question);
        selfLearningService.recordQuestionPlan(question.getId(), plan.toPromptText());

        AgentRequest agentRequest = requestBuilder.build(
                questionId, projectSpace, question.getConversation().getId(), members,
                question.getText().trim(), question.getRole(),
                plan.toPromptText(), plan, question.getLogUploadIds()
        );
        QueryPlan queryPlan = queryPlanRepository.findByQuestionId(questionId).orElse(null);
        return new PendingQuestion(toResponse(question, queryPlan), agentRequest);
    }

    @Transactional(readOnly = true)
    public AgentRequest buildAgentRequest(CreateQuestionRequest request, Long conversationId) {
        ProjectSpace projectSpace = projectSpaceService.getEntity(request.getProjectSpaceId());
        List<ProjectSpaceMember> members = projectSpaceService.members(projectSpace.getId());
        if (members.isEmpty()) {
            throw new InvalidStateException("项目空间没有可用仓库");
        }
        QuestionPlan plan = planFor(request);
        return requestBuilder.build(
                null, projectSpace, conversationId, members,
                request.getText().trim(), request.getRole(),
                plan.toPromptText(), plan, request.getLogUploadIds()
        );
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(Long id) {
        return repository.findById(id)
                .map(q -> toResponse(q, queryPlanRepository.findByQuestionId(id).orElse(null)))
                .orElseThrow(() -> new ResourceNotFoundException("问题", id));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> list() {
        List<Question> questions = repository.findTop50ByOrderByCreatedAtDesc();
        Map<Long, QueryPlan> plans = loadPlans(questions);
        return questions.stream()
                .map(q -> toResponse(q, plans.get(q.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listByConversation(Long conversationId) {
        List<Question> questions = repository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Map<Long, QueryPlan> plans = loadPlans(questions);
        return questions.stream()
                .map(q -> toResponse(q, plans.get(q.getId())))
                .toList();
    }

    private QuestionResponse toResponse(Question question, QueryPlan queryPlan) {
        return QuestionResponse.from(question, queryPlan,
                projectSpaceName(question.getProjectSpaceId()),
                repositoryName(question.getProjectSpaceId(), question.getRepositoryId()),
                logUploadBriefs(question), objectMapper);
    }

    private String projectSpaceName(Long projectSpaceId) {
        if (projectSpaceId == null) {
            return null;
        }
        return projectSpaceService.getEntity(projectSpaceId).getName();
    }

    private String repositoryName(Long projectSpaceId, Long repositoryId) {
        if (repositoryId == null || projectSpaceId == null) {
            return null;
        }
        return projectSpaceService.members(projectSpaceId).stream()
                .map(ProjectSpaceMember::getRepository)
                .filter(r -> r != null && repositoryId.equals(r.getId()))
                .map(CodeRepository::getName)
                .findFirst()
                .orElse(null);
    }

    /**
     * 当请求带有 logUploadId 时强制使用 LOG_ANALYSIS 类型，否则按文本关键词匹配。
     */
    private QuestionPlan planFor(CreateQuestionRequest request) {
        String text = request.getText().trim();
        if (request.getLogUploadIds() != null && !request.getLogUploadIds().isEmpty()) {
            return questionPlanner.planForType(text, request.getRole(), QuestionType.LOG_ANALYSIS);
        }
        return questionPlanner.plan(text, request.getRole());
    }

    private Map<Long, QueryPlan> loadPlans(List<Question> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = questions.stream().map(Question::getId).toList();
        return queryPlanRepository.findByQuestionIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getQuestion().getId(), Function.identity()));
    }

    private Map<Long, List<LogUploadBriefResponse>> loadLogUploads(List<Question> questions) {
        return questions.stream()
                .filter(q -> !q.getLogUploadIds().isEmpty())
                .collect(Collectors.toMap(Question::getId, this::logUploadBriefs));
    }

    private List<LogUploadBriefResponse> logUploadBriefs(Question question) {
        Set<Long> uploadIds = question.getLogUploadIds().stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (uploadIds.isEmpty()) {
            return List.of();
        }
        return uploadIds.stream()
                .map(uploadId -> {
                    LogUpload upload = logUploadService.get(uploadId);
                    return LogUploadBriefResponse.from(upload, logUploadService.listFiles(uploadId));
                })
                .toList();
    }

    private List<ProjectSpaceMember> validateProjectSpace(ProjectSpace projectSpace) {
        if (projectSpace.getStatus() != ProjectSpaceStatus.READY) {
            throw new InvalidStateException("项目空间尚未完成 CodeGraph 索引");
        }
        List<ProjectSpaceMember> members = projectSpaceService.members(projectSpace.getId());
        if (members.isEmpty()) {
            throw new InvalidStateException("项目空间没有可用仓库");
        }
        return members;
    }

    private void validateLogUploads(CreateQuestionRequest request, ProjectSpace projectSpace) {
        if (request.getLogUploadIds() == null || request.getLogUploadIds().isEmpty()) {
            return;
        }
        for (Long logUploadId : request.getLogUploadIds()) {
            LogUpload upload = logUploadService.get(logUploadId);
            Long uploadProjectSpaceId = upload.getProjectSpace() == null ? null : upload.getProjectSpace().getId();
            if (!projectSpace.getId().equals(uploadProjectSpaceId)) {
                throw new ValidationException("日志上传不属于当前项目空间");
            }
            if (upload.getStatus() != LogUploadStatus.READY) {
                throw new InvalidStateException("日志上传尚未完成预处理，当前状态：" + upload.getStatus());
            }
        }
    }

    private void validateResumeLogUpload(Question question, ProjectSpace projectSpace) {
        List<Long> logUploadIds = question.getLogUploadIds();
        if (logUploadIds.isEmpty()) {
            return;
        }
        validateLogUploads(toCreateQuestionRequest(question), projectSpace);
    }

    private Question createQuestion(CreateQuestionRequest request, ProjectSpace projectSpace, ProjectSpaceMember primary) {
        Question question = new Question();
        question.setProjectSpaceId(projectSpace.getId());
        question.setRepositoryId(primary.getRepository() == null ? null : primary.getRepository().getId());
        question.setText(request.getText().trim());
        question.setRole(request.getRole());
        question.setBranchWorkspaceId(primary.getBranchWorkspace() == null ? null : primary.getBranchWorkspace().getId());
        question.setBranchName(primary.getBranchName());
        question.setCommitSha(primary.getCommitSha());
        question.setLogUploadIds(request.getLogUploadIds() != null ? request.getLogUploadIds() : List.of());
        question.setConversation(conversationResolver.resolve(request, projectSpace, primary));
        return question;
    }

    private QueryPlan saveQueryPlan(Question question, QuestionPlan plan) {
        QueryPlan queryPlan = new QueryPlan();
        queryPlan.setQuestion(question);
        queryPlan.setType(plan.getType());
        queryPlan.setRewrittenQueriesJson(toJson(plan.getRewrittenQueries()));
        queryPlan.setRecommendedToolsJson(toJson(plan.getRecommendedTools()));
        queryPlan.setRecommendedSkillsJson(toJson(plan.getRecommendedSkills()));
        queryPlan.setConfidence(plan.getConfidence());
        queryPlan.setMatchedSignalsJson(toJson(plan.getMatchedSignals()));
        queryPlan.setAlternativeTypesJson(toJson(plan.getAlternativeTypes()));
        queryPlan.setReasoning(plan.getReasoning());
        return queryPlanRepository.save(queryPlan);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new InvalidStateException("序列化失败", ex);
        }
    }

    private CreateQuestionRequest toCreateQuestionRequest(Question question) {
        Long conversationId = question.getConversation() == null ? null : question.getConversation().getId();
        Long projectSpaceId = question.getProjectSpaceId();
        return new CreateQuestionRequest(
                projectSpaceId,
                conversationId,
                question.getText(),
                question.getRole(),
                question.getLogUploadIds()
        );
    }

    private QuestionPlan toQuestionPlan(QueryPlan queryPlan) {
        return new QuestionPlan(
                queryPlan.getType(),
                readStringList(queryPlan.getRewrittenQueriesJson()),
                readStringList(queryPlan.getRecommendedToolsJson()),
                readStringList(queryPlan.getRecommendedSkillsJson()),
                queryPlan.getReasoning(),
                queryPlan.getConfidence(),
                readStringList(queryPlan.getMatchedSignalsJson()),
                readQuestionTypes(queryPlan.getAlternativeTypesJson())
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException ex) {
            throw new InvalidStateException("反序列化查询规划失败", ex);
        }
    }

    private List<QuestionType> readQuestionTypes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, QUESTION_TYPE_LIST);
        } catch (JsonProcessingException ex) {
            throw new InvalidStateException("反序列化查询规划失败", ex);
        }
    }

    /**
     * 流式问题准备结果，携带已持久化的问题响应与同一规划生成的 Agent 请求。
     */
    public static final class PendingQuestion {
        private final QuestionResponse question;
        private final AgentRequest agentRequest;

        public PendingQuestion(QuestionResponse question, AgentRequest agentRequest) {
            this.question = question;
            this.agentRequest = agentRequest;
        }

        public QuestionResponse getQuestion() {
            return question;
        }

        public AgentRequest getAgentRequest() {
            return agentRequest;
        }
    }
}
