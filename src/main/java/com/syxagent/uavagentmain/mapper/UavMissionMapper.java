package com.syxagent.uavagentmain.mapper;

// MyBatis-Plus 基础 Mapper：提供增删改查、分页等通用方法
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
// 飞行任务实体类
import com.syxagent.uavagentmain.entity.UavMission;
// Spring 注解：标记为 MyBatis 数据访问层 Bean
import org.apache.ibatis.annotations.Mapper;

/**
 * 飞行任务 Mapper 接口
 * 继承 BaseMapper<UavMission> 后自动拥有 insert、deleteById、updateById、selectById、selectList 等方法
 * 无需编写 XML，MyBatis-Plus 自动生成 SQL
 */
@Mapper                                                                     // 标记为 MyBatis Mapper，会被 Spring 扫描并创建代理实现
public interface UavMissionMapper extends BaseMapper<UavMission> {          // BaseMapper<UavMission> 提供 CRUD 模板方法
}
