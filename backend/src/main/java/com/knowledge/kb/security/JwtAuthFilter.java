package com.knowledge.kb.security;

import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SysUserMapper userMapper;
    private final PermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantHeader = request.getHeader("X-Tenant-Id");
            String kbHeader = request.getHeader("X-Kb-Id");
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                TenantContext.setTenantId(Long.valueOf(tenantHeader.trim()));
            }
            if (kbHeader != null && !kbHeader.isBlank()) {
                TenantContext.setKbId(Long.valueOf(kbHeader.trim()));
            }

            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                Claims claims = jwtService.parse(token);
                Long userId = jwtService.userId(claims);
                SysUser user = userMapper.selectById(userId);
                if (user != null && "ACTIVE".equalsIgnoreCase(user.getStatus())) {
                    AuthUser authUser = permissionService.buildAuthUser(
                            user, TenantContext.getTenantId(), TenantContext.getKbId());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
