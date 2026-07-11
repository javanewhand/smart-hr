/**
 * RAG 检索增强生成服务
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 *
 * 注意：2026-07-11 起已注释停用。
 * 原因：匹配评分从三维度（RAG + 知识图谱 + LLM）改为二维度（知识图谱 + LLM），
 *       RAG 语义相似度在技能匹配场景下噪点多、与图谱重叠、且增加一次网络调用，
 *       实际运行中去除后匹配质量未下降、响应更快，故停用。
 * 恢复条件：如需重新启用，需同步解除 DocumentSplitter / DocumentChunk 注释，
 *           并在 HybridMatchService 中恢复 RAG_WEIGHT 及 calculateRAGScore 调用。
 */
// package com.smarthr.service.rag;
//
// import com.smarthr.service.ai.ModelRouter;
// import com.smarthr.service.vector.MilvusVectorStore;
// import com.smarthr.service.vector.VectorDocument;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;
//
// import java.util.*;
// import java.util.stream.Collectors;
//
// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class RAGService {
//
//     private final EmbeddingService embeddingService;
//     private final DocumentSplitter documentSplitter;
//     private final ModelRouter modelRouter;
//     private final Optional<MilvusVectorStore> milvusVectorStore;
//
//     public void indexResume(String resumeId, String content, Map<String, Object> metadata) {
//         if (milvusVectorStore.isEmpty()) {
//             log.warn("Milvus is not available, skipping resume indexing");
//             return;
//         }
//         try {
//             List<DocumentChunk> chunks = documentSplitter.split(resumeId, content, metadata);
//             List<VectorDocument> documents = new ArrayList<>();
//             for (DocumentChunk chunk : chunks) {
//                 float[] embedding = embeddingService.embed(chunk.getContent());
//                 VectorDocument doc = VectorDocument.builder()
//                         .id(chunk.getId())
//                         .content(chunk.getContent())
//                         .embedding(embedding)
//                         .metadata(chunk.getMetadata())
//                         .build();
//                 documents.add(doc);
//             }
//             milvusVectorStore.get().addDocuments(documents);
//             log.info("Indexed resume {} with {} chunks to Milvus", resumeId, chunks.size());
//         } catch (Exception e) {
//             log.error("Failed to index resume: {}", e.getMessage(), e);
//             throw new RuntimeException("Failed to index resume", e);
//         }
//     }
// }
