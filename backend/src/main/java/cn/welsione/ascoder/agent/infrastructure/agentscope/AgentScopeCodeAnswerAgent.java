package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.application.AgentConfigService;
import cn.welsione.ascoder.agent.application.AgentRunRecordService;
import cn.welsione.ascoder.agent.application.AgentRuntimeRegistry;
import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.AgentRunRecord;
import cn.welsione.ascoder.agent.domain.AgentRunStatus;
import cn.welsione.ascoder.agent.domain.AgentStreamEvent;
import cn.welsione.ascoder.agent.domain.AgentStreamEventType;
import cn.welsione.ascoder.agent.domain.AgentStreamSource;
import cn.welsione.ascoder.agent.domain.AgentToolCall;
import cn.welsione.ascoder.agent.domain.AgentToolResult;
import cn.welsione.ascoder.agent.infrastructure.prompt.SpecialistResultData;
import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import cn.welsione.ascoder.question.planning.QuestionPlan;
import cn.welsione.ascoder.agent.port.CodeAnswerAgent;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventSource;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 AgentScope 的代码回答 Agent 实现，使用 ReAct 模式调用 CodeGraph 工具生成回答。
 * 职责边界：协调 orchestrator/specialist 的编排与回答组装。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentScopeCodeAnswerAgent implements CodeAnswerAgent {

    private static final String TOOLING_WARNINGS_PATH = "prompts/agentscope-tooling-warnings.md";

    private final AgentConfigService agentConfigService;
    private final ConfigDrivenAgentFactory configDrivenAgentFactory;
    private final ConfigDrivenToolingBuilder configDrivenToolingBuilder;
    private final SpecialistTaskAssembler taskAssembler;
    private final AgentRunRecordService agentRunRecordService;
    private final AgentRuntimeRegistry agentRuntimeRegistry;
    private final PromptManager promptManager;

    @Override
    public Flux<AgentStreamEvent> streamAnswer(AgentRequest request) {
        log.info("Agent 开始流式回答，项目空间={}，问题={}", request.getProjectSpaceName(), request.getText());
        List<AgentConfig> configs = agentConfigService.listEnabled();

        // ORCHESTRATOR 唯一性校验（3.6 节兜底）
        AgentConfig orchestrator = configs.stream()
                .filter(c -> c.getAgentRole() == AgentRole.ORCHESTRATOR)
                .reduce((a, b) -> { throw new InvalidStateException("必须恰好启用一个编排 Agent"); })
                .orElseThrow(() -> new InvalidStateException("没有启用的编排 Agent"));

        // 委派候选：enabled=true SPECIALIST 中 required || supports
        List<AgentConfig> specialists = configs.stream()
                .filter(c -> c.getAgentRole() == AgentRole.SPECIALIST)
                .filter(c -> supports(c, request))
                .sorted(java.util.Comparator.comparingInt(AgentConfig::getSortOrder))
                .toList();

        AtomicReference<String> codeContext = new AtomicReference<>(request.getCodeContext());
        AgentTooling orchestratorTooling = configDrivenToolingBuilder.buildFor(orchestrator, request);
        Map<String, SpecialistResultData> specialistResults = new ConcurrentHashMap<>();

        // 按 sortOrder 顺序执行 SPECIALIST
        Flux<AgentStreamEvent> specialistFlux = Flux.empty();
        for (AgentConfig specialist : specialists) {
            specialistFlux = specialistFlux.concatWith(
                    specialistEvents(request, codeContext, specialistResults, specialist, orchestratorTooling));
        }

        Flux<Event> orchestratorFlux = Flux.defer(() -> {
            // 默认值处理（与旧 synthesisPrompt 逻辑一致）
            String selfLearning = request.getSelfLearningContext();
            if (selfLearning == null || selfLearning.isBlank()) { selfLearning = "未检索到可用自学习经验。"; }
            String research = resultText(specialistResults, "code-researcher");
            if (research == null || research.isBlank()) { research = "未产出有效结果。"; }
            String impact = resultText(specialistResults, "impact-analyzer");

            TaskPromptContext context = buildTaskContext(request, research, impact, orderedResults(specialistResults, specialists));
            context.setSelfLearningContext(selfLearning);
            String prompt = taskAssembler.synthesisPrompt(orchestrator, context);
            if (!orchestratorTooling.getWarnings().isEmpty()) {
                prompt = prompt + "\n\n" + promptManager.renderFramework(TOOLING_WARNINGS_PATH, ToolingWarningsPromptData.class, new ToolingWarningsPromptData(orchestratorTooling.getWarnings()));
                log.warn("工具装配存在警告：{}", orchestratorTooling.getWarnings());
            }
            return streamOrchestrator(orchestrator, orchestratorTooling, prompt, request);
        });

        Flux<AgentStreamEvent> orchestratorEvents = orchestratorFlux.map(this::toStreamEvent);
        Flux<AgentStreamEvent> selfLearningEvents = selfLearningEvents(request);

        return Flux.concat(
                selfLearningEvents,
                specialistFlux,
                Flux.just(handoffStreamEvent(
                        request, "orchestrator", "Ascoder", "final-answer", "最终回答",
                        "汇总成答",
                        "父级 Agent 开始综合 specialist 输出、项目上下文和回答视角生成最终答案。"
                )),
                orchestratorEvents
        );
    }

    private Flux<Event> streamOrchestrator(AgentConfig config, AgentTooling tooling, String prompt, AgentRequest request) {
        log.info("ORCHESTRATOR 开始汇总，agentId={}", config.getAgentId());
        // 运行态接入
        AgentRunRecord runRecord = agentRunRecordService.start(
                config.getAgentId(), config.getId(), request.getQuestionId(), request.getConversationId(), 1, "汇总 specialist 输出");
        agentRuntimeRegistry.markRunning(config.getAgentId(), request.getQuestionId(), runRecord.getId());

        HarnessAgent agent = configDrivenAgentFactory.build(config, request, tooling);
        return agent.stream(List.of(Msg.builder().textContent(prompt).build()),
                        AgentRuntimeHelper.streamOptions(), AgentRuntimeHelper.runtimeContext(request))
                .map(event -> event.withSource(AgentRuntimeHelper.agentSource(request, config.getAgentId(), config.getDisplayName(), 0)))
                .doFinally(signal -> {
                    agentRunRecordService.finish(runRecord.getId(), AgentRunStatus.SUCCEEDED, null, 0, 0, null);
                    agentRuntimeRegistry.markIdle(config.getAgentId(), runRecord.getId(), AgentRunStatus.SUCCEEDED);
                });
    }

    private boolean supports(AgentConfig config, AgentRequest request) {
        if (config.isRequired()) { return true; }
        String role = request.getRole();
        if (role != null) {
            String normalized = role.trim().toLowerCase().replace('-', '_').replace(' ', '_');
            if (config.getRoleKeys().contains(normalized)) { return true; }
        }
        String question = request.getText();
        if (question != null && !question.isBlank()) {
            for (String keyword : config.getQuestionKeywords()) {
                if (question.contains(keyword)) { return true; }
            }
        }
        return false;
    }

    private Flux<AgentStreamEvent> specialistEvents(
            AgentRequest request,
            AtomicReference<String> codeContext,
            Map<String, SpecialistResultData> specialistResults,
            AgentConfig config,
            AgentTooling orchestratorTooling
    ) {
        return Flux.concat(
                Flux.just(handoffStreamEvent(
                        request, "orchestrator", "Ascoder",
                        config.getAgentId(), config.getDisplayName(),
                        config.getHandoffTitle(), config.getHandoffDescription()
                )),
                Flux.defer(() -> {
                    // markRunning 前二次确认 enabled
                    if (!agentConfigService.get(config.getId()).isEnabled()) {
                        log.info("Agent 已被禁用，跳过执行，agentId={}", config.getAgentId());
                        return Flux.empty();
                    }

                    String researchResult = resultText(specialistResults, "code-researcher");
                    String impactResult = resultText(specialistResults, "impact-analyzer");
                    // 默认值处理（与旧 specialistTask 逻辑一致）
                    String selfLearning = request.getSelfLearningContext();
                    if (selfLearning == null || selfLearning.isBlank()) { selfLearning = "未检索到可用自学习经验。"; }
                    if (researchResult == null || researchResult.isBlank()) { researchResult = "未产出有效代码证据。"; }
                    if (impactResult == null || impactResult.isBlank()) { impactResult = "未产出独立影响分析。"; }

                    TaskPromptContext context = buildTaskContext(request, researchResult, impactResult, null);
                    context.setSelfLearningContext(selfLearning);
                    String task = taskAssembler.taskPrompt(config, context);

                    AgentTooling specialistTooling = configDrivenToolingBuilder.buildFor(config, request);
                    if (!specialistTooling.getWarnings().isEmpty()) {
                        task = task + "\n\n" + promptManager.renderFramework(TOOLING_WARNINGS_PATH, ToolingWarningsPromptData.class, new ToolingWarningsPromptData(specialistTooling.getWarnings()));
                        log.warn("Specialist 工具装配存在警告，agentId={}：{}", config.getAgentId(), specialistTooling.getWarnings());
                    }

                    // 运行态接入
                    AgentRunRecord runRecord = agentRunRecordService.start(
                            config.getAgentId(), config.getId(), request.getQuestionId(), request.getConversationId(), 1, request.getText());
                    agentRuntimeRegistry.markRunning(config.getAgentId(), request.getQuestionId(), runRecord.getId());

                    return streamSpecialist(
                            configDrivenAgentFactory.build(config, request, specialistTooling),
                            task, request, config, specialistResults,
                            runRecord
                    ).map(this::toStreamEvent);
                }),
                Flux.just(handoffStreamEvent(
                        request, config.getAgentId(), config.getDisplayName(),
                        "orchestrator", "Ascoder",
                        config.getReturnTitle(), config.getReturnDescription()
                ))
        );
    }

    private Flux<Event> streamSpecialist(
            HarnessAgent agent,
            String task,
            AgentRequest request,
            AgentConfig config,
            Map<String, SpecialistResultData> specialistResults,
            AgentRunRecord runRecord
    ) {
        return agent.stream(List.of(Msg.builder().textContent(task).build()),
                        AgentRuntimeHelper.streamOptions(), AgentRuntimeHelper.childRuntimeContext(request, config.getAgentId()))
                .map(event -> {
                    if (event.getType() == EventType.AGENT_RESULT && event.getMessage() != null) {
                        String content = event.getMessage().getTextContent();
                        if (content != null && !content.isBlank()) {
                            specialistResults.put(config.getAgentId(), new SpecialistResultData(
                                    config.getAgentId(), config.getDisplayName(), content));
                        }
                    }
                    return event.withSource(AgentRuntimeHelper.agentSource(request, config.getAgentId(), config.getDisplayName(), 1));
                })
                .doFinally(signal -> {
                    agentRunRecordService.finish(runRecord.getId(), AgentRunStatus.SUCCEEDED, null, 0, 0, null);
                    agentRuntimeRegistry.markIdle(config.getAgentId(), runRecord.getId(), AgentRunStatus.SUCCEEDED);
                });
    }

    private List<SpecialistResultData> orderedResults(Map<String, SpecialistResultData> specialistResults,
                                                      List<AgentConfig> specialists) {
        return specialists.stream()
                .map(config -> specialistResults.get(config.getAgentId()))
                .filter(result -> result != null)
                .toList();
    }

    private Flux<AgentStreamEvent> selfLearningEvents(AgentRequest request) {
        String context = request.getSelfLearningContext();
        if (context == null || context.isBlank()) {
            return Flux.empty();
        }
        AgentStreamSource source = new AgentStreamSource(
                "self-learning", "Self Learning Agent", null, null, null, null, 1, "orchestrator/self-learning");
        return Flux.just(
                handoffStreamEvent(request, "orchestrator", "Ascoder", "self-learning", "Self Learning Agent",
                        "检索正式知识", "父级 Agent 委派自学习 Agent 检索项目空间正式知识、代码名词和防错提醒。"),
                AgentStreamEvent.builder().type(AgentStreamEventType.REASONING)
                        .content("正在检索项目空间中已审核的正式知识、结构化语境和负面案例提醒。").source(source).build(),
                AgentStreamEvent.builder().type(AgentStreamEventType.SUMMARY)
                        .content(context).source(source).build(),
                handoffStreamEvent(request, "self-learning", "Self Learning Agent", "orchestrator", "Ascoder",
                        "知识线索回传", "自学习 Agent 已回传审核后的正式知识线索，等待父级 Agent 结合当前代码证据判断。")
        );
    }

    private AgentStreamEvent handoffStreamEvent(AgentRequest request, String fromId, String fromName,
                                                String toId, String toName, String title, String description) {
        String content = """
                {"fromAgentId":"%s","fromAgentName":"%s","toAgentId":"%s","toAgentName":"%s","title":"%s","description":"%s"}
                """.formatted(fromId, fromName, toId, toName, title, description).trim();
        return AgentStreamEvent.builder()
                .type(AgentStreamEventType.HANDOFF)
                .content(content)
                .source(toStreamSource(AgentRuntimeHelper.agentSource(request, "orchestrator", "Ascoder", 0)))
                .build();
    }

    private TaskPromptContext buildTaskContext(AgentRequest request, String researchResult, String impactResult,
                                               List<SpecialistResultData> specialistResults) {
        QuestionPlan plan = request.getQuestionPlan();
        TaskPromptContext context = taskAssembler.buildContext(
                request,
                plan != null ? plan.toPromptText() : "未提供。",
                plan != null ? plan.getType().name() : "UNKNOWN",
                plan != null ? plan.getRecommendedTools() : List.of(),
                plan != null ? plan.getRewrittenQueries() : List.of(),
                plan != null ? plan.getRecommendedSkills() : List.of(),
                plan != null ? plan.getReasoning() : "未提供查询规划"
        );
        context.setResearchResult(researchResult);
        context.setImpactResult(impactResult);
        context.setSpecialistResults(specialistResults);
        return context;
    }

    private String resultText(Map<String, SpecialistResultData> specialistResults, String agentId) {
        SpecialistResultData result = specialistResults.get(agentId);
        return result == null ? "" : result.getResult();
    }

    private AgentStreamEvent toStreamEvent(Event event) {
        String content = event.getMessage() != null && event.getMessage().getTextContent() != null
                ? event.getMessage().getTextContent()
                : "";
        AgentStreamEvent.AgentStreamEventBuilder builder = AgentStreamEvent.builder()
                .type(isHandoffContent(content) ? AgentStreamEventType.HANDOFF : toStreamEventType(event.getType()))
                .content(content)
                .last(event.isLast())
                .messageId(event.getMessageId())
                .source(toStreamSource(event.getSource()));
        if (event.getMessage() != null) {
            event.getMessage().getContentBlocks(ToolUseBlock.class).forEach(block ->
                    builder.toolCall(new AgentToolCall(block.getId(), block.getName(), block.getInput()))
            );
            event.getMessage().getContentBlocks(ToolResultBlock.class).forEach(block ->
                    builder.toolResult(new AgentToolResult(
                            block.getId(),
                            block.getName(),
                            toolResultText(block, content),
                            block.isSuspended()
                    ))
            );
        }
        return builder.build();
    }

    private AgentStreamEventType toStreamEventType(EventType type) {
        if (type == EventType.REASONING) {
            return AgentStreamEventType.REASONING;
        }
        if (type == EventType.TOOL_RESULT) {
            return AgentStreamEventType.TOOL_RESULT;
        }
        if (type == EventType.HINT) {
            return AgentStreamEventType.HINT;
        }
        if (type == EventType.SUMMARY) {
            return AgentStreamEventType.SUMMARY;
        }
        if (type == EventType.AGENT_RESULT) {
            return AgentStreamEventType.AGENT_RESULT;
        }
        return AgentStreamEventType.EVENT;
    }

    private boolean isHandoffContent(String content) {
        return content != null
                && content.startsWith("{\"fromAgentId\":")
                && content.contains("\"toAgentId\":")
                && content.contains("\"title\":");
    }

    private AgentStreamSource toStreamSource(EventSource source) {
        if (source == null) {
            return new AgentStreamSource("orchestrator", "Ascoder", null, null, null, null, 0, "orchestrator");
        }
        return new AgentStreamSource(
                source.getAgentId() == null ? "subagent" : source.getAgentId(),
                source.getAgentName() == null ? source.getAgentId() : source.getAgentName(),
                source.getAgentKey(),
                source.getSessionId(),
                source.getParentSessionId(),
                source.getTaskId(),
                source.getDepth(),
                source.getPath()
        );
    }

    private String toolResultText(ToolResultBlock block, String fallbackContent) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock output : block.getOutput()) {
            if (output instanceof TextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            } else if (output != null) {
                text.append(output);
            }
        }
        return text.isEmpty() ? fallbackContent : text.toString();
    }

    @Value
    static class ToolingWarningsPromptData {
        List<String> warnings;
    }
}
