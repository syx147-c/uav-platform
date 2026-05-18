package com.syxagent.uavagentmain.handler;

import lombok.extern.slf4j.Slf4j;                                          // Lombok：日志注解
import org.springframework.stereotype.Component;                           // Spring：标记为组件 Bean
import org.springframework.web.socket.CloseStatus;                         // WebSocket 关闭状态
import org.springframework.web.socket.TextMessage;                         // WebSocket 文本消息
import org.springframework.web.socket.WebSocketSession;                    // WebSocket 会话对象（代表一个客户端连接）
import org.springframework.web.socket.handler.TextWebSocketHandler;        // Spring WebSocket 文本消息处理基类
import java.io.IOException;                                                // IO 异常
import java.util.Set;                                                      // 集合接口
import java.util.concurrent.CopyOnWriteArraySet;                           // 线程安全的 Set 实现，适合读多写少场景

/**
 * 遥测数据 WebSocket 处理器
 * 职责：
 * 1. 管理所有前端客户端的 WebSocket 连接
 * 2. 向前端实时推送无人机遥测数据（GPS、姿态、电量等）
 * 3. 支持心跳检测（ping/pong）
 * 使用线程安全的 CopyOnWriteArraySet 存储所有活跃连接
 */
@Slf4j                                                                      // 日志
@Component                                                                  // 注册为 Spring Bean
public class TelemetryWebSocketHandler extends TextWebSocketHandler {       // 继承 Spring 的文本 WebSocket 处理基类
    /**
     * 线程安全的连接集合
     * CopyOnWriteArraySet：写入时复制整个数组，读操作无锁，适合 WebSocket 连接这种"写少读多"的场景
     */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    /**
     * 客户端连接建立时的回调
     * @param session 新建立的 WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);                                              // 将新连接加入集合
        log.info("WebSocket 连接建立: {}, 当前连接数: {}", session.getId(), sessions.size());
    }

    /**
     * 客户端断开连接时的回调
     * @param session 断开的会话
     * @param status 关闭状态码
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);                                           // 从集合中移除断开的连接
        log.info("WebSocket 连接断开: {}, 当前连接数: {}", session.getId(), sessions.size());
    }

    /**
     * 收到客户端消息时的回调
     * 用于处理心跳检测：前端发送 "ping"，后端回复 "pong"
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();                              // 获取消息内容
        if ("ping".equals(payload)) {                                       // 心跳检测请求
            try {
                session.sendMessage(new TextMessage("pong"));               // 回复心跳
            } catch (IOException e) {
                log.warn("发送心跳回复失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 向所有已连接的前端客户端广播遥测数据
     * @param message JSON 格式的遥测数据字符串
     */
    public void broadcast(String message) {
        for (WebSocketSession session : sessions) {                         // 遍历所有连接
            if (session.isOpen()) {                                         // 只向仍处于打开状态的连接发送
                try {
                    session.sendMessage(new TextMessage(message));           // 发送文本消息
                } catch (IOException e) {
                    log.warn("向 {} 发送消息失败: {}", session.getId(), e.getMessage());
                }
            }
        }
    }
}
