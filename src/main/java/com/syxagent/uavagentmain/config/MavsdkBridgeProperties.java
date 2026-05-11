package com.syxagent.uavagentmain.config;

import lombok.Data;                                                        // Lombok：自动生成 getter/setter
import org.springframework.boot.context.properties.ConfigurationProperties; // Spring Boot：将 application.yml 中以指定前缀开头的配置自动绑定到此类
import org.springframework.stereotype.Component;                           // Spring：标记为容器管理的 Bean

/**
 * MAVSDK Bridge 配置属性类
 * 自动读取 application.yml 中 mavsdk.bridge 开头的配置项
 * 例如：mavsdk.bridge.url = http://172.28.128.1:8000
 */
@Data                                                                       // 自动生成 url 属性的 getter/setter
@Component                                                                  // 注册为 Spring Bean，可被其他组件注入
@ConfigurationProperties(prefix = "mavsdk.bridge")                          // 绑定 application.yml 中以 "mavsdk.bridge" 为前缀的属性
public class MavsdkBridgeProperties {

    /** Python MAVSDK Bridge 的 HTTP 地址（FastAPI 服务运行在 WSL2 中） */
    private String url = "http://172.28.128.1:8000";                        // 默认值，可被 application.yml 覆盖
}
