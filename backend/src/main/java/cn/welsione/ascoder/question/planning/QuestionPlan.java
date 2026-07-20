package cn.welsione.ascoder.question.planning;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * 问题规划结果，包含问题类型、检索建议、推荐工具和分类可信度。
 */
@Value
@AllArgsConstructor
public class QuestionPlan {
    QuestionType type;
    List<String> rewrittenQueries;
    List<String> recommendedTools;
    List<String> recommendedSkills;
    String reasoning;
    double confidence;
    List<String> matchedSignals;
    List<QuestionType> alternativeTypes;

    public String toPromptText() {
        return """
                以下规划仅供参考，Agent 必须自行判断是否采用：
                问题类型：%s
                规划置信度：%.2f
                命中信号：%s
                候选类型：%s
                候选检索词：
                - %s
                初步判断：%s
                """.formatted(
                type,
                confidence,
                matchedSignals.isEmpty() ? "无明确命中，使用兜底规划" : String.join("，", matchedSignals),
                alternativeTypes.isEmpty() ? "无" : alternativeTypes,
                String.join("\n- ", rewrittenQueries),
                reasoning
        );
    }
}
