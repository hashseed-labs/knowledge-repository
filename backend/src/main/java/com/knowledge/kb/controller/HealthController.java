package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.HealthVO;
import com.knowledge.kb.integration.EtcdConfigService;
import com.knowledge.kb.integration.MilvusService;
import com.knowledge.kb.integration.MinioService;
import com.knowledge.kb.integration.OllamaClient;
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
    private final OllamaClient ollamaClient;
    private final EtcdConfigService etcdConfigService;

    @Operation(summary = "依赖健康探测")
    @GetMapping
    public ApiResponse<HealthVO> health() {
        Map<String, Boolean> deps = new LinkedHashMap<>();
        deps.put("mysql", pingMysql());
        deps.put("minio", minioService.ping());
        deps.put("milvus", milvusService.ping());
        deps.put("ollama", ollamaClient.ping());
        deps.put("etcd", etcdConfigService.ping());
        boolean healthy = deps.values().stream().allMatch(Boolean::booleanValue);
        return ApiResponse.ok(HealthVO.builder().healthy(healthy).dependencies(deps).build());
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
