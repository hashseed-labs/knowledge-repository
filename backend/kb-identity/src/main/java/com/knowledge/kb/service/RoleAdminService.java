package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.kb.audit.Audited;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.entity.SysPermission;
import com.knowledge.kb.entity.SysRole;
import com.knowledge.kb.entity.SysRolePermission;
import com.knowledge.kb.mapper.SysPermissionMapper;
import com.knowledge.kb.mapper.SysRoleMapper;
import com.knowledge.kb.mapper.SysRolePermissionMapper;
import com.knowledge.kb.security.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    public List<Map<String, Object>> listRoles() {
        Long tenantId = TenantContext.requireTenantId();
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .and(w -> w.eq(SysRole::getTenantId, 0L).or().eq(SysRole::getTenantId, tenantId))
                .orderByAsc(SysRole::getId));
        return roles.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("tenantId", r.getTenantId());
            m.put("code", r.getCode());
            m.put("name", r.getName());
            m.put("builtin", r.getBuiltin());
            List<Long> permIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                            .eq(SysRolePermission::getRoleId, r.getId()))
                    .stream().map(SysRolePermission::getPermissionId).toList();
            m.put("permissionIds", permIds);
            return m;
        }).toList();
    }

    public List<SysPermission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getId));
    }

    @Audited(action = "ROLE_CREATE", resourceType = "ROLE")
    @Transactional
    public Long create(RoleSaveReq req) {
        Long tenantId = TenantContext.requireTenantId();
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)
                .eq(SysRole::getCode, req.getCode()));
        if (exists != null && exists > 0) throw new BizException("角色编码已存在");
        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setCode(req.getCode());
        role.setName(req.getName());
        role.setBuiltin(0);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        bindPermissions(role.getId(), req.getPermissionIds());
        return role.getId();
    }

    @Audited(action = "ROLE_UPDATE", resourceType = "ROLE")
    @Transactional
    public void update(Long id, RoleSaveReq req) {
        Long tenantId = TenantContext.requireTenantId();
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new BizException("角色不存在");
        if (role.getBuiltin() != null && role.getBuiltin() == 1 && role.getTenantId() == 0L) {
            // allow permission update on builtin template by cloning? For simplicity update perms of tenant copy only
        }
        if (!Long.valueOf(0L).equals(role.getTenantId()) && !tenantId.equals(role.getTenantId())) {
            throw new BizException("无权修改该角色");
        }
        if (req.getName() != null) role.setName(req.getName());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
        if (req.getPermissionIds() != null) {
            rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
            bindPermissions(id, req.getPermissionIds());
        }
    }

    private void bindPermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null) return;
        for (Long pid : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }

    @Data
    public static class RoleSaveReq {
        private String code;
        private String name;
        private List<Long> permissionIds;
    }
}
