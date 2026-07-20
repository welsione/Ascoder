package cn.welsione.ascoder.selflearning;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Self Learning Agent 只读工具，提供正式知识、候选洞察和原始记录检索。
 */
@RequiredArgsConstructor
public class SelfLearningAgentTools {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int MAX_FIELD_CHARS = 900;

    private final Long projectSpaceId;
    private final LearningKnowledgeItemJpaRepository knowledgeRepository;
    private final LearningInsightJpaRepository insightRepository;
    private final LearningRawEventJpaRepository rawEventRepository;

    @Tool(
            name = "self_learning_search_knowledge",
            description = """
                    Search approved project-space knowledge. These results are historical
                    clues only; current code and Git evidence still have priority.
                    """
    )
    public Mono<ToolResultBlock> searchKnowledge(
            @ToolParam(name = "query", required = false, description = "Optional keyword query.") String query,
            @ToolParam(name = "limit", required = false, description = "Max results, default 5, capped at 20.") Integer limit
    ) {
        return Mono.fromCallable(() -> {
            List<LearningKnowledgeItem> items = knowledgeRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                    .filter(this::usableKnowledge)
                    .filter(item -> matches(item, query))
                    .limit(normalizedLimit(limit))
                    .toList();
            return success(renderKnowledge(items));
        });
    }

    @Tool(
            name = "self_learning_pending_insights",
            description = """
                    List pending review learning insights generated from raw events.
                    Use this to avoid presenting unreviewed insights as facts.
                    """
    )
    public Mono<ToolResultBlock> pendingInsights(
            @ToolParam(name = "limit", required = false, description = "Max results, default 5, capped at 20.") Integer limit
    ) {
        return Mono.fromCallable(() -> {
            List<LearningInsight> items = insightRepository.findByProjectSpace_IdOrderByUpdatedAtDesc(projectSpaceId).stream()
                    .filter(item -> item.getStatus() == LearningInsightStatus.PENDING_REVIEW)
                    .limit(normalizedLimit(limit))
                    .toList();
            return success(renderInsights(items));
        });
    }

    @Tool(
            name = "self_learning_recent_raw_events",
            description = """
                    List recent raw learning events: user questions, QueryPlans, Agent outputs,
                    tool results, feedback, corrections, and evidence traces.
                    """
    )
    public Mono<ToolResultBlock> recentRawEvents(
            @ToolParam(name = "eventType", required = false, description = "Optional event type such as USER_QUESTION or ASSISTANT_ANSWER.") String eventType,
            @ToolParam(name = "limit", required = false, description = "Max results, default 5, capped at 20.") Integer limit
    ) {
        return Mono.fromCallable(() -> {
            LearningRawEventType type = parseEventType(eventType);
            List<LearningRawEvent> items = rawEventRepository.findByProjectSpace_IdOrderByCreatedAtDesc(projectSpaceId).stream()
                    .filter(item -> type == null || item.getEventType() == type)
                    .limit(normalizedLimit(limit))
                    .toList();
            return success(renderRawEvents(items));
        });
    }

    private ToolResultBlock success(String content) {
        return new ToolResultBlock(null, null,
                List.of(TextBlock.builder().text(content).build()),
                null);
    }

    private boolean usableKnowledge(LearningKnowledgeItem item) {
        return item.getStatus() == LearningKnowledgeStatus.ACTIVE
                || item.getStatus() == LearningKnowledgeStatus.VERIFIED
                || item.getStatus() == LearningKnowledgeStatus.NEGATIVE;
    }

    private boolean matches(LearningKnowledgeItem item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String lowerQuery = query.toLowerCase();
        String haystack = String.join(" ",
                nullToEmpty(item.getTitle()),
                nullToEmpty(item.getContent()),
                nullToEmpty(item.getSummary()),
                nullToEmpty(item.getApplicableScope()),
                nullToEmpty(item.getTags())
        ).toLowerCase();
        for (String token : lowerQuery.split("[\\s,，。；;：:、]+")) {
            if (token.length() >= 2 && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private int normalizedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private LearningRawEventType parseEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        try {
            return LearningRawEventType.valueOf(eventType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String renderKnowledge(List<LearningKnowledgeItem> items) {
        if (items.isEmpty()) {
            return "未找到可召回的正式知识。";
        }
        StringBuilder builder = new StringBuilder("正式知识检索结果：\n");
        for (LearningKnowledgeItem item : items) {
            builder.append("- #").append(item.getId()).append(' ')
                    .append(item.getTitle()).append('\n')
                    .append("  type=").append(item.getType())
                    .append(", status=").append(item.getStatus())
                    .append(", confidence=").append(item.getConfidence()).append('\n')
                    .append("  content=").append(truncate(item.getContent())).append('\n');
            if (item.getApplicableScope() != null) {
                builder.append("  scope=").append(truncate(item.getApplicableScope())).append('\n');
            }
            if (item.getGitProvenanceJson() != null) {
                builder.append("  gitProvenance=").append(truncate(item.getGitProvenanceJson())).append('\n');
            }
        }
        return builder.toString();
    }

    private String renderInsights(List<LearningInsight> items) {
        if (items.isEmpty()) {
            return "没有待审核候选洞察。";
        }
        StringBuilder builder = new StringBuilder("待审核候选洞察：\n");
        for (LearningInsight item : items) {
            builder.append("- #").append(item.getId()).append(' ')
                    .append(item.getTitle()).append('\n')
                    .append("  type=").append(item.getType())
                    .append(", status=").append(item.getStatus())
                    .append(", confidence=").append(item.getConfidence()).append('\n')
                    .append("  conclusion=").append(truncate(item.getConclusion())).append('\n');
            if (item.getWarnings() != null) {
                builder.append("  warnings=").append(truncate(item.getWarnings())).append('\n');
            }
        }
        return builder.toString();
    }

    private String renderRawEvents(List<LearningRawEvent> items) {
        if (items.isEmpty()) {
            return "没有匹配的原始记录。";
        }
        StringBuilder builder = new StringBuilder("最近原始记录：\n");
        for (LearningRawEvent item : items) {
            builder.append("- #").append(item.getId())
                    .append(" type=").append(item.getEventType())
                    .append(", agent=").append(nullToEmpty(item.getAgentId()))
                    .append(", questionId=").append(item.getQuestion() == null ? "-" : item.getQuestion().getId())
                    .append('\n')
                    .append("  summary=").append(truncate(item.getSummary())).append('\n');
        }
        return builder.toString();
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_FIELD_CHARS ? value : value.substring(0, MAX_FIELD_CHARS) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
