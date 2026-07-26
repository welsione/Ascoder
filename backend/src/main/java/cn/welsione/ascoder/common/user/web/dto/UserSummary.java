package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户摘要（列表项，不含敏感信息）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private boolean enabled;
    private boolean accountNonLocked;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private Set<String> roles;
}
