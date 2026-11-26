package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.kb.audit.Audited;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.entity.KbSpace;
import com.knowledge.kb.entity.SysRole;
import com.knowledge.kb.entity.SysTenant;
import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.entity.SysUserRole;
import com.knowledge.kb.entity.SysUserTenant;
import com.knowledge.kb.mapper.KbMemberMapper;
import com.knowledge.kb.mapper.KbSpaceMapper;
import com.knowledge.kb.mapper.SysRoleMapper;
import com.knowledge.kb.mapper.SysTenantMapper;
import com.knowledge.kb.mapper.SysUserMapper;
import com.knowledge.kb.mapper.SysUserRoleMapper;
import com.knowledge.kb.mapper.SysUserTenantMapper;
import com.knowledge.kb.security.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private final SysTenantMapper tenantMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final KbSpaceMapper kbSpaceMapper;
    private final KbMemberMapper kbMemberMapper;

    public PageResult<Map<String, Object>> page(long page, long size, String keyword) {
        LambdaQueryWrapper<SysTenant> q = new LambdaQueryWrapper<SysTenant>().orderByDesc(SysTenant::getId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SysTenant::getCode, keyword).or().like(SysTenant::getName, keyword));
        }
        Page<SysTenant> p = tenantMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> records = p.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), records);
    }

    public List<Map<String, Object>> listAll() {
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>().orderByAsc(SysTenant::getId))
                .stream().map(this::toMap).toList();
    }

    @Audited(action = "TENANT_CREATE", resourceType = "TENANT")
    @Transactional
    public Long create(TenantCreateReq req) {
        String code = normalizeCode(req.getCode());
        Long exists = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getCode, code));
        if (exists != null && exists > 0) {
            throw new BizException("组织编码已存在");
        }
        SysTenant tenant = new SysTenant();
        tenant.setCode(code);
        tenant.setName(req.getName().trim());
        tenant.setStatus(statusOrActive(req.getStatus()));
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);

        Long userId = SecurityUtils.currentUserId();
        linkUser(userId, tenant.getId(), false);
        Long adminRoleId = requireBuiltinRole("TENANT_ADMIN");
        Long kbManagerRoleId = requireBuiltinRole("KB_MANAGER");
        grantRole(userId, tenant.getId(), adminRoleId);
        seedDefaultKb(tenant.getId(), userId, kbManagerRoleId);
        return tenant.getId();
    }

    @Audited(action = "TENANT_UPDATE", resourceType = "TENANT")
    @Transactional
    public void update(Long id, TenantUpdateReq req) {
        SysTenant tenant = requireTenant(id);
        if (req.getName() != null && !req.getName().isBlank()) {
            tenant.setName(req.getName().trim());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            String next = statusOrActive(req.getStatus());
            if ("DISABLED".equals(next)) {
                ensureDeletable(tenant);
            }
            tenant.setStatus(next);
        }
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
    }

    @Audited(action = "TENANT_DELETE", resourceType = "TENANT")
    @Transactional
    public void delete(Long id) {
        SysTenant tenant = requireTenant(id);
        ensureDeletable(tenant);
        if ("DISABLED".equalsIgnoreCase(tenant.getStatus())) {
            throw new BizException("组织已删除");
        }
        tenant.setStatus("DISABLED");
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
    }

    private void ensureDeletable(SysTenant tenant) {
        if ("default".equalsIgnoreCase(tenant.getCode())) {
            throw new BizException("默认组织不可删除");
        }
    }

    public List<Map<String, Object>> listMembers(Long tenantId) {
        requireTenant(tenantId);
        List<SysUserTenant> links = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, tenantId)
                .orderByAsc(SysUserTenant::getId));
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(links.stream().map(SysUserTenant::getUserId).toList())
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUserTenant link : links) {
            SysUser user = users.get(link.getUserId());
            if (user == null) continue;
            List<SysUserRole> roles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, user.getId())
                    .eq(SysUserRole::getTenantId, tenantId));
            List<Long> roleIds = roles.stream().map(SysUserRole::getRoleId).toList();
            String roleName = roleIds.stream()
                    .map(rid -> roleNames.getOrDefault(rid, String.valueOf(rid)))
                    .collect(Collectors.joining(", "));
            Map<String, Object> m = new HashMap<>();
            m.put("userId", user.getId());
            m.put("username", user.getUsername());
            m.put("displayName", user.getDisplayName());
            m.put("status", user.getStatus());
            m.put("isDefault", link.getIsDefault() != null && link.getIsDefault() == 1);
            m.put("roleIds", roleIds);
            m.put("roleName", roleName.isBlank() ? "-" : roleName);
            m.put("joinedAt", link.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    public List<Map<String, Object>> listCandidates(Long tenantId, String keyword) {
        requireTenant(tenantId);
        Set<Long> inTenant = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                        .eq(SysUserTenant::getTenantId, tenantId)).stream()
                .map(SysUserTenant::getUserId)
                .collect(Collectors.toCollection(HashSet::new));
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, "ACTIVE")
                .orderByDesc(SysUser::getId)
                .last("limit 50");
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword));
        }
        return userMapper.selectList(q).stream()
                .filter(u -> !inTenant.contains(u.getId()))
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("displayName", u.getDisplayName());
                    return m;
                }).toList();
    }

    @Audited(action = "TENANT_MEMBER_ADD", resourceType = "TENANT")
    @Transactional
    public void addMember(Long tenantId, TenantMemberReq req) {
        requireTenant(tenantId);
        SysUser user = userMapper.selectById(req.getUserId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BizException("用户已禁用，无法加入组织");
        }
        Long exists = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, req.getUserId())
                .eq(SysUserTenant::getTenantId, tenantId));
        if (exists != null && exists > 0) {
            throw new BizException("用户已在该组织中");
        }
        linkUser(req.getUserId(), tenantId, false);
        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            for (Long roleId : req.getRoleIds()) {
                ensureAssignableRole(roleId);
                grantRole(req.getUserId(), tenantId, roleId);
            }
        } else {
            grantRole(req.getUserId(), tenantId, requireBuiltinRole("VIEWER"));
        }
    }

    @Audited(action = "TENANT_MEMBER_UPDATE", resourceType = "TENANT")
    @Transactional
    public void updateMemberRoles(Long tenantId, Long userId, TenantMemberRolesReq req) {
        requireTenant(tenantId);
        ensureMember(userId, tenantId);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getTenantId, tenantId));
        if (req.getRoleIds() != null) {
            for (Long roleId : req.getRoleIds()) {
                ensureAssignableRole(roleId);
                grantRole(userId, tenantId, roleId);
            }
        }
    }

    @Audited(action = "TENANT_MEMBER_REMOVE", resourceType = "TENANT")
    @Transactional
    public void removeMember(Long tenantId, Long userId) {
        requireTenant(tenantId);
        ensureMember(userId, tenantId);
        if (userId.equals(SecurityUtils.currentUserId())) {
            throw new BizException("不能移除自己");
        }
        Long remain = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, tenantId));
        if (remain != null && remain <= 1) {
            throw new BizException("组织至少保留一名成员");
        }
        userTenantMapper.delete(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getTenantId, tenantId));
    }

    private SysTenant requireTenant(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException("组织不存在");
        }
        return tenant;
    }

    private void ensureMember(Long userId, Long tenantId) {
        Long c = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId));
        if (c == null || c == 0) {
            throw new BizException("用户不在该组织");
        }
    }

    private void ensureAssignableRole(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在: " + roleId);
        }
        // 仅允许内置角色或当前不校验租户自定义角色归属（组织管理场景用内置角色即可）
        if (role.getTenantId() != null && role.getTenantId() != 0L) {
            // 允许租户自定义角色，但调用方一般传内置角色
        }
    }

    private void seedDefaultKb(Long tenantId, Long adminId, Long kbManagerRoleId) {
        KbSpace space = new KbSpace();
        space.setTenantId(tenantId);
        space.setName("默认知识库");
        space.setDescription("组织初始化知识库");
        space.setStatus("ACTIVE");
        space.setCreatedBy(adminId);
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());
        kbSpaceMapper.insert(space);

        KbMember member = new KbMember();
        member.setKbId(space.getId());
        member.setUserId(adminId);
        member.setRoleId(kbManagerRoleId);
        member.setCreatedAt(LocalDateTime.now());
        kbMemberMapper.insert(member);
    }

    private void linkUser(Long userId, Long tenantId, boolean isDefault) {
        Long cnt = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId));
        if (cnt != null && cnt > 0) {
            return;
        }
        SysUserTenant link = new SysUserTenant();
        link.setUserId(userId);
        link.setTenantId(tenantId);
        link.setIsDefault(isDefault ? 1 : 0);
        link.setCreatedAt(LocalDateTime.now());
        userTenantMapper.insert(link);
    }

    private void grantRole(Long userId, Long tenantId, Long roleId) {
        Long cnt = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getTenantId, tenantId)
                .eq(SysUserRole::getRoleId, roleId));
        if (cnt != null && cnt > 0) {
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setTenantId(tenantId);
        ur.setRoleId(roleId);
        ur.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insert(ur);
    }

    private Long requireBuiltinRole(String code) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, 0L)
                .eq(SysRole::getCode, code)
                .last("limit 1"));
        if (role == null) {
            throw new BizException("缺少内置角色: " + code);
        }
        return role.getId();
    }

    private Map<String, Object> toMap(SysTenant t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("status", t.getStatus());
        m.put("createdAt", t.getCreatedAt());
        m.put("updatedAt", t.getUpdatedAt());
        Long members = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, t.getId()));
        m.put("memberCount", members == null ? 0 : members);
        return m;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("组织编码不能为空");
        }
        String c = code.trim().toLowerCase(Locale.ROOT);
        if (!c.matches("^[a-z][a-z0-9_-]{1,62}$")) {
            throw new BizException("组织编码需以字母开头，仅含小写字母/数字/_/-，长度 2~63");
        }
        return c;
    }

    private String statusOrActive(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(s) && !"DISABLED".equals(s)) {
            throw new BizException("状态仅支持 ACTIVE / DISABLED");
        }
        return s;
    }

    @Data
    public static class TenantCreateReq {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private String status;
    }

    @Data
    public static class TenantUpdateReq {
        private String name;
        private String status;
    }

    @Data
    public static class TenantMemberReq {
        @NotNull
        private Long userId;
        private List<Long> roleIds;
    }

    @Data
    public static class TenantMemberRolesReq {
        private List<Long> roleIds;
    }
}
