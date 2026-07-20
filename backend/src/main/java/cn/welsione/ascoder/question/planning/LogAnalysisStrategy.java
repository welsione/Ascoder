package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日志分析问题分类策略，主要由 {@code logUploadId} 触发；关键词命中时也可作为辅助识别。
 */
@Component
class LogAnalysisStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    LogAnalysisStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("logAnalysis");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.LOG_ANALYSIS;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(originalQuestion, "exception stack trace error log root cause");
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("log_summary", "log_exception_groups", "log_search", "log_snippet",
                "codegraph_search", "codegraph_context");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("bug_root_cause_analysis");
    }
}
