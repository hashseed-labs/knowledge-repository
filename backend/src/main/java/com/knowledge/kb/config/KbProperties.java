package com.knowledge.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "kb")
public class KbProperties {

    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Seed seed = new Seed();
    private Minio minio = new Minio();
    private Milvus milvus = new Milvus();
    private Ollama ollama = new Ollama();
    private Etcd etcd = new Etcd();
    private Chunk chunk = new Chunk();
    private Rag rag = new Rag();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenTtlMinutes = 120;
        private long refreshTokenTtlDays = 7;
    }

    @Data
    public static class Seed {
        private boolean enabled = true;
        private String adminUsername = "admin";
        private String adminPassword = "admin123";
        private String tenantCode = "default";
        private String tenantName = "默认组织";
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class Milvus {
        private String host;
        private int port = 19530;
        private String collection = "kb_chunks";
        private int dimension = 512;
        private int topK = 5;
    }

    @Data
    public static class Ollama {
        private String baseUrl;
        private String embedModel;
        private String llmModel;
        private double temperature = 0.2;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 300000;
    }

    @Data
    public static class Etcd {
        private String endpoints;
        private String configPrefix = "/kb/config";
    }

    @Data
    public static class Chunk {
        private int maxChars = 600;
        private int overlapChars = 80;
    }

    @Data
    public static class Rag {
        private String systemPrompt;
    }
}
