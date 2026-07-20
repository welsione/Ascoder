package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 被调用方分析问题分类策略。
 */
@Component
class CalleeAnalysisStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    CalleeAnalysisStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("calleeAnalysis");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.CALLEE_ANALYSIS;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "callee calls dependencies internal flow");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_search", "codegraph_callees", "codegraph_context");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("spring_boot_analysis", "code_review_analysis");
    }
}
