package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Self Learning Agent 运行服务，负责后台整理原始记录为候选洞察、运行记录管理、
 * 历史聊天导入与旧粒度数据清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private static final int DEFAULT_AGENT_RUN_LIMIT = 12;
    private static final int MAX_AGENT_RUN_LIMIT = 30;
    private static final Pattern CODE_SYMBOL_PATTERN = Pattern.compile(
            "\\b[A-Z][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*(?:\\(\\))?\\b"
    );
    private static final Set<LearningRawEventType> LEGACY_SPLIT_EVENT_TYPES = Set.of(
            LearningRawEventType.USER_QUESTION,
            LearningRawEventType.QUERY_PLAN,
            LearningRawEventType.ASSISTANT_ANSWER
    );

    private final SelfLearningEntityLoader entityLoader;
    private final ConversationRecordHelper conversationRecordHelper;
    private final SelfLearningInsightAgent insightAgent;
    private final InsightFieldTruncator insightFieldTruncator;
    private final TransactionTemplate transactionTemplate;

    public SelfLearningAgentRunResponse runSelfLearningAgent(Long projectSpaceId, Integer limit) {
        return runSelfLearningAgent(projectSpaceId, limit, null);
    }

    public SelfLearningAgentRunResponse runSelfLearningAgent(Long projectSpaceId, Integer limit, Long runId) {
        int normalizedLimit = normalizeAgentRunLimit(limit);
        AgentRunWork work = loadAgentRunWork(projectSpaceId, normalizedLimit);
        if (!work.isEnabled()) {
            return new SelfLearningAgentRunResponse(0, 0, 0, "自学习未开启，未整理原始记录。");
        }
        if (!work.isRawEventCaptureEnabled()) {
            return new SelfLearningAgentRunResponse(0, 0, 0, "原始事件记录未开启，没有可整理的数据入口。");
        }
        if (work.getGroups().isEmpty()) {
            return new SelfLearningAgentRunResponse(0, 0, work.getUsedRawEventCount(), "没有新的原始记录需要整理。");
        }

        int createdCount = 0;
        int consumedCount = 0;
        int failedCount = 0;
        List<String> failures = new ArrayList<>();
        for (List<LearningRawEvent> group : work.getGroups()) {
            String rawEventIdsJson = SelfLearningTextUtil.toJsonArray(group.stream().map(LearningRawEvent::getId).toList());
            try {
                LearningInsight insight = insightFromRawEventGroup(work.getProjectSpace(), group);
                saveAgentInsight(projectSpaceId, insight);
                createdCount++;
                consumedCount += group.size();
                updateAgentRunProgress(runId, createdCount, consumedCount, failedCount, rawEventIdsJson, SelfLearningTextUtil.toJsonArrayText(failures));
                log.info("Self Learning Agent 候选洞察已保存，projectSpaceId={}，rawEventIds={}，createdInsights={}，consumedRawEvents={}",
                        projectSpaceId, rawEventIdsJson, createdCount, consumedCount);
            } catch (SelfLearningInsightException ex) {
                failedCount++;
                failures.add(conversationFailureJson(rawEventIdsJson, ex.getMessage()));
                updateAgentRunProgress(runId, createdCount, consumedCount, failedCount, rawEventIdsJson, SelfLearningTextUtil.toJsonArrayText(failures));
                log.warn("Self Learning Agent conversation 整理失败，projectSpaceId={}，rawEventIds={}，error={}",
                        projectSpaceId, rawEventIdsJson, ex.getMessage());
            }
        }
        log.info("Self Learning Agent 整理原始记录完成，projectSpaceId={}，createdInsights={}，consumedRawEvents={}，failedConversations={}",
                projectSpaceId, createdCount, consumedCount, failedCount);
        return new SelfLearningAgentRunResponse(
                null,
                agentRunStatus(createdCount, failedCount),
                createdCount,
                consumedCount,
                Math.max(0, work.getRecentRawEventCount() - consumedCount),
                failedCount,
                agentRunMessage(createdCount, failedCount)
        );
    }

    @Transactional
    public LearningAgentRun createAgentRun(Long projectSpaceId, Integer limit) {
        ProjectSpace space = entityLoader.projectSpace(projectSpaceId);
        LearningAgentRun run = new LearningAgentRun();
        run.setProjectSpace(space);
        run.setLimitCount(normalizeAgentRunLimit(limit));
        run.setMessage("Self Learning Agent 已进入后台队列。");
        return entityLoader.saveAgentRun(run);
    }

    @Transactional
    public void markAgentRunRunning(Long runId) {
        LearningAgentRun run = entityLoader.agentRun(runId);
        run.start();
        run.setMessage("Self Learning Agent 正在整理原始 conversation 记录。");
        entityLoader.saveAgentRun(run);
    }

    @Transactional
    public void completeAgentRun(Long runId, SelfLearningAgentRunResponse response) {
        LearningAgentRun run = entityLoader.agentRun(runId);
        LearningAgentRunStatus status = response.getStatus() == null
                ? agentRunStatus(response.getCreatedInsightCount(), response.getFailedConversationCount())
                : response.getStatus();
        run.complete(status, response);
        entityLoader.saveAgentRun(run);
    }

    @Transactional
    public void failAgentRun(Long runId, String errorMessage) {
        LearningAgentRun run = entityLoader.agentRun(runId);
        run.fail(errorMessage);
        entityLoader.saveAgentRun(run);
    }

    @Transactional(readOnly = true)
    public List<LearningAgentRunResponse> listAgentRuns(Long projectSpaceId) {
        entityLoader.projectSpace(projectSpaceId);
        return entityLoader.agentRunsByProjectSpace(projectSpaceId).stream()
                .map(LearningAgentRunResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LearningRawEventResponse> listRawEvents(Long projectSpaceId) {
        entityLoader.projectSpace(projectSpaceId);
        return entityLoader.rawEventsByProjectSpace(projectSpaceId).stream()
                .map(LearningRawEventResponse::from)
                .toList();
    }

    @Transactional
    public ImportHistoryRawEventsResponse importHistoryRawEvents(Long projectSpaceId) {
        ProjectSpace space = entityLoader.projectSpace(projectSpaceId);
        SelfLearningSettings settings = entityLoader.settings(projectSpaceId);
        if (!settings.isEnabled()) {
            return new ImportHistoryRawEventsResponse(0, 0, 0, "自学习未开启，未导入历史聊天。");
        }
        if (!settings.isRawEventCaptureEnabled()) {
            return new ImportHistoryRawEventsResponse(0, 0, 0, "原始事件记录未开启，未导入历史聊天。");
        }

        List<Question> questions = entityLoader.questionsInProjectSpace(projectSpaceId);
        if (questions.isEmpty()) {
            return new ImportHistoryRawEventsResponse(0, 0, 0, "当前项目空间没有可导入的历史聊天。");
        }

        Map<Long, QueryPlan> queryPlans = entityLoader.queryPlansByQuestionIds(
                        questions.stream().map(Question::getId).toList()
                ).stream()
                .collect(Collectors.toMap(item -> item.getQuestion().getId(), item -> item));
        Set<String> existingKeys = conversationRecordHelper.existingConversationRecordKeys(projectSpaceId);
        int importedConversations = 0;
        int importedEvents = 0;
        int skippedEvents = 0;

        for (List<Question> conversationQuestions : conversationRecordHelper.groupQuestionsByConversation(questions)) {
            ConversationRecordHelper.ImportResult result = conversationRecordHelper.importConversationRecord(space, conversationQuestions, queryPlans, existingKeys, false);
            importedEvents += result.importedCount();
            skippedEvents += result.skippedCount();
            if (result.importedCount() > 0) {
                importedConversations++;
            }
        }

        log.info("导入历史聊天为自学习原始记录完成，projectSpaceId={}，conversations={}，events={}，skipped={}",
                projectSpaceId, importedConversations, importedEvents, skippedEvents);
        return new ImportHistoryRawEventsResponse(
                importedConversations,
                importedEvents,
                skippedEvents,
                importedEvents == 0 ? "没有新的历史聊天需要导入。" : "已导入 " + importedConversations + " 个会话原始记录。"
        );
    }

    @Transactional
    public CleanupLegacyRawEventsResponse cleanupLegacyRawEvents(Long projectSpaceId) {
        entityLoader.projectSpace(projectSpaceId);
        List<LearningRawEvent> legacyEvents = entityLoader.rawEventsByProjectSpace(projectSpaceId).stream()
                .filter(item -> LEGACY_SPLIT_EVENT_TYPES.contains(item.getEventType()))
                .toList();
        if (legacyEvents.isEmpty()) {
            return new CleanupLegacyRawEventsResponse(0, 0, 0, "没有需要清理的旧粒度原始记录。");
        }

        Set<Long> legacyIds = legacyEvents.stream()
                .map(LearningRawEvent::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        List<LearningInsight> removableInsights = entityLoader.insightsByProjectSpace(projectSpaceId).stream()
                .filter(item -> item.getStatus() == LearningInsightStatus.DRAFT
                        || item.getStatus() == LearningInsightStatus.PENDING_REVIEW
                        || item.getStatus() == LearningInsightStatus.REJECTED)
                .filter(item -> referencesAnyRawEvent(item.getSourceRawEventIdsJson(), legacyIds))
                .toList();
        List<LearningKnowledgeItem> staleKnowledge = entityLoader.knowledgeItemsByProjectSpace(projectSpaceId).stream()
                .filter(item -> referencesAnyRawEvent(item.getSourceRawEventIdsJson(), legacyIds))
                .filter(item -> item.getStatus() == LearningKnowledgeStatus.ACTIVE
                        || item.getStatus() == LearningKnowledgeStatus.VERIFIED)
                .toList();

        for (LearningKnowledgeItem item : staleKnowledge) {
            item.markStale("来源原始记录来自旧拆分粒度，已清理旧数据，请基于 conversation 级原始记录重新复核。");
        }
        entityLoader.saveKnowledgeItems(staleKnowledge);
        entityLoader.deleteInsights(removableInsights);
        entityLoader.deleteRawEvents(legacyEvents);

        log.info("清理旧粒度自学习原始记录完成，projectSpaceId={}，rawEvents={}，insights={}，staleKnowledge={}",
                projectSpaceId, legacyEvents.size(), removableInsights.size(), staleKnowledge.size());
        return new CleanupLegacyRawEventsResponse(
                legacyEvents.size(),
                removableInsights.size(),
                staleKnowledge.size(),
                "已清理 " + legacyEvents.size() + " 条旧粒度原始记录。"
        );
    }

    @Transactional
    public void recordQuestionPlan(Long questionId, String queryPlanText) {
        // 原始记录以完整 conversation 为单位沉淀，问题规划会在回答完成后随会话整体写入。
    }

    private void updateAgentRunProgress(
            Long runId,
            int createdCount,
            int consumedCount,
            int failedCount,
            String rawEventIdsJson,
            String failuresJson
    ) {
        if (runId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            LearningAgentRun run = entityLoader.agentRun(runId);
            run.progress(createdCount, consumedCount, failedCount, rawEventIdsJson, failuresJson);
            entityLoader.saveAgentRun(run);
        });
    }

    private LearningAgentRunStatus agentRunStatus(int createdCount, int failedCount) {
        if (failedCount > 0 && createdCount == 0) {
            return LearningAgentRunStatus.FAILED;
        }
        if (failedCount > 0) {
            return LearningAgentRunStatus.PARTIAL_FAILED;
        }
        if (createdCount == 0) {
            return LearningAgentRunStatus.SKIPPED;
        }
        return LearningAgentRunStatus.SUCCEEDED;
    }

    private String agentRunMessage(int createdCount, int failedCount) {
        if (failedCount > 0 && createdCount == 0) {
            return "Self Learning Agent 整理失败，未生成候选洞察。";
        }
        if (failedCount > 0) {
            return "已生成 " + createdCount + " 条待审核候选洞察，" + failedCount + " 个 conversation 整理失败。";
        }
        if (createdCount == 0) {
            return "没有新的原始记录需要整理。";
        }
        return "已生成 " + createdCount + " 条待审核候选洞察。";
    }

    private String conversationFailureJson(String rawEventIdsJson, String errorMessage) {
        return "{\"rawEventIds\":" + rawEventIdsJson
                + ",\"errorMessage\":" + conversationRecordHelper.jsonString(errorMessage)
                + ",\"failedAt\":" + conversationRecordHelper.jsonString(new Date().toString())
                + "}";
    }

    private AgentRunWork loadAgentRunWork(Long projectSpaceId, int normalizedLimit) {
        return transactionTemplate.execute(status -> {
            ProjectSpace space = entityLoader.projectSpace(projectSpaceId);
            SelfLearningSettings settings = entityLoader.settings(projectSpaceId);
            if (!settings.isEnabled() || !settings.isRawEventCaptureEnabled()) {
                return new AgentRunWork(
                        space,
                        settings.isEnabled(),
                        settings.isRawEventCaptureEnabled(),
                        List.of(),
                        0,
                        0
                );
            }
            Set<Long> usedRawEventIds = usedRawEventIds(projectSpaceId);
            List<LearningRawEvent> recentRawEvents = entityLoader.recentRawEvents(projectSpaceId, 50);
            List<LearningRawEvent> candidates = recentRawEvents.stream()
                    .filter(item -> item.getId() != null && !usedRawEventIds.contains(item.getId()))
                    .filter(item -> item.getSummary() != null && !item.getSummary().isBlank())
                    .limit(normalizedLimit)
                    .toList();
            List<List<LearningRawEvent>> groups = groupRawEvents(candidates);
            initializeAgentRunGroups(groups);
            return new AgentRunWork(
                    space,
                    true,
                    true,
                    groups,
                    recentRawEvents.size(),
                    usedRawEventIds.size()
            );
        });
    }

    private void saveAgentInsight(Long projectSpaceId, LearningInsight insight) {
        transactionTemplate.executeWithoutResult(status -> {
            ProjectSpace managedSpace = entityLoader.projectSpace(projectSpaceId);
            Long repositoryId = insight.getRepository() == null ? null : insight.getRepository().getId();
            insight.setProjectSpace(managedSpace);
            insight.setRepository(entityLoader.findRepository(repositoryId));
            entityLoader.saveInsight(insight);
        });
    }

    private void initializeAgentRunGroups(List<List<LearningRawEvent>> groups) {
        for (List<LearningRawEvent> group : groups) {
            for (LearningRawEvent event : group) {
                if (event.getProjectSpace() != null) {
                    event.getProjectSpace().getName();
                }
                if (event.getRepository() != null) {
                    event.getRepository().getId();
                }
                if (event.getQuestion() != null) {
                    event.getQuestion().getId();
                }
            }
        }
    }

    private int normalizeAgentRunLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_AGENT_RUN_LIMIT;
        }
        return Math.min(limit, MAX_AGENT_RUN_LIMIT);
    }

    private Set<Long> usedRawEventIds(Long projectSpaceId) {
        return entityLoader.insightsByProjectSpace(projectSpaceId).stream()
                .flatMap(item -> conversationRecordHelper.parseJsonIds(item.getSourceRawEventIdsJson()).stream())
                .collect(Collectors.toSet());
    }

    private boolean referencesAnyRawEvent(String sourceRawEventIdsJson, Set<Long> rawEventIds) {
        if (rawEventIds.isEmpty()) {
            return false;
        }
        return conversationRecordHelper.parseJsonIds(sourceRawEventIdsJson).stream().anyMatch(rawEventIds::contains);
    }

    private List<List<LearningRawEvent>> groupRawEvents(List<LearningRawEvent> events) {
        Map<String, List<LearningRawEvent>> groups = new LinkedHashMap<>();
        for (LearningRawEvent event : events) {
            String key = event.getQuestion() == null || event.getQuestion().getId() == null
                    ? "raw-" + event.getId()
                    : "question-" + event.getQuestion().getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        }
        return new ArrayList<>(groups.values());
    }

    private LearningInsight insightFromRawEventGroup(ProjectSpace space, List<LearningRawEvent> events) {
        LearningRawEvent first = events.get(0);
        LearningInsight insight = new LearningInsight();
        insight.setProjectSpace(space);
        insight.setRepository(first.getRepository());
        insight.setSourceRawEventIdsJson(SelfLearningTextUtil.toJsonArray(events.stream().map(LearningRawEvent::getId).toList()));
        insight.setSourceQuestionIdsJson(SelfLearningTextUtil.toJsonArray(events.stream()
                .map(LearningRawEvent::getQuestion)
                .filter(question -> question != null && question.getId() != null)
                .map(Question::getId)
                .distinct()
                .toList()));
        SelfLearningInsightDraft draft = agentDraft(space, events);
        insight.setType(draft.getType() == null ? inferKnowledgeType(events) : draft.getType());
        insight.setStatus(LearningInsightStatus.PENDING_REVIEW);
        insight.setTitle(insightFieldTruncator.truncateTitle(
                SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getTitle()), agentInsightTitle(first))));
        insight.setSummary(insightFieldTruncator.truncateSummary(
                SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getSummary()), agentInsightSummary(events))));
        insight.setConclusion(insightFieldTruncator.truncateConclusion(
                SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getConclusion()), agentInsightConclusion(events))));
        insight.setBusinessContext(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getBusinessContext()), agentBusinessContext(events)));
        insight.setGlossaryMappingsJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getGlossaryMappingsJson()), agentGlossaryMappings(events)));
        insight.setCodeSymbolsJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getCodeSymbolsJson()), agentCodeSymbols(events)));
        insight.setWarnings(SelfLearningTextUtil.mergeWarnings(draft.getWarnings()));
        insight.setApplicableScope(SelfLearningTextUtil.firstNonBlank(
                SelfLearningTextUtil.trimToNull(draft.getApplicableScope()),
                "项目空间：" + space.getName() + "；来源：完整会话原始记录 " + events.size() + " 条。"
        ));
        insight.setEvidenceJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getEvidenceJson()), first.getEvidenceJson()));
        insight.setGitProvenanceJson(SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getGitProvenanceJson()), first.getGitProvenanceJson()));
        insight.setTags(insightFieldTruncator.truncateTags(
                SelfLearningTextUtil.firstNonBlank(SelfLearningTextUtil.trimToNull(draft.getTags()), "self-learning-agent,conversation-record")));
        insight.setConfidence(SelfLearningTextUtil.normalizeAgentConfidence(draft.getConfidence()));
        return insight;
    }

    private SelfLearningInsightDraft agentDraft(ProjectSpace space, List<LearningRawEvent> events) {
        Optional<SelfLearningInsightDraft> draft = insightAgent.summarize(space, events);
        if (draft != null && draft.isPresent()) {
            return draft.get();
        }
        throw new SelfLearningInsightException("Self Learning Agent 未生成可审核洞察草稿。");
    }

    private LearningKnowledgeType inferKnowledgeType(List<LearningRawEvent> events) {
        boolean hasCorrection = events.stream()
                .anyMatch(item -> item.getEventType() == LearningRawEventType.USER_CORRECTION
                        || item.getEventType() == LearningRawEventType.USER_FEEDBACK);
        if (hasCorrection) {
            return LearningKnowledgeType.NEGATIVE_EXAMPLE;
        }
        boolean hasConversation = events.stream().anyMatch(item -> item.getEventType() == LearningRawEventType.CONVERSATION_RECORD);
        if (hasConversation) {
            return LearningKnowledgeType.QUESTION_ANSWER;
        }
        boolean hasPlan = events.stream().anyMatch(item -> item.getEventType() == LearningRawEventType.QUERY_PLAN);
        if (hasPlan) {
            return LearningKnowledgeType.QUESTION_ANSWER;
        }
        return LearningKnowledgeType.BUSINESS_CONTEXT;
    }

    private String agentInsightTitle(LearningRawEvent first) {
        String prefix = switch (first.getEventType()) {
            case USER_CORRECTION, USER_FEEDBACK -> "用户反馈洞察";
            case QUERY_PLAN -> "问题分析路径洞察";
            case TOOL_RESULT, CODE_EVIDENCE, GIT_EVIDENCE -> "证据使用洞察";
            default -> "项目问答洞察";
        };
        return prefix + "：" + SelfLearningTextUtil.truncate(first.getSummary(), 80);
    }

    private String agentInsightSummary(List<LearningRawEvent> events) {
        StringBuilder builder = new StringBuilder("Self Learning Agent 从原始记录中整理出以下上下文：");
        for (LearningRawEvent event : events) {
            builder.append("\n- ").append(event.getEventType()).append("：")
                    .append(SelfLearningTextUtil.truncate(event.getSummary(), 260));
        }
        return SelfLearningTextUtil.truncate(builder.toString(), 1800);
    }

    private String agentInsightConclusion(List<LearningRawEvent> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("候选结论：这些原始记录可能沉淀为项目经验，需管理员确认是否对后续用户有帮助。\n");
        builder.append("结构化语境：\n");
        for (LearningRawEvent event : events) {
            builder.append("- ").append(event.getEventType()).append("：")
                    .append(SelfLearningTextUtil.truncate(event.getSummary(), 420)).append('\n');
        }
        builder.append("审核建议：确认业务语境、代码术语映射、适用范围、反例提醒和 Git 证据后，再归纳为正式知识。");
        return SelfLearningTextUtil.truncate(builder.toString(), 5000);
    }

    private String agentBusinessContext(List<LearningRawEvent> events) {
        return events.stream()
                .map(LearningRawEvent::getSummary)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> SelfLearningTextUtil.truncate(value, 160))
                .collect(Collectors.joining("\n", "原始上下文：\n", ""));
    }

    private String agentGlossaryMappings(List<LearningRawEvent> events) {
        List<String> symbols = extractedCodeSymbols(events);
        if (symbols.isEmpty()) {
            return null;
        }
        return symbols.stream()
                .limit(10)
                .map(symbol -> "{\"term\":" + conversationRecordHelper.jsonString(symbol)
                        + ",\"meaning\":\"代码符号，需管理员审核确认业务含义和适用范围\""
                        + ",\"source\":\"Self Learning Agent 从原始记录提取\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String agentCodeSymbols(List<LearningRawEvent> events) {
        List<String> symbols = extractedCodeSymbols(events);
        if (symbols.isEmpty()) {
            return null;
        }
        return symbols.stream()
                .limit(20)
                .map(symbol -> conversationRecordHelper.jsonString(symbol))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private List<String> extractedCodeSymbols(List<LearningRawEvent> events) {
        return events.stream()
                .map(LearningRawEvent::getSummary)
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> {
                    List<String> symbols = new ArrayList<>();
                    Matcher matcher = CODE_SYMBOL_PATTERN.matcher(value);
                    while (matcher.find()) {
                        symbols.add(matcher.group());
                    }
                    return symbols.stream();
                })
                .map(symbol -> symbol.endsWith("()") ? symbol.substring(0, symbol.length() - 2) : symbol)
                .filter(symbol -> symbol.length() >= 3)
                .distinct()
                .toList();
    }

    /**
     * 单次 Agent 运行的工作集合，包含项目空间、设置开关与待整理分组。
     */
    @lombok.Value
    private static class AgentRunWork {
        ProjectSpace projectSpace;
        boolean enabled;
        boolean rawEventCaptureEnabled;
        List<List<LearningRawEvent>> groups;
        int recentRawEventCount;
        int usedRawEventCount;
    }
}
