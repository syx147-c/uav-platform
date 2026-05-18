package com.syxagent.uavagentmain.resilience;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * LLM API 熔断器 + 重试保护
 *
 * 为什么需要熔断器？
 * - DeepSeek API 可能因限流、欠费、网络故障等原因不可用
 * - 如果 Agent 每次请求都等待 60s 超时，用户体验极差
 * - 熔断器在检测到连续失败后"快速失败"（fail-fast），直接返回 fallback，不等待超时
 *
 * 为什么需要重试？
 * - 网络瞬时波动导致的临时失败不值得熔断
 * - 指数退避重试（1s → 2s → 4s）给服务恢复时间
 *
 * 配置：
 * - 熔断阈值：50% 请求失败 → OPEN（拒绝请求 30s）
 * - 半开状态：30s 后允许 3 次探测请求，成功则关闭熔断
 * - 重试：最多 3 次，指数退避（1s → 2s → 4s）
 * - 慢调用阈值：响应 > 10s 视为慢调用，累计 > 50% 也触发熔断
 */
@Slf4j
@Component
public class LLMCircuitBreaker {

    private CircuitBreaker circuitBreaker;
    private Retry retry;

    @PostConstruct
    public void init() {
        // 熔断器配置
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)               // 失败率 50% 触发熔断
                .slowCallRateThreshold(50)              // 慢调用率 50% 触发熔断
                .slowCallDurationThreshold(Duration.ofSeconds(10)) // 超过 10s 视为慢调用
                .waitDurationInOpenState(Duration.ofSeconds(30))   // OPEN 状态 30s 后进入半开
                .permittedNumberOfCallsInHalfOpenState(3)          // 半开状态允许 3 次探测
                .slidingWindowSize(10)                  // 滑动窗口 10 次请求
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        circuitBreaker = cbRegistry.circuitBreaker("llm-api");

        // 重试配置
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))    // 初始等待 1s
                .intervalBiFunction((attempt, either) -> {
                    // 指数退避：1s → 2s → 4s（返回 Long 毫秒数）
                    return (long) (1000 * Math.pow(2, attempt));
                })
                .retryOnException(e -> !(e instanceof IllegalArgumentException)) // 参数错误不重试
                .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("llm-retry");

        // 注册事件监听
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("LLM 熔断器状态变更: {} → {}", event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onCallNotPermitted(event ->
                        log.warn("LLM 调用被熔断器拒绝（快速失败）"))
                .onError(event ->
                        log.warn("LLM 调用失败（熔断器滑动窗口统计中）"));

        retry.getEventPublisher()
                .onRetry(event ->
                        log.info("LLM 调用重试: 第 {} 次, 等待 {}ms", event.getNumberOfRetryAttempts(),
                                event.getWaitInterval().toMillis()));

        log.info("LLM 熔断器 + 重试已初始化");
    }

    /**
     * 使用熔断器 + 重试包装 LLM 调用
     *
     * @param supplier LLM 调用逻辑（如 () -> agent.chat(message)）
     * @param fallback 降级响应（如 "AI 服务暂时不可用，请稍后重试"）
     * @return LLM 响应或降级内容
     */
    public String executeWithProtection(Supplier<String> supplier, Supplier<String> fallback) {
        Supplier<String> protectedSupplier = Retry
                .decorateSupplier(retry, supplier);
        protectedSupplier = CircuitBreaker
                .decorateSupplier(circuitBreaker, protectedSupplier);

        try {
            return protectedSupplier.get();
        } catch (Exception e) {
            log.error("LLM 调用失败（已重试 + 熔断）: {}", e.getMessage());
            return fallback.get();
        }
    }

    /** 查询熔断器当前状态（用于 Actuator 监控） */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /** 查询熔断器指标（用于 Actuator 监控） */
    public CircuitBreaker.Metrics getMetrics() {
        return circuitBreaker.getMetrics();
    }
}
