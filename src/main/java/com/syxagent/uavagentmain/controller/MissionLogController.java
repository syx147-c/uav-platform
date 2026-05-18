package com.syxagent.uavagentmain.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.syxagent.uavagentmain.entity.UavFlightLog;
import com.syxagent.uavagentmain.entity.UavMission;
import com.syxagent.uavagentmain.mapper.UavFlightLogMapper;
import com.syxagent.uavagentmain.mapper.UavMissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务/日志查询 REST 接口
 */
@Tag(name = "任务与日志", description = "飞行任务列表、飞行日志查询、系统健康检查")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MissionLogController {

    private final UavMissionMapper missionMapper;
    private final UavFlightLogMapper logMapper;

    @Operation(summary = "任务列表", description = "获取最近 50 条飞行任务记录")
    @GetMapping("/missions")
    public ResponseEntity<List<UavMission>> listMissions() {
        var qw = new QueryWrapper<UavMission>();
        qw.orderByDesc("created_at");
        qw.last("LIMIT 50");
        return ResponseEntity.ok(missionMapper.selectList(qw));
    }

    @Operation(summary = "任务详情", description = "根据 ID 获取单个任务详情（含任务计划 JSON）")
    @GetMapping("/missions/{id}")
    public ResponseEntity<UavMission> getMission(@PathVariable Long id) {
        UavMission mission = missionMapper.selectById(id);
        return mission != null ? ResponseEntity.ok(mission)
                               : ResponseEntity.notFound().build();
    }

    @Operation(summary = "飞行日志", description = "获取飞行日志，可按事件类型和来源筛选")
    @GetMapping("/flight-logs")
    public ResponseEntity<List<UavFlightLog>> listFlightLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String source) {
        var qw = new QueryWrapper<UavFlightLog>();
        if (eventType != null) qw.eq("event_type", eventType);
        if (source != null) qw.eq("source", source);
        qw.orderByDesc("created_at");
        qw.last("LIMIT 100");
        return ResponseEntity.ok(logMapper.selectList(qw));
    }

    @Operation(summary = "健康检查", description = "系统健康状态检查（Docker/负载均衡探活）")
    @GetMapping("/system/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
