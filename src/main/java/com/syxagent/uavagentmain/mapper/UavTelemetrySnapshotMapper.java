package com.syxagent.uavagentmain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;                      // MyBatis-Plus 基础 Mapper
import com.syxagent.uavagentmain.entity.UavTelemetrySnapshot;                // 遥测快照实体
import org.apache.ibatis.annotations.Mapper;                                 // Spring MyBatis 注解

/**
 * 遥测快照 Mapper — 操作 uav_telemetry_snapshot 表
 */
@Mapper                                                                       // 注册为 MyBatis Mapper Bean
public interface UavTelemetrySnapshotMapper extends BaseMapper<UavTelemetrySnapshot> {  // 继承通用 CRUD
}
