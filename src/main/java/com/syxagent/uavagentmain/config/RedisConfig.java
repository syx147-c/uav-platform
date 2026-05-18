package com.syxagent.uavagentmain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置 — 序列化 + 连接池
 *
 * 用途：
 * 1. Token 黑名单（登出后失效 JWT）
 * 2. 遥测数据缓存（TTL 5s，减少 Bridge HTTP 调用）
 * 3. API 频率限制（滑动窗口计数器）
 */
@Configuration
public class RedisConfig {

    /** JSON 序列化 RedisTemplate — 用于存储对象（遥测快照、用户会话等） */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /** 字符串专用 RedisTemplate — 用于计数器、锁、Token 黑名单 */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
