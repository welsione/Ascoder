package cn.welsione.ascoder.agent.extension.config;

import cn.welsione.ascoder.agent.domain.AgentRole;
import cn.welsione.ascoder.agent.domain.SpecialistTaskKind;

import java.util.List;

/**
 * 内置 Agent 配置目录，集中声明系统预置的全部 Agent 定义。
 *
 * <p>内容与 Flyway V31__agent_configs.sql、V33__self_learning_agent_configs.sql 保持一致，
 * 供 {@link BuiltinAgentConfigInitializer} 在启动时补全缺失记录。已存在的记录不会被覆盖，
 * 管理员通过界面或后续 Flyway 迁移修改的 prompt 不会被回退。</p>
 */
public final class BuiltinAgentConfigCatalog {

    private static final List<String> ALL_TOOLS =
            List.of("codegraph", "git", "file", "text", "command", "self-learning", "log");

    private BuiltinAgentConfigCatalog() {
    }

    public static List<BuiltinAgentConfigDefinition> all() {
        return List.of(
                orchestrator(),
                codeResearcher(),
                impactAnalyzer(),
                productManager(),
                testManager(),
                selfLearningInsight(),
                selfLearningInsightReview(),
                selfLearningInsightRefine()
        );
    }

    /** 最终汇总 Agent，基于 specialist 输出生成最终回答。 */
    private static BuiltinAgentConfigDefinition orchestrator() {
        return new BuiltinAgentConfigDefinition(
                "orchestrator", "Orchestrator",
                "Ascoder 最终汇总智能体，基于 specialist 输出生成最终回答。",
                AgentRole.ORCHESTRATOR, null,
                BuiltinAgentPrompts.ORCHESTRATOR_SYSTEM,
                BuiltinAgentPrompts.ORCHESTRATOR_TASK,
                12, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                true, true, null, null, null, null, 0);
    }

    /** 代码证据检索 Agent，必选，使用 CodeGraph 与 Git 工具定位代码。 */
    private static BuiltinAgentConfigDefinition codeResearcher() {
        return new BuiltinAgentConfigDefinition(
                "code-researcher", "Code Researcher",
                "Dedicated code evidence researcher for locating files, symbols, entry points, call relationships, and Git provenance.",
                AgentRole.SPECIALIST, SpecialistTaskKind.CODE_RESEARCH,
                BuiltinAgentPrompts.CODE_RESEARCHER_SYSTEM,
                BuiltinAgentPrompts.CODE_RESEARCHER_TASK,
                100, null,
                List.of(), List.of(), ALL_TOOLS, List.of(), List.of(),
                true, true,
                "任务委派", "父级 Agent 将代码定位、调用链、Git 证据和原始文件检索交给 Code Researcher。",
                "证据回传", "Code Researcher 已回传文件路径、符号、调用关系和工具证据。",
                1);
    }

    /** 影响分析 Agent，面向变更、回归风险与依赖扩散。 */
    private static BuiltinAgentConfigDefinition impactAnalyzer() {
        return new BuiltinAgentConfigDefinition(
                "impact-analyzer", "Impact Analyzer",
                "Dedicated impact analyzer for regression scope, dependency spread, and affected test analysis.",
                AgentRole.SPECIALIST, SpecialistTaskKind.IMPACT_ANALYSIS,
                BuiltinAgentPrompts.IMPACT_ANALYZER_SYSTEM,
                BuiltinAgentPrompts.IMPACT_ANALYZER_TASK,
                8, null,
                List.of("tester"),
                List.of("影响", "风险", "回归", "测试", "改动", "修改", "变更"),
                ALL_TOOLS, List.of(), List.of(),
                false, true,
                "风险复核", "问题涉及改动影响、回归风险或验证范围，父级 Agent 邀请影响分析 Agent 加入。",
                "影响结论回传", "影响分析 Agent 已回传风险点、影响范围和验证建议。",
                2);
    }

    /** 产品经理 Agent，把代码证据翻译成业务语言并评估需求合理性。 */
    private static BuiltinAgentConfigDefinition productManager() {
        return new BuiltinAgentConfigDefinition(
                "product-manager", "Product Manager Agent",
                "Product-facing specialist that explains business logic in plain language and evaluates whether new logic is suitable as a requirement.",
                AgentRole.SPECIALIST, SpecialistTaskKind.PRODUCT_REVIEW,
                BuiltinAgentPrompts.PRODUCT_MANAGER_SYSTEM,
                BuiltinAgentPrompts.PRODUCT_MANAGER_TASK,
                8, null,
                List.of("product_manager"),
                List.of("需求", "业务", "客户", "逻辑", "产品", "规则", "是否合适"),
                ALL_TOOLS, List.of(), List.of(),
                false, true,
                "产品语境分析", "父级 Agent 邀请产品经理 Agent 将代码证据转成客户可理解的业务逻辑，并评估需求合理性。",
                "产品结论回传", "产品经理 Agent 已回传业务解释、需求判断和待确认问题。",
                3);
    }

    /** 测试经理 Agent，从用户逻辑与代码证据拆解测试点、用例与自动化建议。 */
    private static BuiltinAgentConfigDefinition testManager() {
        return new BuiltinAgentConfigDefinition(
                "test-manager", "Test Manager Agent",
                "Testing specialist that derives test points, test cases, and automation suggestions from user logic and code evidence.",
                AgentRole.SPECIALIST, SpecialistTaskKind.TEST_REVIEW,
                BuiltinAgentPrompts.TEST_MANAGER_SYSTEM,
                BuiltinAgentPrompts.TEST_MANAGER_TASK,
                8, null,
                List.of("tester"),
                List.of("测试", "用例", "验证", "自动化", "覆盖", "边界", "验收"),
                ALL_TOOLS, List.of(), List.of(),
                false, true,
                "测试策略分析", "父级 Agent 邀请测试经理 Agent 基于代码证据拆解测试点、用例和自动化建议。",
                "测试结论回传", "测试经理 Agent 已回传测试点、测试用例建议和自动化建议。",
                4);
    }

    /** 自学习洞察 Agent，把会话原始记录整理成待审核候选洞察。 */
    private static BuiltinAgentConfigDefinition selfLearningInsight() {
        return new BuiltinAgentConfigDefinition(
                "self-learning-insight", "Self Learning Insight",
                "自学习洞察 Agent，把会话原始记录整理成待审核候选洞察，由 SelfLearningController 手动触发。",
                AgentRole.SELF_LEARNING, null,
                BuiltinAgentPrompts.SELF_LEARNING_INSIGHT_SYSTEM,
                BuiltinAgentPrompts.SELF_LEARNING_INSIGHT_TASK,
                1, 1500,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                false, true, null, null, null, null, 10);
    }

    /** 洞察复核 Agent，基于代码证据复核候选洞察。 */
    private static BuiltinAgentConfigDefinition selfLearningInsightReview() {
        return new BuiltinAgentConfigDefinition(
                "self-learning-insight-review", "Insight Review",
                "洞察复核 Agent，基于代码证据复核候选洞察（verify），由 SelfLearningController 手动触发。",
                AgentRole.SELF_LEARNING, null,
                BuiltinAgentPrompts.INSIGHT_REVIEW_SYSTEM,
                BuiltinAgentPrompts.INSIGHT_REVIEW_TASK,
                1, 1500,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                false, true, null, null, null, null, 11);
    }

    /** 洞察微调 Agent，按管理员自然语言指令微调候选洞察。 */
    private static BuiltinAgentConfigDefinition selfLearningInsightRefine() {
        return new BuiltinAgentConfigDefinition(
                "self-learning-insight-refine", "Insight Refine",
                "洞察微调 Agent，按管理员自然语言指令微调候选洞察（refine），由 SelfLearningController 手动触发。",
                AgentRole.SELF_LEARNING, null,
                BuiltinAgentPrompts.INSIGHT_REFINE_SYSTEM,
                BuiltinAgentPrompts.INSIGHT_REFINE_TASK,
                1, 1500,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                false, true, null, null, null, null, 12);
    }
}
