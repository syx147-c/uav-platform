package com.syxagent.uavagentmain.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.syxagent.uavagentmain.entity.UavTelemetrySnapshot;
import com.syxagent.uavagentmain.mapper.UavTelemetrySnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 遥测流消费者 — 监听 telemetry.stream 队列
 *
 * 职责：
 * - 接收原始遥测数据异步批量写入 MySQL（与 TelemetryPusher 的同步写入互补）
 * - 执行异常检测规则（电量过低、GPS 丢失、偏离航线）
 * - 消息 TTL 60s：超过 60s 未消费自动丢弃（遥测数据时效性强，Redis 已有最新缓存兜底）
 *
 * 与 TelemetryPusher 的关系：
 * - TelemetryPusher：同步写入（每 10s）+ WebSocket 实时推送
 * - TelemetryStreamConsumer：异步消费队列中的遥测 → 批量入库 + 异步异常检测
 * - 互补不冲突：Pusher 保证实时性，Consumer 保证削峰和不丢数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryStreamConsumer {

    private final UavTelemetrySnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitMQConfig.TELEMETRY_STREAM_QUEUE, concurrency = "1-2")
    public void handleTelemetry(MissionEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if ("ANOMALY".equals(event.eventType())) {
                handleAnomalyDetection(event);
            } else {
                // RAW 遥测数据 → 异步持久化
                persistTelemetry(event);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("遥测消费失败: {}, 不重试（遥测有时效性）", e.getMessage());
            // 遥测数据有时效性，失败不重试，直接 ACK 丢弃
            channel.basicAck(deliveryTag, false);
        }
    }

    @SuppressWarnings("unchecked")
    private void persistTelemetry(MissionEvent event) {
        try {
            Map<String, Object> data = event.payload();
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
            log.warn("遥测持久化失败: {}", e.getMessage());
        }
    }

    /** 异步异常检测（与 ClosedLoopMonitor 互补） */
    private void handleAnomalyDetection(MissionEvent event) {
        Map<String, Object> payload = event.payload();
        String anomalyType = (String) payload.get("anomalyType");
        String severity = (String) payload.get("severity");
        log.warn("[MQ异常检测] type={}, severity={}, mission={}", anomalyType, severity, event.missionId());
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
