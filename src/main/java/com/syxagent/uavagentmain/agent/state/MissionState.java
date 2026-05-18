package com.syxagent.uavagentmain.agent.state;

/**
 * 飞行任务状态枚举
 * 定义无人机任务执行的各个阶段
 *
 * 状态流转：
 * IDLE → ARMING → TAKEOFF → WAYPOINT → HOVER → RTL → LAND → IDLE
 * 任意飞行状态可紧急切入 HOLD（悬停保持）
 */
public enum MissionState {

    /** 空闲 — 无人机在地面，等待任务指令 */
    IDLE("空闲"),

    /** 解锁中 — 电机解锁，飞控自检通过，等待起飞 */
    ARMING("解锁中"),

    /** 起飞 — 正在爬升至目标高度 */
    TAKEOFF("起飞"),

    /** 航点飞行 — 正在飞往指定 GPS 坐标 */
    WAYPOINT("航点飞行"),

    /** 悬停 — 在当前位置保持悬停 */
    HOVER("悬停"),

    /** 返航 — 正在返回起飞点 */
    RTL("返航"),

    /** 降落 — 正在下降至地面 */
    LAND("降落"),

    /** 紧急保持 — 因异常/手动触发立即悬停 */
    HOLD("紧急保持"),

    /** 执行中 — AI Agent 正在解析意图并调用工具 */
    EXECUTING("执行中"),

    /** 失败 — LLM 调用或工具执行异常 */
    FAILED("失败");

    private final String description;

    MissionState(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
