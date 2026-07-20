package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 配置分析问题分类策略。
 */
@Component
class ConfigAnalysisStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    ConfigAnalysisStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("configAnalysis");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.CONFIG_ANALYSIS;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of("application.yml configuration properties environment", originalQuestion);
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_files", "codegraph_context", "codegraph_search");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("spring_boot_analysis", "maven_project_analysis");
    }
}
