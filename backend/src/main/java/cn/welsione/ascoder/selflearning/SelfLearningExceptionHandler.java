package cn.welsione.ascoder.selflearning;

import cn.welsione.ascoder.common.exception.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 自学习模块异常处理器，将 {@link SelfLearningInsightException} 映射为 HTTP 422。
 *
 * <p>独立于 {@link GlobalExceptionHandler}，避免 common 模块反向依赖 selflearning 模块。
 * 响应格式复用 {@link GlobalExceptionHandler} 的 buildResponse 结构。</p>
 */
@RestControllerAdvice
public class SelfLearningExceptionHandler {

    @ExceptionHandler(SelfLearningInsightException.class)
    public ResponseEntity<Map<String, Object>> handleInsightError(SelfLearningInsightException ex) {
        return GlobalExceptionHandler.buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }
}
