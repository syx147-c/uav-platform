package com.syxagent.uavagentmain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syxagent.uavagentmain.config.MavsdkBridgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Perception HTTP Client — 调用 Bridge 的感知端点
 *
 * 架构:
 *   Java → HTTP → bridge.py (/perception/*) → YOLOv11 + ByteTrack + Depth Anything V2
 *
 * 端点映射:
 *   POST /perception/detect       → 目标检测
 *   POST /perception/track        → 多目标跟踪
 *   POST /perception/depth        → 单目深度估计
 *   POST /perception/avoid/enable → 启用避障
 *   POST /perception/avoid/step   → 单帧避障推理
 *   POST /perception/follow       → 目标跟随
 *   GET  /perception/status       → 感知状态
 */
@Slf4j
@Service
public class PerceptionClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerceptionClient(MavsdkBridgeProperties props) {
        this.baseUrl = props.getUrl();

        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30)); // 推理可能较慢，放宽超时
        this.restTemplate = new RestTemplate(factory);
    }

    // ═══════════════════════════════════════════════════
    // 目标检测
    // ═══════════════════════════════════════════════════

    /**
     * 对单帧图像运行 YOLOv11 目标检测
     *
     * @param imageBytes JPEG/PNG 图片字节
     * @return 检测结果: {detections: [{bbox, confidence, class_name, ...}], count, elapsed_ms}
     */
    public Map<String, Object> detect(byte[] imageBytes) {
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            var body = Map.of("image_base64", b64);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/detect", body, Map.class);
            return result != null ? result : Map.of("error", "empty_response");
        } catch (Exception e) {
            log.warn("目标检测失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 多目标跟踪
    // ═══════════════════════════════════════════════════

    /**
     * 运行检测 + ByteTrack 跟踪
     *
     * @param imageBytes 图片字节
     * @return 跟踪结果: {tracks: [{track_id, bbox, center, ...}], active_tracks}
     */
    public Map<String, Object> track(byte[] imageBytes) {
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            var body = Map.of("image_base64", b64);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/track", body, Map.class);
            return result != null ? result : Map.of("error", "empty_response");
        } catch (Exception e) {
            log.warn("目标跟踪失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 单目深度估计
    // ═══════════════════════════════════════════════════

    /**
     * Depth Anything V2 单目深度估计
     *
     * @param imageBytes 图片字节
     * @return 深度结果: {obstacle_regions: [{sector, min_depth, obstacle}], obstacle_detected, depth_map_shape, elapsed_ms}
     */
    public Map<String, Object> getDepth(byte[] imageBytes) {
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            var body = Map.of("image_base64", b64);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/depth", body, Map.class);
            return result != null ? result : Map.of("error", "empty_response");
        } catch (Exception e) {
            log.warn("深度估计失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 避障控制
    // ═══════════════════════════════════════════════════

    /**
     * 启用实时障碍物避让
     */
    public Map<String, Object> enableAvoidance() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/avoid/enable", null, Map.class);
            return result != null ? result : Map.of("status", "ok");
        } catch (Exception e) {
            log.warn("启用避障失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 禁用障碍物避让
     */
    public Map<String, Object> disableAvoidance() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/avoid/disable", null, Map.class);
            return result != null ? result : Map.of("status", "ok");
        } catch (Exception e) {
            log.warn("禁用避障失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 单帧避障: 推理 + 执行避障速度指令
     *
     * @param imageBytes 图片字节
     * @return {command: {vx, vy, vz, yaw, sector, obstacle_detected}, executed}
     */
    public Map<String, Object> avoidStep(byte[] imageBytes) {
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            var body = Map.of("image_base64", b64);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/avoid/step", body, Map.class);
            return result != null ? result : Map.of("error", "empty_response");
        } catch (Exception e) {
            log.warn("避障步进失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 目标跟随
    // ═══════════════════════════════════════════════════

    /**
     * 检测 + 跟踪 + 跟随目标 (person/car)
     *
     * @param imageBytes 图片字节
     * @return {status, target: {track_id, bbox, center}, command: {vx, vy, vz, yaw}}
     */
    public Map<String, Object> follow(byte[] imageBytes) {
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            var body = Map.of("image_base64", b64);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(
                    baseUrl + "/perception/follow", body, Map.class);
            return result != null ? result : Map.of("error", "empty_response");
        } catch (Exception e) {
            log.warn("目标跟随失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 状态查询
    // ═══════════════════════════════════════════════════

    public Map<String, Object> getPerceptionStatus() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(
                    baseUrl + "/perception/status", Map.class);
            return result != null ? result : Map.of("error", "empty_response");
        } catch (Exception e) {
            log.warn("感知状态查询失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}
