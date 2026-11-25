package com.knowledge.kb.security;

public final class TenantContext {

    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Long> KB = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT.get();
    }

    public static void setKbId(Long kbId) {
        KB.set(kbId);
    }

    public static Long getKbId() {
        return KB.get();
    }

    public static void clear() {
        TENANT.remove();
        KB.remove();
    }

    public static Long requireTenantId() {
        Long id = getTenantId();
        if (id == null) {
            throw new IllegalStateException("缺少租户上下文 X-Tenant-Id");
        }
        return id;
    }

    public static Long requireKbId() {
        Long id = getKbId();
        if (id == null) {
            throw new IllegalStateException("缺少知识库上下文 X-Kb-Id");
        }
        return id;
    }
}
