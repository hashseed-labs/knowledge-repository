package com.knowledge.kb.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kb.ai", name = "embedding-provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final KbProperties kbProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Float> embed(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", kbProperties.getOllama().getEmbedModel());
            body.put("prompt", text);
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(kbProperties.getOllama().getBaseUrl()) + "/api/embeddings"))
                    .timeout(Duration.ofMillis(kbProperties.getOllama().getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new BizException("Ollama embedding 失败: HTTP " + response.statusCode() + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embedding = root.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new BizException("Ollama embedding 响应缺少 embedding 字段");
            }
            List<Float> vectors = new ArrayList<>(embedding.size());
            embedding.forEach(n -> vectors.add((float) n.asDouble()));
            return vectors;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Ollama embedding 调用异常: " + e.getMessage());
        }
    }

    @Override
    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(kbProperties.getOllama().getBaseUrl()) + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            return httpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String provider() {
        return "ollama";
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(kbProperties.getOllama().getConnectTimeoutMs()))
                .build();
    }

    private String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
