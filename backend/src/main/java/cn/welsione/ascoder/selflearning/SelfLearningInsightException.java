package cn.welsione.ascoder.selflearning;

/**
 * Self Learning Agent 整理单个 conversation 失败时抛出的领域异常。
 */
public class SelfLearningInsightException extends RuntimeException {
    public SelfLearningInsightException(String message) {
        super(message);
    }

    public SelfLearningInsightException(String message, Throwable cause) {
        super(message, cause);
    }
}
