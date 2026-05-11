package com.syxagent.uavagentmain.service;

import com.syxagent.uavagentmain.config.MavsdkBridgeProperties;            // MAVSDK Bridge 配置属性
import lombok.RequiredArgsConstructor;                                     // Lombok：自动生成包含 final 字段的构造器，用于构造函数注入
import lombok.extern.slf4j.Slf4j;                                          // Lombok：自动生成 SLF4J 日志对象 log
import org.springframework.stereotype.Service;                             // Spring：标记为业务层 Bean
import org.springframework.web.client.RestTemplate;                        // Spring：HTTP 客户端，用于调用 REST API

import java.util.Map;                                                      // Java 集合：键值对，用于存储 JSON 响应

/**
 * MAVSDK Bridge HTTP 客户端
 * 职责：通过 HTTP 协议调用 WSL2 中运行的 Python FastAPI Bridge 服务
 * 将自然语言指令最终转换为对无人机的控制命令
 *
 * 调用链路：
 * DroneController → MavsdkBridgeClient → [HTTP] → FastAPI Bridge → [MAVSDK] → PX4 → Gazebo
 */
@Slf4j                                                                      // 编译时生成 log 日志对象
@Service                                                                     // 注册为 Spring 业务层 Bean
@RequiredArgsConstructor                                                     // 生成包含所有 final 字段的构造函数
public class MavsdkBridgeClient {

    private final MavsdkBridgeProperties props;                              // 注入 Bridge 配置（包含 URL）
    private final RestTemplate restTemplate = new RestTemplate();            // Spring 的 HTTP 客户端工具

    /**
     * 获取无人机当前遥测数据
     * 调用 FastAPI 的 GET /telemetry 接口
     */
    public Map<String, Object> getTelemetry() {
        try {
            // 发送 HTTP GET 请求到 Python Bridge，获取 JSON 格式的遥测数据
            return restTemplate.getForObject(props.getUrl() + "/telemetry", Map.class);
        } catch (Exception e) {
            // 如果 WSL2 中的 Bridge 未启动或网络不通，记录警告并返回错误信息
            log.warn("无法获取遥测数据: {}", e.getMessage());
            return Map.of("error", "bridge_unreachable");                   // Map.of() 创建一个不可变的单键 Map
        }
    }

    /**
     * 发送起飞指令
     * 调用 FastAPI 的 POST /takeoff 接口
     */
    public String takeoff() {
        try {
            // POST 请求体为 null，因为起飞不携带附加参数
            restTemplate.postForObject(props.getUrl() + "/takeoff", null, Map.class);
            return "ok";                                                    // 起飞指令发送成功
        } catch (Exception e) {
            log.warn("起飞指令发送失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 发送返航降落指令
     * 调用 FastAPI 的 POST /land 接口
     */
    public String land() {
        try {
            restTemplate.postForObject(props.getUrl() + "/land", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("降落指令发送失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 发送紧急悬停指令（快通道 - 无锁、无队列、立即执行）
     * 调用 FastAPI 的 POST /hold 接口
     */
    public String hold() {
        try {
            restTemplate.postForObject(props.getUrl() + "/hold", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("悬停指令发送失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }
}
