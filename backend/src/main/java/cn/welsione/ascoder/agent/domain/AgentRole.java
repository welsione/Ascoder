package cn.welsione.ascoder.agent.domain;

/**
 * Agent 在编排流程中的角色，决定其在流程中的位置与装配方式。
 *
 * <ul>
 *   <li>{@link #ORCHESTRATOR}：最终汇总 Agent，读取所有 SPECIALIST 结果做综合回答。</li>
 *   <li>{@link #SPECIALIST}：委派子 Agent，按触发条件参与委派。</li>
 *   <li>{@link #PLANNER}：查询规划 Agent（一期保留枚举值，暂不打通配置化）。</li>
 *   <li>{@link #SELF_LEARNING}：自学习 Agent（Insight/Review/Refine），不参与问答编排委派，
 *       由 SelfLearningController 手动触发，配置仅作为运行参数来源。</li>
 * </ul>
 */
public enum AgentRole {
    ORCHESTRATOR,
    SPECIALIST,
    PLANNER,
    SELF_LEARNING
}
