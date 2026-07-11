/**
 * AI 模型适配器接口
 * 定义所有 AI 模型适配器必须实现的方法
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service.ai;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface AIModelAdapter {

    /**
     * 获取模型唯一标识
     */
    String getModelId();

    /**
     * 获取模型显示名称
     */
    String getModelName();

    /**
     * 判断模型是否可用
     */
    boolean isEnabled();

    /**
     * 同步对话
     *
     * @param messages 对话消息列表
     * @return 模型响应
     */
    String chat(List<Map<String, String>> messages);

    /**
     * 流式对话
     *
     * @param messages 对话消息列表
     * @return 流式响应
     */
    Flux<String> stream(List<Map<String, String>> messages);

    /**
     * 文本向量化（两个方法在embedding实现，与adaptor类进行了解耦）
     *
     * @param text 待向量化的文本
     * @return 向量数组
     */
    /*float[] embed(String text);

    *//**
     * 批量文本向量化
     *
     * @param texts 待向量化的文本列表
     * @return 向量数组列表
     *//*
    List<float[]> embedBatch(List<String> texts);*/
}

