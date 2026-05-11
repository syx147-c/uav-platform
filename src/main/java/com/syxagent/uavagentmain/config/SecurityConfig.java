package com.syxagent.uavagentmain.config;

import org.springframework.context.annotation.Bean;                        // Spring：声明 Bean 的方法
import org.springframework.context.annotation.Configuration;               // Spring：配置类
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // Spring Security：HTTP 安全配置构建器
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Spring Security：启用 Web 安全
import org.springframework.security.config.http.SessionCreationPolicy;     // Spring Security：会话创建策略枚举
import org.springframework.security.web.SecurityFilterChain;               // Spring Security：安全过滤器链

/**
 * Spring Security 安全配置类
 * 当前为开发阶段，放通所有请求，不做认证拦截
 * 后续 Phase 4 接入 JWT 认证时再细化权限控制
 */
@Configuration                                                              // 标记为配置类
@EnableWebSecurity                                                          // 启用 Spring Security 的 Web 安全功能
public class SecurityConfig {

    /**
     * 配置安全过滤器链
     * @param http Spring Security 提供的 HTTP 安全构建器
     * @return 安全过滤器链 Bean
     */
    @Bean                                                                   // 声明返回值是一个 Spring Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                                   // 关闭 CSRF 保护（WebSocket 连接不需要 CSRF Token）
            .sessionManagement(sm -> sm.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))                       // 设为无状态模式（不使用 HttpSession，为后续 JWT 做准备）
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws/**", "/api/**", "/telemetry")         // WebSocket 端点、REST API 接口
                    .permitAll()                                            // 放通无需认证
                .anyRequest().permitAll()                                   // 所有其他请求也放通
            );
        return http.build();                                                // 构建并返回安全过滤器链
    }
}
