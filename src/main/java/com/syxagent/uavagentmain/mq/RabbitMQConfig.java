package com.syxagent.uavagentmain.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑配置 — Topic 交换机 + 3 个业务队列 + 死信队列
 *
 * 拓扑结构：
 * ┌──────────────┐
 * │ uav.topic     │  Topic 交换机
 * └──┬──┬──┬──────┘
 *    │  │  │
 *    ▼  ▼  ▼
 *  mission.events   → 任务状态变更异步处理（写日志、推送通知）
 *  telemetry.stream → 遥测数据异步处理（批量入库 + 异常检测）
 *  log.ingest       → 操作日志异步消费（批量写入 ES）
 *
 * 死信处理：
 * 消费失败 3 次 → 路由到 uav.dlx 死信交换机 → uav.dlq 死信队列 → 人工排查
 *
 * 可靠性保证：
 * - Publisher Confirm 模式（消息投递确认）
 * - 手动 ACK（spring.rabbitmq.listener.simple.acknowledge-mode: manual 在 yml 配置）
 * - 持久化队列 + 持久化消息
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 交换机 ====================

    /** Topic 交换机：支持通配符路由（* 匹配一个词，# 匹配零或多个词） */
    static final String TOPIC_EXCHANGE = "uav.topic";

    /** 死信交换机：消费失败消息的终点 */
    static final String DLX_EXCHANGE = "uav.dlx";

    // ==================== 队列 ====================

    /** 任务事件队列 — 状态变更 → 写操作日志 + 推送通知 */
    static final String MISSION_EVENTS_QUEUE = "mission.events";

    /**
     * 遥测流队列 — 遥测数据异步处理
     * TTL = 60 秒：防止消费者宕机时消息堆积（遥测同时写 MySQL 兜底）
     */
    static final String TELEMETRY_STREAM_QUEUE = "telemetry.stream";

    /** 日志索引队列 — 操作日志批量写入 ElasticSearch（lazy 模式：优先写磁盘） */
    static final String LOG_INGEST_QUEUE = "log.ingest";

    /** 死信队列 — 人工检查失败消息 */
    static final String DLQ = "uav.dlq";

    // ==================== Routing Key ====================

    public static final String RK_MISSION_STATUS  = "mission.status.changed";
    public static final String RK_MISSION_COMMAND = "mission.command.sent";
    public static final String RK_TELEMETRY_RAW   = "telemetry.raw";
    public static final String RK_TELEMETRY_ANOMALY = "telemetry.anomaly";
    public static final String RK_LOG_OPERATION    = "log.operation";

    // ==================== Bean 定义 ====================

    @Bean
    public TopicExchange uavTopicExchange() {
        return ExchangeBuilder.topicExchange(TOPIC_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange uavDlxExchange() {
        return ExchangeBuilder.topicExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue missionEventsQueue() {
        return QueueBuilder.durable(MISSION_EVENTS_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey("dlq.mission")
                .build();
    }

    @Bean
    public Queue telemetryStreamQueue() {
        return QueueBuilder.durable(TELEMETRY_STREAM_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey("dlq.telemetry")
                .ttl(60_000) // 消息 60 秒后自动过期（遥测数据时效性强）
                .build();
    }

    @Bean
    public Queue logIngestQueue() {
        return QueueBuilder.durable(LOG_INGEST_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey("dlq.log")
                .withArgument("x-queue-mode", "lazy") // 直接写磁盘，节省内存
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding bindMissionEvents() {
        return BindingBuilder.bind(missionEventsQueue()).to(uavTopicExchange()).with(RK_MISSION_STATUS);
    }

    @Bean
    public Binding bindMissionCommands() {
        return BindingBuilder.bind(missionEventsQueue()).to(uavTopicExchange()).with(RK_MISSION_COMMAND);
    }

    @Bean
    public Binding bindTelemetryRaw() {
        return BindingBuilder.bind(telemetryStreamQueue()).to(uavTopicExchange()).with(RK_TELEMETRY_RAW);
    }

    @Bean
    public Binding bindTelemetryAnomaly() {
        return BindingBuilder.bind(telemetryStreamQueue()).to(uavTopicExchange()).with(RK_TELEMETRY_ANOMALY);
    }

    @Bean
    public Binding bindLogOperation() {
        return BindingBuilder.bind(logIngestQueue()).to(uavTopicExchange()).with(RK_LOG_OPERATION);
    }

    @Bean
    public Binding bindDlxMission() {
        return BindingBuilder.bind(dlqQueue()).to(uavDlxExchange()).with("dlq.mission");
    }

    @Bean
    public Binding bindDlxTelemetry() {
        return BindingBuilder.bind(dlqQueue()).to(uavDlxExchange()).with("dlq.telemetry");
    }

    @Bean
    public Binding bindDlxLog() {
        return BindingBuilder.bind(dlqQueue()).to(uavDlxExchange()).with("dlq.log");
    }

    // ==================== 消息转换 + 确认 ====================

    /**
     * Jackson 消息转换器 — 替代默认的 Java 序列化（更小、更快、跨语言兼容）
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置 RabbitTemplate：JSON 转换 + Publisher Confirm
     * Publisher Confirm 保证消息确实投递到了 Broker，防止消息丢失
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                // 投递失败 → 记录日志，后续可通过定时任务补偿
                org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class)
                        .warn("消息投递确认失败: id={}, cause={}", correlationData.getId(), cause);
            }
        });
        return template;
    }
}
