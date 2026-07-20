package cn.welsione.ascoder.agent.extension.skill;

import cn.welsione.ascoder.agent.infrastructure.prompt.PromptManager;

import java.util.List;

/** 内置 Skill 目录，集中声明系统预置的各项分析 Skill 定义。 */
public final class BuiltinSkillCatalog {

    private BuiltinSkillCatalog() {
    }

    public static List<BuiltinSkillDefinition> all(PromptManager promptManager) {
        return List.of(
                springBootAnalysis(promptManager),
                mavenProjectAnalysis(promptManager),
                vueAnalysis(promptManager),
                codeReviewAnalysis(promptManager),
                bugRootCauseAnalysis(promptManager),
                impactAnalysis(promptManager)
        );
    }

    private static BuiltinSkillDefinition springBootAnalysis(PromptManager promptManager) {
        return new BuiltinSkillDefinition(
                "spring_boot_analysis",
                "分析 Spring Boot 入口、Controller、Service、配置、依赖注入和请求流程。",
                skillContent("spring_boot_analysis", promptManager)
        );
    }

    private static BuiltinSkillDefinition mavenProjectAnalysis(PromptManager promptManager) {
        return new BuiltinSkillDefinition(
                "maven_project_analysis",
                "分析 Maven 模块、依赖、构建、测试命令和 Spring Boot Maven 项目结构。",
                skillContent("maven_project_analysis", promptManager)
        );
    }

    private static BuiltinSkillDefinition vueAnalysis(PromptManager promptManager) {
        return new BuiltinSkillDefinition(
                "vue_analysis",
                "分析 Vue/Vite/TypeScript 前端组件、视图、组合式函数、服务层和页面交互。",
                skillContent("vue_analysis", promptManager)
        );
    }

    private static BuiltinSkillDefinition codeReviewAnalysis(PromptManager promptManager) {
        return new BuiltinSkillDefinition(
                "code_review_analysis",
                "面向代码评审，优先发现行为缺陷、风险、边界条件和缺失测试。",
                skillContent("code_review_analysis", promptManager)
        );
    }

    private static BuiltinSkillDefinition bugRootCauseAnalysis(PromptManager promptManager) {
        return new BuiltinSkillDefinition(
                "bug_root_cause_analysis",
                "分析 Bug、异常、失败原因和可验证的排查路径。",
                skillContent("bug_root_cause_analysis", promptManager)
        );
    }

    private static BuiltinSkillDefinition impactAnalysis(PromptManager promptManager) {
        return new BuiltinSkillDefinition(
                "impact_analysis",
                "分析修改某个类、方法、配置或文件可能影响的调用方、测试和业务路径。",
                skillContent("impact_analysis", promptManager)
        );
    }

    private static String skillContent(String skillName, PromptManager promptManager) {
        return promptManager.getSkillContent(skillName);
    }
}
