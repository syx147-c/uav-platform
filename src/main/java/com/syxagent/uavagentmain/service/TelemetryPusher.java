package com.syxagent.uavagentmain.service;

import com.fasterxml.jackson.databind.ObjectMapper;                        // Jackson：Java 对象与 JSON 之间的序列化/反序列化工具
import com.syxagent.uavagentmain.handler.TelemetryWebSocketHandler;        // WebSocket 处理器（用于广播）
import lombok.RequiredArgsConstructor;                                     // Lombok：构造器注入
import lombok.SneakyThrows;                                                // Lombok：自动捕获受检异常，避免写 try-catch
import lombok.extern.slf4j.Slf4j;                                          // Lombok：日志
import org.springframework.scheduling.annotation.EnableScheduling;         // Spring：启用定时任务支持
import org.springframework.scheduling.annotation.Scheduled;                 // Spring：标记定时方法
import org.springframework.stereotype.Service;                             // Spring：业务层 Bean

import java.util.Map;                                                      // Java 集合：键值对

/**
 * 遥测数据定时推送服务
 * 每 2 秒从 MAVSDK Bridge 拉取一次遥测数据，并通过 WebSocket 广播给所有前端客户端
 *
 * 数据流：
 * PX4 → MAVSDK Bridge(WSL2) → [HTTP] → Java 后端 → [WebSocket] → Vue 前端
 */
@Slf4j                                                                      // 日志
@Service                                                                     // 注册为 Spring 业务层 Bean
@EnableScheduling                                                           // 启用 Spring 定时任务
@RequiredArgsConstructor                                                     // 构造器注入所有 final 字段
public class TelemetryPusher {

    private final MavsdkBridgeClient bridge;                                  // MAVSDK Bridge HTTP 客户端
    private final TelemetryWebSocketHandler wsHandler;                        // WebSocket 广播处理器
    private final ObjectMapper mapper = new ObjectMapper();                   // Jackson JSON 序列化器

    /**
     * 每 2 秒执行一次：拉取遥测数据 → 序列化为 JSON → WebSocket 广播
     * fixedRate = 2000 表示从上次执行开始计时 2000ms 后执行下次
     */
    @Scheduled(fixedRate = 2000)                                              // 每 2 秒触发一次
    @SneakyThrows                                                             // 自动捕获并抛出受检异常
    public void pushTelemetry() {
        Map<String, Object> data = bridge.getTelemetry();                     // 通过 HTTP 调用 Python Bridge 获取遥测数据
        if (data.containsKey("error")) {                                      // 如果获取失败（Bridge 未运行或不可达）
            return;                                                           // 跳过本次推送
        }
        String json = mapper.writeValueAsString(data);                        // 将 Map 序列化为 JSON 字符串
        wsHandler.broadcast(json);                                            // 通过 WebSocket 广播给所有前端客户端
    }
}
