package cn.welsione.ascoder.question.planning;

import lombok.Value;

import java.util.List;

/**
 * 问题规划策略的评分结果，用于在多个候选意图之间选择最可信的规划。
 */
@Value
class QuestionPlannerScore {
    QuestionPlannerStrategy strategy;
    int score;
    List<String> matchedKeywords;

    boolean matched() {
        return score > 0;
    }
}
