package com.syxagent.uavagentmain.controller;

import com.syxagent.uavagentmain.service.MavsdkBridgeClient;              // MAVSDK Bridge HTTP 客户端
import lombok.RequiredArgsConstructor;                                     // Lombok：构造器注入
import org.springframework.web.bind.annotation.*;                          // Spring MVC 注解集合

import java.util.Map;                                                      // Java 集合：键值对

/**
 * 无人机控制 REST 接口
 * 提供前端调用飞控指令的 HTTP API
 *
 * 接口设计：
 * GET  /api/drone/telemetry  — 获取实时遥测（GPS、姿态、电量等）
 * POST /api/drone/takeoff     — 起飞到默认高度
 * POST /api/drone/land        — 返航降落
 * POST /api/drone/hold        — 紧急悬停
 */
@RestController                                                             // 组合注解 = @Controller + @ResponseBody（返回值自动序列化为 JSON）
@RequestMapping("/api/drone")                                               // 统一 URL 前缀：所有方法路径都以此开头
@RequiredArgsConstructor                                                    // 构造函数注入 Bridge 客户端
public class DroneController {

    private final MavsdkBridgeClient bridge;                                 // MAVSDK Bridge 客户端（通过构造器注入）

    /**
     * 获取无人机实时遥测数据
     * GET /api/drone/telemetry
     * 前端调用此接口获取当前位置、高度、电量等状态
     */
    @GetMapping("/telemetry")                                                // 处理 HTTP GET 请求
    public Map<String, Object> getTelemetry() {
        return bridge.getTelemetry();                                        // 委托 Bridge 客户端获取数据
    }

    /**
     * 发送起飞指令
     * POST /api/drone/takeoff
     */
    @PostMapping("/takeoff")                                                 // 处理 HTTP POST 请求
    public String takeoff() {
        return bridge.takeoff();
    }

    /**
     * 发送返航降落指令
     * POST /api/drone/land
     */
    @PostMapping("/land")
    public String land() {
        return bridge.land();
    }

    /**
     * 发送紧急悬停指令
     * POST /api/drone/hold
     * 此指令为安全快通道，不经 LLM、不经消息队列，直接发送到飞控
     */
    @PostMapping("/hold")
    public String hold() {
        return bridge.hold();
    }
}
