package cn.welsione.ascoder.question.planning;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 校验并归一化 Planner Agent 产出的规划草稿，避免非法类型或未授权工具进入主链路。
 */
@Component
class QuestionPlanValidator {

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "codegraph_context",
            "codegraph_search",
            "codegraph_callers",
            "codegraph_callees",
            "codegraph_impact",
            "codegraph_affected",
            "codegraph_files",
            "log_summary",
            "log_exception_groups",
            "log_search",
            "log_snippet"
    );
    private static final Set<String> ALLOWED_SKILLS = Set.of(
            "spring_boot_analysis",
            "maven_project_analysis",
            "code_review_analysis",
            "bug_root_cause_analysis",
            "impact_analysis",
            "vue_analysis"
    );

    Optional<QuestionPlan> validate(AgentQuestionPlanDraft draft, QuestionPlan fallback) {
        if (draft == null) {
            return Optional.empty();
        }
        Optional<QuestionType> type = parseQuestionType(draft.getType());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        List<String> rewrittenQueries = cleanStrings(draft.getRewrittenQueries(), 6);
        if (rewrittenQueries.isEmpty()) {
            return Optional.empty();
        }
        List<String> recommendedTools = filterAllowed(draft.getRecommendedTools(), ALLOWED_TOOLS, fallback.getRecommendedTools());
        List<String> recommendedSkills = filterAllowed(draft.getRecommendedSkills(), ALLOWED_SKILLS, fallback.getRecommendedSkills());
        return Optional.of(new QuestionPlan(
                type.get(),
                rewrittenQueries,
                recommendedTools,
                recommendedSkills,
                cleanReasoning(draft.getReasoning(), fallback.getReasoning()),
                confidence(draft.getConfidence(), fallback.getConfidence()),
                matchedSignals(draft.getMatchedSignals(), fallback),
                alternativeTypes(draft.getAlternativeTypes(), type.get(), fallback)
        ));
    }

    private Optional<QuestionType> parseQuestionType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(QuestionType.values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }

    private List<String> cleanStrings(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(limit)
                .toList();
    }

    private List<String> filterAllowed(List<String> values, Set<String> allowed, List<String> fallback) {
        List<String> cleaned = cleanStrings(values, 8).stream()
                .filter(allowed::contains)
                .toList();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String cleanReasoning(String reasoning, String fallback) {
        if (reasoning == null || reasoning.isBlank()) {
            return fallback;
        }
        return reasoning.trim();
    }

    private double confidence(Double value, double fallback) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return fallback;
        }
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return Math.round(clamped * 100.0) / 100.0;
    }

    private List<String> matchedSignals(List<String> values, QuestionPlan fallback) {
        LinkedHashSet<String> signals = new LinkedHashSet<>(fallback.getMatchedSignals());
        signals.add("PLANNER_AGENT:structured_output");
        signals.addAll(cleanStrings(values, 8));
        return List.copyOf(signals);
    }

    private List<QuestionType> alternativeTypes(List<String> values, QuestionType selectedType, QuestionPlan fallback) {
        LinkedHashSet<QuestionType> alternatives = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .map(this::parseQuestionType)
                    .flatMap(Optional::stream)
                    .filter(type -> type != selectedType)
                    .forEach(alternatives::add);
        }
        fallback.getAlternativeTypes().stream()
                .filter(type -> type != selectedType)
                .forEach(alternatives::add);
        return alternatives.stream().limit(2).toList();
    }
}
