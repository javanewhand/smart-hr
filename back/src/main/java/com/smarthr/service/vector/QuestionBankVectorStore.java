/**
 * Milvus 向量存储 - 面试题库专用
 * 与简历向量集合隔离，便于不同索引策略和数据生命周期
 */
package com.smarthr.service.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smarthr.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.GetResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@ConditionalOnBean(MilvusClientV2.class)
public class QuestionBankVectorStore implements VectorStore {

    private static final String STORE_ID = "questions";
    private static final String ID_FIELD = "id";
    private static final String CONTENT_FIELD = "content";
    private static final String VECTOR_FIELD = "embedding";
    private static final String METADATA_FIELD = "metadata";

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties properties;
    private final Gson gson = new Gson();

    public QuestionBankVectorStore(MilvusClientV2 milvusClient, MilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        createCollectionIfNotExists(false);
    }

    private void createCollectionIfNotExists(boolean forceReload) {
        String collectionName = properties.getQuestionCollectionName();
        try {
            boolean exists = milvusClient.hasCollection(
                    HasCollectionReq.builder().collectionName(collectionName).build()
            );

            if (exists) {
                if (forceReload) {
                    milvusClient.dropCollection(io.milvus.v2.service.collection.request.DropCollectionReq.builder()
                            .collectionName(collectionName)
                            .build());
                    log.info("Dropped existing question collection {} for recreation", collectionName);
                } else {
                    log.info("Question collection {} already exists, loading", collectionName);
                    LoadCollectionReq loadReq = LoadCollectionReq.builder()
                            .collectionName(collectionName)
                            .build();
                    try {
                        milvusClient.loadCollection(loadReq);
                    } catch (Exception loadEx) {
                        log.warn("Load Milvus question collection {} failed (non-fatal): {}", collectionName, loadEx.getMessage());
                    }
                    return;
                }
            }

            log.info("Creating Milvus question collection: {}", collectionName);

            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
            schema.addField(AddFieldReq.builder()
                    .fieldName(ID_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .isPrimaryKey(true)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(CONTENT_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(VECTOR_FIELD)
                    .dataType(DataType.FloatVector)
                    .dimension(properties.getEmbeddingDimension())
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(METADATA_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());

            IndexParam indexParam = IndexParam.builder()
                    .fieldName(VECTOR_FIELD)
                    .indexType(IndexParam.IndexType.IVF_FLAT)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("nlist", properties.getNlist()))
                    .build();

            milvusClient.createCollection(CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(Collections.singletonList(indexParam))
                    .build());

            try {
                milvusClient.loadCollection(LoadCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());
            } catch (Exception loadEx) {
                log.warn("Load Milvus question collection {} failed (non-fatal): {}", collectionName, loadEx.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to init question collection {}: {}", collectionName, e.getMessage(), e);
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

            InsertResp resp = milvusClient.insert(InsertReq.builder()
                    .collectionName(properties.getQuestionCollectionName())
                    .data(data)
                    .build());
            log.debug("Inserted {} question docs, insertCnt={}", documents.size(), resp.getInsertCnt());
        } catch (Exception e) {
            log.error("Failed to add question documents: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add question documents", e);
        }
    }

    @Override
    public void deleteDocument(String id) {
        deleteDocuments(Collections.singletonList(id));
    }

    @Override
    public void deleteDocuments(List<String> ids) {
        try {
            String filter = ID_FIELD + " in [\"" + String.join("\",\"", ids) + "\"]";
            DeleteResp resp = milvusClient.delete(DeleteReq.builder()
                    .collectionName(properties.getQuestionCollectionName())
                    .filter(filter)
                    .build());
            log.debug("Deleted {} question docs, deleteCnt={}", ids.size(), resp.getDeleteCnt());
        } catch (Exception e) {
            log.error("Failed to delete question documents: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete question documents", e);
        }
    }

    @Override
    public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK) {
        return similaritySearch(queryEmbedding, topK, null);
    }

    @Override
    public List<VectorDocument> similaritySearch(float[] queryEmbedding, int topK, String filter) {
        try {
            var queryVector = new io.milvus.v2.service.vector.request.data.FloatVec(toFloatList(queryEmbedding));
            SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                    .collectionName(properties.getQuestionCollectionName())
                    .data(Collections.singletonList(queryVector))
                    .topK(topK)
                    .outputFields(Arrays.asList(ID_FIELD, CONTENT_FIELD, METADATA_FIELD))
                    .searchParams(Map.of("nprobe", properties.getNprobe()));

            if (filter != null && !filter.isEmpty()) {
                builder.filter(filter);
            }

            SearchResp resp = milvusClient.search(builder.build());
            List<VectorDocument> results = new ArrayList<>();
            if (!resp.getSearchResults().isEmpty()) {
                for (SearchResp.SearchResult r : resp.getSearchResults().get(0)) {
                    Map<String, Object> entity = r.getEntity();
                    results.add(VectorDocument.builder()
                            .id((String) entity.get(ID_FIELD))
                            .content((String) entity.get(CONTENT_FIELD))
                            .score(r.getScore())
                            .metadata(parseMetadata((String) entity.get(METADATA_FIELD)))
                            .build());
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to search question bank: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search question bank", e);
        }
    }

    @Override
    public VectorDocument getDocument(String id) {
        try {
            GetResp resp = milvusClient.get(GetReq.builder()
                    .collectionName(properties.getQuestionCollectionName())
                    .ids(Collections.singletonList(id))
                    .outputFields(Arrays.asList(ID_FIELD, CONTENT_FIELD, METADATA_FIELD))
                    .build());
            if (resp.getGetResults().isEmpty()) {
                return null;
            }
            Map<String, Object> entity = resp.getGetResults().get(0).getEntity();
            return VectorDocument.builder()
                    .id((String) entity.get(ID_FIELD))
                    .content((String) entity.get(CONTENT_FIELD))
                    .metadata(parseMetadata((String) entity.get(METADATA_FIELD)))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get question doc: {}", e.getMessage(), e);
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
            QueryResp resp = milvusClient.query(QueryReq.builder()
                    .collectionName(properties.getQuestionCollectionName())
                    .filter("")
                    .build());
            return resp.getQueryResults().size();
        } catch (Exception e) {
            log.error("Failed to count question docs: {}", e.getMessage(), e);
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
