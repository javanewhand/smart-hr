/**
 * 阿里云百炼模型适配器
 * 使用 Spring AI Alibaba 框架集成
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service.ai;

import com.smarthr.config.AIModelProperties.AliyunConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class AliyunAdapter implements AIModelAdapter {

    private static final String MODEL_ID = "aliyun";
    private static final String MODEL_NAME = "阿里云百炼";

    private final AliyunConfig config;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public AliyunAdapter(AliyunConfig config, ChatClient chatClient, EmbeddingModel embeddingModel) {
        this.config = config;
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        log.info("AliyunAdapter initialized (enabled: {})", config.isEnabled());
    }

    @Override
    public String getModelId() {
        return MODEL_ID;
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        if (!isEnabled()) {
            throw new IllegalStateException("Aliyun model is not enabled");
        }

        try {
            List<Message> aiMessages = convertMessages(messages);
            Prompt prompt = new Prompt(aiMessages);
            
            String response = chatClient.prompt(prompt)
                    .call()
                    .content();
            
            log.debug("Chat completed, response length: {}", response != null ? response.length() : 0);
            return response;
        } catch (Exception e) {
            log.error("Chat error with Aliyun: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to chat with Aliyun model", e);
        }
    }

    @Override
    public Flux<String> stream(List<Map<String, String>> messages) {
        if (!isEnabled()) {
            return Flux.error(new IllegalStateException("Aliyun model is not enabled"));
        }

        try {
            List<Message> aiMessages = convertMessages(messages);
            Prompt prompt = new Prompt(aiMessages);
            
            return chatClient.prompt(prompt)
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("Stream error with Aliyun: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("Failed to stream with Aliyun model", e));
        }
    }

    /*@Override
    public float[] embed(String text) {
        if (!isEnabled()) {
            throw new IllegalStateException("Aliyun model is not enabled");
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            float[] embedding = response.getResult().getOutput();
            log.debug("Embedding completed, dimension: {}", embedding.length);
            return embedding;
        } catch (Exception e) {
            log.error("Embedding error with Aliyun: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to embed with Aliyun model", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled()) {
            throw new IllegalStateException("Aliyun model is not enabled");
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            List<float[]> embeddings = new ArrayList<>();
            response.getResults().forEach(result -> embeddings.add(result.getOutput()));
            log.debug("Batch embedding completed, count: {}", embeddings.size());
            return embeddings;
        } catch (Exception e) {
            log.error("Batch embedding error with Aliyun: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to batch embed with Aliyun model", e);
        }
    }*/

    /**
     * 将消息 Map 转换为 Spring AI Message 对象
     */
    private List<Message> convertMessages(List<Map<String, String>> messages) {
        List<Message> aiMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            
            switch (role.toLowerCase()) {
                case "system" -> aiMessages.add(new SystemMessage(content));
                case "user" -> aiMessages.add(new UserMessage(content));
                case "assistant" -> aiMessages.add(new AssistantMessage(content));
                default -> log.warn("Unknown message role: {}", role);
            }
        }
        return aiMessages;
    }
}
