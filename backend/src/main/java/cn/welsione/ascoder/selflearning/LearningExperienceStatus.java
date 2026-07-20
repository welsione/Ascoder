package cn.welsione.ascoder.selflearning;

/**
 * 自学习经验状态，控制经验是否可参与后续回答。
 */
public enum LearningExperienceStatus {
    CANDIDATE,
    VERIFIED,
    ACTIVE,
    DEPRECATED,
    REJECTED,
    NEGATIVE
}
