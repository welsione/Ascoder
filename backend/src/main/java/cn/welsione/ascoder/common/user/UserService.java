package cn.welsione.ascoder.common.user;

import cn.welsione.ascoder.common.exception.DuplicateException;
import cn.welsione.ascoder.common.exception.InvalidStateException;
import cn.welsione.ascoder.common.exception.ResourceNotFoundException;
import cn.welsione.ascoder.common.exception.ValidationException;
import cn.welsione.ascoder.common.user.domain.Role;
import cn.welsione.ascoder.common.user.domain.User;
import cn.welsione.ascoder.common.user.domain.UserRole;
import cn.welsione.ascoder.common.user.persistence.PermissionJpaRepository;
import cn.welsione.ascoder.common.user.persistence.RoleJpaRepository;
import cn.welsione.ascoder.common.user.persistence.RolePermissionJpaRepository;
import cn.welsione.ascoder.common.user.persistence.UserJpaRepository;
import cn.welsione.ascoder.common.user.persistence.UserRoleJpaRepository;
import cn.welsione.ascoder.common.user.web.dto.CreateUserRequest;
import cn.welsione.ascoder.common.user.web.dto.ResetPasswordRequest;
import cn.welsione.ascoder.common.user.web.dto.UpdateUserRequest;
import cn.welsione.ascoder.common.user.web.dto.UserDetail;
import cn.welsione.ascoder.common.user.web.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务，管理员进行用户 CRUD、启停、解锁、角色分配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final UserRoleJpaRepository userRoleRepository;
    private final RolePermissionJpaRepository rolePermissionRepository;
    private final PermissionJpaRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 列出所有用户（不含密码）。
     */
    public List<UserSummary> list() {
        return userRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 获取用户详情（含角色列表）。
     */
    public UserDetail get(Long id) {
        User user = findUser(id);
        Set<String> roles = getRoleCodesByUserId(id);
        return new UserDetail(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.isEnabled(), user.isAccountNonLocked(),
                user.isPasswordChanged(), user.getLastLoginAt(), user.getCreatedAt(), roles);
    }

    /**
     * 创建用户（管理员创建，不经过注册流程）。
     */
    @Transactional
    public UserDetail create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateException("用户名已存在: " + request.getUsername());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateException("邮箱已存在: " + request.getEmail());
        }
        validatePassword(request.getPassword());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPasswordChanged(true);
        user = userRepository.save(user);

        assignRoles(user.getId(), request.getRoleCodes());

        log.info("管理员创建用户: username={}", request.getUsername());
        Set<String> roles = getRoleCodesByUserId(user.getId());
        return new UserDetail(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.isEnabled(), user.isAccountNonLocked(),
                user.isPasswordChanged(), user.getLastLoginAt(), user.getCreatedAt(), roles);
    }

    /**
     * 更新用户基本信息。
     */
    @Transactional
    public UserDetail update(Long id, UpdateUserRequest request) {
        User user = findUser(id);
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().isBlank() ? null : request.getEmail());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        userRepository.save(user);
        return get(id);
    }

    /**
     * 重置用户密码。
     */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        User user = findUser(id);
        validatePassword(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChanged(true);
        userRepository.save(user);
        log.info("管理员重置用户密码: userId={}", id);
    }

    /**
     * 解锁用户账户。
     */
    @Transactional
    public void unlock(Long id) {
        User user = findUser(id);
        user.setAccountNonLocked(true);
        user.setLoginFailCount(0);
        userRepository.save(user);
        log.info("管理员解锁用户: userId={}", id);
    }

    /**
     * 分配用户角色。
     */
    @Transactional
    public UserDetail assignRoles(Long id, Set<String> roleCodes) {
        findUser(id);
        Set<Role> roles = roleCodes.stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new ValidationException("角色不存在: " + code)))
                .collect(Collectors.toSet());

        userRoleRepository.deleteByUserId(id);
        for (Role role : roles) {
            UserRole ur = new UserRole();
            ur.setUserId(id);
            ur.setRoleId(role.getId());
            userRoleRepository.save(ur);
        }
        log.info("分配用户角色: userId={}, roles={}", id, roleCodes);
        return get(id);
    }

    /**
     * 删除用户（内置 admin 不可删除）。
     */
    @Transactional
    public void delete(Long id) {
        User user = findUser(id);
        if ("admin".equals(user.getUsername())) {
            throw new InvalidStateException("默认管理员不可删除");
        }
        userRoleRepository.deleteByUserId(id);
        userRepository.delete(user);
        log.info("管理员删除用户: userId={}, username={}", id, user.getUsername());
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + id));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new ValidationException("密码长度不能少于 6 个字符");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("密码必须包含字母和数字");
        }
    }

    private Set<String> getRoleCodesByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId())
                        .map(Role::getCode).orElse(""))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }

    private UserSummary toSummary(User user) {
        Set<String> roles = getRoleCodesByUserId(user.getId());
        return new UserSummary(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.isEnabled(), user.isAccountNonLocked(),
                user.getLastLoginAt(), user.getCreatedAt(), roles);
    }
}
