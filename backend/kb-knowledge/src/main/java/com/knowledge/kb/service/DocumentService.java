package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.kb.audit.Audited;
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
import com.knowledge.kb.security.SecurityUtils;
import com.knowledge.kb.security.TenantContext;
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
    private final KbSpaceService kbSpaceService;
    private final AuditService auditService;

    @Audited(action = "DOC_UPLOAD", resourceType = "DOCUMENT")
    public DocumentVO upload(MultipartFile file) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        kbSpaceService.assertKbAccess(kbId);

        if (file == null || file.isEmpty()) {
            throw new BizException("文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "untitled.md" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BizException("仅支持 Markdown (.md) 文件");
        }

        String objectKey = "docs/" + tenantId + "/" + kbId + "/" + UUID.randomUUID() + "/" + original;
        minioService.upload(file, objectKey);

        KbDocument doc = new KbDocument();
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setCreatedBy(SecurityUtils.currentUserId());
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
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        kbSpaceService.assertKbAccess(kbId);
        Page<KbDocument> p = documentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getTenantId, tenantId)
                        .eq(KbDocument::getKbId, kbId)
                        .orderByDesc(KbDocument::getCreatedAt));
        List<DocumentVO> records = p.getRecords().stream().map(this::toVo).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), records);
    }

    public DocumentVO detail(Long id) {
        return toVo(requireDoc(id));
    }

    @Audited(action = "DOC_DELETE", resourceType = "DOCUMENT")
    @Transactional
    public void delete(Long id) {
        KbDocument doc = requireDoc(id);
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
        auditService.record("DOC_DELETE", "DOCUMENT", String.valueOf(id), true, doc.getTitle());
    }

    private KbDocument requireDoc(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        Long kbId = TenantContext.requireKbId();
        kbSpaceService.assertKbAccess(kbId);
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null || !tenantId.equals(doc.getTenantId()) || !kbId.equals(doc.getKbId())) {
            throw new BizException("文档不存在");
        }
        return doc;
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
