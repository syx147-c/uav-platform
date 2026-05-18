package com.syxagent.uavagentmain.controller;

import com.syxagent.uavagentmain.config.JwtTokenProvider;
import com.syxagent.uavagentmain.entity.SysUser;
import com.syxagent.uavagentmain.mapper.SysUserMapper;
import com.syxagent.uavagentmain.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证控制器 — JWT 无状态认证 + Redis Token 黑名单"
 */
@Tag(name = "认证", description = "用户注册/登录/登出 — JWT HS256 令牌 + Redis 黑名单软撤销")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final JwtTokenProvider jwtProvider;
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Operation(summary = "用户登录", description = "验证用户名密码，返回 JWT Token（有效期 2h）")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        SysUser user = userMapper.selectByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        String token = jwtProvider.generateToken(username, user.getRole());
        return ResponseEntity.ok(Map.of(
            "token", token,
            "username", username,
            "role", user.getRole()
        ));
    }

    @Operation(summary = "用户注册", description = "创建新用户，密码 BCrypt 加密，返回 JWT Token")
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        if (userMapper.selectByUsername(username) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtProvider.generateToken(username, "USER");
        return ResponseEntity.ok(Map.of(
            "token", token,
            "username", username,
            "role", "USER"
        ));
    }

    @Operation(summary = "登出", description = "将当前 JWT Token 加入 Redis 黑名单，TTL 对齐 Token 剩余有效期，实现软撤销")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long remaining = jwtProvider.getRemainingTtlSeconds(token);
            blacklistService.blacklist(token, remaining);
        }
        return ResponseEntity.ok(Map.of("ok", true, "message", "已登出"));
    }
}
