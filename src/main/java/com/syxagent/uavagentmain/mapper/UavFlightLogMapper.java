package com.syxagent.uavagentmain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;                    // MyBatis-Plus 基础 Mapper
import com.syxagent.uavagentmain.entity.UavFlightLog;                      // 飞行日志实体
import org.apache.ibatis.annotations.Mapper;                               // Spring MyBatis 注解

/**
 * 飞行日志 Mapper — 操作 uav_flight_log 表
 */
@Mapper                                                                     // 注册为 MyBatis Mapper Bean
public interface UavFlightLogMapper extends BaseMapper<UavFlightLog> {      // 继承通用 CRUD
}
