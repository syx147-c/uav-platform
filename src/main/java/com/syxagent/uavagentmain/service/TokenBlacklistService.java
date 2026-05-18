package com.syxagent.uavagentmain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT Token 黑名单服务 — 基于 Redis
 *
 * 问题：JWT 无状态，签发后无法撤销，用户登出后 Token 仍然有效。
 * 解决：登出时将 Token 加入 Redis 黑名单，TTL = Token 剩余有效期。
 *       JwtAuthenticationFilter 验证时先查黑名单，命中则拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "jwt:blacklist:";

    /**
     * 将 Token 加入黑名单
     * @param token  JWT 原始字符串
     * @param ttlSeconds  Token 剩余有效秒数（与 JWT 过期时间对齐）
     */
    public void blacklist(String token, long ttlSeconds) {
        if (ttlSeconds <= 0) return; // 已过期的不需要加入黑名单
        String key = KEY_PREFIX + token;
        stringRedisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        log.debug("Token 已加入黑名单，TTL={}s", ttlSeconds);
    }

    /**
     * 检查 Token 是否在黑名单中（已被登出）
     * @param token JWT 原始字符串
     * @return true = 已失效
     */

















    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + token));
    }
}
