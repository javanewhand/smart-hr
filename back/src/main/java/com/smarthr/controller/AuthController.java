/**
 * 认证控制器
 * 处理登录、注册、Token 刷新等 API
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.controller;

import com.smarthr.dto.ApiResponse;
import com.smarthr.dto.auth.AuthResponse;
import com.smarthr.dto.auth.ChangePasswordRequest;
import com.smarthr.dto.auth.LoginRequest;
import com.smarthr.dto.auth.RegisterRequest;
import com.smarthr.dto.auth.UpdateProfileRequest;
import com.smarthr.security.UserPrincipal;
import com.smarthr.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、注册、Token 刷新等接口")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 JWT Token")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for user: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ApiResponse.success(response, "登录成功");
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户并返回 JWT Token")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for user: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ApiResponse.success(response, "注册成功");
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 获取新的 Access Token")
    public ApiResponse<AuthResponse> refreshToken(@RequestParam String refreshToken) {
        log.info("Token refresh request");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ApiResponse.success(response, "Token 刷新成功");
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "获取当前登录用户的信息")
    public ApiResponse<AuthResponse.UserInfo> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ApiResponse.unauthorized("未登录或 Token 已失效");
        }
        log.info("Get current user: {}", userPrincipal.getUsername());
        AuthResponse.UserInfo userInfo = authService.getCurrentUser(userPrincipal);
        return ApiResponse.success(userInfo);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出（客户端清除 Token 即可）")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ApiResponse.unauthorized("未登录或 Token 已失效");
        }
        log.info("User logout: {}", userPrincipal.getUsername());
        return ApiResponse.successMessage("登出成功");
    }

    @PutMapping("/profile")
    @Operation(summary = "更新个人资料", description = "更新当前登录用户的用户名、邮箱、角色")
    public ApiResponse<AuthResponse.UserInfo> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Update profile for user: {}", userPrincipal.getUsername());
        AuthResponse.UserInfo updated = authService.updateProfile(userPrincipal.getId(), request);
        return ApiResponse.success(updated, "个人资料更新成功");
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "验证原密码后修改为新密码")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Change password for user: {}", userPrincipal.getUsername());
        authService.changePassword(userPrincipal.getId(), request);
        return ApiResponse.successMessage("密码修改成功");
    }
}
