package cn.welsione.ascoder.common.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Access Token + Refresh Token 对。
 */
@Getter
@AllArgsConstructor
public class TokenPair {
    private final String accessToken;
    private final String refreshToken;
    private final long expiresIn;
}
