/**
 * AI 模型路由器
 * 支持按用户偏好或默认模型进行路由
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service.ai;

import com.smarthr.config.AIModelProperties;
import com.smarthr.entity.User;
import com.smarthr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRouter {

    private static final String USER_MODEL_PREFIX = "user:model:";
    private static final Duration USER_MODEL_TTL = Duration.ofHours(24);

    private final ModelRegistry modelRegistry;
    private final AIModelProperties aiModelProperties;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取当前模型适配器（无用户上下文，走默认模型）
     */
    public AIModelAdapter route() {
        return getDefaultAdapter();
    }

    /**
     * 获取指定用户的模型适配器
     */
    public AIModelAdapter route(Long userId) {
        return resolveAdapter(userId);
    }

    /**
     * 获取当前模型信息
     */
    public Map<String, Object> getCurrentModelInfo() {
        AIModelAdapter adapter = resolveAdapter(null);
        return Map.of(
                "modelId", adapter.getModelId(),
                "modelName", adapter.getModelName(),
                "enabled", adapter.isEnabled()
        );
    }

    /**
     * 获取指定用户的当前模型信息
     */
    public Map<String, Object> getCurrentModelInfo(Long userId) {
        AIModelAdapter adapter = resolveAdapter(userId);
        return Map.of(
                "modelId", adapter.getModelId(),
                "modelName", adapter.getModelName(),
                "enabled", adapter.isEnabled()
        );
    }

    /**
     * 获取所有可用的模型列表
     */
    public List<Map<String, Object>> getAvailableModels() {
        String currentId = getDefaultAdapter().getModelId();
        return buildModelList(currentId);
    }

    /**
     * 获取指定用户可用模型列表，并标记当前模型
     */
    public List<Map<String, Object>> getAvailableModels(Long userId) {
        String currentId = resolveAdapter(userId).getModelId();
        return buildModelList(currentId);
    }

    private List<Map<String, Object>> buildModelList(String currentId) {
        return modelRegistry.getEnabled().stream()
                .sorted(Comparator.comparing(AIModelAdapter::getModelId))
                .map(adapter -> Map.<String, Object>of(
                        "modelId", adapter.getModelId(),
                        "modelName", adapter.getModelName(),
                        "enabled", adapter.isEnabled(),
                        "current", adapter.getModelId().equals(currentId)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取默认模型适配器
     */
    private AIModelAdapter getDefaultAdapter() {
        // 优先使用默认配置（通常为 aliyun），不可用则尝试 fallback 顺序：aliyun -> openai
        String defaultModelId = aiModelProperties.getDefaultModel();
        if (defaultModelId != null) {
            var adapter = modelRegistry.get(defaultModelId).filter(AIModelAdapter::isEnabled);
            if (adapter.isPresent()) {
                return adapter.get();
            }
        }

        // 常规优先级：阿里云优先，其次 OpenAI
        var aliyun = modelRegistry.get("aliyun").filter(AIModelAdapter::isEnabled);
        if (aliyun.isPresent()) {
            return aliyun.get();
        }
        var openai = modelRegistry.get("openai").filter(AIModelAdapter::isEnabled);
        if (openai.isPresent()) {
            return openai.get();
        }

        throw new IllegalStateException("No available AI model. Please check DashScope/OpenAI configuration.");
    }

    /**
     * 根据用户偏好或默认模型解析适配器
     */
    // 每次 LLM 调用都会触发，缓存用户偏好可消除无意义的 DB 查询
    private AIModelAdapter resolveAdapter(Long userId) {
        if (userId != null) {
            String cacheKey = USER_MODEL_PREFIX + userId;
            // 先查 Redis，命中则跳过 DB 查询
            String preferred = null;
            try {
                preferred = redisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to read user model cache: {}", e.getMessage());
            }

            // 未命中则查 DB 并回写缓存
            if (preferred == null) {
                preferred = userRepository.findById(userId)
                        .map(User::getPreferredModel)
                        .orElse(null);
                if (preferred != null) {
                    try {
                        redisTemplate.opsForValue().set(cacheKey, preferred, USER_MODEL_TTL);
                    } catch (Exception e) {
                        log.warn("Failed to write user model cache: {}", e.getMessage());
                    }
                }
            }

            if (preferred != null && modelRegistry.isEnabled(preferred)) {
                return modelRegistry.get(preferred).orElseGet(this::getDefaultAdapter);
            }
        }
        return getDefaultAdapter();
    }

    /**
     * 清除用户模型缓存，切换模型后立即调用以保证一致性
     */
    public void evictUserModelCache(Long userId) {
        try {
            redisTemplate.delete(USER_MODEL_PREFIX + userId);
            log.debug("Evicted model cache for user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to evict user model cache: {}", e.getMessage());
        }
    }
}
