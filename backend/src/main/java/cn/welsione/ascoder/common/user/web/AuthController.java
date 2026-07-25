package cn.welsione.ascoder.common.user.web;

import cn.welsione.ascoder.common.user.AuthService;
import cn.welsione.ascoder.common.user.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 REST 接口，提供注册、登录、Token 刷新、登出等功能。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 检查系统是否已初始化（是否已有用户）。
     */
    @GetMapping("/init-status")
    public InitStatusResponse initStatus() {
        return authService.getInitStatus();
    }

    /**
     * 用户注册。首个用户自动获得 ADMIN 角色。
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * 用户登录，返回 Access Token + Refresh Token。
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 刷新 Token（Rotation：吊销旧 Token，发放新 Token）。
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 用户登出，吊销 Refresh Token。
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }

    /**
     * 获取当前用户信息。
     */
    @GetMapping("/me")
    public AuthResponse.UserInfo me() {
        return authService.me();
    }
}
