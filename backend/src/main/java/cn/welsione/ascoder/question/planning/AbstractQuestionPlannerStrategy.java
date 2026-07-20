package cn.welsione.ascoder.question.planning;

import java.util.List;

/**
 * 问题分类策略的骨架实现，提供通用的关键词匹配和推理文本生成。
 * 子类只需定义具体的关键词、改写查询、推荐工具和技能。
 */
abstract class AbstractQuestionPlannerStrategy implements QuestionPlannerStrategy {

    @Override
    public String reasoning(String role) {
        String roleText = role == null || role.isBlank() ? "developer" : role;
        return "基于问题关键词识别为 " + questionType() + "，面向 " + roleText + " 角色优先提供可验证的代码证据。";
    }

    boolean matches(String normalizedQuestion) {
        return score(normalizedQuestion).matched();
    }

    QuestionPlannerScore score(String normalizedQuestion) {
        List<String> matchedKeywords = keywords().stream()
                .filter(normalizedQuestion::contains)
                .toList();
        int score = matchedKeywords.stream()
                .mapToInt(this::scoreKeyword)
                .sum();
        return new QuestionPlannerScore(this, score, matchedKeywords);
    }

    private int scoreKeyword(String keyword) {
        int lengthWeight = Math.min(keyword.length(), 16);
        int phraseWeight = keyword.contains(" ") || keyword.length() >= 3 ? 8 : 0;
        return 10 + lengthWeight + phraseWeight;
    }
}
