package com.syxagent.uavagentmain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 遥测数据 Redis 缓存服务
 *
 * 设计意图：
 * - Agent 的 queryTelemetry 频繁被 LLM 调用，每次都走 Bridge HTTP 浪费资源
 * - Redis 缓存遥测快照，TTL 5 秒，保证数据新鲜度的同时减少 Bridge 压力
 * - 相比 in-memory volatile 字段：支持多节点共享（未来扩展）、自动过期、监控 Redis 命中率
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "telemetry:latest";
    private static final long TTL_SECONDS = 5; // 5 秒过期，平衡实时性与性能

    /** 写入遥测缓存 */
    public void put(Map<String, Object> telemetry) {
        redisTemplate.opsForValue().set(CACHE_KEY, telemetry, TTL_SECONDS, TimeUnit.SECONDS);
    }

    /** 读取遥测缓存 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        // 反序列化可能返回 LinkedHashMap，需要转换
        try {
            return objectMapper.convertValue(cached, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** 缓存是否命中 */
    public boolean has() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_KEY));
    }

    /** 手动清除缓存（仿真中断时） */
    public void clear() {
        redisTemplate.delete(CACHE_KEY);
    }
}
