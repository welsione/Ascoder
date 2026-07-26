package cn.welsione.ascoder.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

/**
 * JWT Token 生成与验证工具。
 * Access Token 短期（30 分钟），含 roles + permissions 声明。
 * Refresh Token 长期（7 天），仅含 userId + jti，支持 Rotation。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String ISSUER = "ascoder";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;
    private final Environment environment;
    private final String rawAccessSecret;
    private final String rawRefreshSecret;

    public JwtTokenProvider(
            @Value("${ascoder.security.jwt.access-secret:}") String accessSecret,
            @Value("${ascoder.security.jwt.refresh-secret:}") String refreshSecret,
            @Value("${ascoder.security.jwt.access-token-validity-ms:1800000}") long accessTokenValidityMs,
            @Value("${ascoder.security.jwt.refresh-token-validity-ms:604800000}") long refreshTokenValidityMs,
            Environment environment
    ) {
        this.rawAccessSecret = accessSecret;
        this.rawRefreshSecret = refreshSecret;
        this.accessKey = resolveKey(accessSecret, "access");
        this.refreshKey = resolveKey(refreshSecret, "refresh");
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
        this.environment = environment;
    }

    /**
     * 生产环境启动时校验 JWT 密钥是否已配置。
     * 若使用默认密钥则拒绝启动，防止密钥泄露导致 Token 伪造。
     */
    @PostConstruct
    void validateKeyConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = false;
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile)) {
                isProd = true;
                break;
            }
        }
        if (isProd && (isDefaultKey(rawAccessSecret) || isDefaultKey(rawRefreshSecret))) {
            throw new IllegalStateException(
                    "生产环境必须配置 JWT 密钥！请设置环境变量 ASCODER_JWT_ACCESS_SECRET 和 ASCODER_JWT_REFRESH_SECRET");
        }
    }

    private boolean isDefaultKey(String secret) {
        return secret == null || secret.isBlank();
    }

    /**
     * 生成 Access + Refresh Token 对。
     */
    public TokenPair generateTokenPair(Long userId, String username,
                                        Set<String> roles, Set<String> permissions) {
        String accessToken = generateAccessToken(userId, username, roles, permissions);
        String refreshToken = generateRefreshToken(userId);
        return new TokenPair(accessToken, refreshToken, accessTokenValidityMs / 1000);
    }

    /**
     * 生成 Access Token，包含用户 ID、用户名、角色和权限。
     */
    private String generateAccessToken(Long userId, String username,
                                        Set<String> roles, Set<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValidityMs);

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissions)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(accessKey)
                .compact();
    }

    /**
     * 生成 Refresh Token，仅包含用户 ID 和唯一标识（jti）。
     */
    private String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenValidityMs);

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(refreshKey)
                .compact();
    }

    /**
     * 验证 Access Token，返回 Claims。无效时抛 JwtException。
     */
    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Refresh Token，返回 Claims。无效时抛 JwtException。
     */
    public Claims validateRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户 ID。
     */
    public Long getUserIdFromToken(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Refresh Token Claims 中提取 jti。
     */
    public String getJtiFromRefreshToken(Claims claims) {
        return claims.getId();
    }

    /**
     * 解析密钥：优先使用配置的 Base64 密钥，未配置则使用开发环境默认密钥。
     * 生产环境必须通过 ASCODER_JWT_ACCESS_SECRET / ASCODER_JWT_REFRESH_SECRET 配置。
     */
    private SecretKey resolveKey(String base64Secret, String keyName) {
        if (base64Secret != null && !base64Secret.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
            if (keyBytes.length >= 32) {
                log.info("JWT {} 密钥从配置加载", keyName);
                return Keys.hmacShaKeyFor(keyBytes);
            }
            log.warn("JWT {} 密钥长度不足 32 字节，使用开发环境默认密钥", keyName);
        }
        // 开发环境默认密钥（64 字节，确保 >= 256 bits），仅用于本地开发与测试
        log.warn("JWT {} 密钥未配置，使用开发环境默认密钥（仅用于开发环境），生产环境必须设置环境变量", keyName);
        String devKey = "ascoder-dev-" + keyName + "-default-secret-key-256bits-padding!!";
        return Keys.hmacShaKeyFor(devKey.getBytes(StandardCharsets.UTF_8));
    }
}
