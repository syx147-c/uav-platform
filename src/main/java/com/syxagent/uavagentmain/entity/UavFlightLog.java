package com.syxagent.uavagentmain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 飞行日志实体类 — 映射 uav_flight_log 表
 * 记录飞行过程中每个关键事件（解锁、起飞、航点、悬停、返航、降落等）
 */
@Data                                                                      // Lombok：自动生成 getter/setter
@TableName("uav_flight_log")                                                // 数据库表名
public class UavFlightLog {

    @TableId(type = IdType.AUTO)                                            // 主键自增
    private Long id;                                                        // 日志唯一标识

    private Long missionId;                                                 // 关联的飞行任务 ID

    private String eventType;                                               // 事件类型：ARM（解锁）、TAKEOFF（起飞）、WAYPOINT（航点）、HOVER（悬停）、RTL（返航）、LAND（降落）、HOLD（急停）

    private String eventData;                                               // 事件详情，JSON 格式（含坐标、高度、速度等）

    private String source;                                                  // 指令来源：AGENT（智能体）、MANUAL（手动）、EMERGENCY（紧急）

    private LocalDateTime createdAt;                                        // 事件发生时间
}
