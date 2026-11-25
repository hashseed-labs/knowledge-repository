package com.knowledge.kb.integration;

import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {

    private final KbProperties kbProperties;
    private MilvusServiceClient client;

    @PostConstruct
    public void init() {
        try {
            client = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(kbProperties.getMilvus().getHost())
                    .withPort(kbProperties.getMilvus().getPort())
                    .build());
            ensureCollection();
        } catch (Exception e) {
            log.warn("Milvus init failed (will retry on use): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    private synchronized MilvusServiceClient client() {
        if (client == null) {
            client = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(kbProperties.getMilvus().getHost())
                    .withPort(kbProperties.getMilvus().getPort())
                    .build());
            ensureCollection();
        }
        return client;
    }

    public synchronized void ensureCollection() {
        String collection = kbProperties.getMilvus().getCollection();
        R<Boolean> has = client().hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        if (has.getData() != null && has.getData()) {
            client().loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .build());
            return;
        }

        int dim = kbProperties.getMilvus().getDimension();
        R<RpcStatus> create = client().createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("Knowledge base chunks with tenant isolation")
                .addFieldType(FieldType.newBuilder().withName("id").withDataType(DataType.Int64).withPrimaryKey(true).withAutoID(false).build())
                .addFieldType(FieldType.newBuilder().withName("tenant_id").withDataType(DataType.Int64).build())
                .addFieldType(FieldType.newBuilder().withName("kb_id").withDataType(DataType.Int64).build())
                .addFieldType(FieldType.newBuilder().withName("doc_id").withDataType(DataType.Int64).build())
                .addFieldType(FieldType.newBuilder().withName("chunk_id").withDataType(DataType.Int64).build())
                .addFieldType(FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector).withDimension(dim).build())
                .build());
        if (create.getStatus() != R.Status.Success.getCode()) {
            throw new BizException("创建 Milvus collection 失败: " + create.getMessage());
        }

        R<RpcStatus> index = client().createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":1024}")
                .build());
        if (index.getStatus() != R.Status.Success.getCode()) {
            throw new BizException("创建 Milvus index 失败: " + index.getMessage());
        }

        client().loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        log.info("Milvus collection ready: {} dim={}", collection, dim);
    }

    public void insert(long milvusId, long tenantId, long kbId, long docId, long chunkId, List<Float> embedding) {
        try {
            ensureCollection();
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", Collections.singletonList(milvusId)));
            fields.add(new InsertParam.Field("tenant_id", Collections.singletonList(tenantId)));
            fields.add(new InsertParam.Field("kb_id", Collections.singletonList(kbId)));
            fields.add(new InsertParam.Field("doc_id", Collections.singletonList(docId)));
            fields.add(new InsertParam.Field("chunk_id", Collections.singletonList(chunkId)));
            fields.add(new InsertParam.Field("embedding", Collections.singletonList(embedding)));

            R<MutationResult> result = client().insert(InsertParam.newBuilder()
                    .withCollectionName(kbProperties.getMilvus().getCollection())
                    .withFields(fields)
                    .build());
            if (result.getStatus() != R.Status.Success.getCode()) {
                throw new BizException("Milvus insert 失败: " + result.getMessage());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Milvus insert 异常: " + e.getMessage());
        }
    }

    public void flush() {
        client().flush(FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(kbProperties.getMilvus().getCollection()))
                .build());
    }

    public List<SearchHit> search(List<Float> embedding, int topK, long tenantId, long kbId) {
        try {
            ensureCollection();
            String expr = "tenant_id == " + tenantId + " && kb_id == " + kbId;
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(kbProperties.getMilvus().getCollection())
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(List.of("id", "tenant_id", "kb_id", "doc_id", "chunk_id"))
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(embedding))
                    .withVectorFieldName("embedding")
                    .withExpr(expr)
                    .withParams("{\"nprobe\":16}")
                    .build();
            R<SearchResults> response = client().search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new BizException("Milvus search 失败: " + response.getMessage());
            }
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchHit> hits = new ArrayList<>();
            if (wrapper.getRowRecords(0) == null) {
                return hits;
            }
            wrapper.getRowRecords(0).forEach(row -> {
                SearchHit hit = new SearchHit();
                hit.setMilvusId(((Number) row.get("id")).longValue());
                hit.setDocId(((Number) row.get("doc_id")).longValue());
                hit.setChunkId(((Number) row.get("chunk_id")).longValue());
                Object score = row.get("score");
                if (score instanceof Number n) {
                    hit.setScore(n.floatValue());
                }
                hits.add(hit);
            });
            return hits;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Milvus search 异常: " + e.getMessage());
        }
    }

    public void deleteByDocId(long docId) {
        try {
            ensureCollection();
            R<MutationResult> result = client().delete(DeleteParam.newBuilder()
                    .withCollectionName(kbProperties.getMilvus().getCollection())
                    .withExpr("doc_id == " + docId)
                    .build());
            if (result.getStatus() != R.Status.Success.getCode()) {
                throw new BizException("Milvus delete 失败: " + result.getMessage());
            }
            flush();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Milvus delete 异常: " + e.getMessage());
        }
    }

    public boolean ping() {
        try {
            ensureCollection();
            R<Boolean> has = client().hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(kbProperties.getMilvus().getCollection())
                    .build());
            return has.getStatus() == R.Status.Success.getCode();
        } catch (Exception e) {
            return false;
        }
    }

    @lombok.Data
    public static class SearchHit {
        private long milvusId;
        private long docId;
        private long chunkId;
        private float score;
    }
}
