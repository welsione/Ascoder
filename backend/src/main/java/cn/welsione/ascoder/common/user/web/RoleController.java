package cn.welsione.ascoder.common.user.web;

import cn.welsione.ascoder.common.user.RoleService;
import cn.welsione.ascoder.common.user.web.dto.AssignPermissionsRequest;
import cn.welsione.ascoder.common.user.web.dto.CreateRoleRequest;
import cn.welsione.ascoder.common.user.web.dto.RoleDetail;
import cn.welsione.ascoder.common.user.web.dto.RoleSummary;
import cn.welsione.ascoder.common.user.web.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限管理 REST 接口（管理员）。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE:MANAGE')")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleSummary> list() {
        return roleService.list();
    }

    @GetMapping("/{id}")
    public RoleDetail get(@PathVariable Long id) {
        return roleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDetail create(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    public RoleDetail update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }

    @PutMapping("/{id}/permissions")
    public RoleDetail assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request) {
        return roleService.assignPermissions(id, request.getPermissionCodes());
    }
}
