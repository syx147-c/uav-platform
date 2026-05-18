package com.syxagent.uavagentmain.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * API 频率限制拦截器 — Redis 滑动窗口算法
 *
 * 设计：
 * - 使用 Redis Sorted Set（ZSET）实现滑动窗口
 * - Key = 用户标识 + API 路径，Score = 请求时间戳
 * - 每次请求：清理窗口外的旧记录 → 计数 → 超阈值返回 429
 *
 * 规则：
 * - /api/agent/chat ：60 秒内最多 20 次（LLM 调用成本高）
 * - /api/drone/*    ：60 秒内最多 60 次（飞控指令可高频）
 * - /api/auth/*     ：60 秒内最多 30 次（登录注册容忍度高）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "ratelimit:";

    /** 检查频率限制 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true; // 非 API 不限制

        String clientId = resolveClientId(request);
        int limit = getLimit(path);
        long windowMs = 60_000; // 60 秒窗口

        String redisKey = KEY_PREFIX + clientId + ":" + path;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowMs;

        // Redis 原子操作：删除窗口外记录 + 添加当前请求 + 计数
        stringRedisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
        Long count = stringRedisTemplate.opsForZSet().zCard(redisKey);

        if (count != null && count >= limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
            log.warn("频率限制触发: client={}, path={}, count={}, limit={}", clientId, path, count, limit);
            return false;
        }

        stringRedisTemplate.opsForZSet().add(redisKey, String.valueOf(now), now);
        stringRedisTemplate.expire(redisKey, 120, TimeUnit.SECONDS); // key 2 分钟后自动清理
        return true;
    }

    /** 根据请求路径返回限流阈值 */
    private int getLimit(String path) {
        if (path.startsWith("/api/agent/chat"))  return 20; // LLM 调用成本高，限制严
        if (path.startsWith("/api/drone/"))      return 60; // 飞控指令高频
        if (path.startsWith("/api/auth/"))        return 30;
        return 100; // 其他 API 默认
    }

    /** 解析客户端标识：优先取用户名，其次取 IP */
    private String resolveClientId(HttpServletRequest request) {
        java.security.Principal principal = request.getUserPrincipal();
        if (principal != null) return principal.getName();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
