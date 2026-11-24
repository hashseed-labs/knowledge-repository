package com.knowledge.kb.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtcdSmokeTest {

    @Test
    void canGetFromLocalEtcdHttpApi() throws Exception {
        String key = Base64.getEncoder().encodeToString("/kb/config/topK".getBytes(StandardCharsets.UTF_8));
        String body = "{\"key\":\"" + key + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:2379/v3/kv/range"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode root = new ObjectMapper().readTree(response.body());
        assertTrue(root.path("count").asInt() >= 1 || root.path("kvs").isArray());
    }
}
