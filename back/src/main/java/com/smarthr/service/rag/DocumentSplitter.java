/**
 * 文档分块器
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 *
 * 注意：2026-07-11 起已注释停用。
 * 原因：唯一调用方 RAGService 已停用，此分块器无其他消费者。
 *      分块原用于将长简历按 500 字 + 50 字重叠切块后分别向量化存入 Milvus，
 *      但当前 ResumeService 已将整份简历不做切分直接向量化，故此功能暂无使用场景。
 * 恢复条件：如需恢复 RAG 管道，解除 RAGService 注释后此文件同步解除即可。
 */
// package com.smarthr.service.rag;
//
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Component;
//
// import java.util.*;
// import java.util.regex.Pattern;
//
// @Slf4j
// @Component
// public class DocumentSplitter {
//
//     private static final int DEFAULT_CHUNK_SIZE = 500;
//     private static final int DEFAULT_OVERLAP_SIZE = 50;
//     private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\n+");
//     private static final Pattern SENTENCE_PATTERN = Pattern.compile("[。！？.!?]+");
//
//     public List<DocumentChunk> split(String documentId, String content, Map<String, Object> metadata) {
//         return split(documentId, content, metadata, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
//     }
//
//     public List<DocumentChunk> split(String documentId, String content, Map<String, Object> metadata,
//                                      int chunkSize, int overlapSize) {
//         if (content == null || content.isEmpty()) return Collections.emptyList();
//         List<DocumentChunk> chunks = new ArrayList<>();
//         String[] paragraphs = PARAGRAPH_PATTERN.split(content);
//         StringBuilder currentChunk = new StringBuilder();
//         int chunkIndex = 0;
//         for (String paragraph : paragraphs) {
//             paragraph = paragraph.trim();
//             if (paragraph.isEmpty()) continue;
//             if (currentChunk.length() + paragraph.length() > chunkSize && currentChunk.length() > 0) {
//                 chunks.add(createChunk(documentId, currentChunk.toString(), chunkIndex++, metadata));
//                 String overlap = getOverlap(currentChunk.toString(), overlapSize);
//                 currentChunk = new StringBuilder(overlap);
//             }
//             if (paragraph.length() > chunkSize) {
//                 if (currentChunk.length() > 0) {
//                     chunks.add(createChunk(documentId, currentChunk.toString(), chunkIndex++, metadata));
//                     currentChunk = new StringBuilder();
//                 }
//                 List<DocumentChunk> subChunks = splitLargeParagraph(documentId, paragraph, metadata, chunkIndex, chunkSize, overlapSize);
//                 chunks.addAll(subChunks);
//                 chunkIndex += subChunks.size();
//             } else {
//                 if (currentChunk.length() > 0) currentChunk.append("\n\n");
//                 currentChunk.append(paragraph);
//             }
//         }
//         if (currentChunk.length() > 0) chunks.add(createChunk(documentId, currentChunk.toString(), chunkIndex, metadata));
//         log.debug("Document {} split into {} chunks", documentId, chunks.size());
//         return chunks;
//     }
//
//     private List<DocumentChunk> splitLargeParagraph(String documentId, String paragraph, Map<String, Object> metadata, int startIndex, int chunkSize, int overlapSize) { ... }
//     private String getOverlap(String text, int overlapSize) { ... }
//     private DocumentChunk createChunk(String documentId, String content, int index, Map<String, Object> metadata) { ... }
// }
