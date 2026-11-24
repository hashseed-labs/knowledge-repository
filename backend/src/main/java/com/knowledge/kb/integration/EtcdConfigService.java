package com.knowledge.kb.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.config.KbProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uses etcd HTTP JSON API (/v3/kv/*) instead of jetcd/gRPC,
 * to avoid gRPC version conflicts with milvus-sdk-java.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtcdConfigService {

    private final KbProperties kbProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private volatile boolean available;

    @PostConstruct
    public void init() {
        refresh();
        if (available) {
            log.info("Etcd dynamic config ready via HTTP API, prefix={}", kbProperties.getEtcd().getConfigPrefix());
        } else {
            log.warn("Etcd unavailable, fallback to local application.yml");
        }
    }

    @Scheduled(fixedDelayString = "${kb.etcd.refresh-ms:5000}")
    public void scheduledRefresh() {
        refresh();
    }

    private synchronized void refresh() {
        try {
            String prefix = kbProperties.getEtcd().getConfigPrefix();
            if (!prefix.endsWith("/")) {
                // keep as-is; range_end still computed correctly
            }
            String keyB64 = Base64.getEncoder().encodeToString(prefix.getBytes(StandardCharsets.UTF_8));
            String rangeEndB64 = Base64.getEncoder().encodeToString(prefixRangeEnd(prefix).getBytes(StandardCharsets.UTF_8));
            String body = objectMapper.createObjectNode()
                    .put("key", keyB64)
                    .put("range_end", rangeEndB64)
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(kbProperties.getEtcd().getEndpoints()) + "/v3/kv/range"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            Map<String, String> next = new ConcurrentHashMap<>();
            JsonNode kvs = root.get("kvs");
            if (kvs != null && kvs.isArray()) {
                for (JsonNode kv : kvs) {
                    String fullKey = decodeB64(kv.path("key").asText());
                    String value = decodeB64(kv.path("value").asText());
                    next.put(shortKey(fullKey), value);
                }
            }
            // detect changes
            if (!next.equals(cache)) {
                for (Map.Entry<String, String> e : next.entrySet()) {
                    if (!e.getValue().equals(cache.get(e.getKey()))) {
                        log.info("Etcd config updated: {}={}", e.getKey(), e.getValue());
                    }
                }
                for (String oldKey : cache.keySet()) {
                    if (!next.containsKey(oldKey)) {
                        log.info("Etcd config removed: {}", oldKey);
                    }
                }
                cache.clear();
                cache.putAll(next);
            }
            available = true;
        } catch (Exception e) {
            if (available) {
                log.warn("Etcd refresh failed: {}", e.toString());
            }
            available = false;
        }
    }

    public int getTopK() {
        return getInt("topK", kbProperties.getMilvus().getTopK());
    }

    public int getChunkMaxChars() {
        return getInt("chunkSize", kbProperties.getChunk().getMaxChars());
    }

    public int getChunkOverlapChars() {
        return getInt("chunkOverlap", kbProperties.getChunk().getOverlapChars());
    }

    public double getTemperature() {
        String v = cache.get("temperature");
        if (v != null) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return kbProperties.getOllama().getTemperature();
    }

    public String getSystemPrompt() {
        String v = cache.get("systemPrompt");
        if (v != null && !v.isBlank()) {
            return v;
        }
        return kbProperties.getRag().getSystemPrompt();
    }

    public boolean ping() {
        if (!available) {
            refresh();
        }
        return available;
    }

    private int getInt(String key, int defaultValue) {
        String v = cache.get(key);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String shortKey(String fullKey) {
        String prefix = kbProperties.getEtcd().getConfigPrefix();
        if (fullKey.startsWith(prefix + "/")) {
            return fullKey.substring(prefix.length() + 1);
        }
        if (fullKey.startsWith(prefix)) {
            String rest = fullKey.substring(prefix.length());
            return rest.startsWith("/") ? rest.substring(1) : rest;
        }
        return fullKey;
    }

    private static String decodeB64(String b64) {
        if (b64 == null || b64.isBlank()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    /**
     * Etcd prefix range_end: increment last byte of prefix.
     */
    private static String prefixRangeEnd(String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] end = bytes.clone();
        for (int i = end.length - 1; i >= 0; i--) {
            if ((end[i] & 0xff) < 0xff) {
                end[i]++;
                byte[] trimmed = new byte[i + 1];
                System.arraycopy(end, 0, trimmed, 0, i + 1);
                return new String(trimmed, StandardCharsets.UTF_8);
            }
        }
        return prefix + "\0";
    }

    private String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String first = url.split(",")[0].trim();
        return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
    }
}
