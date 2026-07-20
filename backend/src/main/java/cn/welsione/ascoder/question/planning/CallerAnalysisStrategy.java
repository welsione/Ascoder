package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 调用方分析问题分类策略。
 */
@Component
class CallerAnalysisStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    CallerAnalysisStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("callerAnalysis");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.CALLER_ANALYSIS;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "caller called by references usages");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_search", "codegraph_callers", "codegraph_context");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("spring_boot_analysis", "code_review_analysis");
    }
}
