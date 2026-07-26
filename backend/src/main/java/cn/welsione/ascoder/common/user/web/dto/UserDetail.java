package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户详情（含角色）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDetail {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private boolean enabled;
    private boolean accountNonLocked;
    private boolean passwordChanged;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private Set<String> roles;
}
