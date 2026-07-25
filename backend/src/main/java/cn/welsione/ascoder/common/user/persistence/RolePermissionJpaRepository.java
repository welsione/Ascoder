package cn.welsione.ascoder.common.user.persistence;

import cn.welsione.ascoder.common.user.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 角色-权限关联数据访问。
 */
public interface RolePermissionJpaRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);
}
