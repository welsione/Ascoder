package cn.welsione.ascoder.common.user.persistence;

import cn.welsione.ascoder.common.user.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 权限数据访问。
 */
public interface PermissionJpaRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByModule(String module);
    Optional<Permission> findByCode(String code);
}
