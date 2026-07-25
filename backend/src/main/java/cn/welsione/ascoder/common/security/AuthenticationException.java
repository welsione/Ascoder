package cn.welsione.ascoder.common.security;

import cn.welsione.ascoder.common.exception.DomainException;

/**
 * 认证失败异常，对应 HTTP 401。
 */
public class AuthenticationException extends DomainException {

    public AuthenticationException(String message) {
        super("UNAUTHORIZED", message);
    }
}
