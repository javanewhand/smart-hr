/**
 * PgVector 向量存储实现
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 *
 * 注意：2026-07-11 起已注释停用。
 * 原因：项目使用 Milvus 作为唯一向量存储（MilvusVectorStore + QuestionBankVectorStore），
 *       PgVector 自始至终未被集成，Spring AI pgvector 依赖也未引入，
 *       且 application.yml 中已显式 spring.ai.vectorstore.pgvector.enabled=false。
 * 恢复条件：若未来有轻量部署场景不想起 Milvus 容器，可引入 pgvector 依赖并启用以替代。
 */
// package com.smarthr.service.vector;
//
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.document.Document;
// import org.springframework.ai.vectorstore.SearchRequest;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
// import org.springframework.stereotype.Service;
//
// import java.util.*;
// import java.util.stream.Collectors;
//
// @Slf4j
// @Service
// @RequiredArgsConstructor
// @ConditionalOnClass(name = "org.springframework.ai.vectorstore.pgvector.PgVectorStore")
// public class PgVectorStore implements VectorStore {
//
//     private static final String STORE_ID = "pgvector-jd";
//     private final org.springframework.ai.vectorstore.VectorStore springVectorStore;
//
//     @Override public String getStoreId() { return STORE_ID; }
//     @Override public void addDocument(VectorDocument document) { addDocuments(Collections.singletonList(document)); }
//     @Override public void addDocuments(List<VectorDocument> documents) { ... }
//     @Override public void deleteDocument(String id) { deleteDocuments(Collections.singletonList(id)); }
//     @Override public void deleteDocuments(List<String> ids) { ... }
//     @Override public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK) { return similaritySearch(queryEmbedding, topK, null); }
//     @Override public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK, String filter) { return Collections.emptyList(); }
//     @Override public VectorDocument getDocument(String id) { return null; }
//     @Override public boolean exists(String id) { return false; }
//     @Override public long count() { return 0; }
//
//     public List<VectorDocument> similaritySearchByText(String query, int topK) { return similaritySearchByText(query, topK, null); }
//     public List<VectorDocument> similaritySearchByText(String query, int topK, String filterExpression) { ... }
// }
