package cn.welsione.ascoder.common.exception;

/**
 * 资源不存在异常，对应 HTTP 404。
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " 不存在: " + id);
    }

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
