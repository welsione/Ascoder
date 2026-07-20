package cn.welsione.ascoder.question.planning;

import java.util.Optional;

/**
 * 问题规划 Agent 端口，用于在规则规划低置信或多意图时生成结构化规划草稿。
 */
public interface QuestionPlanningAgent {

    /**
     * 基于原始问题、用户角色和规则规划结果生成更语义化的规划草稿。
     */
    Optional<AgentQuestionPlanDraft> plan(String question, String role, QuestionPlan rulePlan);
}
