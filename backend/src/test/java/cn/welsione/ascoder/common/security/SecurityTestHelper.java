package cn.welsione.ascoder.common.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * 测试辅助类，在集成测试中模拟认证用户。
 *
 * <p>使用方式：在 {@code @BeforeEach} 中调用 {@link #setupAdmin()}，
 * 在 {@code @AfterEach} 中调用 {@link #clear()}。</p>
 */
public class SecurityTestHelper {

    private SecurityTestHelper() {
    }

    /**
     * 设置当前线程的认证用户为管理员（ADMIN 角色，拥有所有权限）。
     */
    public static void setupAdmin() {
        setupUser(1L, "admin", Set.of("ADMIN"), Set.of());
    }

    /**
     * 设置当前线程的认证用户。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param roles       角色编码集合
     * @param permissions 权限编码集合
     */
    public static void setupUser(Long userId, String username, Set<String> roles, Set<String> permissions) {
        SecurityUser securityUser = SecurityUser.of(userId, username, roles, permissions);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * 清除当前线程的认证上下文。
     */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
