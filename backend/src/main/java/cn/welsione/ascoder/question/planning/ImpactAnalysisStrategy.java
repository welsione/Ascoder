package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 影响分析问题分类策略。
 */
@Component
class ImpactAnalysisStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    ImpactAnalysisStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("impactAnalysis");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.IMPACT_ANALYSIS;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "impact affected references dependencies tests");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_impact", "codegraph_callers", "codegraph_affected");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("impact_analysis", "code_review_analysis");
    }
}
