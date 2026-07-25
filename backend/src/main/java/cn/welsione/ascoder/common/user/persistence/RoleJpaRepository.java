package cn.welsione.ascoder.common.user.persistence;

import cn.welsione.ascoder.common.user.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 角色数据访问。
 */
public interface RoleJpaRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}
