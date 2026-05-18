package com.syxagent.uavagentmain.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 3.0 配置
 *
 * 访问地址：
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 *
 * 用于：
 * - 前端团队查看 API 文档
 * - 面试时展示 API 设计规范
 * - Postman 导入 JSON 自动生成测试集合
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI uavAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UAV Agent 飞行控制平台 API")
                        .description("""
                                基于 Spring Boot 3 + LangChain4j + Redis + RabbitMQ + MinIO 的
                                无人机自然语言操控平台 REST API 文档。

                                核心功能：
                                - LLM Agent 自然语言飞控接口（/api/agent）
                                - 无人机快通道手动控制接口（/api/drone）
                                - JWT 认证接口（/api/auth）
                                - MinIO 对象存储接口（/api/storage）
                                - 任务/日志查询接口（/api/missions, /api/flight-logs）
                                """)
                        .version("v2.0.0")
                        .contact(new Contact()
                                .name("UAV Agent Team")
                                .email("dev@uav-agent.local"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发环境")
                ));
    }
}
