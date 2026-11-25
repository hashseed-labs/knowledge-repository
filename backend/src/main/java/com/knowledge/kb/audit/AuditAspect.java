package com.knowledge.kb.audit;

import com.knowledge.kb.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        boolean success = true;
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            error = t;
            throw t;
        } finally {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", pjp.getSignature().toShortString());
            if (error != null) {
                detail.put("error", error.getMessage());
            }
            auditService.record(audited.action(), audited.resourceType(), null, success, detail);
        }
    }
}
