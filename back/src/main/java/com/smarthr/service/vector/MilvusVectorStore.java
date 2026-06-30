/**
 * Milvus 向量存储实现
 * 用于存储和检索简历向量
 *
 * @author QinFeng Luo
 * @date 2026/01/09
 */
package com.smarthr.service.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smarthr.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
@ConditionalOnBean(MilvusClientV2.class)
public class MilvusVectorStore implements VectorStore {

    private static final String STORE_ID = "resume";
    private static final String ID_FIELD = "id";
    private static final String CONTENT_FIELD = "content";
    private static final String VECTOR_FIELD = "embedding";
    private static final String METADATA_FIELD = "metadata";

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;
    private final Gson gson = new Gson();

    public MilvusVectorStore(MilvusClientV2 milvusClient, MilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        createCollectionIfNotExists(false);
    }

    /**
     * 创建集合（如不存在）并尝试加载
     *
     * @param forceReload 是否强制 reload 已存在的集合
     */
    private void createCollectionIfNotExists(boolean forceReload) {
        try {
            String collectionName = properties.getCollectionName();
            
            // 检查集合是否存在
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            
            boolean exists = milvusClient.hasCollection(hasReq);
            
            if (exists) {
                milvusClient.dropCollection(io.milvus.v2.service.collection.request.DropCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());
                log.info("Dropped existing collection {} for recreation", collectionName);
            }

            log.info("Creating Milvus collection: {}", collectionName);
                
                // 创建 Schema
                CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                        .build();
                
                // ID 字段
                schema.addField(AddFieldReq.builder()
                        .fieldName(ID_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(128)
                        .isPrimaryKey(true)
                        .build());
                
                // 内容字段
                schema.addField(AddFieldReq.builder()
                        .fieldName(CONTENT_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(65535)
                        .build());
                
                // 向量字段
                schema.addField(AddFieldReq.builder()
                        .fieldName(VECTOR_FIELD)
                        .dataType(DataType.FloatVector)
                        .dimension(properties.getEmbeddingDimension())
                        .build());
                
                // 元数据字段
                schema.addField(AddFieldReq.builder()
                        .fieldName(METADATA_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(65535)
                        .build());
                
                // 创建索引
                IndexParam indexParam = IndexParam.builder()
                        .fieldName(VECTOR_FIELD)
                        .indexType(IndexParam.IndexType.IVF_FLAT)
                        .metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("nlist", properties.getNlist()))
                        .build();
                
                // 创建集合
                CreateCollectionReq createReq = CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .collectionSchema(schema)
                        .indexParams(Collections.singletonList(indexParam))
                        .build();
                
                milvusClient.createCollection(createReq);
                log.info("Milvus collection created: {}", collectionName);

            LoadCollectionReq loadReq = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            try {
                milvusClient.loadCollection(loadReq);
                log.info("Milvus collection loaded: {}", collectionName);
            } catch (Exception loadEx) {
                log.warn("Load Milvus collection {} failed (non-fatal): {}", collectionName, loadEx.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to initialize Milvus collection: {}", e.getMessage(), e);
        }
    }

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
            List<JsonObject> data = new ArrayList<>();
            
            for (VectorDocument doc : documents) {
                JsonObject row = new JsonObject();
                row.addProperty(ID_FIELD, doc.getId());
                row.addProperty(CONTENT_FIELD, doc.getContent());
                row.add(VECTOR_FIELD, gson.toJsonTree(toFloatList(doc.getEmbedding())));
                row.addProperty(METADATA_FIELD, gson.toJson(doc.getMetadata()));
                data.add(row);
            }
            
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(properties.getCollectionName())
                    .data(data)
                    .build();
            
            InsertResp response = milvusClient.insert(insertReq);
            log.debug("Inserted {} documents into Milvus, insertCnt: {}", documents.size(), response.getInsertCnt());
            
        } catch (Exception e) {
            log.error("Failed to add documents to Milvus: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add documents to Milvus", e);
        }
    }

    @Override
    public void deleteDocument(String id) {
        deleteDocuments(Collections.singletonList(id));
    }

    @Override
    public void deleteDocuments(List<String> ids) {
        try {
            // 使用过滤表达式删除
            String filter = ID_FIELD + " in [\"" + String.join("\",\"", ids) + "\"]";
            
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(properties.getCollectionName())
                    .filter(filter)
                    .build();
            
            milvusClient.delete(deleteReq);
            log.debug("Deleted {} documents from Milvus", ids.size());
            
        } catch (Exception e) {
            log.error("Failed to delete documents from Milvus: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete documents from Milvus", e);
        }
    }

    @Override
    public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK) {
        return similaritySearch(queryEmbedding, topK, null);
    }

    @Override
    public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK, String filter) {
        try {
            io.milvus.v2.service.vector.request.data.FloatVec queryVector = 
                    new io.milvus.v2.service.vector.request.data.FloatVec(toFloatList(queryEmbedding));
            
            SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                    .collectionName(properties.getCollectionName())
                    .data(Collections.singletonList(queryVector))
                    .topK(topK)
                    .outputFields(Arrays.asList(ID_FIELD, CONTENT_FIELD, METADATA_FIELD))
                    .searchParams(Map.of("nprobe", properties.getNprobe()));
            
            if (filter != null && !filter.isEmpty()) {
                builder.filter(filter);
            }
            
            SearchResp response = milvusClient.search(builder.build());
            
            List<VectorDocument> results = new ArrayList<>();
            List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();
            
            if (!searchResults.isEmpty()) {
                for (SearchResp.SearchResult result : searchResults.get(0)) {
                    Map<String, Object> entity = result.getEntity();
                    
                    VectorDocument doc = VectorDocument.builder()
                            .id((String) entity.get(ID_FIELD))
                            .content((String) entity.get(CONTENT_FIELD))
                            .score(result.getScore())
                            .metadata(parseMetadata((String) entity.get(METADATA_FIELD)))
                            .build();
                    
                    results.add(doc);
                }
            }
            
            log.debug("Similarity search returned {} results from Milvus", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to search in Milvus: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search in Milvus", e);
        }
    }

    @Override
    public VectorDocument getDocument(String id) {
        try {
            GetReq getReq = GetReq.builder()
                    .collectionName(properties.getCollectionName())
                    .ids(Collections.singletonList(id))
                    .outputFields(Arrays.asList(ID_FIELD, CONTENT_FIELD, METADATA_FIELD))
                    .build();
            
            GetResp response = milvusClient.get(getReq);
            List<QueryResp.QueryResult> results = response.getGetResults();
            
            if (results.isEmpty()) {
                return null;
            }
            
            Map<String, Object> entity = results.get(0).getEntity();
            return VectorDocument.builder()
                    .id((String) entity.get(ID_FIELD))
                    .content((String) entity.get(CONTENT_FIELD))
                    .metadata(parseMetadata((String) entity.get(METADATA_FIELD)))
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to get document from Milvus: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean exists(String id) {
        return getDocument(id) != null;
    }

    @Override
    public long count() {
        try {
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(properties.getCollectionName())
                    .filter("")
                    .build();
            
            QueryResp response = milvusClient.query(queryReq);
            return response.getQueryResults().size();
            
        } catch (Exception e) {
            log.error("Failed to count documents in Milvus: {}", e.getMessage(), e);
            return 0;
        }
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        return gson.fromJson(json, Map.class);
    }
}
