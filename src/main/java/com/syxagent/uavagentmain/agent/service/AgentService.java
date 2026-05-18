package com.syxagent.uavagentmain.agent.service;

import com.syxagent.uavagentmain.agent.config.AgentConfig;
import com.syxagent.uavagentmain.agent.prompt.FlightPromptTemplate;
import com.syxagent.uavagentmain.agent.state.MissionState;
import com.syxagent.uavagentmain.agent.state.MissionStateMachine;
import com.syxagent.uavagentmain.agent.tool.FlightControlTools;
import com.syxagent.uavagentmain.agent.tool.PerceptionTools;
import com.syxagent.uavagentmain.entity.UavFlightLog;
import com.syxagent.uavagentmain.entity.UavMission;
import com.syxagent.uavagentmain.mapper.UavFlightLogMapper;
import com.syxagent.uavagentmain.mapper.UavMissionMapper;
import com.syxagent.uavagentmain.mq.MissionEventPublisher;
import com.syxagent.uavagentmain.resilience.LLMCircuitBreaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Agent 编排服务
 * 接收用户自然语言 → LLM 解析意图 → 调用飞行工具执行 → 返回结构化报告
 */
interface UavAgent {
    @SystemMessage(FlightPromptTemplate.SYSTEM_PROMPT)
    String chat(@UserMessage String userMessage);
}

@Slf4j
@Service
public class AgentService {

    public record ToolStep(String tool, String result, String status) {}
    public record ChatResult(String reply, List<ToolStep> steps) {}

    private final ChatLanguageModel model;
    private final FlightControlTools tools;
    private final PerceptionTools perceptionTools;
    private final AgentConfig config;
    private final MissionStateMachine stateMachine;
    private final UavMissionMapper missionMapper;
    private final UavFlightLogMapper logMapper;
    private final LLMCircuitBreaker circuitBreaker;
    private final MissionEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 按 sessionId 隔离的对话记忆 */
    private final ConcurrentHashMap<String, ChatMemory> memories = new ConcurrentHashMap<>();

    /** 按 sessionId 隔离的任务状态（修复多用户竞争） */
    private final ConcurrentHashMap<String, MissionState> sessionStates = new ConcurrentHashMap<>();

    /** 按 sessionId 缓存的 AiServices 代理 */
    private final ConcurrentHashMap<String, UavAgent> agentCache = new ConcurrentHashMap<>();

    public AgentService(ChatLanguageModel model, FlightControlTools tools,
                        PerceptionTools perceptionTools,
                        AgentConfig config, MissionStateMachine stateMachine,
                        UavMissionMapper missionMapper, UavFlightLogMapper logMapper,
                        LLMCircuitBreaker circuitBreaker, MissionEventPublisher eventPublisher) {
        this.model = model;
        this.tools = tools;
        this.perceptionTools = perceptionTools;
        this.config = config;
        this.stateMachine = stateMachine;
        this.missionMapper = missionMapper;
        this.logMapper = logMapper;
        this.circuitBreaker = circuitBreaker;
        this.eventPublisher = eventPublisher;
    }

    /** 获取当前活跃的全局摘要状态 */
    public MissionState getCurrentState() {
        for (MissionState s : sessionStates.values()) {
            if (s == MissionState.EXECUTING || s == MissionState.TAKEOFF
                || s == MissionState.WAYPOINT || s == MissionState.HOVER) {
                return s;
            }
        }
        return MissionState.IDLE;
    }

    /**
     * 处理用户自然语言聊天消息
     */
    public ChatResult chat(String sessionId, String message) {
        ChatMemory memory = memories.computeIfAbsent(sessionId, k -> config.createChatMemory());

        // 缓存 UavAgent 代理，避免每次重新构建
        UavAgent agent = agentCache.computeIfAbsent(sessionId, k ->
            AiServices.builder(UavAgent.class)
                .chatLanguageModel(model)
                .tools(tools, perceptionTools)
                .chatMemory(memory)
                .build()
        );

        log.info("Agent 收到 [session={}]: {}", sessionId, message);
        MissionState oldState = getSessionState(sessionId);
        updateState(sessionId, MissionState.EXECUTING);

        // 使用 Resilience4j 熔断器 + 重试保护 LLM 调用
        String reply = circuitBreaker.executeWithProtection(
            () -> agent.chat(message),                                          // 正常调用
            () -> "AI 服务暂时不可达（已熔断），请稍后重试。在此期间可使用 /api/drone 快通道手动控制。" // 降级响应
        );

        log.info("Agent 回复 [session={}]: {}", sessionId, reply);

        if (getSessionState(sessionId) != MissionState.FAILED) {
            updateState(sessionId, MissionState.IDLE);
        }

        // 发布异步事件（RabbitMQ → 异步写审计日志）
        try {
            eventPublisher.publishStatusChanged(null, sessionId, oldState.name(), getSessionState(sessionId).name());
        } catch (Exception ignored) {
            // RabbitMQ 不可用时不影响主流程
        }

        List<ToolStep> steps = new ArrayList<>();
        for (Object[] rec : tools.drainCallRecords()) {
            steps.add(new ToolStep(
                (String) rec[0], (String) rec[1], (String) rec[2]
            ));
        }

        saveMission(message, reply);
        saveFlightLog(message, reply, steps);

        return new ChatResult(reply, steps);
    }

    /** 获取指定会话的状态 */
    public MissionState getSessionState(String sessionId) {
        return sessionStates.getOrDefault(sessionId, MissionState.IDLE);
    }

    /** 更新指定会话的状态 */
    public void updateState(String sessionId, MissionState target) {
        MissionState current = getSessionState(sessionId);
        MissionState next = stateMachine.transition(current, target);
        sessionStates.put(sessionId, next);
    }

    /** 取消指定会话的任务 */
    public void cancelMission(String sessionId) {
        updateState(sessionId, MissionState.IDLE);
        agentCache.remove(sessionId);
        log.info("任务已取消 [session={}]", sessionId);
    }

    /** 应用关闭时清理 */
    @PreDestroy
    public void cleanup() {
        sessionStates.forEach((sid, state) -> {
            if (state != MissionState.IDLE && state != MissionState.FAILED) {
                log.warn("应用关闭，重置活跃任务 [session={}, state={}]", sid, state);
                sessionStates.put(sid, MissionState.FAILED);
            }
        });
        agentCache.clear();
        memories.clear();
    }

    private void saveMission(String userInput, String agentReply) {
        try {
            UavMission mission = new UavMission();
            mission.setTitle(extractTitle(userInput));
            mission.setDescription(userInput);
            mission.setTaskPlan(objectMapper.writeValueAsString(Map.of("plan", agentReply)));
            mission.setState(getCurrentState().name());
            mission.setCreatedAt(LocalDateTime.now());
            mission.setUpdatedAt(LocalDateTime.now());
            missionMapper.insert(mission);
        } catch (Exception e) {
            log.warn("保存任务失败: {}", e.getMessage());
        }
    }

    private void saveFlightLog(String userMsg, String reply, List<ToolStep> steps) {
        try {
            UavFlightLog logEntry = new UavFlightLog();
            logEntry.setEventType("LLM_CHAT");
            var data = Map.of(
                "user", truncate(userMsg, 300),
                "reply", truncate(reply, 500),
                "toolCalls", steps.stream().map(s ->
                    Map.of("tool", s.tool(), "result", s.result())
                ).toList()
            );
            logEntry.setEventData(objectMapper.writeValueAsString(data));
            logEntry.setSource("AGENT");
            logEntry.setCreatedAt(LocalDateTime.now());
            logMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("保存日志失败: {}", e.getMessage());
        }
    }

    private String extractTitle(String input) {
        if (input == null || input.isBlank()) return "未命名任务";
        return input.length() > 30 ? input.substring(0, 30) + "..." : input;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
