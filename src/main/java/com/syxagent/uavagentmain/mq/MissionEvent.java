package com.syxagent.uavagentmain.mq;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 任务事件 — RabbitMQ 消息体
 * 记录任务生命周期中的每一次状态变更或指令下发，供异步消费者处理（写日志、推送通知、异常检测）
 */
public record MissionEvent(
        /** 事件唯一 ID（幂等去重用） */
        String eventId,
        /** 事件类型：STATUS_CHANGED / COMMAND_SENT / TASK_COMPLETED / ANOMALY */
        String eventType,
        /** 任务 ID */
        Long missionId,
        /** 会话 ID */
        String sessionId,
        /** 事件产生时间 */
        Instant timestamp,
        /** 事件携带的附加数据 */
        Map<String, Object> payload
) implements Serializable {

    /** 工厂方法：创建任务状态变更事件 */
    public static MissionEvent statusChanged(Long missionId, String sessionId,
                                              String oldStatus, String newStatus) {
        return new MissionEvent(
                UUID.randomUUID().toString(),
                "STATUS_CHANGED",
                missionId,
                sessionId,
                Instant.now(),
                Map.of("oldStatus", oldStatus, "newStatus", newStatus)
        );
    }

    /** 工厂方法：创建飞控指令下发事件 */
    public static MissionEvent commandSent(Long missionId, String sessionId,
                                            Long commandId, String commandType, String payload) {
        return new MissionEvent(
                UUID.randomUUID().toString(),
                "COMMAND_SENT",
                missionId,
                sessionId,
                Instant.now(),
                Map.of("commandId", commandId, "commandType", commandType, "payload", payload)
        );
    }

    /** 工厂方法：创建遥测异常事件 */
    public static MissionEvent anomaly(Long missionId, String sessionId,
                                        String anomalyType, String severity) {
        return new MissionEvent(
                UUID.randomUUID().toString(),
                "ANOMALY",
                missionId,
                sessionId,
                Instant.now(),
                Map.of("anomalyType", anomalyType, "severity", severity)
        );
    }

    /** 工厂方法：创建操作日志事件 */
    public static MissionEvent operationLog(Long missionId, String sessionId,
                                             String operation, Map<String, Object> params) {
        return new MissionEvent(
                UUID.randomUUID().toString(),
                "OPERATION_LOG",
                missionId,
                sessionId,
                Instant.now(),
                Map.of("operation", operation, "params", params)
        );
    }
}
