/**
 * AI 模型自动配置类
 * 支持阿里云百炼 + OpenAI 的模型装配和注册
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.config;

import com.smarthr.service.ai.AliyunAdapter;
import com.smarthr.service.ai.ModelRegistry;
import com.smarthr.service.ai.OpenAIAdapter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AIModelAutoConfiguration {

    private final AIModelProperties properties;
    private final ModelRegistry modelRegistry;

    @PostConstruct
    public void init() {
        log.info("AI Model Auto Configuration initialized");
        log.info("Default model: {}", properties.getDefaultModel());
    }

    /**
     * 创建阿里云 ChatClient Bean
     * 使用 Spring AI Alibaba 自动配置的 ChatModel（dashscopeChatModel）
     */
    @Bean
    @ConditionalOnProperty(prefix = "smart-hr.ai.aliyun", name = "enabled", havingValue = "true")
    public ChatClient dashscopeChatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }



    /**
     * 创建 OpenAI ChatClient Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "smart-hr.ai.openai", name = "enabled", havingValue = "true")
    public ChatClient openAiChatClient(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 阿里云百炼模型适配器
     * 使用 Spring AI Alibaba 自动注入的 ChatModel 和 EmbeddingModel
     */
    @Bean
    @ConditionalOnProperty(prefix = "smart-hr.ai.aliyun", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AliyunAdapter aliyunAdapter(@Qualifier("dashscopeChatClient") ChatClient chatClient,
                                       @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel) {
        AliyunAdapter adapter = new AliyunAdapter(properties.getAliyun(), chatClient, embeddingModel);
        modelRegistry.register(adapter);
        log.info("Aliyun adapter registered successfully");
        return adapter;
    }
    /**
     * OpenAI 模型适配器
     */
    @Bean
    @ConditionalOnProperty(prefix = "smart-hr.ai.openai", name = "enabled", havingValue = "true")
    public OpenAIAdapter openAIAdapter(@Qualifier("openAiChatClient") ChatClient chatClient,
                                       @Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        OpenAIAdapter adapter = new OpenAIAdapter(properties.getOpenai(), chatClient, embeddingModel);
        modelRegistry.register(adapter);
        log.info("OpenAI adapter registered successfully");
        return adapter;
    }

    /**
     * 默认 EmbeddingModel，根据 default-model 选择；解决多模型同时启用时的注入歧义。
     */
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(@Qualifier("dashscopeEmbeddingModel") ObjectProvider<EmbeddingModel> dashscopeEmbeddingModel,
                                                @Qualifier("openAiEmbeddingModel") ObjectProvider<EmbeddingModel> openAiEmbeddingModel) {
        try {
            EmbeddingModel dashscope = dashscopeEmbeddingModel.getIfAvailable();
            if (dashscope != null) {
                return dashscope;
            }
        } catch (Exception e) {
            log.warn("DashScope EmbeddingModel unavailable: {}", e.getMessage());
        }
        try {
            EmbeddingModel openai = openAiEmbeddingModel.getIfAvailable();
            if (openai != null) {
                return openai;
            }
        } catch (Exception e) {
            log.warn("OpenAI EmbeddingModel unavailable: {}", e.getMessage());
        }
        throw new IllegalStateException("No EmbeddingModel available. Check DashScope/OpenAI configuration.");
    }
}
