package com.knowledge.kb.security;

import com.knowledge.kb.entity.SysUser;
import com.knowledge.kb.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.DispatcherType;
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

    /**
     * SSE / SseEmitter 会触发 ASYNC 二次分发；默认跳过 ASYNC 会导致收尾时无认证 → AccessDenied，
     * 响应已提交后连接挂起，前端一直显示「生成中」。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

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
            // 请求线程已启动异步（SSE）时不要清掉上下文，留给 ASYNC 分发使用；ASYNC 结束时再清
            if (request.getDispatcherType() == DispatcherType.ASYNC || !request.isAsyncStarted()) {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        }
    }
}
