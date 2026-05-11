package com.syxagent.uavagentmain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 遥测快照实体类 — 映射 uav_telemetry_snapshot 表
 * 存储无人机实时遥测数据的历史快照（用于飞行回放和分析）
 * 实时数据存 Redis，历史数据定期批量写入此表
 */
@Data                                                                      // Lombok：自动生成 getter/setter
@TableName("uav_telemetry_snapshot")                                        // 数据库表名
public class UavTelemetrySnapshot {

    @TableId(type = IdType.AUTO)                                            // 主键自增
    private Long id;                                                        // 快照唯一标识

    private Long missionId;                                                 // 关联的飞行任务 ID

    private Double latitude;                                                // 纬度（度）

    private Double longitude;                                               // 经度（度）

    private Double altitude;                                                // 相对起飞点高度（米）

    private Double velocityX;                                               // X 轴速度（北向，m/s）

    private Double velocityY;                                               // Y 轴速度（东向，m/s）

    private Double velocityZ;                                               // Z 轴速度（向下，m/s）

    private Double roll;                                                    // 横滚角（弧度）

    private Double pitch;                                                   // 俯仰角（弧度）

    private Double yaw;                                                     // 偏航角（弧度）

    private Double batteryVoltage;                                          // 电池电压（V）

    private Integer gpsFix;                                                 // GPS 定位类型（3=3D定位）

    private LocalDateTime createdAt;                                        // 快照记录时间
}
