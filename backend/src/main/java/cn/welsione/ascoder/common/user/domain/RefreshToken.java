package cn.welsione.ascoder.common.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Refresh Token 实体，存储 Token 的 SHA-256 哈希，支持吊销和 Rotation。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refreshTokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    @Column(length = 500)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 是否已吊销 */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 是否已过期 */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /** 是否有效（未吊销且未过期） */
    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }
}
