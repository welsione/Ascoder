package cn.welsione.ascoder.agent.infrastructure.agentscope;

import cn.welsione.ascoder.agent.domain.AgentConfig;
import cn.welsione.ascoder.agent.domain.AgentRequest;
import cn.welsione.ascoder.agent.infrastructure.prompt.AnswerStyle;
import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;
import cn.welsione.ascoder.agent.infrastructure.prompt.SpecialistResultData;
import cn.welsione.ascoder.agent.infrastructure.prompt.TaskPromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Specialist / Orchestrator 的任务提示词组装。
 *
 * <p>根据请求构建 {@link TaskPromptContext}，渲染 Agent 级别的任务模板。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecialistTaskAssembler {

    private final PromptManager promptManager;

    /** 构建 Specialist 的任务提示词。 */
    public String taskPrompt(AgentConfig config, TaskPromptContext context) {
        return promptManager.renderTaskTemplate(config.getAgentId(), TaskPromptContext.class, context);
    }

    /** 构建 Orchestrator 的汇总提示词。 */
    public String synthesisPrompt(AgentConfig orchestratorConfig, TaskPromptContext context) {
        return promptManager.renderTaskTemplate(orchestratorConfig.getAgentId(), TaskPromptContext.class, context);
    }

    /** 根据请求构建 TaskPromptContext。 */
    public TaskPromptContext buildContext(AgentRequest request,
                                          String queryPlanSummary,
                                          String queryPlanType,
                                          java.util.List<String> recommendedTools,
                                          java.util.List<String> rewrittenQueries,
                                          java.util.List<String> recommendedSkills,
                                          String queryPlanReasoning) {
        String roleKey = AnswerStyle.fromRole(request.getRole()).getRoleKey();
        String answerStyleInstruction = promptManager.getAnswerStyle(roleKey);

        return new TaskPromptContext(
                request.getProjectSpaceName(),
                request.getText(),
                queryPlanSummary,
                queryPlanType,
                recommendedTools,
                rewrittenQueries,
                recommendedSkills,
                queryPlanReasoning,
                request.getRepositories(),
                request.getSelfLearningContext(),
                null,  // researchResult — 由运行时填入
                null,  // impactResult — 由运行时填入
                answerStyleInstruction,
                roleKey,
                null   // specialistResults — 由 Orchestrator 运行时填入
        );
    }
}
