package cn.welsione.ascoder.common.security;

import cn.welsione.ascoder.common.exception.DomainException;

/**
 * 授权失败异常，对应 HTTP 403。
 */
public class AuthorizationException extends DomainException {

    public AuthorizationException(String message) {
        super("ACCESS_DENIED", message);
    }
}
