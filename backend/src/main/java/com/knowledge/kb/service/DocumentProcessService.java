package com.knowledge.kb.service;

import com.knowledge.kb.common.BizException;
import com.knowledge.kb.entity.DocumentStatus;
import com.knowledge.kb.entity.KbChunk;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.integration.EtcdConfigService;
import com.knowledge.kb.integration.MilvusService;
import com.knowledge.kb.integration.MinioService;
import com.knowledge.kb.integration.OllamaClient;
import com.knowledge.kb.mapper.KbChunkMapper;
import com.knowledge.kb.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    private static final AtomicLong MILVUS_ID_SEQ = new AtomicLong(System.currentTimeMillis());

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final MinioService minioService;
    private final OllamaClient ollamaClient;
    private final MilvusService milvusService;
    private final MarkdownChunker markdownChunker;
    private final EtcdConfigService etcdConfigService;

    @Async("docTaskExecutor")
    public void processAsync(Long docId) {
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            return;
        }
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        try {
            String content = minioService.downloadAsString(doc.getObjectKey());
            int maxChars = etcdConfigService.getChunkMaxChars();
            int overlap = etcdConfigService.getChunkOverlapChars();
            List<String> chunks = markdownChunker.chunk(content, maxChars, overlap);
            if (chunks.isEmpty()) {
                throw new BizException("文档内容为空，无法分块");
            }

            int index = 0;
            for (String chunkText : chunks) {
                List<Float> embedding = ollamaClient.embed(chunkText);
                long milvusId = MILVUS_ID_SEQ.incrementAndGet();

                KbChunk chunk = new KbChunk();
                chunk.setDocId(docId);
                chunk.setChunkIndex(index++);
                chunk.setContent(chunkText);
                chunk.setTokenEstimate(Math.max(1, chunkText.length() / 2));
                chunk.setMilvusId(milvusId);
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);

                milvusService.insert(milvusId, docId, chunk.getId(), embedding);
            }
            milvusService.flush();

            doc.setStatus(DocumentStatus.READY);
            doc.setChunkCount(chunks.size());
            doc.setErrorMsg(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("Document {} processed, chunks={}", docId, chunks.size());
        } catch (Exception e) {
            log.error("Document {} process failed", docId, e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMsg(trimError(e.getMessage()));
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }

    private String trimError(String msg) {
        if (msg == null) {
            return "unknown error";
        }
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }
}
