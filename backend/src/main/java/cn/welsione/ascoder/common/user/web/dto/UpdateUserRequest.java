package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理员更新用户请求（所有字段可选）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    private String nickname;
    private String email;
    private Boolean enabled;
}
