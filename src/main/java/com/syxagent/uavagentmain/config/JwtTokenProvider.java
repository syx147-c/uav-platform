package com.syxagent.uavagentmain.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 工具类
 * HS256 签名，生成/验证/解析 Access Token
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${jwt.secret:UAV-Agent-Platform-JWT-Secret-Key-2026-Phase4}") String secret,
                            @Value("${jwt.expiration-ms:7200000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** 生成 Access Token */
    public String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 从 Token 中提取用户名 */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 从 Token 中提取角色 */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /** 获取 Token 剩余有效秒数（用于黑名单 TTL 对齐） */
    public long getRemainingTtlSeconds(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            long remaining = exp.getTime() - System.currentTimeMillis();
            return Math.max(0, remaining / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 验证 Token 是否有效 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
