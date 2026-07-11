/**
 * OpenAI 模型适配器
 * 封装 Chat/Embedding 能力，接入 ModelRegistry
 */
package com.smarthr.service.ai;

import com.smarthr.config.AIModelProperties.OpenAIConfig;
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
public class OpenAIAdapter implements AIModelAdapter {

    private static final String MODEL_ID = "openai";
    private static final String MODEL_NAME = "OpenAI";

    private final OpenAIConfig config;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public OpenAIAdapter(OpenAIConfig config, ChatClient chatClient, EmbeddingModel embeddingModel) {
        this.config = config;
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
        log.info("OpenAIAdapter initialized (enabled: {})", config.isEnabled());
    }

    @Override
    public String getModelId() {
        return MODEL_ID;
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * 检测使用的大模型是否可用
     * @return
     */
    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * 注意，这里传递的LIST<MAP<string,string>>传递的是前端整个对话历史，不是单次对话，因此必须转换格式
     * @param messages 对话消息列表
     * @return
     */
    @Override
    public String chat(List<Map<String, String>> messages) {
        if (!isEnabled()) {
            throw new IllegalStateException("OpenAI model is not enabled");
        }
        //由消息列表构建prompt,其中Prompt类里的prompt是整个上下文
        List<Message> aiMessages = convertMessages(messages);
        Prompt prompt = new Prompt(aiMessages);

        String response = chatClient.prompt(prompt).call().content();
        log.debug("OpenAI chat completed, response length: {}", response != null ? response.length() : 0);
        return response;
    }

    /**
     * 该方法为结果流式输出，但并没有被使用
     * @param messages 对话消息列表
     * @return
     */
    @Override
    public Flux<String> stream(List<Map<String, String>> messages) {
        if (!isEnabled()) {
            return Flux.error(new IllegalStateException("OpenAI model is not enabled"));
        }
        List<Message> aiMessages = convertMessages(messages);
        Prompt prompt = new Prompt(aiMessages);
        return chatClient.prompt(prompt).stream().content();
    }

 /*   *//**
     * 文本向量化，把文字转换为AI能理解的数字向量，下面的方法为批量处理
     * @param text 待向量化的文本
     * @return
     *//*
    @Override
    public float[] embed(String text) {
        if (!isEnabled()) {
            throw new IllegalStateException("OpenAI model is not enabled");
        }
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] embedding = response.getResult().getOutput();
        log.debug("OpenAI embedding completed, dimension: {}", embedding.length);
        return embedding;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled()) {
            throw new IllegalStateException("OpenAI model is not enabled");
        }
        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
        List<float[]> embeddings = new ArrayList<>();
        response.getResults().forEach(r -> embeddings.add(r.getOutput()));
        log.debug("OpenAI batch embedding completed, count: {}", embeddings.size());
        return embeddings;
    }*/

    /**
     *
     * 该方法用于将业务层通用的messages转换为springAI看得懂的集合
     * @return
     */
    private List<Message> convertMessages(List<Map<String, String>> messages) {
        List<Message> aiMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            if (role == null || content == null) {
                continue;
            }
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
