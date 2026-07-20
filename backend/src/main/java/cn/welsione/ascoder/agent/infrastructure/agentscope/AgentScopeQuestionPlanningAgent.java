package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.port.ChatModelFactory;
import cn.welsione.ascoder.question.planning.AgentQuestionPlanDraft;
import cn.welsione.ascoder.question.planning.QuestionPlan;
import cn.welsione.ascoder.question.planning.QuestionPlanningAgent;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 基于 AgentScope 结构化输出的 Planner Agent，为低置信规则规划生成语义化规划草稿。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ascoder.llm-provider", havingValue = "agentscope", matchIfMissing = true)
public class AgentScopeQuestionPlanningAgent implements QuestionPlanningAgent {

    private static final String PLANNER_TEMPLATE_PATH = "prompts/query-planner-agent.md";

    private final ChatModelFactory chatModelFactory;
    private final PromptManager promptManager;
    private final int maxIters;

    public AgentScopeQuestionPlanningAgent(ChatModelFactory chatModelFactory,
                                           PromptManager promptManager,
                                           @org.springframework.beans.factory.annotation.Value(
                                                   "${ascoder.agent.query-planner-max-iters:2}") int maxIters) {
        this.chatModelFactory = chatModelFactory;
        this.promptManager = promptManager;
        this.maxIters = maxIters;
    }

    @Override
    public Optional<AgentQuestionPlanDraft> plan(String question, String role, QuestionPlan rulePlan) {
        String prompt = promptManager.renderFramework(PLANNER_TEMPLATE_PATH, PlannerPromptData.class, new PlannerPromptData(question, role, rulePlan));
        HarnessAgent agent = buildAgent();
        Msg response = agent.call(
                List.of(Msg.builder().textContent(prompt).build()),
                AgentQuestionPlanDraft.class
        ).block(chatModelFactory.timeout());
        if (response == null || !response.hasStructuredData()) {
            log.warn("Planner Agent 未返回结构化数据");
            return Optional.empty();
        }
        return Optional.ofNullable(response.getStructuredData(AgentQuestionPlanDraft.class));
    }

    private HarnessAgent buildAgent() {
        return HarnessAgent.builder()
                .name("query-planner")
                .description("Lightweight planner that converts a code question into a structured QueryPlan draft.")
                .sysPrompt("You are Ascoder's query planning agent. Return only a valid structured planning result.")
                .model(chatModelFactory.createDefaultModel())
                .structuredOutputReminder(StructuredOutputReminder.PROMPT)
                .generateOptions(GenerateOptions.builder()
                        .temperature(0.1)
                        .maxTokens(1200)
                        .build())
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(chatModelFactory.timeout())
                        .maxAttempts(chatModelFactory.modelMaxAttempts())
                        .build())
                .maxIters(maxIters)
                .workspace(Path.of(System.getProperty("java.io.tmpdir"), "ascoder-query-planner"))
                .disableSubagents()
                .disableWorkspaceContext()
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .build();
    }
    @Value
    static class PlannerPromptData {
        String question;
        String role;
        QuestionPlan rulePlan;
    }
}
