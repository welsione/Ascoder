package cn.welsione.ascoder.common.exception;

/**
 * 资源重复/冲突异常，对应 HTTP 409。
 */
public class DuplicateException extends DomainException {

    public DuplicateException(String message) {
        super("DUPLICATE", message);
    }

    public DuplicateException(String message, Throwable cause) {
        super("DUPLICATE", message, cause);
    }
}
