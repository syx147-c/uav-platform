package com.syxagent.uavagentmain.entity;

// MyBatis-Plus 注解：标记主键字段的自增策略
import com.baomidou.mybatisplus.annotation.IdType;
// MyBatis-Plus 注解：标记主键字段
import com.baomidou.mybatisplus.annotation.TableId;
// MyBatis-Plus 注解：标记实体类对应的数据库表名
import com.baomidou.mybatisplus.annotation.TableName;
// Lombok 注解：自动生成 getter、setter、toString、equals、hashCode
import lombok.Data;
// Java 8 时间 API：表示日期时间（不含时区信息）
import java.time.LocalDateTime;

/**
 * 飞行任务实体类 — 映射 uav_mission 表
 * 存储用户通过自然语言下达的飞行任务
 */
@Data                                                                      // Lombok：编译时自动生成所有字段的 getter/setter
@TableName("uav_mission")                                                   // 指定数据库表名
public class UavMission {

    @TableId(type = IdType.AUTO)                                            // 主键，数据库自增
    private Long id;                                                        // 任务唯一标识

    private String title;                                                   // 任务标题

    private String description;                                             // 用户输入的原始自然语言描述

    private String taskPlan;                                                // LLM 解析后生成的结构化任务计划（JSON 格式）

    private String state;                                                   // 任务状态：CREATED（已创建）、EXECUTING（执行中）、PAUSED（暂停）、COMPLETED（完成）、FAILED（失败）

    private LocalDateTime createdAt;                                        // 任务创建时间

    private LocalDateTime updatedAt;                                        // 任务最后更新时间
}
