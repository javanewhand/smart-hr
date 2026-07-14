/**
 * 认证服务
 * 处理登录、注册、Token 刷新等业务逻辑
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service;

import com.smarthr.config.JwtProperties;
import com.smarthr.dto.auth.AuthResponse;
import com.smarthr.dto.auth.ChangePasswordRequest;
import com.smarthr.dto.auth.LoginRequest;
import com.smarthr.dto.auth.RegisterRequest;
import com.smarthr.dto.auth.UpdateProfileRequest;
import com.smarthr.entity.User;
import com.smarthr.repository.UserRepository;
import com.smarthr.security.JwtTokenProvider;
import com.smarthr.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * 用户登录
     */
    public AuthResponse login(LoginRequest request) {
        log.info("User login attempt: {}", request.getUsername());

        // 认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // 生成 Token
        String accessToken = jwtTokenProvider.generateToken(userPrincipal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userPrincipal);

        log.info("User logged in successfully: {}", request.getUsername());

        return buildAuthResponse(userPrincipal, accessToken, refreshToken);
    }

    /**
     * 用户注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("User registration attempt: {}", request.getUsername());

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在: " + request.getUsername());
        }

        // 检查邮箱是否已存在
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被使用: " + request.getEmail());
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(User.UserRole.valueOf(request.getRole()))
                .preferredModel("aliyun")
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", request.getUsername());

        // 创建 UserPrincipal 并生成 Token
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String accessToken = jwtTokenProvider.generateToken(userPrincipal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userPrincipal);

        return buildAuthResponse(userPrincipal, accessToken, refreshToken);
    }

    /**
     * 刷新 Token
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Token refresh attempt");

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("无效的刷新令牌");
        }

        String username = jwtTokenProvider.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String newAccessToken = jwtTokenProvider.generateToken(userPrincipal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userPrincipal);

        log.info("Token refreshed successfully for user: {}", username);

        return buildAuthResponse(userPrincipal, newAccessToken, newRefreshToken);
    }

    /**
     * 获取当前用户信息
     */
    public AuthResponse.UserInfo getCurrentUser(UserPrincipal userPrincipal) {
        return AuthResponse.UserInfo.builder()
                .id(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .role(userPrincipal.getRole())
                .preferredModel(userPrincipal.getPreferredModel())
                .build();
    }

    /**
     * 更新用户资料
     */
    @Transactional
    public AuthResponse.UserInfo updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!user.getUsername().equals(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已被使用: " + request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().isEmpty()
                && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被使用: " + request.getEmail());
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        if (request.getRole() != null && !request.getRole().isEmpty()) {
            user.setRole(User.UserRole.valueOf(request.getRole()));
        }

        user = userRepository.save(user);
        log.info("User profile updated: {}", user.getUsername());

        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .preferredModel(user.getPreferredModel())
                .build();
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码不正确");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getUsername());
    }

    /**
     * 构建认证响应
     */
    private AuthResponse buildAuthResponse(UserPrincipal userPrincipal, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(userPrincipal.getId())
                        .username(userPrincipal.getUsername())
                        .email(userPrincipal.getEmail())
                        .role(userPrincipal.getRole())
                        .preferredModel(userPrincipal.getPreferredModel())
                        .build())
                .build();
    }
}


