package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bug 分析问题分类策略。
 */
@Component
class BugAnalysisStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    BugAnalysisStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("bugAnalysis");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.BUG_ANALYSIS;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "exception error failure root cause controller service config");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_context", "codegraph_search", "codegraph_callers");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("bug_root_cause_analysis", "spring_boot_analysis");
    }
}
