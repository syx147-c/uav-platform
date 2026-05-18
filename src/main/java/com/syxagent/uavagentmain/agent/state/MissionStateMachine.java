package com.syxagent.uavagentmain.agent.state;

import lombok.extern.slf4j.Slf4j;                                          // Lombok：日志
import org.springframework.stereotype.Component;                           // Spring：Bean

import java.util.Map;                                                      // 键值对
import java.util.Set;                                                      // 集合

/**
 * 飞行任务状态机
 * 管理任务状态的合法转移，拒绝不符合流程的跳跃（如还未起飞就直接降落）
 *
 * 状态转移表（from → allowed to states）：
 * IDLE    → ARMING
 * ARMING  → TAKEOFF, ABORT(IDLE)
 * TAKEOFF → WAYPOINT, HOVER, HOLD
 * WAYPOINT→ WAYPOINT, HOVER, HOLD
 * HOVER   → WAYPOINT, RTL, LAND, HOLD
 * RTL     → LAND, HOLD
 * LAND    → IDLE
 * HOLD    → RESUME(回原状态), ABORT(IDLE)
 */
@Slf4j                                                                      // 日志
@Component                                                                  // 注册为 Spring Bean
public class MissionStateMachine {

    /**
     * 合法的状态转移映射
     * Key = 当前状态，Value = 可以转移到的目标状态集合
     */
    private static final Map<MissionState, Set<MissionState>> TRANSITIONS = Map.of(
        MissionState.IDLE,     Set.of(MissionState.ARMING, MissionState.TAKEOFF, MissionState.EXECUTING),
        MissionState.ARMING,   Set.of(MissionState.TAKEOFF, MissionState.IDLE),
        MissionState.TAKEOFF,  Set.of(MissionState.WAYPOINT, MissionState.HOVER, MissionState.HOLD, MissionState.RTL, MissionState.LAND),
        MissionState.WAYPOINT, Set.of(MissionState.WAYPOINT, MissionState.HOVER, MissionState.HOLD, MissionState.RTL),
        MissionState.HOVER,    Set.of(MissionState.WAYPOINT, MissionState.RTL, MissionState.LAND, MissionState.HOLD),
        MissionState.RTL,      Set.of(MissionState.LAND, MissionState.HOLD),
        MissionState.LAND,     Set.of(MissionState.IDLE),
        MissionState.HOLD,     Set.of(MissionState.TAKEOFF, MissionState.WAYPOINT, MissionState.HOVER, MissionState.RTL, MissionState.LAND, MissionState.IDLE),
        MissionState.EXECUTING, Set.of(MissionState.IDLE, MissionState.TAKEOFF, MissionState.WAYPOINT, MissionState.HOVER, MissionState.RTL, MissionState.LAND, MissionState.HOLD, MissionState.FAILED),
        MissionState.FAILED, Set.of(MissionState.IDLE)
    );

    /**
     * 验证状态转移是否合法
     *
     * @param from 当前任务状态
     * @param to   目标任务状态
     * @return true 表示可以转移，false 表示非法转移
     */
    public boolean canTransition(MissionState from, MissionState to) {
        Set<MissionState> allowed = TRANSITIONS.get(from);
        if (allowed == null) return false;
        boolean legal = allowed.contains(to);
        if (!legal) {
            log.warn("非法状态转移: {} → {}（允许的目标: {}）", from, to, allowed);
        }
        return legal;
    }

    /**
     * 执行状态转移
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return 如果转移合法返回目标状态，否则返回当前状态（不变）
     */
    public MissionState transition(MissionState current, MissionState target) {
        if (canTransition(current, target)) {
            log.info("状态转移: {} → {}", current.getDescription(), target.getDescription());
            return target;
        }
        log.warn("拒绝状态转移: {} → {}，维持当前状态: {}", current.getDescription(), target.getDescription(), current.getDescription());
        return current;                                                      // 非法转移：状态不变
    }

    /**
     * 获取当前状态允许的所有目标状态
     */
    public Set<MissionState> allowedTransitions(MissionState state) {
        return TRANSITIONS.getOrDefault(state, Set.of());
    }
}
