package cn.welsione.ascoder.common.user.persistence;

import cn.welsione.ascoder.common.user.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用户-角色关联数据访问。
 */
public interface UserRoleJpaRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
