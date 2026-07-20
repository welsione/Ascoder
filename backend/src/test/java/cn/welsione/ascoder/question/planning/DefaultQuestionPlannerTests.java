package cn.welsione.ascoder.question.planning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class DefaultQuestionPlannerTests {

    private DefaultQuestionPlanner planner;

    @BeforeEach
    void setUp() {
        QuestionPlannerKeywords kw = new QuestionPlannerKeywords(
                new org.springframework.core.io.ClassPathResource("question-planner-keywords.yml")
        );
        kw.load();
        planner = new DefaultQuestionPlanner(List.of(
                new EntryPointStrategy(kw),
                new BusinessFlowStrategy(kw),
                new CallerAnalysisStrategy(kw),
                new CalleeAnalysisStrategy(kw),
                new ImpactAnalysisStrategy(kw),
                new BugAnalysisStrategy(kw),
                new ConfigAnalysisStrategy(kw),
                new LogAnalysisStrategy(kw),
                new GeneralExplanationStrategy()
        ), kw);
    }

    @Test
    void plansEntryPointQuestionWithCodeKeywords() {
        QuestionPlan plan = planner.plan("这个项目的后端入口类是什么？", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.ENTRY_POINT);
        assertThat(plan.getRewrittenQueries()).anyMatch(query -> query.contains("SpringBootApplication"));
        assertThat(plan.getRecommendedTools()).contains("codegraph_search", "codegraph_context");
        assertThat(plan.getRecommendedSkills()).contains("spring_boot_analysis", "maven_project_analysis");
        assertThat(plan.getConfidence()).isGreaterThan(0.5);
        assertThat(plan.getMatchedSignals()).contains("ENTRY_POINT:入口");
    }

    @Test
    void plansCallerQuestionWithCallerTool() {
        QuestionPlan plan = planner.plan("这个方法是谁调用的？", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.CALLER_ANALYSIS);
        assertThat(plan.getRecommendedTools()).contains("codegraph_callers");
        assertThat(plan.getRecommendedSkills()).contains("spring_boot_analysis", "code_review_analysis");
    }

    @Test
    void prefersHigherScoringPlanOverEarlierSingleKeywordMatch() {
        QuestionPlan plan = planner.plan("这个报错日志里为什么出现 exception stack trace？", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.LOG_ANALYSIS);
        assertThat(plan.getAlternativeTypes()).contains(QuestionType.BUG_ANALYSIS);
        assertThat(plan.getMatchedSignals()).anyMatch(signal -> signal.contains("日志"));
        assertThat(plan.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void fallsBackToGeneralPlanWhenNoStrategyScores() {
        QuestionPlan plan = planner.plan("帮我整体看一下这个模块", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.GENERAL_EXPLANATION);
        assertThat(plan.getConfidence()).isEqualTo(0.25);
        assertThat(plan.getMatchedSignals()).isEmpty();
    }

    @Test
    void forcedPlanTypeCarriesExplicitSignalWhenQuestionHasNoKeywordMatch() {
        QuestionPlan plan = planner.planForType("帮我看一下这个附件", "developer", QuestionType.LOG_ANALYSIS);

        assertThat(plan.getType()).isEqualTo(QuestionType.LOG_ANALYSIS);
        assertThat(plan.getConfidence()).isGreaterThan(0.8);
        assertThat(plan.getMatchedSignals()).contains("LOG_ANALYSIS:forced");
        assertThat(plan.toPromptText()).contains("LOG_ANALYSIS:forced");
    }
}
