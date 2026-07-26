package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * 角色详情（含权限编码列表）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleDetail {
    private Long id;
    private String code;
    private String name;
    private String description;
    private boolean builtin;
    private boolean enabled;
    private Set<String> permissions;
}
