package com.knowledge.kb.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.entity.SysPermission;
import com.knowledge.kb.entity.SysRole;
import com.knowledge.kb.entity.SysRolePermission;
import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.entity.SysUserRole;
import com.knowledge.kb.mapper.KbMemberMapper;
import com.knowledge.kb.mapper.SysPermissionMapper;
import com.knowledge.kb.mapper.SysRoleMapper;
import com.knowledge.kb.mapper.SysRolePermissionMapper;
import com.knowledge.kb.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final KbMemberMapper kbMemberMapper;

    public AuthUser buildAuthUser(SysUser user, Long tenantId, Long kbId) {
        Set<Long> roleIds = new HashSet<>();
        Set<String> roleCodes = new HashSet<>();

        if (tenantId != null) {
            List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, user.getId())
                    .eq(SysUserRole::getTenantId, tenantId));
            for (SysUserRole ur : userRoles) {
                roleIds.add(ur.getRoleId());
            }
        }

        boolean tenantAdmin = false;
        if (!roleIds.isEmpty()) {
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
            for (SysRole role : roles) {
                roleCodes.add(role.getCode());
                if ("TENANT_ADMIN".equals(role.getCode())) {
                    tenantAdmin = true;
                }
            }
        }

        if (kbId != null && !tenantAdmin) {
            KbMember member = kbMemberMapper.selectOne(new LambdaQueryWrapper<KbMember>()
                    .eq(KbMember::getKbId, kbId)
                    .eq(KbMember::getUserId, user.getId())
                    .last("limit 1"));
            if (member != null) {
                roleIds.add(member.getRoleId());
                SysRole kbRole = roleMapper.selectById(member.getRoleId());
                if (kbRole != null) {
                    roleCodes.add(kbRole.getCode());
                }
            }
        }

        Set<String> permissions = resolvePermissions(roleIds);
        if (tenantAdmin) {
            permissions.addAll(permissionMapper.selectList(null).stream()
                    .map(SysPermission::getCode)
                    .collect(Collectors.toSet()));
        }

        return new AuthUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getDisplayName(),
                "ACTIVE".equalsIgnoreCase(user.getStatus()),
                permissions,
                roleCodes
        );
    }

    public Set<String> resolvePermissions(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        List<SysRolePermission> rps = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, roleIds));
        if (rps.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> permIds = rps.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toSet());
        return permissionMapper.selectBatchIds(permIds).stream()
                .map(SysPermission::getCode)
                .collect(Collectors.toSet());
    }
}
