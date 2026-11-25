package com.knowledge.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.kb.common.BizException;
import com.knowledge.kb.config.KbProperties;
import com.knowledge.kb.dto.LoginRequest;
import com.knowledge.kb.dto.TokenResponse;
import com.knowledge.kb.entity.SysRefreshToken;
import com.knowledge.kb.entity.SysTenant;
import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.entity.SysUserTenant;
import com.knowledge.kb.mapper.SysRefreshTokenMapper;
import com.knowledge.kb.mapper.SysTenantMapper;
import com.knowledge.kb.mapper.SysUserMapper;
import com.knowledge.kb.mapper.SysUserTenantMapper;
import com.knowledge.kb.security.AuthUser;
import com.knowledge.kb.security.JwtService;
import com.knowledge.kb.security.PermissionService;
import com.knowledge.kb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final SysTenantMapper tenantMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final KbProperties kbProperties;
    private final AuditService auditService;

    public TokenResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditService.record("LOGIN", "USER", null, false, Map.of("username", request.getUsername()));
            throw new BizException(401, "用户名或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(403, "用户已禁用");
        }
        Long tenantId = resolveTenantId(user.getId(), request.getTenantId());
        SysTenant tenant = tenantMapper.selectById(tenantId);
        AuthUser authUser = permissionService.buildAuthUser(user, tenantId, null);
        String access = jwtService.createAccessToken(user.getId(), user.getUsername());
        String refresh = issueRefreshToken(user.getId());
        auditService.record("LOGIN", "USER", String.valueOf(user.getId()), true, Map.of("tenantId", tenantId));
        return buildTokenResponse(access, refresh, authUser, tenant, listTenants(user.getId()));
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        SysRefreshToken row = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getToken, refreshToken)
                .last("limit 1"));
        if (row == null || row.getRevoked() != null && row.getRevoked() == 1
                || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(401, "刷新令牌无效或已过期");
        }
        SysUser user = userMapper.selectById(row.getUserId());
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(401, "用户无效");
        }
        row.setRevoked(1);
        refreshTokenMapper.updateById(row);
        Long tenantId = resolveTenantId(user.getId(), null);
        SysTenant tenant = tenantMapper.selectById(tenantId);
        AuthUser authUser = permissionService.buildAuthUser(user, tenantId, null);
        String access = jwtService.createAccessToken(user.getId(), user.getUsername());
        String refresh = issueRefreshToken(user.getId());
        return buildTokenResponse(access, refresh, authUser, tenant, listTenants(user.getId()));
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        SysRefreshToken row = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getToken, refreshToken)
                .last("limit 1"));
        if (row != null) {
            row.setRevoked(1);
            refreshTokenMapper.updateById(row);
        }
        auditService.record("LOGOUT", "USER", String.valueOf(SecurityUtils.currentUserId()), true, null);
    }

    public TokenResponse.UserInfo me(Long tenantId) {
        AuthUser current = SecurityUtils.currentUser();
        SysUser user = userMapper.selectById(current.getId());
        Long tid = tenantId != null ? tenantId : resolveTenantId(user.getId(), null);
        SysTenant tenant = tenantMapper.selectById(tid);
        AuthUser authUser = permissionService.buildAuthUser(user, tid, null);
        return TokenResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .tenantId(tid)
                .tenantName(tenant == null ? null : tenant.getName())
                .tenants(listTenants(user.getId()))
                .permissions(authUser.getPermissions())
                .roles(authUser.getRoleCodes())
                .build();
    }

    private Long resolveTenantId(Long userId, Long preferred) {
        List<SysUserTenant> links = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId));
        if (links.isEmpty()) {
            throw new BizException("用户未加入任何组织");
        }
        if (preferred != null) {
            boolean ok = links.stream().anyMatch(l -> preferred.equals(l.getTenantId()));
            if (!ok) {
                throw new BizException(403, "用户不属于该组织");
            }
            return preferred;
        }
        return links.stream()
                .filter(l -> l.getIsDefault() != null && l.getIsDefault() == 1)
                .map(SysUserTenant::getTenantId)
                .findFirst()
                .orElse(links.get(0).getTenantId());
    }

    private List<TokenResponse.TenantItem> listTenants(Long userId) {
        List<SysUserTenant> links = userTenantMapper.selectList(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getUserId, userId));
        List<TokenResponse.TenantItem> items = new ArrayList<>();
        for (SysUserTenant link : links) {
            SysTenant t = tenantMapper.selectById(link.getTenantId());
            if (t == null) continue;
            items.add(TokenResponse.TenantItem.builder()
                    .id(t.getId())
                    .code(t.getCode())
                    .name(t.getName())
                    .isDefault(link.getIsDefault() != null && link.getIsDefault() == 1)
                    .build());
        }
        return items;
    }

    private String issueRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        SysRefreshToken row = new SysRefreshToken();
        row.setUserId(userId);
        row.setToken(token);
        row.setExpiresAt(LocalDateTime.now().plusDays(kbProperties.getJwt().getRefreshTokenTtlDays()));
        row.setRevoked(0);
        row.setCreatedAt(LocalDateTime.now());
        refreshTokenMapper.insert(row);
        return token;
    }

    private TokenResponse buildTokenResponse(String access, String refresh, AuthUser authUser,
                                             SysTenant tenant, List<TokenResponse.TenantItem> tenants) {
        return TokenResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .expiresInSeconds(kbProperties.getJwt().getAccessTokenTtlMinutes() * 60)
                .user(TokenResponse.UserInfo.builder()
                        .id(authUser.getId())
                        .username(authUser.getUsername())
                        .displayName(authUser.getDisplayName())
                        .tenantId(tenant == null ? null : tenant.getId())
                        .tenantName(tenant == null ? null : tenant.getName())
                        .tenants(tenants)
                        .permissions(authUser.getPermissions())
                        .roles(authUser.getRoleCodes())
                        .build())
                .build();
    }
}
