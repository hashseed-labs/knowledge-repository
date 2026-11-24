package com.knowledge.kb.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final KbProperties kbProperties;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(kbProperties.getOllama().getConnectTimeoutMs()))
                .build();
    }

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

    public void generateStream(String prompt, double temperature, Consumer<String> onToken) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", kbProperties.getOllama().getLlmModel());
            body.put("prompt", prompt);
            body.put("stream", true);
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", temperature);
            body.put("options", options);
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(kbProperties.getOllama().getBaseUrl()) + "/api/generate"))
                    .timeout(Duration.ofMillis(kbProperties.getOllama().getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<InputStream> response = httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new BizException("Ollama generate 失败: HTTP " + response.statusCode() + " " + err);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    if (node.has("response") && !node.get("response").asText().isEmpty()) {
                        onToken.accept(node.get("response").asText());
                    }
                    if (node.path("done").asBoolean(false)) {
                        break;
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Ollama generate 调用异常: " + e.getMessage());
        }
    }

    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(kbProperties.getOllama().getBaseUrl()) + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
