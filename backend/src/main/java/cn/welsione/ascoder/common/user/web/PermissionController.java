package cn.welsione.ascoder.common.user.web;

import cn.welsione.ascoder.common.user.RoleService;
import cn.welsione.ascoder.common.user.web.dto.PermissionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限查询 REST 接口（管理员）。
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE:MANAGE')")
public class PermissionController {

    private final RoleService roleService;

    @GetMapping
    public List<PermissionSummary> list() {
        return roleService.listPermissions();
    }
}
