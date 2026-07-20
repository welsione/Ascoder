package cn.welsione.ascoder.selflearning;

/**
 * 自学习原始事件类型，描述系统留痕的交互和证据来源。
 */
public enum LearningRawEventType {
    CONVERSATION_RECORD,
    USER_QUESTION,
    USER_FOLLOW_UP,
    ASSISTANT_ANSWER,
    AGENT_OUTPUT,
    QUERY_PLAN,
    DELEGATION_PLAN,
    TOOL_CALL,
    TOOL_RESULT,
    CODE_EVIDENCE,
    GIT_EVIDENCE,
    USER_FEEDBACK,
    USER_CORRECTION,
    VERIFICATION_RESULT
}
