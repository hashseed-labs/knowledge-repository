package com.knowledge.kb.controller;

import com.knowledge.kb.common.ApiResponse;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.entity.SysAuditLog;
import com.knowledge.kb.service.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "审计日志")
@RestController
@RequestMapping("/api/admin/audits")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:audit:view')")
    public ApiResponse<PageResult<SysAuditLog>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String action) {
        return ApiResponse.ok(auditService.page(page, size, action));
    }
}
