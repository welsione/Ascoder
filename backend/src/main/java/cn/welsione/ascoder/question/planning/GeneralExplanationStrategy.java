package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用解释问题分类策略（兜底策略，无匹配关键词）。
 */
@Component
class GeneralExplanationStrategy extends AbstractQuestionPlannerStrategy {

    GeneralExplanationStrategy() {
        // 兜底策略不需要关键词加载
    }

    @Override
    public List<String> keywords() {
        return List.of();
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.GENERAL_EXPLANATION;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "class method module overview");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_context", "codegraph_search");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("code_review_analysis");
    }

    /** 兜底策略始终匹配。 */
    @Override
    boolean matches(String normalizedQuestion) {
        return true;
    }
}
