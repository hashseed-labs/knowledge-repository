package com.knowledge.kb.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼 OpenAI 兼容 Embedding：POST /embeddings
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kb.ai", name = "embedding-provider", havingValue = "dashscope")
public class DashScopeEmbeddingClient implements EmbeddingClient {

    private final KbProperties kbProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Float> embed(String text) {
        KbProperties.DashScope cfg = kbProperties.getDashscope();
        requireApiKey(cfg);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.getEmbedModel());
            body.put("input", text);
            body.put("encoding_format", "float");
            if (cfg.getEmbedDimensions() > 0) {
                body.put("dimensions", cfg.getEmbedDimensions());
            }
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(cfg.getBaseUrl()) + "/embeddings"))
                    .timeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient(cfg).send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BizException("DashScope embedding 失败: HTTP " + response.statusCode() + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embedding = root.path("data").path(0).path("embedding");
            if (!embedding.isArray()) {
                throw new BizException("DashScope embedding 响应缺少 data[0].embedding");
            }
            List<Float> vectors = new ArrayList<>(embedding.size());
            embedding.forEach(n -> vectors.add((float) n.asDouble()));
            return vectors;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("DashScope embedding 调用异常: " + e.getMessage());
        }
    }

    @Override
    public boolean ping() {
        KbProperties.DashScope cfg = kbProperties.getDashscope();
        if (!StringUtils.hasText(cfg.getApiKey())) {
            return false;
        }
        try {
            embed("ping");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String provider() {
        return "dashscope";
    }

    private void requireApiKey(KbProperties.DashScope cfg) {
        if (!StringUtils.hasText(cfg.getApiKey())) {
            throw new BizException("未配置 kb.dashscope.api-key（可用环境变量 DASHSCOPE_API_KEY）");
        }
    }

    private HttpClient httpClient(KbProperties.DashScope cfg) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .build();
    }

    private String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
