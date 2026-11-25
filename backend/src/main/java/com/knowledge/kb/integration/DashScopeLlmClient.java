package com.knowledge.kb.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 阿里云百炼 OpenAI 兼容 Chat Completions 流式：POST /chat/completions
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kb.ai", name = "llm-provider", havingValue = "dashscope")
public class DashScopeLlmClient implements LlmClient {

    private final KbProperties kbProperties;
    private final ObjectMapper objectMapper;

    @Override
    public void generateStream(String prompt, double temperature, Consumer<String> onToken) {
        KbProperties.DashScope cfg = kbProperties.getDashscope();
        requireApiKey(cfg);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.getLlmModel());
            body.put("stream", true);
            body.put("temperature", temperature);
            // 百炼扩展参数：混合思考模型（如 qwen3.7-plus）默认开启思考，须显式关闭
            body.put("enable_thinking", cfg.isEnableThinking());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(cfg.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<InputStream> response = httpClient(cfg).send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new BizException("DashScope chat 失败: HTTP " + response.statusCode() + " " + err);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode choice = node.path("choices").path(0);
                    JsonNode delta = choice.path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                        onToken.accept(delta.asText());
                    }
                    // 部分网关不发 [DONE]，以 finish_reason 作为流结束
                    if (!choice.path("finish_reason").isMissingNode()
                            && !choice.path("finish_reason").isNull()
                            && !choice.path("finish_reason").asText().isEmpty()) {
                        break;
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("DashScope chat 调用异常: " + e.getMessage());
        }
    }

    @Override
    public boolean ping() {
        KbProperties.DashScope cfg = kbProperties.getDashscope();
        if (!StringUtils.hasText(cfg.getApiKey())) {
            return false;
        }
        try {
            // lightweight models list if available; fallback to tiny non-stream chat is costly, so just check key+baseUrl reachability
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(cfg.getBaseUrl()) + "/models"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient(cfg).send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
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
