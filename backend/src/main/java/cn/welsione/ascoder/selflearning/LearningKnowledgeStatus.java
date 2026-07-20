package cn.welsione.ascoder.selflearning;

/**
 * 正式知识状态，控制知识是否可参与回答召回。
 */
public enum LearningKnowledgeStatus {
    ACTIVE,
    VERIFIED,
    STALE,
    DEPRECATED,
    REJECTED,
    NEGATIVE
}
