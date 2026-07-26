package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 更新角色请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {
    private String name;
    private String description;
    private Boolean enabled;
}
