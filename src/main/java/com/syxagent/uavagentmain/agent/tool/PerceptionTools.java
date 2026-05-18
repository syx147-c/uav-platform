package com.syxagent.uavagentmain.agent.tool;

import com.syxagent.uavagentmain.service.PerceptionClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 感知工具集 — LLM 可通过 @Tool 调用视觉感知能力
 *
 * 涵盖四大 SOTA 算法:
 *   1. YOLOv11 — 目标检测
 *   2. ByteTrack v2 — 多目标跟踪
 *   3. Depth Anything V2 — 单目深度估计
 *   4. EGO-Planner-inspired — 避障规划
 *
 * 使用场景:
 *   "搜索前方区域是否有车辆"
 *   → LLM 调用 detectObjects() → 返回检测到的物体列表
 *
 *   "跟踪那个穿红衣服的人"
 *   → LLM 调用 trackTarget() → ByteTrack 分配唯一 ID 持续跟踪
 *
 *   "飞到目标位置，但自动避开路上的障碍物"
 *   → LLM 调用 enableObstacleAvoidance() → 启用避障后飞往目标
 *
 * 面试亮点:
 *   - Function Calling: LLM 通过 @Tool 注解自动决定何时调用感知能力
 *   - 多模态 Agent: 视觉感知 + 飞控执行 = 完整的感知-决策-执行闭环
 */
@Slf4j
@Component
public class PerceptionTools {

    private final PerceptionClient perceptionClient;

    public PerceptionTools(PerceptionClient perceptionClient) {
        this.perceptionClient = perceptionClient;
    }

    // ═══════════════════════════════════════════════════
    // LLM 可调用工具
    // ═══════════════════════════════════════════════════

    @Tool("检测无人机前方画面中的物体（人、车、自行车等），返回检测到的物体列表")
    public String detectObjects(
            @P("base64编码的摄像头图像数据，如果用户没有提供则传空字符串来获取最新感知结果")
            String imageBase64) {
        log.info("Tool: detectObjects() called");

        if (imageBase64 == null || imageBase64.isBlank()) {
            // 返回最近一次检测结果
            Map<String, Object> status = perceptionClient.getPerceptionStatus();
            if (status.containsKey("error")) {
                return "感知模块未就绪，无法检测物体。请确认 Bridge 已启动并初始化感知引擎。";
            }
            return String.format(
                "感知引擎状态: %s, 活跃跟踪数: %s, 处理帧率: %s FPS。请提供摄像头图像以获取具体检测结果。",
                status.getOrDefault("status", "unknown"),
                status.getOrDefault("active_tracks", 0),
                status.getOrDefault("processing_fps", 0)
            );
        }

        Map<String, Object> result = perceptionClient.detect(
                java.util.Base64.getDecoder().decode(imageBase64));

        if (result.containsKey("error")) {
            return "目标检测失败: " + result.get("error");
        }

        @SuppressWarnings("unchecked")
        var detections = (java.util.List<Map<String, Object>>) result.getOrDefault("detections", java.util.List.of());
        int count = (int) result.getOrDefault("count", 0);
        double elapsedMs = ((Number) result.getOrDefault("elapsed_ms", 0.0)).doubleValue();

        if (count == 0) {
            return "未检测到任何物体（耗时 " + String.format("%.1f", elapsedMs) + "ms）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("检测到 %d 个物体 (%.1fms):\n", count, elapsedMs));
        for (Map<String, Object> d : detections) {
            sb.append(String.format("  - %s (置信度: %.2f, 位置: %s)\n",
                    d.get("class_name"),
                    ((Number) d.getOrDefault("confidence", 0.0)).doubleValue(),
                    d.get("center")));
        }
        return sb.toString().trim();
    }

    @Tool("启用目标跟踪，持续追踪画面中的物体并分配唯一跟踪ID。若画面中有移动目标，可调用此工具开始跟踪")
    public String trackTarget(
            @P("base64编码的摄像头图像数据")
            String imageBase64) {
        log.info("Tool: trackTarget() called");

        if (imageBase64 == null || imageBase64.isBlank()) {
            return "请提供摄像头图像以开始跟踪。";
        }

        Map<String, Object> result = perceptionClient.track(
                java.util.Base64.getDecoder().decode(imageBase64));

        if (result.containsKey("error")) {
            return "目标跟踪失败: " + result.get("error");
        }

        @SuppressWarnings("unchecked")
        var tracks = (java.util.List<Map<String, Object>>) result.getOrDefault("tracks", java.util.List.of());
        int activeTracks = (int) result.getOrDefault("active_tracks", 0);

        if (activeTracks == 0) {
            return "未检测到可跟踪的运动目标。画面中可能没有明显物体。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("当前跟踪 %d 个目标:\n", activeTracks));
        for (Map<String, Object> t : tracks) {
            sb.append(String.format("  - ID=%s, 类别=%s, 置信度=%.2f, 位置=%s\n",
                    t.get("track_id"),
                    t.getOrDefault("class_id", "unknown"),
                    ((Number) t.getOrDefault("confidence", 0.0)).doubleValue(),
                    t.get("center")));
        }
        return sb.toString().trim();
    }

    @Tool("启用障碍物避让模式 — 无人机利用深度估计算法自动检测前方障碍物并规划安全路径绕行")
    public String enableObstacleAvoidance() {
        log.info("Tool: enableObstacleAvoidance() called");

        Map<String, Object> result = perceptionClient.enableAvoidance();

        if (result.containsKey("error")) {
            return "避障启用失败: " + result.get("error");
        }

        return String.format(
            "障碍物避让已启用。安全距离: %.0f米, 危险距离: %.0f米。无人机将自动避让前方障碍物。",
            ((Number) result.getOrDefault("safe_distance_m", 5.0)).doubleValue(),
            ((Number) result.getOrDefault("danger_distance_m", 2.0)).doubleValue()
        );
    }

    @Tool("禁用障碍物避让模式，恢复手动控制")
    public String disableObstacleAvoidance() {
        log.info("Tool: disableObstacleAvoidance() called");

        Map<String, Object> result = perceptionClient.disableAvoidance();

        if (result.containsKey("error")) {
            return "避障禁用失败: " + result.get("error");
        }

        return "障碍物避让已禁用，恢复手动控制模式。";
    }

    @Tool("让无人机自动跟随画面中检测到的目标（如人、车辆），计算跟随速度指令")
    public String followTarget(
            @P("base64编码的摄像头图像数据")
            String imageBase64) {
        log.info("Tool: followTarget() called");

        if (imageBase64 == null || imageBase64.isBlank()) {
            return "请提供摄像头图像以开始目标跟随。";
        }

        Map<String, Object> result = perceptionClient.follow(
                java.util.Base64.getDecoder().decode(imageBase64));

        if (result.containsKey("error")) {
            return "目标跟随失败: " + result.get("error");
        }

        String status = (String) result.getOrDefault("status", "unknown");

        if ("no_target".equals(status)) {
            return "未检测到可跟随的目标。画面中可能没有明显物体。";
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) result.get("target");
        @SuppressWarnings("unchecked")
        Map<String, Object> command = (Map<String, Object>) result.get("command");

        if (target == null || command == null) {
            return "跟随数据不可用。";
        }

        return String.format(
            "正在%s目标 ID=%s (类别=%s, 置信度=%.2f)。跟随指令: vx=%.1f m/s, vy=%.1f m/s, yaw=%.1f°。",
            status,
            target.get("track_id"),
            target.getOrDefault("class_id", "unknown"),
            ((Number) target.getOrDefault("confidence", 0.0)).doubleValue(),
            ((Number) command.getOrDefault("vx", 0.0)).doubleValue(),
            ((Number) command.getOrDefault("vy", 0.0)).doubleValue(),
            ((Number) command.getOrDefault("yaw", 0.0)).doubleValue()
        );
    }

    @Tool("获取当前感知系统的运行状态（检测/跟踪/深度/避障是否启用、处理帧率等）")
    public String queryPerceptionStatus() {
        log.info("Tool: queryPerceptionStatus() called");

        Map<String, Object> status = perceptionClient.getPerceptionStatus();

        if (status.containsKey("error")) {
            return "感知模块状态查询失败: " + status.get("error");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> models = (Map<String, Object>) status.getOrDefault("models", Map.of());

        return String.format(
            "感知系统状态:\n" +
            "  运行状态: %s\n" +
            "  目标检测: %s (YOLOv11)\n" +
            "  目标跟踪: %s (ByteTrack v2)\n" +
            "  深度估计: %s (Depth Anything V2)\n" +
            "  避障模式: %s (active=%s)\n" +
            "  处理帧率: %s FPS\n" +
            "  计算设备: %s",
            status.getOrDefault("status", "unknown"),
            Boolean.TRUE.equals(status.getOrDefault("detection_enabled", false)) ? "启用" : "禁用",
            Boolean.TRUE.equals(status.getOrDefault("tracking_enabled", false)) ? "启用" : "禁用",
            Boolean.TRUE.equals(status.getOrDefault("depth_enabled", false)) ? "启用" : "禁用",
            Boolean.TRUE.equals(status.getOrDefault("avoidance_enabled", false)) ? "启用" : "禁用",
            status.getOrDefault("avoidance_active", false),
            status.getOrDefault("processing_fps", 0.0),
            status.getOrDefault("device", "unknown")
        );
    }
}
