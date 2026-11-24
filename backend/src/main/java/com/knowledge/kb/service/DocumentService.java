package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.dto.DocumentVO;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.entity.DocumentStatus;
import com.knowledge.kb.entity.KbChunk;
import com.knowledge.kb.entity.KbDocument;
import com.knowledge.kb.integration.MilvusService;
import com.knowledge.kb.integration.MinioService;
import com.knowledge.kb.mapper.KbChunkMapper;
import com.knowledge.kb.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final MinioService minioService;
    private final MilvusService milvusService;
    private final DocumentProcessService documentProcessService;

    public DocumentVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "untitled.md" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BizException("仅支持 Markdown (.md) 文件");
        }

        String objectKey = "docs/" + UUID.randomUUID() + "/" + original;
        minioService.upload(file, objectKey);

        KbDocument doc = new KbDocument();
        doc.setTitle(original);
        doc.setObjectKey(objectKey);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setChunkCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        documentProcessService.processAsync(doc.getId());
        return toVo(doc);
    }

    public PageResult<DocumentVO> page(long page, long size) {
        Page<KbDocument> p = documentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<KbDocument>().orderByDesc(KbDocument::getCreatedAt));
        List<DocumentVO> records = p.getRecords().stream().map(this::toVo).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), records);
    }

    public DocumentVO detail(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        return toVo(doc);
    }

    @Transactional
    public void delete(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        try {
            milvusService.deleteByDocId(id);
        } catch (Exception e) {
            log.warn("Delete milvus vectors for doc {} failed: {}", id, e.getMessage());
        }
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, id));
        documentMapper.deleteById(id);
        try {
            minioService.delete(doc.getObjectKey());
        } catch (Exception e) {
            log.warn("Delete minio object {} failed: {}", doc.getObjectKey(), e.getMessage());
        }
    }

    private DocumentVO toVo(KbDocument doc) {
        return DocumentVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .errorMsg(doc.getErrorMsg())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
