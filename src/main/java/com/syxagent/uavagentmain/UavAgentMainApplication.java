package com.syxagent.uavagentmain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UAV 飞行控制平台 - 主启动类
 *
 * 注解说明：
 * - @EnableScheduling：定时拉取遥测 + 闭环安全监控
 * - @EnableAsync：模拟地面站异步执行飞控任务
 * - @EnableRabbit：启用 RabbitMQ 监听器（@RabbitListener 注解生效）
 */
@SpringBootApplication
@MapperScan("com.syxagent.uavagentmain.mapper")
@EnableScheduling
@EnableAsync
@EnableRabbit
public class UavAgentMainApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 嵌入式 Tomcat 服务器，加载所有 Bean
        SpringApplication.run(UavAgentMainApplication.class, args);
    }

}
