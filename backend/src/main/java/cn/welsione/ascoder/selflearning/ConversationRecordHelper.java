package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.question.application.QuestionQueryPort;
import cn.welsione.ascoder.question.domain.Question;
import cn.welsione.ascoder.question.domain.QueryPlan;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话原始记录写入助手，封装完整多轮会话到 LearningRawEvent 的沉淀逻辑，
 * 供 InsightService 与 AgentRunService 复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationRecordHelper {

    private final LearningRawEventJpaRepository rawEventRepository;
    private final QuestionQueryPort questionQueryPort;
    private final SelfLearningEntityLoader entityLoader;
    private final ObjectMapper objectMapper;

    /**
     * 解析 JSON 数组形式的 id 列表，供 InsightService / AgentRunService 间接复用 ObjectMapper。
     */
    public List<Long> parseJsonIds(String value) {
        return SelfLearningTextUtil.parseJsonIds(objectMapper, value);
    }

    /**
     * 将字符串序列化为 JSON 字符串字面量，供 InsightService / AgentRunService 间接复用 ObjectMapper。
     */
    public String jsonString(String value) {
        return SelfLearningTextUtil.jsonString(objectMapper, value);
    }

    public Set<String> existingConversationRecordKeys(Long projectSpaceId) {
        return rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId).stream()
                .filter(item -> item.getEventType() == LearningRawEventType.CONVERSATION_RECORD)
                .map(this::conversationRecordKey)
                .collect(Collectors.toSet());
    }

    public ImportResult importConversationRecord(
            ProjectSpace space,
            List<Question> questions,
            Map<Long, QueryPlan> queryPlans,
            Set<String> existingKeys,
            boolean allowUpdate
    ) {
        if (questions.isEmpty()) {
            return ImportResult.skipped();
        }
        String key = conversationRecordKey(questions.get(0));
        if (existingKeys.contains(key) && !allowUpdate) {
            return ImportResult.skipped();
        }
        upsertConversationRecord(space, questions, queryPlans);
        existingKeys.add(key);
        return ImportResult.imported();
    }

    public LearningRawEvent upsertConversationRecord(
            ProjectSpace space,
            List<Question> questions,
            Map<Long, QueryPlan> queryPlans
    ) {
        Question first = questions.get(0);
        String key = conversationRecordKey(first);
        LearningRawEvent event = rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(space.getId()).stream()
                .filter(item -> item.getEventType() == LearningRawEventType.CONVERSATION_RECORD)
                .filter(item -> conversationRecordKey(item).equals(key))
                .findFirst()
                .orElseGet(LearningRawEvent::new);
        Question last = questions.get(questions.size() - 1);
        event.setProjectSpace(space);
        event.setRepository(entityLoader.repository(last.getRepositoryId()));
        event.setBranchName(last.getBranchName());
        event.setQuestion(last);
        event.setConversationId(last.getConversation() == null ? null : last.getConversation().getId());
        event.setAgentId("conversation-recorder");
        event.setEventType(LearningRawEventType.CONVERSATION_RECORD);
        event.setSummary(conversationSummary(questions));
        event.setEventPayloadJson(conversationPayload(key, questions, queryPlans));
        event.setEvidenceJson(conversationEvidence(questions));
        event.setGitProvenanceJson(conversationGitProvenance(questions));
        event.setSourceCreatedAt(first.getCreatedAt());
        return rawEventRepository.save(event);
    }

    public List<Question> questionsInConversation(Question question) {
        if (question.getConversation() == null || question.getConversation().getId() == null) {
            return List.of(question);
        }
        List<Question> questions = questionQueryPort.findByConversationId(question.getConversation().getId());
        return questions.isEmpty() ? List.of(question) : questions;
    }

    public Map<Long, QueryPlan> queryPlansByQuestion(List<Question> questions) {
        List<Long> ids = questions.stream()
                .map(Question::getId)
                .filter(id -> id != null)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionQueryPort.findByQuestionIdIn(ids).stream()
                .collect(Collectors.toMap(item -> item.getQuestion().getId(), item -> item));
    }

    public List<List<Question>> groupQuestionsByConversation(List<Question> questions) {
        Map<String, List<Question>> groups = new LinkedHashMap<>();
        for (Question question : questions) {
            groups.computeIfAbsent(conversationRecordKey(question), ignored -> new ArrayList<>()).add(question);
        }
        return new ArrayList<>(groups.values());
    }

    private String conversationSummary(List<Question> questions) {
        StringBuilder builder = new StringBuilder();
        builder.append("完整多轮对话，共 ").append(questions.size()).append(" 轮。");
        for (Question question : questions) {
            builder.append("\n- 用户：").append(SelfLearningTextUtil.truncate(question.getText(), 220));
            String answer = SelfLearningTextUtil.firstNonBlank(question.getAnswerSummary(), question.getAnswer());
            if (answer != null && !answer.isBlank()) {
                builder.append("\n  回答：").append(SelfLearningTextUtil.truncate(answer, 260));
            }
        }
        return SelfLearningTextUtil.truncate(builder.toString(), 3000);
    }

    private String conversationPayload(String key, List<Question> questions, Map<Long, QueryPlan> queryPlans) {
        String turns = questions.stream()
                .map(question -> conversationTurnJson(question, queryPlans.get(question.getId())))
                .collect(Collectors.joining(",", "[", "]"));
        return "{"
                + "\"importKey\":" + SelfLearningTextUtil.jsonString(objectMapper, key)
                + ",\"source\":" + SelfLearningTextUtil.jsonString(objectMapper, "conversation-record")
                + ",\"conversationId\":" + SelfLearningTextUtil.jsonString(objectMapper, conversationIdValue(questions.get(0)))
                + ",\"turns\":" + turns
                + "}";
    }

    private String conversationTurnJson(Question question, QueryPlan queryPlan) {
        return "{"
                + "\"questionId\":" + question.getId()
                + ",\"status\":" + SelfLearningTextUtil.jsonString(objectMapper, question.getStatus() == null ? null : question.getStatus().name())
                + ",\"userQuestion\":" + SelfLearningTextUtil.jsonString(objectMapper, SelfLearningTextUtil.truncate(question.getText(), 3000))
                + ",\"queryPlan\":" + SelfLearningTextUtil.jsonString(objectMapper, queryPlan == null ? null : SelfLearningTextUtil.truncate(queryPlanSummary(queryPlan), 3000))
                + ",\"answerSummary\":" + SelfLearningTextUtil.jsonString(objectMapper, SelfLearningTextUtil.truncate(question.getAnswerSummary(), 2000))
                + ",\"answer\":" + SelfLearningTextUtil.jsonString(objectMapper, SelfLearningTextUtil.truncate(question.getAnswer(), 6000))
                + ",\"analysisProcess\":" + SelfLearningTextUtil.jsonString(objectMapper, SelfLearningTextUtil.truncate(question.getAnalysisProcess(), 4000))
                + "}";
    }

    private String queryPlanSummary(QueryPlan queryPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append("问题类型：").append(queryPlan.getType()).append('\n');
        builder.append("改写查询：").append(queryPlan.getRewrittenQueriesJson()).append('\n');
        builder.append("推荐工具：").append(queryPlan.getRecommendedToolsJson()).append('\n');
        if (queryPlan.getRecommendedSkillsJson() != null) {
            builder.append("推荐技能：").append(queryPlan.getRecommendedSkillsJson()).append('\n');
        }
        builder.append("置信度：").append(queryPlan.getConfidence()).append('\n');
        if (queryPlan.getMatchedSignalsJson() != null) {
            builder.append("命中信号：").append(queryPlan.getMatchedSignalsJson()).append('\n');
        }
        if (queryPlan.getReasoning() != null && !queryPlan.getReasoning().isBlank()) {
            builder.append("推理说明：").append(queryPlan.getReasoning());
        }
        return builder.toString();
    }

    private String conversationEvidence(List<Question> questions) {
        List<String> evidence = questions.stream()
                .map(Question::getAnswerEvidenceJson)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(value -> SelfLearningTextUtil.jsonString(objectMapper, value))
                .toList();
        if (evidence.isEmpty()) {
            return null;
        }
        return evidence.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private String conversationGitProvenance(List<Question> questions) {
        List<String> items = questions.stream()
                .filter(question -> question.getCommitSha() != null || question.getBranchName() != null)
                .map(question -> "{"
                        + "\"questionId\":" + question.getId()
                        + ",\"branchName\":" + SelfLearningTextUtil.jsonString(objectMapper, question.getBranchName())
                        + ",\"commitSha\":" + SelfLearningTextUtil.jsonString(objectMapper, question.getCommitSha())
                        + ",\"source\":" + SelfLearningTextUtil.jsonString(objectMapper, "question-history")
                        + "}")
                .distinct()
                .toList();
        if (items.isEmpty()) {
            return null;
        }
        return items.stream().collect(Collectors.joining(",", "[", "]"));
    }

    public String conversationRecordKey(LearningRawEvent event) {
        if (event.getConversationId() != null) {
            return "conversation-" + event.getConversationId();
        }
        if (event.getQuestion() != null && event.getQuestion().getId() != null) {
            return "question-" + event.getQuestion().getId();
        }
        return "raw-" + event.getId();
    }

    public String conversationRecordKey(Question question) {
        if (question.getConversation() != null && question.getConversation().getId() != null) {
            return "conversation-" + question.getConversation().getId();
        }
        return "question-" + question.getId();
    }

    private String conversationIdValue(Question question) {
        if (question.getConversation() == null || question.getConversation().getId() == null) {
            return null;
        }
        return String.valueOf(question.getConversation().getId());
    }

    /**
     * 单条历史原始事件导入结果。
     */
    public static class ImportResult {
        private final int importedCount;
        private final int skippedCount;

        private ImportResult(int importedCount, int skippedCount) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
        }

        public static ImportResult imported() {
            return new ImportResult(1, 0);
        }

        public static ImportResult skipped() {
            return new ImportResult(0, 1);
        }

        public int importedCount() {
            return importedCount;
        }

        public int skippedCount() {
            return skippedCount;
        }
    }
}
