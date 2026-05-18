package com.syxagent.uavagentmain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置类 — JWT 认证 + 接口权限控制
 *
 * 权限分级：
 * - /api/auth/**     公开（登录/注册）
 * - /ws/**            公开（WebSocket 遥测推送，JWT 无法在浏览器 WebSocket 握手时携带标准头）
 * - /actuator/health  公开（容器健康检查）
 * - /api/system/**    公开（系统健康检查）
 * - 其余 /api/**      需要携带有效的 JWT Bearer Token
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /** 密码编码器 — BCrypt 强度 10 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 前后端分离 REST API，禁用 CSRF
            .csrf(csrf -> csrf.disable())
            // 无状态会话 — 每次请求携带 JWT
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证接口 — 公开访问
                .requestMatchers("/api/auth/**").permitAll()
                // WebSocket 遥测推送 — 公开访问（浏览器 WebSocket API 不支持自定义头，JWT 通过 query param 传递）
                .requestMatchers("/ws/**").permitAll()
                // 健康检查 + 监控 — 公开给容器编排和 Prometheus 刮取
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/system/**").permitAll()
                // 所有业务 API — 需携带有效 JWT
                .requestMatchers("/api/**").authenticated()
                // 静态资源放通
                .anyRequest().permitAll()
            )
            // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
