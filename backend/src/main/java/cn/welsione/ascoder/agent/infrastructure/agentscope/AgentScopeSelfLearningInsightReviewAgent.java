package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import cn.welsione.ascoder.codegraph.port.CodeGraphClient;
import cn.welsione.ascoder.codegraph.port.CodeGraphToolResult;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.selflearning.LearningInsight;
import cn.welsione.ascoder.selflearning.LearningRawEvent;
import cn.welsione.ascoder.selflearning.SelfLearningInsightDraft;
import cn.welsione.ascoder.selflearning.SelfLearningInsightException;
import cn.welsione.ascoder.selflearning.SelfLearningInsightReviewAgent;
import cn.welsione.ascoder.selflearning.SelfLearningInsightVerification;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 AgentScope 的候选洞察审核 Agent，结合 CodeGraph 证据进行复核和微调建议生成。
 *
 * <p>verify 从 AgentConfig agentId="self-learning-insight-review" 加载配置，
 * refine 从 AgentConfig agentId="self-learning-insight-refine" 加载配置；
 * config 为空时回退硬编码默认值（D2 兜底策略）。单 Bean 双 agentId，每次方法调用独立查询。</p>
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ascoder.llm-provider", havingValue = "agentscope", matchIfMissing = true)
public class AgentScopeSelfLearningInsightReviewAgent implements SelfLearningInsightReviewAgent {

    static final String REVIEW_AGENT_ID = "self-learning-insight-review";
    static final String REFINE_AGENT_ID = "self-learning-insight-refine";

    private static final Pattern CODE_SYMBOL_PATTERN = Pattern.compile(
            "\\b[A-Z][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*(?:\\(\\))?\\b"
    );
    private static final int MAX_SYMBOLS = 8;
    private static final int MAX_RAW_EVENT_CHARS = 6000;
    private static final int MAX_CODE_EVIDENCE_CHARS = 9000;
    private static final int FALLBACK_MAX_TOKENS = 1800;
    private static final int FALLBACK_MAX_ITERS = 1;

    private final AgentConfigService agentConfigService;
    private final ChatModelFactory chatModelFactory;
    private final CodeGraphClient codeGraphClient;
    private final ObjectMapper objectMapper;
    private final PromptManager promptManager;

    @org.springframework.beans.factory.annotation.Value("${ascoder.project-space-root:./data/project-spaces}")
    private String projectSpaceRoot;

    @Override
    public SelfLearningInsightVerification verify(
            ProjectSpace projectSpace,
            LearningInsight insight,
            List<LearningRawEvent> rawEvents
    ) {
        AgentConfig config = agentConfigService.getByAgentId(REVIEW_AGENT_ID).orElse(null);
        if (config == null) {
            log.warn("未找到 {} AgentConfig，回退硬编码默认配置", REVIEW_AGENT_ID);
        }
        String codeEvidence = collectCodeEvidence(projectSpace, insight);
        String taskText = buildTaskText(REVIEW_AGENT_ID, ReviewPromptData.class, new ReviewPromptData(
                projectSpace.getName(),
                insightText(insight),
                rawEventsText(rawEvents),
                codeEvidence
        ));
        try {
            Msg response = buildVerifyAgent(config).call(
                    List.of(Msg.builder().textContent(taskText).build()),
                    SelfLearningInsightVerification.class
            ).block(chatModelFactory.timeout());
            SelfLearningInsightVerification verification = parse(response, SelfLearningInsightVerification.class);
            if (verification == null) {
                throw new SelfLearningInsightException("Insight Review Agent 未返回结构化复核结果");
            }
            return verification;
        } catch (RuntimeException ex) {
            if (ex instanceof SelfLearningInsightException insightException) {
                throw insightException;
            }
            throw new SelfLearningInsightException("Insight Review Agent 复核失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public SelfLearningInsightDraft refine(
            ProjectSpace projectSpace,
            LearningInsight insight,
            List<LearningRawEvent> rawEvents,
            String instruction
    ) {
        AgentConfig config = agentConfigService.getByAgentId(REFINE_AGENT_ID).orElse(null);
        if (config == null) {
            log.warn("未找到 {} AgentConfig，回退硬编码默认配置", REFINE_AGENT_ID);
        }
        String taskText = buildTaskText(REFINE_AGENT_ID, RefinePromptData.class, new RefinePromptData(
                projectSpace.getName(),
                insightText(insight),
                rawEventsText(rawEvents),
                instruction
        ));
        try {
            Msg response = buildRefineAgent(config).call(
                    List.of(Msg.builder().textContent(taskText).build()),
                    SelfLearningInsightDraft.class
            ).block(chatModelFactory.timeout());
            SelfLearningInsightDraft draft = parse(response, SelfLearningInsightDraft.class);
            if (draft == null) {
                throw new SelfLearningInsightException("Insight Review Agent 未返回结构化微调建议");
            }
            return draft;
        } catch (RuntimeException ex) {
            if (ex instanceof SelfLearningInsightException insightException) {
                throw insightException;
            }
            throw new SelfLearningInsightException("Insight Review Agent 微调失败：" + ex.getMessage(), ex);
        }
    }

    private HarnessAgent buildVerifyAgent(AgentConfig config) {
        return baseAgent("self-learning-review-agent", config, promptManager.getSystemPrompt(REVIEW_AGENT_ID));
    }

    private HarnessAgent buildRefineAgent(AgentConfig config) {
        return baseAgent("self-learning-refine-agent", config, promptManager.getSystemPrompt(REFINE_AGENT_ID));
    }

    private HarnessAgent baseAgent(String name, AgentConfig config, String sysPrompt) {
        int maxTokens = config != null && config.getMaxTokens() != null ? config.getMaxTokens() : FALLBACK_MAX_TOKENS;
        int maxIters = config != null ? config.getMaxIters() : FALLBACK_MAX_ITERS;

        return HarnessAgent.builder()
                .name(name)
                .description("Reviews and refines self-learning insights for administrator approval.")
                .sysPrompt(sysPrompt)
                .model(config != null ? chatModelFactory.createModel(config) : chatModelFactory.createDefaultModel())
                .structuredOutputReminder(StructuredOutputReminder.PROMPT)
                .generateOptions(GenerateOptions.builder()
                        .temperature(0.2)
                        .maxTokens(maxTokens)
                        .build())
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(chatModelFactory.timeout())
                        .maxAttempts(chatModelFactory.modelMaxAttempts())
                        .build())
                .maxIters(maxIters)
                .workspace(Path.of(System.getProperty("java.io.tmpdir"), "ascoder-self-learning-review-agent"))
                .disableSubagents()
                .disableWorkspaceContext()
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .build();
    }

    private <T> String buildTaskText(String agentId, Class<T> dataClass, T data) {
        return promptManager.renderTaskTemplate(agentId, dataClass, data);
    }

    private String collectCodeEvidence(ProjectSpace projectSpace, LearningInsight insight) {
        Path repositoryPath = Path.of(projectSpace.resolveRootPath(projectSpaceRoot));
        Set<String> symbols = extractedSymbols(insight);
        StringBuilder builder = new StringBuilder();
        builder.append("repositoryPath: ").append(repositoryPath).append('\n');
        if (symbols.isEmpty()) {
            appendCodeGraphResult(builder, "context", insight.getTitle(), safeContext(repositoryPath, insight.getTitle()));
            return truncate(builder.toString(), MAX_CODE_EVIDENCE_CHARS);
        }
        for (String symbol : symbols) {
            appendCodeGraphResult(builder, "query", symbol, safeQuery(repositoryPath, symbol));
        }
        return truncate(builder.toString(), MAX_CODE_EVIDENCE_CHARS);
    }

    private CodeGraphToolResult safeContext(Path repositoryPath, String question) {
        try {
            return codeGraphClient.context(repositoryPath, question);
        } catch (RuntimeException ex) {
            log.warn("候选洞察复核 CodeGraph context 失败，question={}，error={}", question, ex.getMessage());
            return new CodeGraphToolResult(false, ex.getMessage());
        }
    }

    private CodeGraphToolResult safeQuery(Path repositoryPath, String symbol) {
        try {
            return codeGraphClient.query(repositoryPath, symbol, 8, null);
        } catch (RuntimeException ex) {
            log.warn("候选洞察复核 CodeGraph 查询失败，symbol={}，error={}", symbol, ex.getMessage());
            return new CodeGraphToolResult(false, ex.getMessage());
        }
    }

    private void appendCodeGraphResult(StringBuilder builder, String tool, String input, CodeGraphToolResult result) {
        builder.append("\n--- CodeGraph ").append(tool).append(": ").append(input).append(" ---\n");
        builder.append("success: ").append(result.isSuccess()).append('\n');
        builder.append(truncate(result.getOutput(), 1800)).append('\n');
    }

    private Set<String> extractedSymbols(LearningInsight insight) {
        Set<String> symbols = new LinkedHashSet<>();
        appendJsonSymbols(symbols, insight.getCodeSymbolsJson());
        appendRegexSymbols(symbols, insight.getTitle());
        appendRegexSymbols(symbols, insight.getConclusion());
        appendRegexSymbols(symbols, insight.getBusinessContext());
        return symbols.stream()
                .map(symbol -> symbol.endsWith("()") ? symbol.substring(0, symbol.length() - 2) : symbol)
                .filter(symbol -> symbol.length() >= 3)
                .limit(MAX_SYMBOLS)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void appendJsonSymbols(Set<String> symbols, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            appendJsonNodeSymbols(symbols, node);
        } catch (JsonProcessingException ex) {
            appendRegexSymbols(symbols, json);
        }
    }

    private void appendJsonNodeSymbols(Set<String> symbols, JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            appendRegexSymbols(symbols, node.asText());
            return;
        }
        if (node.isArray() || node.isObject()) {
            node.elements().forEachRemaining(child -> appendJsonNodeSymbols(symbols, child));
        }
    }

    private void appendRegexSymbols(Set<String> symbols, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Matcher matcher = CODE_SYMBOL_PATTERN.matcher(value);
        while (matcher.find()) {
            symbols.add(matcher.group());
        }
    }

    private String insightText(LearningInsight insight) {
        return """
                id: %s
                type: %s
                status: %s
                title: %s
                summary: %s
                conclusion: %s
                businessContext: %s
                glossaryMappingsJson: %s
                codeSymbolsJson: %s
                warnings: %s
                applicableScope: %s
                evidenceJson: %s
                gitProvenanceJson: %s
                tags: %s
                confidence: %s
                """.formatted(
                insight.getId(),
                insight.getType(),
                insight.getStatus(),
                nullToEmpty(insight.getTitle()),
                nullToEmpty(insight.getSummary()),
                nullToEmpty(insight.getConclusion()),
                nullToEmpty(insight.getBusinessContext()),
                nullToEmpty(insight.getGlossaryMappingsJson()),
                nullToEmpty(insight.getCodeSymbolsJson()),
                nullToEmpty(insight.getWarnings()),
                nullToEmpty(insight.getApplicableScope()),
                nullToEmpty(insight.getEvidenceJson()),
                nullToEmpty(insight.getGitProvenanceJson()),
                nullToEmpty(insight.getTags()),
                insight.getConfidence()
        );
    }

    private String rawEventsText(List<LearningRawEvent> rawEvents) {
        StringBuilder builder = new StringBuilder();
        for (LearningRawEvent event : rawEvents) {
            builder.append("\n--- RawEvent #").append(event.getId()).append(" ---\n");
            builder.append("eventType: ").append(event.getEventType()).append('\n');
            builder.append("summary: ").append(truncate(event.getSummary(), 800)).append('\n');
            builder.append("payload: ").append(truncate(event.getEventPayloadJson(), 1800)).append('\n');
            builder.append("evidence: ").append(truncate(event.getEvidenceJson(), 1000)).append('\n');
            builder.append("gitProvenance: ").append(truncate(event.getGitProvenanceJson(), 800)).append('\n');
        }
        return truncate(builder.toString(), MAX_RAW_EVENT_CHARS);
    }

    private <T> T parse(Msg response, Class<T> type) {
        if (response == null) {
            return null;
        }
        if (response.hasStructuredData()) {
            T structured = response.getStructuredData(type);
            if (structured != null) {
                return structured;
            }
        }
        String json = extractJsonObject(response.getTextContent());
        if (json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            log.warn("Insight Review Agent 文本 JSON 解析失败：{}", ex.getOriginalMessage());
            return null;
        }
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = stripMarkdownFence(text.trim());
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return "";
        }
        int end = findJsonObjectEnd(trimmed, start);
        if (end < 0) {
            return "";
        }
        return trimmed.substring(start, end + 1);
    }

    private String stripMarkdownFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        int closingFenceStart = text.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFenceStart <= firstLineEnd) {
            return text;
        }
        return text.substring(firstLineEnd + 1, closingFenceStart).trim();
    }

    private int findJsonObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...[truncated]";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Value
    static class ReviewPromptData {
        String projectSpaceName;
        String insightText;
        String rawEventsText;
        String codeEvidenceText;
    }

    @Value
    static class RefinePromptData {
        String projectSpaceName;
        String insightText;
        String rawEventsText;
        String instruction;
    }
}
