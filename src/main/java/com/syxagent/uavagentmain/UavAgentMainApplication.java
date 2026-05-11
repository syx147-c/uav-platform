package com.syxagent.uavagentmain;

// MyBatis-Plus 的 Mapper 扫描注解：自动扫描指定包下的所有 Mapper 接口
import org.mybatis.spring.annotation.MapperScan;
// Spring Boot 应用启动入口
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 开启 Spring 定时任务调度（用于定时推送遥测数据）
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UAV 飞行控制平台 - 主启动类
 */
@SpringBootApplication                                                     // 组合注解：包含 @Configuration、@EnableAutoConfiguration、@ComponentScan
@MapperScan("com.syxagent.uavagentmain.mapper")                           // 扫描 MyBatis-Plus Mapper 接口所在包
@EnableScheduling                                                         // 启用定时任务（TelemetryPusher 每 2 秒推送遥测）
public class UavAgentMainApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 嵌入式 Tomcat 服务器，加载所有 Bean
        SpringApplication.run(UavAgentMainApplication.class, args);
    }

}
