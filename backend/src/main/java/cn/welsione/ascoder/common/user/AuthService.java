package cn.welsione.ascoder.common.user;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.common.security.AuthenticationException;
import cn.welsione.ascoder.common.security.AuthenticatedUser;
import cn.welsione.ascoder.common.security.jwt.JwtTokenProvider;
import cn.welsione.ascoder.common.security.jwt.TokenPair;
import cn.welsione.ascoder.common.user.domain.*;
import cn.welsione.ascoder.common.user.persistence.*;
import cn.welsione.ascoder.common.user.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证核心服务：注册、登录、Token 刷新、登出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final UserRoleJpaRepository userRoleRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final PermissionJpaRepository permissionRepository;
    private final RolePermissionJpaRepository rolePermissionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${ascoder.security.login.max-fail-count:5}")
    private int maxFailCount;

    @Value("${ascoder.security.login.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    /**
     * 检查系统是否已初始化（是否已有用户）。
     */
    public InitStatusResponse getInitStatus() {
        return new InitStatusResponse(userRepository.count() > 0);
    }

    /**
     * 用户注册。首个用户自动获得 ADMIN 角色。
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateException("用户名已存在: " + request.getUsername());
        }

        validatePassword(request.getPassword());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user = userRepository.save(user);

        boolean isFirstUser = userRepository.count() == 1;
        String roleCode = isFirstUser ? "ADMIN" : "USER";
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("内置角色不存在: " + roleCode));

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);

        log.info("用户注册成功: username={}, role={}, isFirstUser={}", request.getUsername(), roleCode, isFirstUser);

        Set<String> roles = Set.of(roleCode);
        Set<String> permissions = getPermissionsByRoles(Set.of(role.getId()));
        TokenPair tokenPair = jwtTokenProvider.generateTokenPair(user.getId(), user.getUsername(), roles, permissions);
        saveRefreshToken(user.getId(), tokenPair.getRefreshToken(), null);

        return buildAuthResponse(tokenPair, user, roles, permissions);
    }

    /**
     * 用户登录。
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthenticationException("用户名或密码错误"));

        if (!user.isEnabled()) {
            throw new AuthenticationException("账户已被禁用");
        }

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationException("账户已被锁定，请稍后再试");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleLoginFailure(user);
            throw new AuthenticationException("用户名或密码错误");
        }

        user.setLoginFailCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        Set<String> roles = getRolesByUserId(user.getId());
        Set<Long> roleIds = getRoleIdsByUserId(user.getId());
        Set<String> permissions = getPermissionsByRoles(roleIds);

        TokenPair tokenPair = jwtTokenProvider.generateTokenPair(user.getId(), user.getUsername(), roles, permissions);
        saveRefreshToken(user.getId(), tokenPair.getRefreshToken(), null);

        log.info("用户登录成功: username={}", user.getUsername());
        return buildAuthResponse(tokenPair, user, roles, permissions);
    }

    /**
     * 刷新 Token（Rotation：吊销旧 Token，发放新 Token）。
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        var claims = jwtTokenProvider.validateRefreshToken(request.getRefreshToken());
        Long userId = jwtTokenProvider.getUserIdFromToken(claims);

        String tokenHash = hashToken(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthenticationException("无效的 Refresh Token"));

        if (!refreshToken.isValid()) {
            throw new AuthenticationException("Refresh Token 已失效");
        }

        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("用户不存在"));
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new AuthenticationException("账户已被禁用或锁定");
        }

        Set<String> roles = getRolesByUserId(user.getId());
        Set<Long> roleIds = getRoleIdsByUserId(user.getId());
        Set<String> permissions = getPermissionsByRoles(roleIds);

        TokenPair tokenPair = jwtTokenProvider.generateTokenPair(user.getId(), user.getUsername(), roles, permissions);
        saveRefreshToken(user.getId(), tokenPair.getRefreshToken(), null);

        log.debug("Token 刷新成功: userId={}", userId);
        return buildAuthResponse(tokenPair, user, roles, permissions);
    }

    /**
     * 用户登出，吊销 Refresh Token。
     */
    @Transactional
    public void logout(RefreshRequest request) {
        try {
            var claims = jwtTokenProvider.validateRefreshToken(request.getRefreshToken());
            Long userId = jwtTokenProvider.getUserIdFromToken(claims);
            String tokenHash = hashToken(request.getRefreshToken());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
                rt.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(rt);
            });
            log.info("用户登出: userId={}", userId);
        } catch (Exception e) {
            log.debug("登出时 Refresh Token 无效: {}", e.getMessage());
        }
    }

    /**
     * 获取当前用户信息。
     */
    public AuthResponse.UserInfo me() {
        AuthenticatedUser current = AuthenticatedUser.current();
        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new AuthenticationException("用户不存在"));
        Set<String> roles = getRolesByUserId(user.getId());
        return new AuthResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                roles,
                current.getPermissions()
        );
    }

    // ========== 私有方法 ==========

    private void validatePassword(String password) {
        if (password.length() < 6) {
            throw new ValidationException("密码长度不能少于 6 个字符");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("密码必须包含字母和数字");
        }
    }

    private void handleLoginFailure(User user) {
        user.setLoginFailCount(user.getLoginFailCount() + 1);
        if (user.getLoginFailCount() >= maxFailCount) {
            user.setAccountNonLocked(false);
            log.warn("账户锁定: username={}, failCount={}", user.getUsername(), user.getLoginFailCount());
        }
        userRepository.save(user);
    }

    private void saveRefreshToken(Long userId, String refreshToken, String userAgent) {
        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hashToken(refreshToken));
        entity.setExpiresAt(LocalDateTime.now().plusDays(7));
        entity.setUserAgent(userAgent);
        refreshTokenRepository.save(entity);
    }

    private Set<String> getRolesByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId())
                        .map(Role::getCode).orElse(""))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<Long> getRoleIdsByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toSet());
    }

    private Set<String> getPermissionsByRoles(Set<Long> roleIds) {
        return roleIds.stream()
                .flatMap(roleId -> rolePermissionRepository.findByRoleId(roleId).stream())
                .map(rp -> permissionRepository.findById(rp.getPermissionId())
                        .map(Permission::getCode).orElse(""))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }

    private AuthResponse buildAuthResponse(TokenPair tokenPair, User user,
                                            Set<String> roles, Set<String> permissions) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(tokenPair.getAccessToken());
        response.setRefreshToken(tokenPair.getRefreshToken());
        response.setExpiresIn(tokenPair.getExpiresIn());
        response.setUser(new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getNickname(), roles, permissions
        ));
        return response;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
