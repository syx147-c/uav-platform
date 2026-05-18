package com.syxagent.uavagentmain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 飞控指令幂等守卫 — 防止同一指令被重复执行
 *
 * 为什么需要幂等？
 * - 用户可能因网络超时重试同一条指令（如点击两次"起飞"）
 * - Agent 可能因 LLM 幻觉重复调用同一个工具
 * - 网络重传可能导致 Bridge 收到重复的 HTTP 请求
 *
 * 方案：Redis SETNX（SET if Not eXists）实现分布式去重
 * - Key = idempotency:{commandType}:{hash}，Value = "1"，TTL = 10 秒
 * - 首次调用：SETNX 成功 → 执行指令
 * - 重复调用：SETNX 失败 → 返回"指令已发送，请勿重复"
 *
 * 为什么 TTL = 10s？
 * - 飞控指令执行很快（起飞~2s、降落~3s、航点~5s）
 * - 10s 足够覆盖正常执行 + 网络延迟，过期后允许重发（如 RTL 失败后再次 RTL）
 *
 * 为什么用 Redis 而不是 synchronized？
 * - Redis 是真正的分布式锁，即使重启也保持状态
 * - synchronized 只在单机 JVM 内有效，重启后丢失
 * - Redis Key 自动过期，不会永久占内存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandIdempotencyGuard {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String PREFIX = "idempotency:";
    private static final int TTL_SECONDS = 10;

    /**
     * 检查指令是否重复
     *
     * @param commandType 指令类型（takeoff/land/hold/rtl/waypoint/velocity）
     * @param contentHash 指令内容的哈希（如 "takeoff:50" 表示起飞到 50m）
     * @return true = 首次调用（允许执行），false = 重复调用（拒绝）
     */
    public boolean allow(String commandType, String contentHash) {
        String key = PREFIX + commandType + ":" + contentHash;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(success)) {
            log.warn("幂等拒绝: {}={}, 指令已发送过（10s 内有效）", commandType, contentHash);
            return false;
        }
        return true;
    }

    /** 生成 UUID 作为幂等键 */
    public String generateKey() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
