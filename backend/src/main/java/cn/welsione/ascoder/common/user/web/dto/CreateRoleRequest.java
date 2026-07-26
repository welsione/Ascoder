package cn.welsione.ascoder.common.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 创建角色请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码最长 50 个字符")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称最长 100 个字符")
    private String name;

    @Size(max = 500, message = "描述最长 500 个字符")
    private String description;
}
