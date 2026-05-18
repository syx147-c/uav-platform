package com.syxagent.uavagentmain.agent.tool;

import com.syxagent.uavagentmain.service.MavsdkBridgeClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 飞行控制工具集 v2.0
 * LLM 通过 @Tool 注解调用这些方法操控无人机
 */
@Slf4j
@Component
public class FlightControlTools {

    private final MavsdkBridgeClient bridge;

    /** 缓存的最近一次遥测 */
    private volatile Map<String, Object> latestTelemetry = Map.of();

    /** 工具调用记录 */
    private final List<Object[]> callRecords = new ArrayList<>();

    public FlightControlTools(MavsdkBridgeClient bridge) {
        this.bridge = bridge;
    }

    private void record(String toolName, String result, boolean ok) {
        synchronized (callRecords) {
            callRecords.add(new Object[] { toolName, result, ok ? "ok" : "fail" });
        }
    }

    public List<Object[]> drainCallRecords() {
        synchronized (callRecords) {
            List<Object[]> copy = new ArrayList<>(callRecords);
            callRecords.clear();
            return copy;
        }
    }

    public void updateTelemetry(Map<String, Object> data) {
        if (data != null && !data.containsKey("error")) {
            this.latestTelemetry = data;
        }
    }

    /** 直接获取遥测（供 ClosedLoopMonitor 使用，不产生工具记录） */
    public Map<String, Object> getLatestTelemetry() {
        return latestTelemetry;
    }

    // ═══════════════════════════════════════════════════
    // LLM 可调用工具
    // ═══════════════════════════════════════════════════

    @Tool("查询无人机当前遥测数据（GPS坐标、高度、速度、电量、飞行状态）")
    public String queryTelemetry() {
        log.info("Tool: queryTelemetry()");
        Map<String, Object> data = bridge.getTelemetry();
        if (data.containsKey("error")) {
            record("queryTelemetry", "MAVSDK Bridge 未连接", false);
            return "无法获取遥测数据，MAVSDK Bridge 可能未连接";
        }
        this.latestTelemetry = data;

        double alt = ((Number) data.getOrDefault("altitude", 0.0)).doubleValue();
        boolean airborne = Math.abs(alt) > 0.5;

        String msg = String.format(
            "当前遥测: 纬度=%.6f, 经度=%.6f, 高度=%.2fm, 电量=%s%%, GPS=%s, 状态=%s",
            ((Number) data.getOrDefault("latitude", 0.0)).doubleValue(),
            ((Number) data.getOrDefault("longitude", 0.0)).doubleValue(),
            alt,
            data.getOrDefault("battery", 0),
            data.getOrDefault("gps_fix", 0),
            airborne ? "飞行中" : "地面"
        );
        record("queryTelemetry", msg, true);
        return msg;
    }

    @Tool("起飞到指定高度（米），默认10m，最大120m")
    public String takeoff(@P("目标高度（米），默认10") double altitude) {
        log.info("Tool: takeoff(altitude={})", altitude);
        double alt = altitude > 0 ? altitude : 10;
        if (alt > 120) alt = 120;
        String result = bridge.takeoff(alt);
        boolean ok = "ok".equals(result);
        String msg = ok ? "起飞指令已发送，目标高度 " + alt + " 米"
                        : "起飞失败: " + result;
        record("takeoff", msg, ok);
        return msg;
    }

    @Tool("降落无人机到地面并自动上锁")
    public String land() {
        log.info("Tool: land()");
        String result = bridge.land();
        boolean ok = "ok".equals(result);
        record("land", ok ? "降落指令已发送" : "降落失败: " + result, ok);
        return ok ? "降落指令已发送，无人机正在降落" : "降落失败: " + result;
    }

    @Tool("紧急悬停 — 立即停止所有水平移动并保持当前位置和高度")
    public String hold() {
        log.info("Tool: hold()");
        String result = bridge.hold();
        boolean ok = "ok".equals(result);
        record("hold", ok ? "紧急悬停已触发" : "悬停失败: " + result, ok);
        return ok ? "紧急悬停指令已发送，无人机保持当前位置" : "悬停失败: " + result;
    }

    @Tool("飞往指定 GPS 航点（纬度、经度、高度）")
    public String gotoWaypoint(
            @P("目标纬度，如 39.9042") double lat,
            @P("目标经度，如 116.4074") double lon,
            @P("目标高度（米）") double alt) {
        log.info("Tool: gotoWaypoint(lat={}, lon={}, alt={})", lat, lon, alt);
        if (lat == 0 && lon == 0) {
            record("gotoWaypoint", "坐标 (0,0) 为非法值", false);
            return "错误: 坐标 (0, 0) 为非法值，拒绝执行";
        }
        String result = bridge.sendWaypoint(lat, lon, alt);
        boolean ok = "ok".equals(result);
        String msg = ok ? "航点已发送: (" + lat + ", " + lon + "), 高度 " + alt + "m"
                        : "航点失败: " + result;
        record("gotoWaypoint", msg, ok);
        return msg;
    }

    @Tool("在当前 GPS 位置悬停指定时长（秒），0=无限悬停等待后续指令")
    public String hover(@P("悬停时长（秒），0 表示无限悬停") int duration) {
        log.info("Tool: hover(duration={})", duration);
        bridge.hold();
        if (duration > 0) {
            int waitSec = Math.min(duration, 30);
            try {
                Thread.sleep(waitSec * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String msg = "悬停完成，已等待 " + waitSec + " 秒";
            record("hover", msg, true);
            return msg;
        }
        String msg = "无人机在当前位置无限悬停，等待后续指令";
        record("hover", msg, true);
        return msg;
    }

    @Tool("返航 — 自动飞回起飞点并降落（Return To Launch）")
    public String returnToLaunch() {
        log.info("Tool: returnToLaunch()");
        String result = bridge.rtl();
        boolean ok = "ok".equals(result);
        record("returnToLaunch", ok ? "返航已触发" : "返航失败: " + result, ok);
        return ok ? "返航指令已发送，无人机正在返回起飞点" : "返航失败: " + result;
    }

    @Tool("解锁电机（ARM），起飞前自动调用，通常无需手动调用")
    public String arm() {
        log.info("Tool: arm()");
        String result = bridge.arm();
        boolean ok = "ok".equals(result);
        record("arm", ok ? "电机已解锁" : "解锁失败: " + result, ok);
        return ok ? "电机已解锁（ARM）" : "解锁失败: " + result;
    }
}
