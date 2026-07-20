package cn.welsione.ascoder.question.planning;

import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 混合问题规划器，在规则规划不确定时调用 Planner Agent，并以规则结果作为兜底。
 *
 * <p>Query Planner 开关与阈值通过设置页热改：{@code agent.query-planner-enabled}、
 * {@code agent.query-planner-confidence-threshold}、
 * {@code agent.query-planner-ambiguous-threshold}。</p>
 */
@Slf4j
@Primary
@Component
public class AgenticQuestionPlanner implements QuestionPlanner {

    private final DefaultQuestionPlanner rulePlanner;
    private final QuestionPlanningAgent planningAgent;
    private final QuestionPlanValidator validator;
    private final RuntimeSettingsService runtimeSettings;

    AgenticQuestionPlanner(DefaultQuestionPlanner rulePlanner,
                           List<QuestionPlanningAgent> planningAgents,
                           QuestionPlanValidator validator,
                           RuntimeSettingsService runtimeSettings) {
        this.rulePlanner = rulePlanner;
        this.planningAgent = planningAgents.isEmpty() ? null : planningAgents.get(0);
        this.validator = validator;
        this.runtimeSettings = runtimeSettings;
    }

    @Override
    public QuestionPlan plan(String question, String role) {
        QuestionPlan rulePlan = rulePlanner.plan(question, role);
        if (!shouldUseAgent(rulePlan, question)) {
            return rulePlan;
        }
        if (planningAgent == null) {
            log.debug("未配置 Planner Agent，使用规则规划结果，类型={}", rulePlan.getType());
            return rulePlan;
        }
        try {
            return planningAgent.plan(question, role, rulePlan)
                    .flatMap(draft -> validator.validate(draft, rulePlan))
                    .map(plan -> {
                        log.info("Planner Agent 规划生效，规则类型={}，Agent类型={}，置信度={}",
                                rulePlan.getType(), plan.getType(), plan.getConfidence());
                        return plan;
                    })
                    .orElseGet(() -> {
                        log.warn("Planner Agent 未产出有效规划，回退规则结果，类型={}", rulePlan.getType());
                        return rulePlan;
                    });
        } catch (RuntimeException ex) {
            log.warn("Planner Agent 调用失败，回退规则结果，类型={}，错误={}", rulePlan.getType(), ex.getMessage());
            return rulePlan;
        }
    }

    @Override
    public QuestionPlan planForType(String question, String role, QuestionType type) {
        return rulePlanner.planForType(question, role, type);
    }

    private boolean shouldUseAgent(QuestionPlan plan, String question) {
        if (!runtimeSettings.readBoolean("agent.query-planner-enabled")) {
            return false;
        }
        double confidenceThreshold = runtimeSettings.readDouble("agent.query-planner-confidence-threshold");
        double ambiguousThreshold = runtimeSettings.readDouble("agent.query-planner-ambiguous-threshold");
        if (plan.getConfidence() < confidenceThreshold) {
            return true;
        }
        if (!plan.getAlternativeTypes().isEmpty() && plan.getConfidence() < ambiguousThreshold) {
            return true;
        }
        return looksContextualFollowUp(question);
    }

    private boolean looksContextualFollowUp(String question) {
        if (question == null) {
            return false;
        }
        String text = question.trim().toLowerCase();
        if (text.length() > 40) {
            return false;
        }
        return text.contains("那") || text.contains("它") || text.contains("上面") || text.contains("刚才")
                || text.contains("还是") || text.contains("影响哪里")
                || text.contains("why still") || text.contains("what about");
    }
}
