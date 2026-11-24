package com.knowledge.kb.integration;

import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final KbProperties kbProperties;
    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        KbProperties.Minio cfg = kbProperties.getMinio();
        minioClient = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            String bucket = kbProperties.getMinio().getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed (will retry on use): {}", e.getMessage());
        }
    }

    public String upload(MultipartFile file, String objectKey) {
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(kbProperties.getMinio().getBucket())
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType() == null ? "text/markdown" : file.getContentType())
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new BizException("上传 MinIO 失败: " + e.getMessage());
        }
    }

    public String downloadAsString(String objectKey) {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(kbProperties.getMinio().getBucket())
                .object(objectKey)
                .build())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("从 MinIO 读取失败: " + e.getMessage());
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(kbProperties.getMinio().getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BizException("删除 MinIO 对象失败: " + e.getMessage());
        }
    }

    public boolean ping() {
        try {
            ensureBucket();
            minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(kbProperties.getMinio().getBucket())
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void putText(String objectKey, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(kbProperties.getMinio().getBucket())
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType("text/markdown")
                    .build());
        } catch (Exception e) {
            throw new BizException("写入 MinIO 失败: " + e.getMessage());
        }
    }
}
