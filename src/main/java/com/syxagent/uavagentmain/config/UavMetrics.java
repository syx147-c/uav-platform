package com.syxagent.uavagentmain.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 自定义 Micrometer 指标 — 暴露给 Prometheus + Grafana
 *
 * 指标分类：
 * - 飞控指令计数器（按类型 + 结果分） → Grafana 展示指令分布饼图
 * - LLM Agent 调用计时器 → 监控 AI 响应延迟 P50/P95/P99
 * - 熔断器状态计数器 → 告警规则（熔断器打开 > 5min 触发告警）
 * - 遥测推送计数器 → 监控 WebSocket 推送频率
 * - 任务状态变更计数器 → 成功率/失败率展示
 *
 * Prometheus 抓取路径：GET /actuator/prometheus
 * Grafana 仪表盘：uav_metrics_* 前缀
 */
@Component
public class UavMetrics {

    private final MeterRegistry registry;

    /** 飞控指令计数器（按类型 + 结果） */
    @Getter
    private Counter droneCommandTotal;

    /** 飞控指令执行时间 */
    @Getter
    private Timer droneCommandTimer;

    /** LLM Agent 调用次数（按状态：success/failure/rejected） */
    @Getter
    private Counter agentCallTotal;

    /** LLM Agent 调用耗时 */
    @Getter
    private Timer agentCallTimer;

    /** 熔断器状态变更（open/closed/half_open） */
    @Getter
    private Counter circuitBreakerTransition;

    /** 遥测数据推送次数 */
    @Getter
    private Counter telemetryPushTotal;

    /** 任务状态变更（按 from → to） */
    @Getter
    private Counter missionStateChange;

    /** 异常检测触发次数（按类型） */
    @Getter
    private Counter anomalyDetectedTotal;

    public UavMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        droneCommandTotal = Counter.builder("uav_drone_commands_total")
                .description("飞控指令执行总数")
                .tag("app", "uav-agent")
                .register(registry);

        droneCommandTimer = Timer.builder("uav_drone_command_duration")
                .description("飞控指令执行耗时")
                .tag("app", "uav-agent")
                .register(registry);

        agentCallTotal = Counter.builder("uav_agent_calls_total")
                .description("LLM Agent 调用总数")
                .tag("app", "uav-agent")
                .register(registry);

        agentCallTimer = Timer.builder("uav_agent_call_duration")
                .description("LLM Agent 调用耗时")
                .publishPercentiles(0.5, 0.95, 0.99) // P50/P95/P99 分位数
                .tag("app", "uav-agent")
                .register(registry);

        circuitBreakerTransition = Counter.builder("uav_circuit_breaker_transitions")
                .description("LLM API 熔断器状态变更次数")
                .tag("app", "uav-agent")
                .register(registry);

        telemetryPushTotal = Counter.builder("uav_telemetry_push_total")
                .description("遥测数据 WebSocket 推送次数")
                .tag("app", "uav-agent")
                .register(registry);

        missionStateChange = Counter.builder("uav_mission_state_changes_total")
                .description("任务状态变更次数")
                .tag("app", "uav-agent")
                .register(registry);

        anomalyDetectedTotal = Counter.builder("uav_anomaly_detected_total")
                .description("闭环监控异常检测触发次数")
                .tag("app", "uav-agent")
                .register(registry);
    }

    /** 记录飞控指令执行 */
    public void recordDroneCommand(String commandType, String result, long durationMs) {
        droneCommandTotal.increment();
        droneCommandTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** 记录 LLM Agent 调用 */
    public void recordAgentCall(String status, long durationMs) {
        agentCallTotal.increment();
        agentCallTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** 记录遥测推送 */
    public void recordTelemetryPush() {
        telemetryPushTotal.increment();
    }

    /** 记录异常检测 */
    public void recordAnomaly(String anomalyType) {
        anomalyDetectedTotal.increment();
    }
}
