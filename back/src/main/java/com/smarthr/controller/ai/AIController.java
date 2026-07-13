/**
 * AI 模型管理控制器
 *
 * @author QinFeng Luo
 * @date 2026/01/15
 */
package com.smarthr.controller.ai;

import com.smarthr.dto.ApiResponse;
import com.smarthr.dto.ai.SwitchModelRequest;
import com.smarthr.entity.User;
import com.smarthr.exception.BusinessException;
import com.smarthr.repository.UserRepository;
import com.smarthr.security.UserPrincipal;
import com.smarthr.service.ai.ModelRegistry;
import com.smarthr.service.ai.ModelRouter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final ModelRouter modelRouter;
    private final ModelRegistry modelRegistry;
    private final UserRepository userRepository;

    /**
     * 获取可用模型列表
     */
    @GetMapping("/models")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getModels(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Map<String, Object>> models = modelRouter.getAvailableModels(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(models));
    }

    /**
     * 获取当前模型信息
     */
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentModel(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> current = modelRouter.getCurrentModelInfo(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(current));
    }

    /**
     * 切换模型
     */
    @PostMapping("/switch")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> switchModel(
            @Valid @RequestBody SwitchModelRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        String modelId = request.getModelId();
        log.info("User {} switching model to {}", userPrincipal.getUsername(), modelId);

        if (!modelRegistry.isEnabled(modelId)) {
            throw new BusinessException("指定的模型不可用或不存在: " + modelId);
        }

        // 记录用户偏好模型，后续可用于路由
        userRepository.findById(userPrincipal.getId())
                .ifPresent(user -> {
                    user.setPreferredModel(modelId);
                    userRepository.save(user);
                });

        // 切换模型后清除 Redis 缓存，避免后续路由读到旧模型偏好
        modelRouter.evictUserModelCache(userPrincipal.getId());

        Map<String, Object> result = Map.of(
                "success", true,
                "modelId", modelId,
                "message", "模型切换成功"
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
