package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 业务流程问题分类策略。
 */
@Component
class BusinessFlowStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    BusinessFlowStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("businessFlow");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.BUSINESS_FLOW;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "controller service flow handler callback status update");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_context", "codegraph_search", "codegraph_callers", "codegraph_callees");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("spring_boot_analysis", "code_review_analysis");
    }
}
