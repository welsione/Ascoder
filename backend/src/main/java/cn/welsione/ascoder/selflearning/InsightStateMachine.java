package cn.welsione.ascoder.selflearning;

/**
 * 候选洞察状态机，负责校验并执行合法的状态转换。
 *
 * <p>合法转换：PENDING_REVIEW → APPROVED | REJECTED，APPROVED → MERGED。
 * 其余转换均抛出 {@link cn.welsione.ascoder.common.exception.InvalidStateException}。</p>
 */
public interface InsightStateMachine {

    /**
     * 在守卫校验通过后执行状态转换，同时记录审核意见和时间。
     *
     * @param insight   待转换的洞察实体
     * @param target    目标状态
     * @param reviewerComment 审核意见（可 null）
     * @throws cn.welsione.ascoder.common.exception.InvalidStateException 转换不合法时
     */
    void transition(LearningInsight insight, LearningInsightStatus target, String reviewerComment);
}
