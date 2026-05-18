package com.syxagent.uavagentmain.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 任务事件发布器
 * 将任务生命周期事件异步投递到 RabbitMQ，解耦核心业务与日志、通知等非核心逻辑。
 *
 * 使用场景：
 * - AgentService 任务状态变更 → 发布 STATUS_CHANGED 事件 → LogConsumer 异步写库
 * - FlightControlTools 工具调用 → 发布 COMMAND_SENT 事件 → 异步记录飞控操作审计
 * - ClosedLoopMonitor 异常检测 → 发布 ANOMALY 事件 → 触发告警通知
 *
 * 可靠性：
 * - 使用 RabbitTemplate.convertAndSend() + Publisher Confirm（在 RabbitMQConfig 中配置）
 * - 消息持久化 + 队列持久化，Broker 重启不丢消息
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /** 发布任务状态变更事件 */
    public void publishStatusChanged(Long missionId, String sessionId, String oldStatus, String newStatus) {
        MissionEvent event = MissionEvent.statusChanged(missionId, sessionId, oldStatus, newStatus);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE, RabbitMQConfig.RK_MISSION_STATUS, event);
        log.debug("事件发布: {} → mission={}, {}→{}", event.eventType(), missionId, oldStatus, newStatus);
    }

    /** 发布飞控指令下发事件 */
    public void publishCommandSent(Long missionId, String sessionId, Long commandId, String commandType, String payload) {
        MissionEvent event = MissionEvent.commandSent(missionId, sessionId, commandId, commandType, payload);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE, RabbitMQConfig.RK_MISSION_COMMAND, event);
        log.debug("事件发布: COMMAND_SENT → mission={}, cmd={}", missionId, commandType);
    }

    /** 发布遥测异常事件 */
    public void publishAnomaly(Long missionId, String sessionId, String anomalyType, String severity) {
        MissionEvent event = MissionEvent.anomaly(missionId, sessionId, anomalyType, severity);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE, RabbitMQConfig.RK_TELEMETRY_ANOMALY, event);
        log.warn("事件发布: ANOMALY → mission={}, type={}, severity={}", missionId, anomalyType, severity);
    }

    /** 发布操作日志事件（异步写入 ES） */
    public void publishOperationLog(Long missionId, String sessionId, String operation, java.util.Map<String, Object> params) {
        MissionEvent event = MissionEvent.operationLog(missionId, sessionId, operation, params);
        rabbitTemplate.convertAndSend(RabbitMQConfig.TOPIC_EXCHANGE, RabbitMQConfig.RK_LOG_OPERATION, event);
    }
}
