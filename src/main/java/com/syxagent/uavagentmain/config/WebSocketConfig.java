package com.syxagent.uavagentmain.config;

import com.syxagent.uavagentmain.handler.TelemetryWebSocketHandler;        // 遥测 WebSocket 处理器
import lombok.RequiredArgsConstructor;                                     // Lombok：构造器注入
import org.springframework.context.annotation.Configuration;               // Spring：标记为配置类
import org.springframework.web.socket.config.annotation.EnableWebSocket;   // Spring WebSocket：启用 WebSocket 支持
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;// Spring WebSocket：配置器接口
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry; // Spring WebSocket：处理器注册中心

/**
 * WebSocket 配置类
 * 将 TelemetryWebSocketHandler 注册到 "/ws/telemetry" 路径
 * 前端通过 ws://localhost:8080/ws/telemetry 连接即可接收实时遥测推送
 */
@Configuration                                                              // 标记为 Spring 配置类（会被 @SpringBootApplication 扫描）
@EnableWebSocket                                                            // 启用 Spring WebSocket 自动配置
@RequiredArgsConstructor                                                    // 生成包含 handler 的构造函数
public class WebSocketConfig implements WebSocketConfigurer {               // 实现 WebSocket 配置器接口

    private final TelemetryWebSocketHandler handler;                        // 注入遥测处理器（构造器注入）

    /**
     * 注册 WebSocket 处理器和路径
     * @param registry Spring 提供的处理器注册中心
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/telemetry")                       // 将 handler 绑定到 /ws/telemetry 路径
                .setAllowedOrigins("*");                                    // 允许所有来源的跨域请求（开发阶段放通，生产环境需限制）
    }
}
