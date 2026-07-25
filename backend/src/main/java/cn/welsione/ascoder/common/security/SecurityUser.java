package cn.welsione.ascoder.common.security;

import io.jsonwebtoken.Claims;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails 实现，从 JWT Claims 构建。
 * 权限列表同时包含角色（ROLE_ 前缀）和权限编码。
 */
@Getter
public class SecurityUser implements UserDetails {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String ROLE_PREFIX = "ROLE_";

    private final Long userId;
    private final String username;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    private SecurityUser(Long userId, String username, Set<String> roles,
                          Set<String> permissions, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
        this.permissions = permissions;
        this.authorities = authorities;
    }

    /**
     * 从 JWT Access Token Claims 构建 SecurityUser。
     */
    @SuppressWarnings("unchecked")
    public static SecurityUser from(Claims claims) {
        Long userId = Long.parseLong(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);

        Set<String> roleSet = roles != null ? Set.copyOf(roles) : Set.of();
        Set<String> permSet = permissions != null ? Set.copyOf(permissions) : Set.of();

        // 构建 authorities：角色加 ROLE_ 前缀 + 权限编码
        List<GrantedAuthority> authorities = java.util.stream.Stream.concat(
                roleSet.stream().map(r -> new SimpleGrantedAuthority(ROLE_PREFIX + r)),
                permSet.stream().map(SimpleGrantedAuthority::new)
        ).collect(Collectors.toList());

        return new SecurityUser(userId, username, roleSet, permSet, authorities);
    }

    /**
     * 是否为管理员。
     */
    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;  // JWT 认证不需要密码
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
