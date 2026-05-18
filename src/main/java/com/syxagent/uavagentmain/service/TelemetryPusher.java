package com.syxagent.uavagentmain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syxagent.uavagentmain.agent.tool.FlightControlTools;
import com.syxagent.uavagentmain.entity.UavTelemetrySnapshot;
import com.syxagent.uavagentmain.handler.TelemetryWebSocketHandler;
import com.syxagent.uavagentmain.mapper.UavTelemetrySnapshotMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 遥测数据定时推送服务 — 三层存储 + WebSocket 实时推送
 *
 * 数据流：
 * MAVSDK Bridge (HTTP) → TelemetryPusher → ① WebSocket 广播（实时）
 *                                          → ② Redis 缓存（Agent 查询，TTL 5s）
 *                                          → ③ MySQL 持久化（每 10s）
 *                                          → ④ MinIO 归档（每 60s，对象存储）
 */
@Slf4j
@Service
@EnableScheduling
public class TelemetryPusher {

    private final MavsdkBridgeClient bridge;
    private final TelemetryWebSocketHandler wsHandler;
    private final FlightControlTools flightTools;
    private final UavTelemetrySnapshotMapper snapshotMapper;
    private final TelemetryCacheService cacheService;
    private final MinioStorageService minioService;
    private final ObjectMapper mapper = new ObjectMapper();

    /** MySQL 持久化计数器：每 5 次调度（2s × 5 = 10s）触发一次入库 */
    private int persistCounter = 0;

    /** MinIO 对象存储归档计数器：每 30 次调度（2s × 30 = 60s）触发一次上传 */
    private int minioCounter = 0;

    /**
     * 构造注入：所有依赖由 Spring 容器自动装配。
     * bridge — MAVSDK 桥接客户端，拉取无人机原始遥测数据
     * wsHandler — WebSocket 处理器，负责将遥测 JSON 广播给所有前端连接
     * flightTools — Agent 工具集，缓存最新遥测供 LLM 查询（如电池、位置）
     * snapshotMapper — MyBatis 映射器，持久化遥测快照到 MySQL
     * cacheService — Redis 缓存服务，缓存最新一条遥测（TTL 5s）
     * minioService — MinIO 对象存储服务，归档遥测快照到 S3 兼容存储
     */
    public TelemetryPusher(MavsdkBridgeClient bridge,
                           TelemetryWebSocketHandler wsHandler,
                           FlightControlTools flightTools,
                           UavTelemetrySnapshotMapper snapshotMapper,
                           TelemetryCacheService cacheService,
                           MinioStorageService minioService) {
        this.bridge = bridge;
        this.wsHandler = wsHandler;
        this.flightTools = flightTools;
        this.snapshotMapper = snapshotMapper;
        this.cacheService = cacheService;
        this.minioService = minioService;
    }

    /**
     * 定时拉取遥测并执行三层存储 + 实时推送。
     * 固定频率 2s 一次，由 Spring @Scheduled 驱动。
     * 流程：MAVSDK 拉取 → Agent 缓存 → Redis 缓存 → WebSocket 广播 → MySQL 持久化（10s） → MinIO 归档（60s）
     */
    @Scheduled(fixedRate = 2000)
    public void pushTelemetry() {
        // 通过 HTTP 从 MAVSDK Bridge 拉取最新遥测键值对
        Map<String, Object> data = bridge.getTelemetry();
        // 桥接未连接时返回 {"error": "..."}，统一序列化为错误包广播给前端
        if (data.containsKey("error")) {
            try {
                String json = mapper.writeValueAsString(Map.of(
                    "error", true, "connected", false, "message", data.get("error")
                ));
                wsHandler.broadcast(json);
            } catch (Exception ignored) { }
            return;
        }

        // ① 更新 Agent 缓存 + Redis 缓存
        flightTools.updateTelemetry(data);
        try {
            cacheService.put(data);
        } catch (Exception e) {
            log.debug("Redis 缓存写入失败（Redis 可能未启动）: {}", e.getMessage());
        }

        // ② WebSocket 实时推送
        try {
            String json = mapper.writeValueAsString(data);
            wsHandler.broadcast(json);
        } catch (Exception e) {
            log.warn("遥测序列化失败: {}", e.getMessage());
        }

        // ③ MySQL 持久化（每 10 秒）
        if (++persistCounter >= 5) {
            persistCounter = 0;
            persistSnapshot(data);
        }

        // ④ MinIO 对象存储归档（每 60 秒）
        if (++minioCounter >= 30) {
            minioCounter = 0;
            try {
                minioService.uploadTelemetrySnapshot(data);
            } catch (Exception e) {
                log.debug("MinIO 归档失败（MinIO 可能未启动）: {}", e.getMessage());
            }
        }
    }

    /**
     * 将遥测 Map 转换为实体对象并写入 MySQL 遥测快照表。
     * 字段映射：telemetry key → UavTelemetrySnapshot 属性
     * 写入失败仅 warn 日志，不阻断后续调度（降级容忍）。
     */
    private void persistSnapshot(Map<String, Object> data) {
        try {
            UavTelemetrySnapshot snap = new UavTelemetrySnapshot();
            snap.setLatitude(toDouble(data.get("latitude")));
            snap.setLongitude(toDouble(data.get("longitude")));
            snap.setAltitude(toDouble(data.get("altitude")));
            snap.setVelocityX(toDouble(data.get("velocityX")));
            snap.setVelocityY(toDouble(data.get("velocityY")));
            snap.setVelocityZ(toDouble(data.get("velocityZ")));
            snap.setBatteryVoltage(toDouble(data.get("battery")));
            snap.setGpsFix(toInt(data.get("gps_fix")));
            snap.setCreatedAt(LocalDateTime.now());
            snapshotMapper.insert(snap);
        } catch (Exception e) {
            log.warn("遥测 DB 持久化失败: {}", e.getMessage());
        }
    }

    /** 安全转为 double：null 或非数字统一返回 0.0，避免 NPE */
    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** 安全转为 int：null 或非数字统一返回 0，避免 NPE */
    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
