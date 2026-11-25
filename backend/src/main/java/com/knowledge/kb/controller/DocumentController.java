package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.DocumentVO;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文档管理")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "上传 Markdown 文档")
    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('kb:doc:write')")
    public ApiResponse<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(documentService.upload(file));
    }

    @Operation(summary = "分页查询文档")
    @GetMapping
    @PreAuthorize("hasAuthority('kb:doc:read')")
    public ApiResponse<PageResult<DocumentVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(documentService.page(page, size));
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:doc:read')")
    public ApiResponse<DocumentVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(documentService.detail(id));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:doc:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.ok(null);
    }
}
