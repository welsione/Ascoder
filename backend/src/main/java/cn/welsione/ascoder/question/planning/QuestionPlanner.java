package cn.welsione.ascoder.question.planning;

/**
 * 问题规划器端口接口，根据问题内容和角色生成查询规划。
 */
public interface QuestionPlanner {

    QuestionPlan plan(String question, String role);

    /**
     * 强制使用指定类型生成规划，跳过关键词匹配；用于 logUploadId 等显式信号。
     */
    QuestionPlan planForType(String question, String role, QuestionType type);
}
