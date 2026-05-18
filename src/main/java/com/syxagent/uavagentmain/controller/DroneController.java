package com.syxagent.uavagentmain.controller;

import com.syxagent.uavagentmain.service.MavsdkBridgeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 无人机控制 REST 接口（快通道 — 直连 MAVSDK Bridge，不经 LLM）
 */
@Tag(name = "无人机快通道", description = "直接控制无人机 — 绕过 LLM Agent，低延迟发送飞控指令")
@Slf4j
@RestController
@RequestMapping("/api/drone")
@RequiredArgsConstructor
public class DroneController {

    private final MavsdkBridgeClient bridge;

    @Operation(summary = "获取遥测", description = "获取无人机当前 GPS/高度/速度/电量/状态")
    @GetMapping("/telemetry")
    public Map<String, Object> getTelemetry() {
        return bridge.getTelemetry();
    }

    @Operation(summary = "起飞", description = "解锁电机并起飞到默认 10m 高度")
    @PostMapping("/takeoff")
    public String takeoff() {
        return bridge.takeoff();
    }

    @Operation(summary = "降落", description = "无人机下降到地面并自动锁定电机")
    @PostMapping("/land")
    public String land() {
        return bridge.land();
    }

    @Operation(summary = "悬停", description = "紧急悬停 — 停止所有水平移动，保持当前位置高度")
    @PostMapping("/hold")
    public String hold() {
        return bridge.hold();
    }

    @Operation(summary = "解锁电机", description = "ARM — 解锁电机（起飞前自动调用，通常无需手动）")
    @PostMapping("/arm")
    public String arm() {
        return bridge.arm();
    }

    @Operation(summary = "锁定电机", description = "DISARM — 立即停止电机运转（仅地面使用）")
    @PostMapping("/disarm")
    public String disarm() {
        return bridge.disarm();
    }

    @Operation(summary = "返航", description = "RTL — 自动飞回起飞点并降落")
    @PostMapping("/rtl")
    public String rtl() {
        return bridge.rtl();
    }

    @Operation(summary = "飞航点", description = "飞往指定 GPS 坐标（纬度、经度、高度）")
    @PostMapping("/waypoint")
    public String waypoint(@RequestBody Map<String, Object> body) {
        double lat = ((Number) body.get("lat")).doubleValue();
        double lon = ((Number) body.get("lon")).doubleValue();
        double alt = ((Number) body.get("alt")).doubleValue();
        return bridge.sendWaypoint(lat, lon, alt);
    }

    @Operation(summary = "速度控制", description = "Offboard 模式：设置 NED 速度（vx/vy/vz/yaw）")
    @PostMapping("/velocity")
    public String velocity(@RequestBody Map<String, Object> body) {
        double vx = toDouble(body.get("vx"));
        double vy = toDouble(body.get("vy"));
        double vz = toDouble(body.get("vz"));
        double yaw = toDouble(body.get("yaw"));
        return bridge.sendVelocity(vx, vy, vz, yaw);
    }

    @Operation(summary = "Bridge 状态", description = "查询 MAVSDK Bridge 自身状态（版本、运行时间）")
    @GetMapping("/status")
    public Map<String, Object> status() {
        return bridge.getStatus();
    }

    @Operation(summary = "紧急停机", description = "立即发送悬停指令，记录告警日志")
    @PostMapping("/emergency")
    public Map<String, Object> emergency() {
        String result = bridge.hold();
        log.warn("EMERGENCY 指令已发送");
        return Map.of("result", result, "timestamp", System.currentTimeMillis());
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
