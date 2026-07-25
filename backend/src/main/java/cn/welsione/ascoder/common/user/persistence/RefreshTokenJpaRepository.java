package cn.welsione.ascoder.common.user.persistence;

import cn.welsione.ascoder.common.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Refresh Token 数据访问。
 */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
    void deleteByUserId(Long userId);
}
