package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.DomainException;

/**
 * Self Learning Agent 整理单个 conversation 失败时抛出的领域异常。
 *
 * <p>对应 HTTP 422 Unprocessable Entity，由 {@link SelfLearningExceptionHandler} 统一映射。</p>
 */
public class SelfLearningInsightException extends DomainException {

    public SelfLearningInsightException(String message) {
        super("INSIGHT_ERROR", message);
    }

    public SelfLearningInsightException(String message, Throwable cause) {
        super("INSIGHT_ERROR", message, cause);
    }
}
