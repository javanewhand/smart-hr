/**
 * PgVector 向量存储实现
 * 用于存储和检索岗位 JD 向量
 * 基于 Spring AI 的 VectorStore 封装
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.ai.vectorstore.pgvector.PgVectorStore")
public class PgVectorStore implements VectorStore {

    private static final String STORE_ID = "pgvector-jd";

    private final org.springframework.ai.vectorstore.VectorStore springVectorStore;

    @Override
    public String getStoreId() {
        return STORE_ID;
    }

    @Override
    public void addDocument(VectorDocument document) {
        addDocuments(Collections.singletonList(document));
    }

    @Override
    public void addDocuments(List<VectorDocument> documents) {
        try {
            List<Document> springDocs = documents.stream()
                    .map(this::toSpringDocument)
                    .collect(Collectors.toList());
            
            springVectorStore.add(springDocs);
            log.debug("Added {} documents to PgVector", documents.size());
            
        } catch (Exception e) {
            log.error("Failed to add documents to PgVector: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add documents to PgVector", e);
        }
    }

    @Override
    public void deleteDocument(String id) {
        deleteDocuments(Collections.singletonList(id));
    }

    @Override
    public void deleteDocuments(List<String> ids) {
        try {
            springVectorStore.delete(ids);
            log.debug("Deleted {} documents from PgVector", ids.size());
            
        } catch (Exception e) {
            log.warn("Failed to delete documents from PgVector: {}", e.getMessage());
            // 不抛出，避免业务流程因向量侧失败而中断
        }
    }

    @Override
    public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK) {
        return similaritySearch(queryEmbedding, topK, null);
    }

    @Override
    public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK, String filter) {
        // Spring AI VectorStore 使用文本查询，这里返回空列表
        log.warn("PgVector similarity search with raw embedding is not directly supported, use text query instead");
        return Collections.emptyList();
    }

    /**
     * 使用文本进行相似度搜索（推荐方式）
     */
    public List<VectorDocument> similaritySearchByText(String query, int topK) {
        return similaritySearchByText(query, topK, null);
    }

    /**
     * 使用文本进行相似度搜索（带过滤条件）
     */
    public List<VectorDocument> similaritySearchByText(String query, int topK, String filterExpression) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(topK);
            
            if (filterExpression != null && !filterExpression.isEmpty()) {
                builder.filterExpression(filterExpression);
            }
            
            List<Document> results = springVectorStore.similaritySearch(builder.build());
            
            List<VectorDocument> vectorDocs = results.stream()
                    .map(this::fromSpringDocument)
                    .collect(Collectors.toList());
            
            log.debug("Similarity search returned {} results from PgVector", vectorDocs.size());
            return vectorDocs;
            
        } catch (Exception e) {
            log.error("Failed to search in PgVector: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search in PgVector", e);
        }
    }

    @Override
    public VectorDocument getDocument(String id) {
        log.warn("Get document by ID is not directly supported by Spring AI VectorStore");
        return null;
    }

    @Override
    public boolean exists(String id) {
        return getDocument(id) != null;
    }

    @Override
    public long count() {
        log.warn("Count is not directly supported by Spring AI VectorStore");
        return 0;
    }

    /**
     * 转换为 Spring AI Document
     */
    private Document toSpringDocument(VectorDocument doc) {
        return new Document(doc.getId(), doc.getContent(), doc.getMetadata() != null ? doc.getMetadata() : new HashMap<>());
    }

    /**
     * 从 Spring AI Document 转换
     */
    private VectorDocument fromSpringDocument(Document doc) {
        return VectorDocument.builder()
                .id(doc.getId())
                .content(doc.getText())
                .metadata(doc.getMetadata())
                .score(doc.getScore() != null ? doc.getScore().floatValue() : null)
                .build();
    }
}
