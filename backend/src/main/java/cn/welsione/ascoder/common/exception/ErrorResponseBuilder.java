package cn.welsione.ascoder.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 错误响应构建工具，统一生成 {@code { timestamp, status, error, code, message }} 格式的响应体。
 *
 * <p>供 {@link GlobalExceptionHandler} 和其他模块的异常处理器复用，
 * 避免暴露 {@link GlobalExceptionHandler} 的内部方法或让各模块自行拼装响应格式。</p>
 */
public final class ErrorResponseBuilder {

    private ErrorResponseBuilder() {
    }

    /**
     * 构建标准错误响应。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 错误描述
     * @return 包含标准错误体的 ResponseEntity
     */
    public static ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
