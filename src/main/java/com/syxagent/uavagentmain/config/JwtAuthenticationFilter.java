package com.syxagent.uavagentmain.config;

import com.syxagent.uavagentmain.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 — 每次请求执行一次
 *
 * 流程：
 * 1. 从 Authorization 头提取 Bearer Token
 * 2. 验证 JWT 签名 + 过期时间
 * 3. 查询 Redis 黑名单（已登出的 Token 拒绝）
 * 4. 通过 → 设置 SecurityContext（Spring Security 后续鉴权使用）
 * 5. 失败 → 不设置 Context，由 SecurityConfig 的 .authenticated() 返回 401
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtProvider;
    private final TokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        // Step 1: 验证 JWT 签名 + 过期
        if (!jwtProvider.validateToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        // Step 2: Redis 黑名单检查（登出后拒绝）
        if (blacklistService.isBlacklisted(token)) {
            log.debug("Token 已被加入黑名单，拒绝访问: {}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        // Step 3: 设置认证上下文
        String username = jwtProvider.getUsername(token);
        String role = jwtProvider.getRole(token);
        var auth = new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority(role != null ? "ROLE_" + role : "ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    /** 从 Authorization: Bearer <token> 头提取 JWT */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
