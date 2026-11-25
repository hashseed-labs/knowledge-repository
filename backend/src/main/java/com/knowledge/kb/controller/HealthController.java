package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.config.KbProperties;
import com.knowledge.kb.dto.HealthVO;
import com.knowledge.kb.integration.EmbeddingClient;
import com.knowledge.kb.integration.EtcdConfigService;
import com.knowledge.kb.integration.LlmClient;
import com.knowledge.kb.integration.MilvusService;
import com.knowledge.kb.integration.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final MinioService minioService;
    private final MilvusService milvusService;
    private final EmbeddingClient embeddingClient;
    private final LlmClient llmClient;
    private final EtcdConfigService etcdConfigService;
    private final KbProperties kbProperties;

    @Operation(summary = "依赖健康探测")
    @GetMapping
    public ApiResponse<HealthVO> health() {
        Map<String, Boolean> deps = new LinkedHashMap<>();
        deps.put("mysql", pingMysql());
        deps.put("minio", minioService.ping());
        deps.put("milvus", milvusService.ping());
        deps.put("embedding", embeddingClient.ping());
        deps.put("llm", llmClient.ping());
        deps.put("etcd", etcdConfigService.ping());
        boolean healthy = deps.values().stream().allMatch(Boolean::booleanValue);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("embeddingProvider", kbProperties.getAi().getEmbeddingProvider());
        meta.put("llmProvider", kbProperties.getAi().getLlmProvider());
        meta.put("embeddingImpl", embeddingClient.provider());
        meta.put("llmImpl", llmClient.provider());
        return ApiResponse.ok(HealthVO.builder().healthy(healthy).dependencies(deps).meta(meta).build());
    }

    private boolean pingMysql() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
