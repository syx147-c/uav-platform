package com.syxagent.uavagentmain.agent.controller;

import com.syxagent.uavagentmain.agent.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LLM Agent REST 控制器
 * LLM Agent 是基于 LangChain4j 的智能飞行助手，接收自然语言指令并自主规划执行
 */
@Tag(name = "AI Agent", description = "LLM Agent 自然语言接口 — 发送飞行指令，LLM 自主规划工具调用")
@Slf4j                                                                      // 日志
@RestController                                                             // REST 控制器
@RequestMapping("/api/agent")                                                // URL 前缀
@RequiredArgsConstructor                                                     // 构造器注入
public class AgentController {

    private final AgentService agentService;                                 // Agent 编排服务

    /**
     * 自然语言聊天接口
     * POST /api/agent/chat
     *
     * 请求体：
     * { "message": "飞到天安门上空50米悬停30秒后返航", "sessionId": "abc123" }
     *
     * 响应：
     * {
     *   "reply": "【takeoff】→ ...\n✅ 任务完成：...",
     *   "sessionId": "abc123",
     *   "state": "EXECUTING",
     *   "steps": [
     *     { "tool": "queryTelemetry", "result": "当前遥测: ...", "status": "ok" },
     *     { "tool": "takeoff", "result": "起飞指令已发送...", "status": "ok" }
     *   ]
     * }
     */
    @Operation(summary = "自然语言聊天", description = "发送自然语言飞行指令，LLM Agent 自主拆解为工具调用序列。支持：起飞/降落/飞航点/悬停/返航/复合指令")
    @ApiResponse(responseCode = "200", description = "Agent 回复 + 工具调用步骤")
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @Parameter(description = "{\"message\": \"起飞到50米悬停30秒后返航\", \"sessionId\": \"abc\"}")
            @RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");               // 用户消息
        String sessionId = request.getOrDefault("sessionId",
                UUID.randomUUID().toString().substring(0, 8));               // 会话 ID

        if (message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "消息不能为空"));
        }

        log.info("收到聊天请求 [session={}]: {}", sessionId, message);

        try {
            // 调用 Agent 服务处理消息
            AgentService.ChatResult result = agentService.chat(sessionId, message);

            // 构造响应：reply + 步骤列表 + 会话状态
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("reply", result.reply());                           // LLM 回复文本
            response.put("sessionId", sessionId);                            // 会话 ID
            response.put("state", agentService.getCurrentState().name());    // 当前任务状态
            response.put("steps", result.steps());                           // 工具执行步骤列表

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Agent 处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Agent 处理失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "查询当前任务状态")
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getState() {
        return ResponseEntity.ok(Map.of(
            "state", agentService.getCurrentState().name(),
            "description", agentService.getCurrentState().getDescription()
        ));
    }

    @Operation(summary = "取消当前任务", description = "中止当前会话的任务并清理 Agent 缓存")
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@RequestBody Map<String, String> request) {
        String sessionId = request.getOrDefault("sessionId", "");
        agentService.cancelMission(sessionId);
        return ResponseEntity.ok(Map.of("ok", true, "message", "任务已取消"));
    }
}
