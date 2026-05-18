package com.syxagent.uavagentmain.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syxagent.uavagentmain.entity.UavFlightLog;
import com.syxagent.uavagentmain.mapper.UavFlightLogMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务事件消费者 — 监听 mission.events 队列
 *
 * 职责：
 * - 任务状态变更 → 写入飞行日志表（uav_flight_log）
 * - 飞控指令下发 → 写入飞控审计日志
 * - 支持手动 ACK + 死信重试
 *
 * 手动 ACK 模式：
 * - 消费成功 → channel.basicAck 确认
 * - 消费失败 → channel.basicNack 拒绝并重新入队
 * - 连续 3 次失败 → 拒绝且不重新入队 → 消息进入死信队列（uav.dlq）
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionEventConsumer {

    private final UavFlightLogMapper logMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 监听任务事件队列，处理状态变更和指令下发事件
     *
     * concurrency = "1-3"：根据消息量动态扩展 1~3 个消费者线程
     */
    @RabbitListener(queues = RabbitMQConfig.MISSION_EVENTS_QUEUE, concurrency = "1-3")
    public void handleMissionEvent(MissionEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.debug("消费事件: type={}, mission={}, session={}", event.eventType(), event.missionId(), event.sessionId());

            switch (event.eventType()) {
                case "STATUS_CHANGED" -> handleStatusChanged(event);
                case "COMMAND_SENT" -> handleCommandSent(event);
                default -> log.debug("忽略未知事件类型: {}", event.eventType());
            }

            // 手动确认消费成功
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("事件消费失败: type={}, mission={}", event.eventType(), event.missionId(), e);

            // 判断是否需要重试（检查重试次数，避免无限重试）
            Integer retryCount = (Integer) message.getMessageProperties()
                    .getHeaders().getOrDefault("x-retry-count", 0);
            if (retryCount < 3) {
                // 重新入队，增加重试计数（Spring AMQP 会自动递增 death count）
                channel.basicNack(deliveryTag, false, true);
            } else {
                // 超过 3 次拒绝，不重新入队 → 进入死信队列
                channel.basicNack(deliveryTag, false, false);
                log.error("事件已进入死信队列: type={}, mission={}", event.eventType(), event.missionId());
            }
        }
    }

    /** 处理状态变更事件：写入飞行日志 */
    private void handleStatusChanged(MissionEvent event) {
        UavFlightLog logEntry = new UavFlightLog();
        logEntry.setEventType("MISSION_STATUS");
        logEntry.setEventData(serialize(event.payload()));
        logEntry.setSource("MQ_CONSUMER");
        logEntry.setCreatedAt(LocalDateTime.now());
        logMapper.insert(logEntry);
    }

    /** 处理飞控指令下发事件：写入审计日志 */
    private void handleCommandSent(MissionEvent event) {
        UavFlightLog logEntry = new UavFlightLog();
        logEntry.setEventType("COMMAND_AUDIT");
        logEntry.setEventData(serialize(event.payload()));
        logEntry.setSource("MQ_CONSUMER");
        logEntry.setCreatedAt(LocalDateTime.now());
        logMapper.insert(logEntry);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
