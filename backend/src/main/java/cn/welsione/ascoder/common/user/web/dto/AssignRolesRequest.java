package cn.welsione.ascoder.common.user.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * 分配用户角色请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignRolesRequest {

    @NotNull(message = "角色编码列表不能为 null")
    private Set<String> roleCodes;
}
