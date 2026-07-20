package cn.welsione.ascoder.question.planning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 默认问题规划器，通过策略模式将问题分类委托给对应的 {@link QuestionPlannerStrategy}。
 * 关键词从 {@code text-planner-keywords.yml} 资源文件加载，可直接编辑无需重新编译。
 */
@Slf4j
@Component
public class DefaultQuestionPlanner implements QuestionPlanner {

    private static final double FALLBACK_CONFIDENCE = 0.25;
    private static final int FORCED_TYPE_SCORE = 35;

    private final List<QuestionPlannerStrategy> strategies;
    private final Map<QuestionType, Integer> orderIndex;
    private final QuestionPlannerKeywords keywords;

    DefaultQuestionPlanner(List<QuestionPlannerStrategy> strategies, QuestionPlannerKeywords keywords) {
        // 按原始 classify 顺序排列策略：ENTRY_POINT → BUSINESS_FLOW → CALLER → CALLEE → IMPACT → BUG → CONFIG → GENERAL
        List<QuestionType> order = List.of(
                QuestionType.LOG_ANALYSIS,
                QuestionType.ENTRY_POINT, QuestionType.BUSINESS_FLOW,
                QuestionType.CALLER_ANALYSIS, QuestionType.CALLEE_ANALYSIS,
                QuestionType.IMPACT_ANALYSIS, QuestionType.BUG_ANALYSIS,
                QuestionType.CONFIG_ANALYSIS, QuestionType.GENERAL_EXPLANATION
        );
        Map<QuestionType, Integer> index = new EnumMap<>(QuestionType.class);
        for (int i = 0; i < order.size(); i++) {
            index.put(order.get(i), i);
        }
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(s -> index.getOrDefault(s.questionType(), Integer.MAX_VALUE)))
                .toList();
        this.orderIndex = Map.copyOf(index);
        this.keywords = keywords;
    }

    @Override
    public QuestionPlan plan(String question, String role) {
        String normalized = normalize(question);
        List<QuestionPlannerScore> scores = score(normalized);
        QuestionPlannerScore selected = select(scores);
        log.info("问题分类完成，类型={}，置信度={}，命中信号={}，问题={}",
                selected.getStrategy().questionType(),
                confidence(selected, scores),
                selected.getMatchedKeywords(),
                question);
        return buildPlan(selected, scores, question, role, normalized);
    }

    @Override
    public QuestionPlan planForType(String question, String role, QuestionType type) {
        QuestionPlannerStrategy strategy = strategies.stream()
                .filter(s -> s.questionType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知问题类型策略：" + type));
        log.info("强制问题分类，类型={}，问题={}", type, question);
        String normalized = normalize(question);
        QuestionPlannerScore scored = strategy instanceof AbstractQuestionPlannerStrategy abs
                ? abs.score(normalized)
                : new QuestionPlannerScore(strategy, 0, List.of());
        QuestionPlannerScore selected = scored.matched()
                ? scored
                : new QuestionPlannerScore(strategy, FORCED_TYPE_SCORE, List.of("forced"));
        return buildPlan(selected, List.of(selected), question, role, normalized);
    }

    private QuestionPlan buildPlan(QuestionPlannerScore selected, List<QuestionPlannerScore> scores,
                                   String question, String role, String normalized) {
        QuestionPlannerStrategy strategy = selected.getStrategy();
        return new QuestionPlan(
                strategy.questionType(),
                strategy.rewrittenQueries(question),
                strategy.recommendedTools(),
                recommendedSkills(strategy, normalized),
                strategy.reasoning(role),
                confidence(selected, scores),
                matchedSignals(selected),
                alternativeTypes(selected, scores)
        );
    }

    private List<QuestionPlannerScore> score(String normalizedQuestion) {
        return strategies.stream()
                .map(strategy -> strategy instanceof AbstractQuestionPlannerStrategy abs
                        ? abs.score(normalizedQuestion)
                        : new QuestionPlannerScore(strategy, 0, List.of()))
                .toList();
    }

    private QuestionPlannerScore select(List<QuestionPlannerScore> scores) {
        if (scores.stream().noneMatch(QuestionPlannerScore::matched)) {
            return strategies.stream()
                    .filter(strategy -> strategy.questionType() == QuestionType.GENERAL_EXPLANATION)
                    .findFirst()
                    .map(strategy -> new QuestionPlannerScore(strategy, 0, List.of()))
                    .orElseGet(() -> new QuestionPlannerScore(strategies.get(strategies.size() - 1), 0, List.of()));
        }
        return scores.stream()
                .max(Comparator.comparingInt(QuestionPlannerScore::getScore)
                        .thenComparing(score -> -orderIndex.getOrDefault(
                                score.getStrategy().questionType(), Integer.MAX_VALUE)))
                .orElseGet(() -> new QuestionPlannerScore(strategies.get(strategies.size() - 1), 0, List.of()));
    }

    private List<String> recommendedSkills(QuestionPlannerStrategy strategy, String normalizedQuestion) {
        List<String> skills = new ArrayList<>(strategy.baseSkills());
        if (containsAny(normalizedQuestion, keywords.dynamic("vue"))) {
            skills.add("vue_analysis");
        }
        if (containsAny(normalizedQuestion, keywords.dynamic("maven"))) {
            skills.add("maven_project_analysis");
        }
        return skills.stream().distinct().toList();
    }

    private String normalize(String question) {
        return question == null ? "" : question.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String source, List<String> targetKeywords) {
        for (String keyword : targetKeywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private double confidence(QuestionPlannerScore selected, List<QuestionPlannerScore> scores) {
        if (!selected.matched()) {
            return FALLBACK_CONFIDENCE;
        }
        int secondScore = scores.stream()
                .filter(score -> score != selected)
                .mapToInt(QuestionPlannerScore::getScore)
                .max()
                .orElse(0);
        double raw = 0.55 + Math.min(selected.getScore(), 45) / 100.0;
        if (secondScore > 0) {
            raw -= Math.min(0.25, secondScore / (double) (selected.getScore() + secondScore) * 0.25);
        }
        return Math.max(0.35, Math.min(0.95, Math.round(raw * 100.0) / 100.0));
    }

    private List<String> matchedSignals(QuestionPlannerScore selected) {
        return selected.getMatchedKeywords().stream()
                .map(keyword -> selected.getStrategy().questionType() + ":" + keyword)
                .toList();
    }

    private List<QuestionType> alternativeTypes(QuestionPlannerScore selected, List<QuestionPlannerScore> scores) {
        return scores.stream()
                .filter(QuestionPlannerScore::matched)
                .filter(score -> score != selected)
                .sorted(Comparator.comparingInt(QuestionPlannerScore::getScore).reversed())
                .map(score -> score.getStrategy().questionType())
                .distinct()
                .limit(2)
                .toList();
    }
}
