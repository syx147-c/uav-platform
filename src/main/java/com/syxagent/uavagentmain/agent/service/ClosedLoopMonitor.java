package com.syxagent.uavagentmain.agent.service;

import com.syxagent.uavagentmain.entity.UavFlightLog;
import com.syxagent.uavagentmain.mapper.UavFlightLogMapper;
import com.syxagent.uavagentmain.service.MavsdkBridgeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 闭环安全监控器
 * 每 3 秒检查遥测，异常时自动触发保护动作。
 * 直接使用 MavsdkBridgeClient，不经过 FlightControlTools，避免污染 LLM 工具记录。
 */
@Slf4j
@Service
public class ClosedLoopMonitor {

    private final MavsdkBridgeClient bridge;
    private final UavFlightLogMapper logMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    private int gpsZeroCount = 0;
    private double lastBattery = 100;
    private boolean alertedLowBattery = false;
    private boolean alertedCritical = false;

    public ClosedLoopMonitor(MavsdkBridgeClient bridge, UavFlightLogMapper logMapper) {
        this.bridge = bridge;
        this.logMapper = logMapper;
    }

    @Scheduled(fixedRate = 3000)
    public void monitor() {
        Map<String, Object> data = bridge.getTelemetry();
        if (data.containsKey("error")) return;

        double alt = toDouble(data.get("altitude"));
        double battery = toDouble(data.get("battery"));
        int gpsFix = toInt(data.get("gps_fix"));
        boolean inAir = alt > 0.5;

        // 1. 电量 < 20% 告警（只告一次）
        if (inAir && battery < 20 && !alertedLowBattery) {
            alertedLowBattery = true;
            log.warn("[闭环] 电量过低: {}%", battery);
            saveAlert("BATTERY_LOW", String.format("电量 %.0f%%, 建议立即降落", battery));
        }
        if (battery >= 20) alertedLowBattery = false;

        // 2. 电量 < 10% 强制降落
        if (inAir && battery < 10 && !alertedCritical) {
            alertedCritical = true;
            log.error("[闭环] 电量危急: {}%, 自动触发 RTL", battery);
            bridge.rtl();
            saveAlert("BATTERY_CRITICAL_RTL", String.format("电量 %.0f%%, 自动返航", battery));
        }
        if (battery >= 10) alertedCritical = false;

        // 3. GPS 丢失检测
        if (gpsFix == 0) {
            gpsZeroCount++;
            if (gpsZeroCount == 5) {
                log.warn("[闭环] GPS 信号弱: 已持续 {} 秒", gpsZeroCount * 3);
                saveAlert("GPS_WEAK", "GPS 持续弱信号");
            }
        } else {
            gpsZeroCount = 0;
        }

        lastBattery = battery;
    }

    private void saveAlert(String type, String detail) {
        try {
            UavFlightLog logEntry = new UavFlightLog();
            logEntry.setEventType(type);
            logEntry.setEventData(mapper.writeValueAsString(Map.of("alert", detail)));
            logEntry.setSource("SAFETY");
            logEntry.setCreatedAt(LocalDateTime.now());
            logMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("保存闭环告警失败: {}", e.getMessage());
        }
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
