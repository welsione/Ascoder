package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 入口点问题分类策略。
 */
@Component
class EntryPointStrategy extends AbstractQuestionPlannerStrategy {

    private final QuestionPlannerKeywords keywords;

    EntryPointStrategy(QuestionPlannerKeywords keywords) {
        this.keywords = keywords;
    }

    @Override
    public List<String> keywords() {
        return keywords.forCategory("entryPoint");
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.ENTRY_POINT;
    }

    @Override
    public List<String> rewrittenQueries(String originalQuestion) {
        return List.of(
                "SpringBootApplication main Application entry class",
                "main method application startup"
        );
    }

    @Override
    public List<String> recommendedTools() {
        return List.of("codegraph_search", "codegraph_context", "codegraph_files");
    }

    @Override
    public List<String> baseSkills() {
        return List.of("spring_boot_analysis", "maven_project_analysis");
    }
}
