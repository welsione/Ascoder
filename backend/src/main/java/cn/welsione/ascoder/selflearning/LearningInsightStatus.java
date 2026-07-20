package cn.welsione.ascoder.selflearning;

/**
 * 候选洞察状态，控制洞察是否可被管理员审核和归纳。
 */
public enum LearningInsightStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    MERGED
}
