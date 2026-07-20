package cn.welsione.ascoder.question.planning;

import java.util.List;

/**
 * 问题分类策略接口，每个 {@link QuestionType} 对应一个策略实现。
 * 由 {@link DefaultQuestionPlanner} 通过 Spring 收集所有策略并按序匹配。
 */
interface QuestionPlannerStrategy {

    /** 匹配关键词列表（小写归一化后匹配）。 */
    List<String> keywords();

    /** 对应的 QuestionType。 */
    QuestionType questionType();

    /** 针对该类型的改写查询。 */
    List<String> rewrittenQueries(String originalQuestion);

    /** 推荐的 CodeGraph 工具列表。 */
    List<String> recommendedTools();

    /** 基础技能列表（不含跨类型的 vue/maven 等动态技能）。 */
    List<String> baseSkills();

    /** 推理说明文本。 */
    String reasoning(String role);
}