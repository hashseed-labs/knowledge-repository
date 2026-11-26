package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.kb.audit.Audited;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.entity.KbMember;
import com.knowledge.kb.entity.KbSpace;
import com.knowledge.kb.entity.SysRole;
import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.mapper.KbMemberMapper;
import com.knowledge.kb.mapper.KbSpaceMapper;
import com.knowledge.kb.mapper.SysRoleMapper;
import com.knowledge.kb.mapper.SysUserMapper;
import com.knowledge.kb.security.AuthUser;
import com.knowledge.kb.security.SecurityUtils;
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
public class KbSpaceService {

    private final KbSpaceMapper kbSpaceMapper;
    private final KbMemberMapper kbMemberMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public List<Map<String, Object>> listAccessible() {
        Long tenantId = TenantContext.requireTenantId();
        AuthUser user = SecurityUtils.currentUser();
        List<KbSpace> spaces;
        if (user.isTenantAdmin() || user.hasPermission("kb:space:manage")) {
            spaces = kbSpaceMapper.selectList(new LambdaQueryWrapper<KbSpace>()
                    .eq(KbSpace::getTenantId, tenantId)
                    .orderByDesc(KbSpace::getId));
        } else {
            List<Long> kbIds = kbMemberMapper.selectList(new LambdaQueryWrapper<KbMember>()
                            .eq(KbMember::getUserId, user.getId()))
                    .stream().map(KbMember::getKbId).toList();
            if (kbIds.isEmpty()) return List.of();
            spaces = kbSpaceMapper.selectList(new LambdaQueryWrapper<KbSpace>()
                    .eq(KbSpace::getTenantId, tenantId)
                    .in(KbSpace::getId, kbIds)
                    .orderByDesc(KbSpace::getId));
        }
        return spaces.stream().map(this::toMap).toList();
    }

    @Audited(action = "KB_CREATE", resourceType = "KB")
    @Transactional
    public Long create(KbSaveReq req) {
        Long tenantId = TenantContext.requireTenantId();
        AuthUser user = SecurityUtils.currentUser();
        KbSpace space = new KbSpace();
        space.setTenantId(tenantId);
        space.setName(req.getName());
        space.setDescription(req.getDescription());
        space.setStatus("ACTIVE");
        space.setCreatedBy(user.getId());
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());
        kbSpaceMapper.insert(space);

        SysRole managerRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, 0L)
                .eq(SysRole::getCode, "KB_MANAGER")
                .last("limit 1"));
        if (managerRole != null) {
            KbMember member = new KbMember();
            member.setKbId(space.getId());
            member.setUserId(user.getId());
            member.setRoleId(managerRole.getId());
            member.setCreatedAt(LocalDateTime.now());
            kbMemberMapper.insert(member);
        }
        return space.getId();
    }

    @Audited(action = "KB_UPDATE", resourceType = "KB")
    public void update(Long id, KbSaveReq req) {
        KbSpace space = requireTenantSpace(id);
        if (req.getName() != null) space.setName(req.getName());
        if (req.getDescription() != null) space.setDescription(req.getDescription());
        if (req.getStatus() != null) space.setStatus(req.getStatus());
        space.setUpdatedAt(LocalDateTime.now());
        kbSpaceMapper.updateById(space);
    }

    public List<Map<String, Object>> listMembers(Long kbId) {
        requireTenantSpace(kbId);
        return kbMemberMapper.selectList(new LambdaQueryWrapper<KbMember>().eq(KbMember::getKbId, kbId))
                .stream().map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", m.getId());
                    map.put("userId", m.getUserId());
                    map.put("roleId", m.getRoleId());
                    SysUser u = userMapper.selectById(m.getUserId());
                    SysRole r = roleMapper.selectById(m.getRoleId());
                    map.put("username", u == null ? null : u.getUsername());
                    map.put("displayName", u == null ? null : u.getDisplayName());
                    map.put("roleCode", r == null ? null : r.getCode());
                    map.put("roleName", r == null ? null : r.getName());
                    return map;
                }).toList();
    }

    @Audited(action = "KB_MEMBER_ADD", resourceType = "KB_MEMBER")
    public void addMember(Long kbId, MemberReq req) {
        requireTenantSpace(kbId);
        Long exists = kbMemberMapper.selectCount(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, req.getUserId()));
        if (exists != null && exists > 0) throw new BizException("成员已存在");
        KbMember member = new KbMember();
        member.setKbId(kbId);
        member.setUserId(req.getUserId());
        member.setRoleId(req.getRoleId());
        member.setCreatedAt(LocalDateTime.now());
        kbMemberMapper.insert(member);
    }

    @Audited(action = "KB_MEMBER_REMOVE", resourceType = "KB_MEMBER")
    public void removeMember(Long kbId, Long userId) {
        requireTenantSpace(kbId);
        kbMemberMapper.delete(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId));
    }

    public void assertKbAccess(Long kbId) {
        AuthUser user = SecurityUtils.currentUser();
        if (user.isTenantAdmin()) return;
        Long c = kbMemberMapper.selectCount(new LambdaQueryWrapper<KbMember>()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, user.getId()));
        if (c == null || c == 0) {
            throw new BizException(403, "无权访问该知识库");
        }
    }

    private KbSpace requireTenantSpace(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        KbSpace space = kbSpaceMapper.selectById(id);
        if (space == null || !tenantId.equals(space.getTenantId())) {
            throw new BizException("知识库不存在");
        }
        return space;
    }

    private Map<String, Object> toMap(KbSpace s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("tenantId", s.getTenantId());
        m.put("name", s.getName());
        m.put("description", s.getDescription());
        m.put("status", s.getStatus());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    @Data
    public static class KbSaveReq {
        private String name;
        private String description;
        private String status;
    }

    @Data
    public static class MemberReq {
        private Long userId;
        private Long roleId;
    }
}
