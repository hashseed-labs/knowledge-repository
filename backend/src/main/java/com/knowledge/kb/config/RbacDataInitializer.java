package com.knowledge.kb.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.entity.KbSpace;
import com.knowledge.kb.entity.SysPermission;
import com.knowledge.kb.entity.SysRole;
import com.knowledge.kb.entity.SysRolePermission;
import com.knowledge.kb.entity.SysTenant;
import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.entity.SysUserRole;
import com.knowledge.kb.entity.SysUserTenant;
import com.knowledge.kb.mapper.KbMemberMapper;
import com.knowledge.kb.mapper.KbSpaceMapper;
import com.knowledge.kb.mapper.SysPermissionMapper;
import com.knowledge.kb.mapper.SysRoleMapper;
import com.knowledge.kb.mapper.SysRolePermissionMapper;
import com.knowledge.kb.mapper.SysTenantMapper;
import com.knowledge.kb.mapper.SysUserMapper;
import com.knowledge.kb.mapper.SysUserRoleMapper;
import com.knowledge.kb.mapper.SysUserTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RbacDataInitializer implements ApplicationRunner {

    private final KbProperties kbProperties;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final KbSpaceMapper kbSpaceMapper;
    private final KbMemberMapper kbMemberMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!kbProperties.getSeed().isEnabled()) {
            return;
        }
        ensureIsolationColumns();
        Map<String, Long> permIds = seedPermissions();
        Map<String, Long> roleIds = seedBuiltinRoles(permIds);
        SysTenant tenant = seedTenant();
        SysUser admin = seedAdmin(tenant.getId(), roleIds.get("TENANT_ADMIN"));
        seedDefaultKb(tenant.getId(), admin.getId(), roleIds.get("KB_MANAGER"));
        log.info("RBAC seed ready. admin={} / {}", kbProperties.getSeed().getAdminUsername(), kbProperties.getSeed().getAdminPassword());
    }

    private void ensureIsolationColumns() {
        addColumnIfMissing("kb_document", "tenant_id", "BIGINT NULL");
        addColumnIfMissing("kb_document", "kb_id", "BIGINT NULL");
        addColumnIfMissing("kb_document", "created_by", "BIGINT NULL");
        addColumnIfMissing("kb_chunk", "tenant_id", "BIGINT NULL");
        addColumnIfMissing("kb_chunk", "kb_id", "BIGINT NULL");
        addColumnIfMissing("kb_chat_session", "tenant_id", "BIGINT NULL");
        addColumnIfMissing("kb_chat_session", "kb_id", "BIGINT NULL");
        addColumnIfMissing("kb_chat_session", "user_id", "BIGINT NULL");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                Integer.class, table, column);
        if (cnt != null && cnt == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Added column {}.{}", table, column);
        }
    }

    private Map<String, Long> seedPermissions() {
        Map<String, String[]> defs = new LinkedHashMap<>();
        defs.put("tenant:user:manage", new String[]{"组织用户管理", "tenant", "API"});
        defs.put("tenant:role:manage", new String[]{"角色权限管理", "tenant", "API"});
        defs.put("tenant:audit:view", new String[]{"查看审计", "tenant", "API"});
        defs.put("kb:space:manage", new String[]{"知识库管理", "kb", "API"});
        defs.put("kb:member:manage", new String[]{"知识库成员", "kb", "API"});
        defs.put("kb:doc:write", new String[]{"文档写入", "kb", "API"});
        defs.put("kb:doc:read", new String[]{"文档读取", "kb", "API"});
        defs.put("kb:chat:ask", new String[]{"知识问答", "kb", "API"});

        Map<String, Long> ids = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : defs.entrySet()) {
            SysPermission existed = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getCode, e.getKey()).last("limit 1"));
            if (existed == null) {
                SysPermission p = new SysPermission();
                p.setCode(e.getKey());
                p.setName(e.getValue()[0]);
                p.setModule(e.getValue()[1]);
                p.setType(e.getValue()[2]);
                p.setCreatedAt(LocalDateTime.now());
                permissionMapper.insert(p);
                ids.put(e.getKey(), p.getId());
            } else {
                ids.put(e.getKey(), existed.getId());
            }
        }
        return ids;
    }

    private Map<String, Long> seedBuiltinRoles(Map<String, Long> permIds) {
        Map<String, List<String>> rolePerms = new LinkedHashMap<>();
        rolePerms.put("TENANT_ADMIN", List.copyOf(permIds.keySet()));
        rolePerms.put("KB_MANAGER", List.of("kb:space:manage", "kb:member:manage", "kb:doc:write", "kb:doc:read", "kb:chat:ask"));
        rolePerms.put("EDITOR", List.of("kb:doc:write", "kb:doc:read", "kb:chat:ask"));
        rolePerms.put("VIEWER", List.of("kb:doc:read", "kb:chat:ask"));

        Map<String, String> names = Map.of(
                "TENANT_ADMIN", "组织管理员",
                "KB_MANAGER", "知识库管理员",
                "EDITOR", "编辑者",
                "VIEWER", "只读用户"
        );

        Map<String, Long> roleIds = new LinkedHashMap<>();
        for (String code : rolePerms.keySet()) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getTenantId, 0L)
                    .eq(SysRole::getCode, code)
                    .last("limit 1"));
            if (role == null) {
                role = new SysRole();
                role.setTenantId(0L);
                role.setCode(code);
                role.setName(names.get(code));
                role.setBuiltin(1);
                role.setCreatedAt(LocalDateTime.now());
                role.setUpdatedAt(LocalDateTime.now());
                roleMapper.insert(role);
            }
            roleIds.put(code, role.getId());
            for (String permCode : rolePerms.get(code)) {
                Long pid = permIds.get(permCode);
                if (pid == null) continue;
                Long count = rolePermissionMapper.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, role.getId())
                        .eq(SysRolePermission::getPermissionId, pid));
                if (count == null || count == 0) {
                    SysRolePermission rp = new SysRolePermission();
                    rp.setRoleId(role.getId());
                    rp.setPermissionId(pid);
                    rolePermissionMapper.insert(rp);
                }
            }
        }
        return roleIds;
    }

    private SysTenant seedTenant() {
        String code = kbProperties.getSeed().getTenantCode();
        SysTenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getCode, code).last("limit 1"));
        if (tenant != null) {
            return tenant;
        }
        tenant = new SysTenant();
        tenant.setCode(code);
        tenant.setName(kbProperties.getSeed().getTenantName());
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        return tenant;
    }

    private SysUser seedAdmin(Long tenantId, Long adminRoleId) {
        String username = kbProperties.getSeed().getAdminUsername();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(kbProperties.getSeed().getAdminPassword()));
            user.setDisplayName("系统管理员");
            user.setStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }
        Long ut = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, user.getId())
                .eq(SysUserTenant::getTenantId, tenantId));
        if (ut == null || ut == 0) {
            SysUserTenant link = new SysUserTenant();
            link.setUserId(user.getId());
            link.setTenantId(tenantId);
            link.setIsDefault(1);
            link.setCreatedAt(LocalDateTime.now());
            userTenantMapper.insert(link);
        }
        Long ur = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId())
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getRoleId, adminRoleId));
        if (ur == null || ur == 0) {
            SysUserRole role = new SysUserRole();
            role.setUserId(user.getId());
            role.setTenantId(tenantId);
            role.setRoleId(adminRoleId);
            role.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(role);
        }
        return user;
    }

    private void seedDefaultKb(Long tenantId, Long adminId, Long kbManagerRoleId) {
        KbSpace space = kbSpaceMapper.selectOne(new LambdaQueryWrapper<KbSpace>()
                .eq(KbSpace::getTenantId, tenantId)
                .eq(KbSpace::getName, "默认知识库")
                .last("limit 1"));
        if (space == null) {
            space = new KbSpace();
            space.setTenantId(tenantId);
            space.setName("默认知识库");
            space.setDescription("系统初始化知识库");
            space.setStatus("ACTIVE");
            space.setCreatedBy(adminId);
            space.setCreatedAt(LocalDateTime.now());
            space.setUpdatedAt(LocalDateTime.now());
            kbSpaceMapper.insert(space);
        }
        Long mem = kbMemberMapper.selectCount(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKbId, space.getId())
                .eq(KbMember::getUserId, adminId));
        if (mem == null || mem == 0) {
            KbMember member = new KbMember();
            member.setKbId(space.getId());
            member.setUserId(adminId);
            member.setRoleId(kbManagerRoleId);
            member.setCreatedAt(LocalDateTime.now());
            kbMemberMapper.insert(member);
        }
        // backfill orphan docs
        jdbcTemplate.update("UPDATE kb_document SET tenant_id=?, kb_id=?, created_by=? WHERE tenant_id IS NULL OR kb_id IS NULL",
                tenantId, space.getId(), adminId);
        jdbcTemplate.update("UPDATE kb_chunk c JOIN kb_document d ON c.doc_id=d.id SET c.tenant_id=d.tenant_id, c.kb_id=d.kb_id WHERE c.tenant_id IS NULL OR c.kb_id IS NULL");
        jdbcTemplate.update("UPDATE kb_chat_session SET tenant_id=?, kb_id=?, user_id=? WHERE tenant_id IS NULL OR kb_id IS NULL OR user_id IS NULL",
                tenantId, space.getId(), adminId);
    }
}
