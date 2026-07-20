package cn.welsione.ascoder.common.exception;

/**
 * 状态非法异常，对应 HTTP 409。
 * 用于业务状态机守卫、重复操作、前置条件不满足等场景。
 */
public class InvalidStateException extends DomainException {

    public InvalidStateException(String message) {
        super("INVALID_STATE", message);
    }

    public InvalidStateException(String message, Throwable cause) {
        super("INVALID_STATE", message, cause);
    }
}
