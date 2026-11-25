package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.kb.audit.Audited;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.dto.PageResult;
import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.entity.SysUserRole;
import com.knowledge.kb.entity.SysUserTenant;
import com.knowledge.kb.mapper.SysUserMapper;
import com.knowledge.kb.mapper.SysUserRoleMapper;
import com.knowledge.kb.mapper.SysUserTenantMapper;
import com.knowledge.kb.security.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final SysUserMapper userMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<Map<String, Object>> page(long page, long size, String keyword) {
        Long tenantId = TenantContext.requireTenantId();
        List<SysUserTenant> links = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, tenantId));
        List<Long> userIds = links.stream().map(SysUserTenant::getUserId).toList();
        if (userIds.isEmpty()) {
            return new PageResult<>(0, page, size, List.of());
        }
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword));
        }
        q.orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> records = p.getRecords().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("displayName", u.getDisplayName());
            m.put("status", u.getStatus());
            m.put("createdAt", u.getCreatedAt());
            List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, u.getId())
                            .eq(SysUserRole::getTenantId, tenantId))
                    .stream().map(SysUserRole::getRoleId).toList();
            m.put("roleIds", roleIds);
            return m;
        }).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), records);
    }

    @Audited(action = "USER_CREATE", resourceType = "USER")
    @Transactional
    public Long create(UserCreateReq req) {
        Long tenantId = TenantContext.requireTenantId();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName());
        user.setStatus(req.getStatus() == null ? "ACTIVE" : req.getStatus());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        SysUserTenant link = new SysUserTenant();
        link.setUserId(user.getId());
        link.setTenantId(tenantId);
        link.setIsDefault(1);
        link.setCreatedAt(LocalDateTime.now());
        userTenantMapper.insert(link);

        bindRoles(user.getId(), tenantId, req.getRoleIds());
        return user.getId();
    }

    @Audited(action = "USER_UPDATE", resourceType = "USER")
    @Transactional
    public void update(Long id, UserUpdateReq req) {
        Long tenantId = TenantContext.requireTenantId();
        ensureInTenant(id, tenantId);
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BizException("用户不存在");
        if (req.getDisplayName() != null) user.setDisplayName(req.getDisplayName());
        if (req.getStatus() != null) user.setStatus(req.getStatus());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        if (req.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, id)
                    .eq(SysUserRole::getTenantId, tenantId));
            bindRoles(id, tenantId, req.getRoleIds());
        }
    }

    private void bindRoles(Long userId, Long tenantId, List<Long> roleIds) {
        if (roleIds == null) return;
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setTenantId(tenantId);
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }
    }

    private void ensureInTenant(Long userId, Long tenantId) {
        Long c = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId)
                .eq(SysUserTenant::getTenantId, tenantId));
        if (c == null || c == 0) throw new BizException("用户不在当前组织");
    }

    @Data
    public static class UserCreateReq {
        private String username;
        private String password;
        private String displayName;
        private String status;
        private List<Long> roleIds;
    }

    @Data
    public static class UserUpdateReq {
        private String displayName;
        private String password;
        private String status;
        private List<Long> roleIds;
    }
}
