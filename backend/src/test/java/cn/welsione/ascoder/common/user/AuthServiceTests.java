package cn.welsione.ascoder.common.user;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.common.security.AuthenticationException;
import cn.welsione.ascoder.common.security.AuthenticatedUser;
import cn.welsione.ascoder.common.security.SecurityUser;
import cn.welsione.ascoder.common.security.jwt.JwtTokenProvider;
import cn.welsione.ascoder.common.user.domain.Role;
import cn.welsione.ascoder.common.user.domain.User;
import cn.welsione.ascoder.common.user.domain.UserRole;
import cn.welsione.ascoder.common.user.persistence.*;
import cn.welsione.ascoder.common.user.web.dto.LoginRequest;
import cn.welsione.ascoder.common.user.web.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserJpaRepository userRepository;

    @Mock
    private RoleJpaRepository roleRepository;

    @Mock
    private UserRoleJpaRepository userRoleRepository;

    @Mock
    private RefreshTokenJpaRepository refreshTokenRepository;

    @Mock
    private PermissionJpaRepository permissionRepository;

    @Mock
    private RolePermissionJpaRepository rolePermissionRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // 默认 JWT Token 生成行为
        lenient().when(jwtTokenProvider.generateTokenPair(any(), anyString(), any(), any()))
                .thenReturn(new cn.welsione.ascoder.common.security.jwt.TokenPair("access", "refresh", 1800L));
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Nested
    class Register {

        @Test
        void shouldRegisterWithUserRole() {
            User savedUser = createUser(2L, "ces", true);
            Role userRole = createRole(2L, "USER");

            when(userRepository.existsByUsername("ces")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(roleRepository.findByCode("USER")).thenReturn(Optional.of(userRole));
            when(userRoleRepository.save(any(UserRole.class))).thenReturn(new UserRole());
            when(refreshTokenRepository.save(any())).thenReturn(new cn.welsione.ascoder.common.user.domain.RefreshToken());

            var response = authService.register(new RegisterRequest("ces", "Password1", null));

            assertThat(response.getUser().getUsername()).isEqualTo("ces");
            assertThat(response.getUser().getRoles()).contains("USER");
            verify(userRepository).save(any(User.class));
        }

        @Test
        void shouldRejectDuplicateUsername() {
            when(userRepository.existsByUsername("admin")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(new RegisterRequest("admin", "Password1", null)))
                    .isInstanceOf(DuplicateException.class);
        }

        @Test
        void shouldRejectWeakPassword() {
            when(userRepository.existsByUsername("newuser")).thenReturn(false);

            assertThatThrownBy(() -> authService.register(new RegisterRequest("newuser", "short", null)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void shouldRejectPasswordWithoutDigit() {
            when(userRepository.existsByUsername("newuser")).thenReturn(false);

            assertThatThrownBy(() -> authService.register(new RegisterRequest("newuser", "onlyletters", null)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("数字");
        }
    }

    @Nested
    class Login {

        @Test
        void shouldLoginSuccessfully() {
            User user = createUser(2L, "ces", true);
            Role userRole = createRole(2L, "USER");

            when(userRepository.findByUsername("ces")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password1", "encoded")).thenReturn(true);
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(userRoleRepository.findByUserId(2L)).thenReturn(List.of(createUserRole(2L, 2L)));
            when(roleRepository.findById(2L)).thenReturn(Optional.of(userRole));
            when(rolePermissionRepository.findByRoleId(2L)).thenReturn(List.of());
            when(refreshTokenRepository.save(any())).thenReturn(new cn.welsione.ascoder.common.user.domain.RefreshToken());

            var response = authService.login(new LoginRequest("ces", "Password1"));

            assertThat(response.getUser().getUsername()).isEqualTo("ces");
        }

        @Test
        void shouldRejectWrongPassword() {
            User user = createUser(2L, "ces", true);
            when(userRepository.findByUsername("ces")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(user);

            assertThatThrownBy(() -> authService.login(new LoginRequest("ces", "wrong")))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        void shouldRejectDisabledUser() {
            User user = createUser(2L, "ces", false);
            when(userRepository.findByUsername("ces")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ces", "Password1")))
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("禁用");
        }

        @Test
        void shouldRejectNonexistentUser() {
            when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", "Password1")))
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Nested
    class Me {

        @Test
        void shouldReturnPermissionsFromDatabase() {
            setupSecurityContext(2L, "ces", Set.of("USER"), Set.of("PROJECT:READ"));
            User user = createUser(2L, "ces", true);
            Role userRole = createRole(2L, "USER");

            when(userRepository.findById(2L)).thenReturn(Optional.of(user));
            when(userRoleRepository.findByUserId(2L)).thenReturn(List.of(createUserRole(2L, 2L)));
            when(roleRepository.findById(2L)).thenReturn(Optional.of(userRole));
            when(rolePermissionRepository.findByRoleId(2L)).thenReturn(List.of());

            var userInfo = authService.me();

            assertThat(userInfo.getUsername()).isEqualTo("ces");
            // 权限来自数据库查询而非 JWT claims
            verify(rolePermissionRepository).findByRoleId(2L);
        }
    }

    private User createUser(Long id, String username, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setEnabled(enabled);
        user.setAccountNonLocked(true);
        user.setLoginFailCount(0);
        user.setPasswordChanged(true);
        user.setLastLoginAt(LocalDateTime.now());
        return user;
    }

    private Role createRole(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code.equals("ADMIN") ? "管理员" : "普通用户");
        return role;
    }

    private UserRole createUserRole(Long userId, Long roleId) {
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        return ur;
    }

    private void setupSecurityContext(Long userId, String username, Set<String> roles, Set<String> permissions) {
        SecurityUser securityUser = SecurityUser.of(userId, username, roles, permissions);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                securityUser, null, securityUser.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
