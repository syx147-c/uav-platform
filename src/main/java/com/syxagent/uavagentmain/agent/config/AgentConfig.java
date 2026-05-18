package com.syxagent.uavagentmain.agent.config;

import dev.langchain4j.memory.ChatMemory;                                   // LangChain4j：对话记忆接口
import dev.langchain4j.memory.chat.MessageWindowChatMemory;                 // LangChain4j：滑动窗口记忆实现
import dev.langchain4j.model.chat.ChatLanguageModel;                         // LangChain4j：聊天语言模型接口
import dev.langchain4j.model.openai.OpenAiChatModel;                        // LangChain4j：OpenAI 兼容聊天模型
import lombok.Data;                                                         // Lombok：自动生成 getter/setter
import org.springframework.boot.context.properties.ConfigurationProperties; // Spring Boot：配置属性绑定
import org.springframework.context.annotation.Bean;                         // Spring：声明 Bean
import org.springframework.context.annotation.Configuration;               // Spring：配置类

import java.time.Duration;                                                  // Java 时间：持续时间

/**
 * LLM Agent 配置类
 * 创建 LangChain4j 的核心 Bean：ChatLanguageModel、ChatMemory
 *
 * DeepSeek API 兼容 OpenAI 格式，使用 langchain4j-open-ai 模块连接
 */
@Data                                                                       // 自动生成 llm 配置属性的 getter/setter
@Configuration                                                              // 标记为 Spring 配置类
@ConfigurationProperties(prefix = "llm")                                    // 读取 application.yml 中 llm.* 配置
public class AgentConfig {

    /** LLM 提供商名称（deepseek / openai / qwen 等） */
    private String provider;

    /** API 密钥（从环境变量 DEEPSEEK_API_KEY 读取，不硬编码在配置文件中） */
    private String apiKey;

    /** 模型名称（deepseek-chat / deepseek-reasoner 等） */
    private String model;

    /** API 基础地址（DeepSeek 为 https://api.deepseek.com） */
    private String baseUrl;

    /**
     * 创建聊天语言模型 Bean
     * OpenAiChatModel 兼容所有 OpenAI 格式的 API（DeepSeek、Qwen 等均支持）
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)                                              // API 密钥
                .modelName(model)                                            // 模型名
                .baseUrl(baseUrl)                                            // API 基础 URL（DeepSeek 端点）
                .timeout(Duration.ofSeconds(60))                             // 请求超时 60 秒
                .maxRetries(2)                                               // 失败重试 2 次
                .logRequests(true)                                           // 开启请求日志（调试用）
                .logResponses(true)                                          // 开启响应日志（调试用）
                .build();
    }

    /**
     * 创建对话记忆
     * 每次创建独立的窗口记忆（保留最近 10 轮对话），避免上下文溢出
     *
     * MessageWindowChatMemory 在达到 maxMessages 上限后，
     * 自动丢弃最早的消息，保持记忆在可控范围内
     */
    public ChatMemory createChatMemory() {
        return MessageWindowChatMemory.withMaxMessages(20);                 // 最多保留 20 条消息（10 轮对话）
    }
}
