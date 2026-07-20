package cn.welsione.ascoder.question.planning;

import cn.welsione.ascoder.runtime.application.RuntimeSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证混合规划器只在需要时调用 Planner Agent，并在输出非法时回退规则规划。
 */
class AgenticQuestionPlannerTests {

    private DefaultQuestionPlanner rulePlanner;
    private QuestionPlanValidator validator;
    private RuntimeSettingsService runtimeSettings;

    @BeforeEach
    void setUp() {
        QuestionPlannerKeywords kw = new QuestionPlannerKeywords(
                new org.springframework.core.io.ClassPathResource("question-planner-keywords.yml")
        );
        kw.load();
        rulePlanner = new DefaultQuestionPlanner(List.of(
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
        validator = new QuestionPlanValidator();
        runtimeSettings = mock(RuntimeSettingsService.class);
        // 默认值：enabled=true, confidence=0.65, ambiguous=0.82
        lenient().when(runtimeSettings.readBoolean(eq("agent.query-planner-enabled"))).thenReturn(true);
        lenient().when(runtimeSettings.readDouble(eq("agent.query-planner-confidence-threshold"))).thenReturn(0.65);
        lenient().when(runtimeSettings.readDouble(eq("agent.query-planner-ambiguous-threshold"))).thenReturn(0.82);
    }

    private AgenticQuestionPlanner newPlanner(List<QuestionPlanningAgent> agents, boolean enabled,
                                              double confidence, double ambiguous) {
        when(runtimeSettings.readBoolean("agent.query-planner-enabled")).thenReturn(enabled);
        when(runtimeSettings.readDouble("agent.query-planner-confidence-threshold")).thenReturn(confidence);
        when(runtimeSettings.readDouble("agent.query-planner-ambiguous-threshold")).thenReturn(ambiguous);
        return new AgenticQuestionPlanner(rulePlanner, agents, validator, runtimeSettings);
    }

    @Test
    void lowConfidencePlanCanBeReplacedByValidAgentDraft() {
        FakePlanningAgent agent = new FakePlanningAgent(validBugDraft());
        AgenticQuestionPlanner planner = newPlanner(List.of(agent), true, 0.65, 0.82);

        QuestionPlan plan = planner.plan("帮我整体看一下这个模块", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.BUG_ANALYSIS);
        assertThat(plan.getRewrittenQueries()).contains("NullPointerException module root cause");
        assertThat(plan.getRecommendedTools()).containsExactly("codegraph_context", "codegraph_search");
        assertThat(plan.getMatchedSignals()).contains("PLANNER_AGENT:structured_output");
        assertThat(agent.calls).isEqualTo(1);
    }

    @Test
    void highConfidencePlanDoesNotCallAgent() {
        FakePlanningAgent agent = new FakePlanningAgent(validBugDraft());
        AgenticQuestionPlanner planner = newPlanner(List.of(agent), true, 0.65, 0.82);

        QuestionPlan plan = planner.plan("这个项目的后端入口类是什么？", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.ENTRY_POINT);
        assertThat(agent.calls).isZero();
    }

    @Test
    void invalidAgentDraftFallsBackToRulePlan() {
        AgentQuestionPlanDraft draft = validBugDraft();
        draft.setType("NO_SUCH_TYPE");
        FakePlanningAgent agent = new FakePlanningAgent(draft);
        AgenticQuestionPlanner planner = newPlanner(List.of(agent), true, 0.65, 0.82);

        QuestionPlan plan = planner.plan("帮我整体看一下这个模块", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.GENERAL_EXPLANATION);
        assertThat(plan.getMatchedSignals()).doesNotContain("PLANNER_AGENT:structured_output");
        assertThat(agent.calls).isEqualTo(1);
    }

    @Test
    void disabledPlannerNeverCallsAgent() {
        FakePlanningAgent agent = new FakePlanningAgent(validBugDraft());
        AgenticQuestionPlanner planner = newPlanner(List.of(agent), false, 0.65, 0.82);

        QuestionPlan plan = planner.plan("帮我整体看一下这个模块", "developer");

        assertThat(plan.getType()).isEqualTo(QuestionType.GENERAL_EXPLANATION);
        assertThat(agent.calls).isZero();
    }

    private AgentQuestionPlanDraft validBugDraft() {
        AgentQuestionPlanDraft draft = new AgentQuestionPlanDraft();
        draft.setType("BUG_ANALYSIS");
        draft.setRewrittenQueries(List.of("NullPointerException module root cause", "controller service exception"));
        draft.setRecommendedTools(List.of("codegraph_context", "codegraph_search", "rm"));
        draft.setRecommendedSkills(List.of("bug_root_cause_analysis", "unknown_skill"));
        draft.setReasoning("问题表达较泛，Planner Agent 将其规划为缺陷根因分析。");
        draft.setConfidence(0.74);
        draft.setMatchedSignals(List.of("PLANNER_AGENT:error_semantics"));
        draft.setAlternativeTypes(List.of("GENERAL_EXPLANATION"));
        return draft;
    }

    private static class FakePlanningAgent implements QuestionPlanningAgent {
        private final AgentQuestionPlanDraft draft;
        private int calls;

        private FakePlanningAgent(AgentQuestionPlanDraft draft) {
            this.draft = draft;
        }

        @Override
        public Optional<AgentQuestionPlanDraft> plan(String question, String role, QuestionPlan rulePlan) {
            calls++;
            return Optional.ofNullable(draft);
        }
    }
}