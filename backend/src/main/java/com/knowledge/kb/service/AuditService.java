package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.entity.SysAuditLog;
import com.knowledge.kb.mapper.SysAuditLogMapper;
import com.knowledge.kb.security.SecurityUtils;
import com.knowledge.kb.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final SysAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public void record(String action, String resourceType, String resourceId, boolean success, Object detail) {
        try {
            SysAuditLog row = new SysAuditLog();
            row.setTenantId(TenantContext.getTenantId());
            try {
                var user = SecurityUtils.currentUser();
                row.setUserId(user.getId());
                row.setUsername(user.getUsername());
            } catch (Exception ignored) {
            }
            row.setAction(action);
            row.setResourceType(resourceType);
            row.setResourceId(resourceId);
            row.setSuccess(success ? 1 : 0);
            HttpServletRequest req = currentRequest();
            if (req != null) {
                row.setIp(clientIp(req));
                row.setUserAgent(trim(req.getHeader("User-Agent"), 500));
            }
            if (detail != null) {
                row.setDetail(objectMapper.writeValueAsString(detail instanceof Map ? detail : Map.of("data", detail)));
            }
            row.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("Write audit log failed: {}", e.getMessage());
        }
    }

    public PageResult<SysAuditLog> page(long page, long size, String action) {
        Long tenantId = TenantContext.requireTenantId();
        LambdaQueryWrapper<SysAuditLog> q = new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getTenantId, tenantId)
                .eq(action != null && !action.isBlank(), SysAuditLog::getAction, action)
                .orderByDesc(SysAuditLog::getId);
        Page<SysAuditLog> p = auditLogMapper.selectPage(new Page<>(page, size), q);
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
