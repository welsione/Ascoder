package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色摘要。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleSummary {
    private Long id;
    private String code;
    private String name;
    private String description;
    private boolean builtin;
    private boolean enabled;
    private int permissionCount;
}
