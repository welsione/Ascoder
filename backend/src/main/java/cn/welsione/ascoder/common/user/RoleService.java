package cn.welsione.ascoder.common.user;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.user.domain.Permission;
import cn.welsione.ascoder.common.user.domain.Role;
import cn.welsione.ascoder.common.user.domain.RolePermission;
import cn.welsione.ascoder.common.user.persistence.PermissionJpaRepository;
import cn.welsione.ascoder.common.user.persistence.RoleJpaRepository;
import cn.welsione.ascoder.common.user.persistence.RolePermissionJpaRepository;
import cn.welsione.ascoder.common.user.web.dto.CreateRoleRequest;
import cn.welsione.ascoder.common.user.web.dto.PermissionSummary;
import cn.welsione.ascoder.common.user.web.dto.RoleDetail;
import cn.welsione.ascoder.common.user.web.dto.RoleSummary;
import cn.welsione.ascoder.common.user.web.dto.UpdateRoleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限管理服务，管理员查询/创建角色、分配权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleJpaRepository roleRepository;
    private final PermissionJpaRepository permissionRepository;
    private final RolePermissionJpaRepository rolePermissionRepository;

    /**
     * 列出所有角色。
     */
    public List<RoleSummary> list() {
        return roleRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 获取角色详情（含权限编码列表）。
     */
    public RoleDetail get(Long id) {
        Role role = findRole(id);
        Set<String> permissions = getPermissionCodesByRoleId(id);
        return new RoleDetail(role.getId(), role.getCode(), role.getName(),
                role.getDescription(), role.isBuiltin(), role.isEnabled(),
                permissions);
    }

    /**
     * 创建自定义角色。
     */
    @Transactional
    public RoleDetail create(CreateRoleRequest request) {
        if (roleRepository.findByCode(request.getCode()).isPresent()) {
            throw new DuplicateException("角色编码已存在: " + request.getCode());
        }
        Role role = new Role();
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setBuiltin(false);
        role.setEnabled(true);
        role = roleRepository.save(role);
        log.info("创建角色: code={}", request.getCode());
        return get(role.getId());
    }

    /**
     * 更新角色（内置角色仅可改描述）。
     */
    @Transactional
    public RoleDetail update(Long id, UpdateRoleRequest request) {
        Role role = findRole(id);
        if (role.isBuiltin()) {
            if (request.getDescription() != null) {
                role.setDescription(request.getDescription());
            }
        } else {
            if (request.getName() != null) {
                role.setName(request.getName());
            }
            if (request.getDescription() != null) {
                role.setDescription(request.getDescription());
            }
            if (request.getEnabled() != null) {
                role.setEnabled(request.getEnabled());
            }
        }
        roleRepository.save(role);
        return get(id);
    }

    /**
     * 删除自定义角色（内置不可删）。
     */
    @Transactional
    public void delete(Long id) {
        Role role = findRole(id);
        if (role.isBuiltin()) {
            throw new InvalidStateException("内置角色不可删除");
        }
        roleRepository.delete(role);
        log.info("删除角色: id={}, code={}", id, role.getCode());
    }

    /**
     * 分配角色权限。
     */
    @Transactional
    public RoleDetail assignPermissions(Long id, Set<String> permissionCodes) {
        findRole(id);
        Set<Permission> permissions = permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new ResourceNotFoundException("权限不存在: " + code)))
                .collect(Collectors.toSet());

        rolePermissionRepository.deleteByRoleId(id);
        for (Permission perm : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(perm.getId());
            rolePermissionRepository.save(rp);
        }
        log.info("分配角色权限: roleId={}, permissions={}", id, permissionCodes);
        return get(id);
    }

    /**
     * 列出所有权限。
     */
    public List<PermissionSummary> listPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> new PermissionSummary(p.getId(), p.getCode(), p.getName(),
                        p.getModule(), p.getDescription()))
                .toList();
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + id));
    }

    private Set<String> getPermissionCodesByRoleId(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(rp -> permissionRepository.findById(rp.getPermissionId())
                        .map(Permission::getCode).orElse(""))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }

    private RoleSummary toSummary(Role role) {
        Set<String> permissions = getPermissionCodesByRoleId(role.getId());
        return new RoleSummary(role.getId(), role.getCode(), role.getName(),
                role.getDescription(), role.isBuiltin(), role.isEnabled(),
                permissions.size());
    }
}
