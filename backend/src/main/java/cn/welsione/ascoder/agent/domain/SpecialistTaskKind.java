package cn.welsione.ascoder.agent.domain;

/**
 * Specialist Agent 的任务类型，声明该 Agent 需要哪些上游输入（数据依赖形状）。
 *
 * <p>枚举值由代码定义（流程形状），不进数据库自由文本。具体任务提示词模板由
 * {@code AgentConfig.taskTemplate} 决定。</p>
 */
public enum SpecialistTaskKind {
    CODE_RESEARCH,
    IMPACT_ANALYSIS,
    PRODUCT_REVIEW,
    TEST_REVIEW
}
