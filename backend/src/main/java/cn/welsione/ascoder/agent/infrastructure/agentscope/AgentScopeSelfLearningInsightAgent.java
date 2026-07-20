package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.port.ChatModelFactory;
import cn.welsione.ascoder.repository.projectspace.ProjectSpace;
import cn.welsione.ascoder.selflearning.LearningRawEvent;
import cn.welsione.ascoder.selflearning.SelfLearningInsightAgent;
import cn.welsione.ascoder.selflearning.SelfLearningInsightDraft;
import cn.welsione.ascoder.selflearning.SelfLearningInsightException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
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
import java.util.List;
import java.util.Optional;

/**
 * 基于 AgentScope 的 Self Learning Agent，把完整会话原始记录整理成管理员可审核的洞察草稿。
 *
 * <p>运行参数（systemPrompt/taskTemplate/modelId/maxTokens/maxIters）从 AgentConfig 按
 * agentId="self-learning-insight" 加载；config 为空时回退硬编码默认值（D2 兜底策略）。</p>
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ascoder.llm-provider", havingValue = "agentscope", matchIfMissing = true)
public class AgentScopeSelfLearningInsightAgent implements SelfLearningInsightAgent {

    static final String AGENT_ID = "self-learning-insight";
    private static final int MAX_SUMMARY_CHARS = 1600;
    private static final int MAX_PAYLOAD_CHARS = 6000;
    private static final int MAX_EVIDENCE_CHARS = 2400;
    private static final int MAX_GIT_PROVENANCE_CHARS = 1600;
    private static final int FALLBACK_MAX_TOKENS = 1500;
    private static final int FALLBACK_MAX_ITERS = 1;

    private final AgentConfigService agentConfigService;
    private final ChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;
    private final PromptManager promptManager;

    @Override
    public Optional<SelfLearningInsightDraft> summarize(ProjectSpace projectSpace, List<LearningRawEvent> rawEvents) {
        if (rawEvents == null || rawEvents.isEmpty()) {
            return Optional.empty();
        }
        AgentConfig config = agentConfigService.getByAgentId(AGENT_ID).orElse(null);
        if (config == null) {
            log.warn("未找到 {} AgentConfig，回退硬编码默认配置", AGENT_ID);
        }
        try {
            Msg response = buildAgent(config).call(
                    List.of(Msg.builder().textContent(prompt(projectSpace, rawEvents, config)).build()),
                    SelfLearningInsightDraft.class
            ).block(chatModelFactory.timeout());
            Optional<SelfLearningInsightDraft> draft = parseDraft(response);
            if (draft.isEmpty()) {
                throw new SelfLearningInsightException("Self Learning Agent 未返回结构化洞察草稿");
            }
            return draft;
        } catch (RuntimeException ex) {
            if (ex instanceof SelfLearningInsightException insightException) {
                throw insightException;
            }
            throw new SelfLearningInsightException("Self Learning Agent 整理洞察失败：" + ex.getMessage(), ex);
        }
    }

    Optional<SelfLearningInsightDraft> parseDraft(Msg response) {
        if (response == null) {
            return Optional.empty();
        }
        if (response.hasStructuredData()) {
            SelfLearningInsightDraft draft = response.getStructuredData(SelfLearningInsightDraft.class);
            if (draft != null) {
                return Optional.of(draft);
            }
        }
        return parseDraftFromText(response.getTextContent());
    }

    Optional<SelfLearningInsightDraft> parseDraftFromText(String text) {
        String json = extractJsonObject(text);
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SelfLearningInsightDraft.class));
        } catch (JsonProcessingException ex) {
            log.warn("Self Learning Agent 文本 JSON 解析失败：{}", ex.getOriginalMessage());
            return Optional.empty();
        }
    }

    String extractJsonObject(String text) {
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

    private HarnessAgent buildAgent(AgentConfig config) {
        String sysPrompt = promptManager.getSystemPrompt(AGENT_ID);
        int maxTokens = config != null && config.getMaxTokens() != null ? config.getMaxTokens() : FALLBACK_MAX_TOKENS;
        int maxIters = config != null ? config.getMaxIters() : FALLBACK_MAX_ITERS;

        return HarnessAgent.builder()
                .name("self-learning-agent")
                .description("Summarizes complete conversation raw records into reviewable learning insight drafts.")
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
                .workspace(Path.of(System.getProperty("java.io.tmpdir"), "ascoder-self-learning-agent"))
                .disableSubagents()
                .disableWorkspaceContext()
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .build();
    }

    private String prompt(ProjectSpace projectSpace, List<LearningRawEvent> rawEvents, AgentConfig config) {
        StringBuilder builder = new StringBuilder();
        for (LearningRawEvent event : rawEvents) {
            builder.append("\n--- RawEvent #").append(event.getId()).append(" ---\n");
            builder.append("eventType: ").append(event.getEventType()).append('\n');
            builder.append("summary:\n").append(truncate(event.getSummary(), MAX_SUMMARY_CHARS)).append('\n');
            builder.append("payload:\n").append(truncate(event.getEventPayloadJson(), MAX_PAYLOAD_CHARS)).append('\n');
            builder.append("evidence:\n").append(truncate(event.getEvidenceJson(), MAX_EVIDENCE_CHARS)).append('\n');
            builder.append("gitProvenance:\n").append(truncate(event.getGitProvenanceJson(), MAX_GIT_PROVENANCE_CHARS)).append('\n');
        }
        String rawEventsText = builder.toString();
        return promptManager.renderTaskTemplate(AGENT_ID, SelfLearningPromptData.class,
                new SelfLearningPromptData(projectSpace.getName(), rawEventsText));
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

    @Value
    static class SelfLearningPromptData {
        String projectSpaceName;
        String rawEventsText;
    }
}
