package cn.welsione.ascoder.common.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * 认证用户上下文，Service 层通过此类获取当前登录用户信息。
 */
public class AuthenticatedUser {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final Long userId;
    private final String username;
    private final Set<String> permissions;
    private final boolean admin;

    public AuthenticatedUser(Long userId, String username, Set<String> permissions, boolean admin) {
        this.userId = userId;
        this.username = username;
        this.permissions = permissions;
        this.admin = admin;
    }

    /**
     * 从 SecurityContext 获取当前用户，未认证时抛 AuthenticationException。
     */
    public static AuthenticatedUser current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser user)) {
            throw new AuthenticationException("未登录或 Token 已过期");
        }
        return new AuthenticatedUser(user.getUserId(), user.getUsername(), user.getPermissions(), user.isAdmin());
    }

    /**
     * 从 SecurityContext 获取当前用户，未认证时返回 null。
     */
    public static AuthenticatedUser currentOrNull() {
        try {
            return current();
        } catch (AuthenticationException e) {
            return null;
        }
    }

    /** 判断是否拥有指定权限 */
    public boolean hasPermission(String permissionCode) {
        return admin || permissions.contains(permissionCode);
    }

    /** 是否为管理员 */
    public boolean isAdmin() {
        return admin;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Set<String> getPermissions() { return permissions; }
}
