package cn.welsione.ascoder.common.user.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 权限摘要。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSummary {
    private Long id;
    private String code;
    private String name;
    private String module;
    private String description;
}
