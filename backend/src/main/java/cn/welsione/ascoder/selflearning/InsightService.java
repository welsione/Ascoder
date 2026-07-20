package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.question.api.QuestionResponse;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 候选洞察管理服务，处理候选洞察的增删改、审核、复核、微调，
 * 以及回答完成后自动沉淀候选洞察。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final SelfLearningEntityLoader entityLoader;
    private final ConversationRecordHelper conversationRecordHelper;
    private final InsightStateMachine insightStateMachine;
    private final SelfLearningInsightReviewAgent insightReviewAgent;
    private final InsightFieldTruncator insightFieldTruncator;

    @Transactional(readOnly = true)
    public List<LearningInsightResponse> listInsights(Long projectSpaceId, LearningInsightStatus status) {
        entityLoader.projectSpace(projectSpaceId);
        return entityLoader.insightsByProjectSpace(projectSpaceId).stream()
                .filter(item -> status == null || item.getStatus() == status)
                .map(LearningInsightResponse::from)
                .toList();
    }

    @Transactional
    public LearningInsightResponse createInsight(Long projectSpaceId, SaveLearningInsightRequest request) {
        ProjectSpace space = entityLoader.projectSpace(projectSpaceId);
        LearningInsight insight = new LearningInsight();
        insight.setProjectSpace(space);
        insight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        applyInsightRequest(insight, request);
        return LearningInsightResponse.from(entityLoader.saveInsight(insight));
    }

    @Transactional
    public LearningInsightResponse updateInsight(Long projectSpaceId, Long insightId, SaveLearningInsightRequest request) {
        LearningInsight insight = entityLoader.insight(projectSpaceId, insightId);
        if (request.getStatus() != null && request.getStatus() != insight.getStatus()) {
            insightStateMachine.transition(insight, request.getStatus(), null);
        }
        applyInsightRequest(insight, request);
        insight.touch();
        return LearningInsightResponse.from(entityLoader.saveInsight(insight));
    }

    @Transactional
    public LearningInsightResponse approveInsight(Long projectSpaceId, Long insightId, ReviewLearningInsightRequest request) {
        LearningInsight insight = entityLoader.insight(projectSpaceId, insightId);
        insightStateMachine.transition(insight, LearningInsightStatus.APPROVED,
                request == null ? null : SelfLearningTextUtil.trimToNull(request.getReviewComment()));
        LearningKnowledgeItem item = knowledgeFromInsight(insight);
        entityLoader.saveKnowledgeItem(item);
        insightStateMachine.transition(insight, LearningInsightStatus.MERGED,
                "已审核通过并归纳为正式知识 #" + item.getId());
        return LearningInsightResponse.from(entityLoader.saveInsight(insight));
    }

    @Transactional
    public LearningInsightResponse rejectInsight(Long projectSpaceId, Long insightId, ReviewLearningInsightRequest request) {
        LearningInsight insight = entityLoader.insight(projectSpaceId, insightId);
        insightStateMachine.transition(insight, LearningInsightStatus.REJECTED,
                request == null ? null : SelfLearningTextUtil.trimToNull(request.getReviewComment()));
        return LearningInsightResponse.from(entityLoader.saveInsight(insight));
    }

    @Transactional(readOnly = true)
    public LearningInsightVerificationResponse verifyInsight(Long projectSpaceId, Long insightId) {
        ProjectSpace space = entityLoader.projectSpace(projectSpaceId);
        LearningInsight insight = entityLoader.insight(projectSpaceId, insightId);
        List<LearningRawEvent> rawEvents = sourceRawEvents(projectSpaceId, insight);
        log.info("复核候选洞察，projectSpaceId={}，insightId={}，rawEventCount={}",
                projectSpaceId, insightId, rawEvents.size());
        SelfLearningInsightVerification verification = insightReviewAgent.verify(space, insight, rawEvents);
        return LearningInsightVerificationResponse.from(insightId, verification);
    }

    @Transactional(readOnly = true)
    public RefineLearningInsightResponse refineInsight(
            Long projectSpaceId,
            Long insightId,
            RefineLearningInsightRequest request
    ) {
        ProjectSpace space = entityLoader.projectSpace(projectSpaceId);
        LearningInsight insight = entityLoader.insight(projectSpaceId, insightId);
        List<LearningRawEvent> rawEvents = sourceRawEvents(projectSpaceId, insight);
        String instruction = SelfLearningTextUtil.trimToNull(request == null ? null : request.getInstruction());
        if (instruction == null) {
            throw new ValidationException("微调指令不能为空。");
        }
        log.info("微调候选洞察，projectSpaceId={}，insightId={}，rawEventCount={}",
                projectSpaceId, insightId, rawEvents.size());
        SelfLearningInsightDraft draft = insightReviewAgent.refine(space, insight, rawEvents, instruction);
        return new RefineLearningInsightResponse(
                insightId,
                saveRequestFromDraft(insight, draft),
                "Insight Refine Agent 已生成可应用到编辑表单的建议稿，保存前请继续人工审核。"
        );
    }

    @Transactional
    public CleanupLegacyInsightsResponse cleanupLegacyInsights(Long projectSpaceId) {
        entityLoader.projectSpace(projectSpaceId);
        List<LearningInsight> legacyInsights = entityLoader.insightsByProjectSpace(projectSpaceId).stream()
                .filter(this::isRemovableLegacyInsight)
                .toList();
        if (legacyInsights.isEmpty()) {
            return new CleanupLegacyInsightsResponse(0, 0, "没有需要清理的旧版候选洞察。");
        }

        Set<Long> legacyInsightIds = legacyInsights.stream()
                .map(LearningInsight::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        List<LearningKnowledgeItem> staleKnowledge = entityLoader.knowledgeItemsByProjectSpace(projectSpaceId).stream()
                .filter(item -> referencesAnyRawEvent(item.getSourceInsightIdsJson(), legacyInsightIds))
                .filter(item -> item.getStatus() == LearningKnowledgeStatus.ACTIVE
                        || item.getStatus() == LearningKnowledgeStatus.VERIFIED)
                .toList();

        for (LearningKnowledgeItem item : staleKnowledge) {
            item.markStale("来源候选洞察来自旧版 Self Learning Agent 规则或旧提示词产物，已清理旧数据，请重新审核新版洞察。");
        }
        entityLoader.saveKnowledgeItems(staleKnowledge);
        entityLoader.deleteInsights(legacyInsights);

        log.info("清理旧版 Self Learning Agent 候选洞察完成，projectSpaceId={}，insights={}，staleKnowledge={}",
                projectSpaceId, legacyInsights.size(), staleKnowledge.size());
        return new CleanupLegacyInsightsResponse(
                legacyInsights.size(),
                staleKnowledge.size(),
                "已清理 " + legacyInsights.size() + " 条旧版候选洞察，原始 conversation 记录已保留，可重新后台整理。"
        );
    }

    @Transactional
    public void createCandidateFromAnswer(Long questionId, QuestionResponse response, String fullAnswer) {
        if (questionId == null) {
            return;
        }
        Question question = entityLoader.findQuestion(questionId);
        if (question == null || question.getProjectSpaceId() == null) {
            return;
        }
        Long projectSpaceId = question.getProjectSpaceId();
        SelfLearningSettings settings = entityLoader.settings(projectSpaceId);
        if (!settings.isEnabled() || (!settings.isRawEventCaptureEnabled() && !settings.isAutoInsightEnabled())) {
            return;
        }
        String conclusion = SelfLearningTextUtil.firstNonBlank(response == null ? null : response.getAnswerSummary(), fullAnswer);
        if (conclusion == null || conclusion.isBlank()) {
            return;
        }
        List<Long> rawEventIds = new ArrayList<>();
        if (settings.isRawEventCaptureEnabled()) {
            List<Question> conversationQuestions = conversationRecordHelper.questionsInConversation(question);
            java.util.Map<Long, cn.welsione.ascoder.question.domain.QueryPlan> queryPlans =
                    conversationRecordHelper.queryPlansByQuestion(conversationQuestions);
            LearningRawEvent rawEvent = conversationRecordHelper.upsertConversationRecord(
                    entityLoader.projectSpace(projectSpaceId),
                    conversationQuestions,
                    queryPlans
            );
            if (rawEvent.getId() != null) {
                rawEventIds.add(rawEvent.getId());
            }
        }
        if (settings.isAutoInsightEnabled()) {
            LearningInsight insight = new LearningInsight();
            insight.setProjectSpace(entityLoader.projectSpace(projectSpaceId));
            insight.setRepository(entityLoader.repository(question.getRepositoryId()));
            insight.setSourceRawEventIdsJson(SelfLearningTextUtil.toJsonArray(rawEventIds));
            insight.setSourceQuestionIdsJson(SelfLearningTextUtil.toJsonArray(List.of(questionId)));
            insight.setType(LearningKnowledgeType.QUESTION_ANSWER);
            insight.setStatus(LearningInsightStatus.PENDING_REVIEW);
            insight.setTitle(SelfLearningTextUtil.candidateTitle(question.getText()).replace("问答经验", "问答洞察"));
            insight.setSummary(SelfLearningTextUtil.truncate(question.getText(), 1200));
            insight.setConclusion(SelfLearningTextUtil.truncate(conclusion, 6000));
            insight.setApplicableScope("由 Self Learning Agent 根据完整会话原始记录整理，需管理员审核后才可归纳为正式知识。");
            insight.setWarnings("候选洞察不是当前代码事实，审核时需要确认代码证据和适用范围。");
            insight.setConfidence(0.35);
            entityLoader.saveInsight(insight);
        }
        log.info("完成自学习原始记录与候选洞察处理，projectSpaceId={}，questionId={}", projectSpaceId, questionId);
    }

    private List<LearningRawEvent> sourceRawEvents(Long projectSpaceId, LearningInsight insight) {
        List<Long> rawEventIds = conversationRecordHelper.parseJsonIds(insight.getSourceRawEventIdsJson());
        if (rawEventIds.isEmpty()) {
            return List.of();
        }
        return entityLoader.rawEventsByIds(rawEventIds).stream()
                .filter(event -> event.getProjectSpace() != null
                        && projectSpaceId.equals(event.getProjectSpace().getId()))
                .toList();
    }

    private boolean referencesAnyRawEvent(String sourceRawEventIdsJson, Set<Long> rawEventIds) {
        if (rawEventIds.isEmpty()) {
            return false;
        }
        return conversationRecordHelper.parseJsonIds(sourceRawEventIdsJson).stream().anyMatch(rawEventIds::contains);
    }

    private boolean isRemovableLegacyInsight(LearningInsight insight) {
        if (insight.getStatus() != LearningInsightStatus.DRAFT
                && insight.getStatus() != LearningInsightStatus.PENDING_REVIEW
                && insight.getStatus() != LearningInsightStatus.REJECTED) {
            return false;
        }
        String tags = SelfLearningTextUtil.nullToEmpty(insight.getTags());
        String title = SelfLearningTextUtil.nullToEmpty(insight.getTitle());
        return tags.contains("rule-fallback")
                || tags.equals("self-learning-agent,raw-events")
                || title.startsWith("项目问答洞察：");
    }

    private void applyInsightRequest(LearningInsight insight, SaveLearningInsightRequest request) {
        insight.setRepository(entityLoader.repository(request.getRepositoryId()));
        insight.setSourceRawEventIdsJson(SelfLearningTextUtil.trimToNull(request.getSourceRawEventIdsJson()));
        insight.setSourceQuestionIdsJson(SelfLearningTextUtil.trimToNull(request.getSourceQuestionIdsJson()));
        insight.setType(request.getType() == null ? LearningKnowledgeType.QUESTION_ANSWER : request.getType());
        insight.setTitle(request.getTitle().trim());
        insight.setSummary(SelfLearningTextUtil.trimToNull(request.getSummary()));
        insight.setConclusion(request.getConclusion().trim());
        insight.setBusinessContext(SelfLearningTextUtil.trimToNull(request.getBusinessContext()));
        insight.setGlossaryMappingsJson(SelfLearningTextUtil.trimToNull(request.getGlossaryMappingsJson()));
        insight.setCodeSymbolsJson(SelfLearningTextUtil.trimToNull(request.getCodeSymbolsJson()));
        insight.setWarnings(SelfLearningTextUtil.trimToNull(request.getWarnings()));
        insight.setApplicableScope(SelfLearningTextUtil.trimToNull(request.getApplicableScope()));
        insight.setEvidenceJson(SelfLearningTextUtil.trimToNull(request.getEvidenceJson()));
        insight.setGitProvenanceJson(SelfLearningTextUtil.trimToNull(request.getGitProvenanceJson()));
        insight.setTags(SelfLearningTextUtil.trimToNull(request.getTags()));
        insight.setConfidence(request.getConfidence() == null ? insight.getConfidence() : request.getConfidence());
    }

    private LearningKnowledgeItem knowledgeFromInsight(LearningInsight insight) {
        LearningKnowledgeItem item = new LearningKnowledgeItem();
        item.setProjectSpace(insight.getProjectSpace());
        item.setRepository(insight.getRepository());
        item.setSourceInsightIdsJson(SelfLearningTextUtil.toJsonArray(List.of(insight.getId())));
        item.setSourceRawEventIdsJson(insight.getSourceRawEventIdsJson());
        item.setType(insight.getType());
        item.setStatus(insight.getType() == LearningKnowledgeType.NEGATIVE_EXAMPLE
                ? LearningKnowledgeStatus.NEGATIVE
                : LearningKnowledgeStatus.VERIFIED);
        item.setTitle(insight.getTitle());
        item.setContent(formalKnowledgeContent(insight));
        item.setSummary(insight.getSummary());
        item.setApplicableScope(insight.getApplicableScope());
        item.setEvidenceJson(insight.getEvidenceJson());
        item.setGitProvenanceJson(insight.getGitProvenanceJson());
        item.setTags(insight.getTags());
        item.setConfidence(Math.max(0.7, insight.getConfidence()));
        return item;
    }

    private String formalKnowledgeContent(LearningInsight insight) {
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "结论", insight.getConclusion());
        appendSection(builder, "业务语境", insight.getBusinessContext());
        appendSection(builder, "代码名词映射", insight.getGlossaryMappingsJson());
        appendSection(builder, "代码符号", insight.getCodeSymbolsJson());
        appendSection(builder, "注意事项", insight.getWarnings());
        return builder.isEmpty() ? insight.getConclusion() : builder.toString();
    }

    private void appendSection(StringBuilder builder, String title, String value) {
        String content = SelfLearningTextUtil.trimToNull(value);
        if (content == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append("## ").append(title).append('\n').append(content);
    }

    private SaveLearningInsightRequest saveRequestFromDraft(LearningInsight insight, SelfLearningInsightDraft draft) {
        SaveLearningInsightRequest request = new SaveLearningInsightRequest();
        request.setRepositoryId(insight.getRepository() == null ? null : insight.getRepository().getId());
        request.setSourceRawEventIdsJson(insight.getSourceRawEventIdsJson());
        request.setSourceQuestionIdsJson(insight.getSourceQuestionIdsJson());
        request.setType(draft.getType() == null ? insight.getType() : draft.getType());
        request.setStatus(insight.getStatus());
        request.setTitle(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getTitle()), insight.getTitle()));
        request.setSummary(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getSummary()), insight.getSummary()));
        request.setConclusion(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getConclusion()), insight.getConclusion()));
        request.setBusinessContext(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getBusinessContext()), insight.getBusinessContext()));
        request.setGlossaryMappingsJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getGlossaryMappingsJson()), insight.getGlossaryMappingsJson()));
        request.setCodeSymbolsJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getCodeSymbolsJson()), insight.getCodeSymbolsJson()));
        request.setWarnings(SelfLearningTextUtil.mergeWarnings(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getWarnings()), insight.getWarnings())));
        request.setApplicableScope(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getApplicableScope()), insight.getApplicableScope()));
        request.setEvidenceJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getEvidenceJson()), insight.getEvidenceJson()));
        request.setGitProvenanceJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getGitProvenanceJson()), insight.getGitProvenanceJson()));
        request.setTags(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getTags()), insight.getTags()));
        request.setConfidence(draft.getConfidence() == null ? insight.getConfidence() : SelfLearningTextUtil.normalizeAgentConfidence(draft.getConfidence()));
        return request;
    }
}
