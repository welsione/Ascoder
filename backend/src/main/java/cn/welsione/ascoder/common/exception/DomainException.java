package cn.welsione.ascoder.common.exception;

import lombok.Getter;

/**
 * 领域异常基类，所有业务层异常均应继承此类。
 * 不携带 HTTP 状态码，由 {@link GlobalExceptionHandler} 统一映射。
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
