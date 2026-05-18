package com.syxagent.uavagentmain.controller;

import com.syxagent.uavagentmain.service.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MinIO 对象存储 REST 接口 — S3 兼容对象存储，支持预签名 URL 前端直传
 */
@Tag(name = "对象存储", description = "MinIO S3 兼容存储 — 遥测快照/任务计划/飞行日志 + 预签名上传下载 URL")
@Slf4j
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class MinioController {

    private final MinioStorageService minioService;

    @Operation(summary = "任务计划列表", description = "列出 MinIO 中所有已保存的任务计划 JSON 文件")
    @GetMapping("/missions")
    public ResponseEntity<List<String>> listMissions() {
        return ResponseEntity.ok(minioService.listMissionPlans());
    }

    @Operation(summary = "飞行日志列表", description = "列出 MinIO 中所有已保存的飞行日志文件")
    @GetMapping("/flight-logs")
    public ResponseEntity<List<String>> listFlightLogs() {
        return ResponseEntity.ok(minioService.listFlightLogs());
    }

    @Operation(summary = "遥测快照列表", description = "列出 MinIO 中指定日期的遥测快照文件（格式: YYYY-MM-DD）")
    @GetMapping("/telemetry/{date}")
    public ResponseEntity<List<String>> listTelemetry(@PathVariable String date) {
        return ResponseEntity.ok(minioService.listTelemetrySnapshots(date));
    }

    @Operation(summary = "预签名上传 URL", description = "生成 MinIO 预签名上传 URL（有效期 10min），前端可直传绕过 Java 后端")
    @PostMapping("/presigned-upload")
    public ResponseEntity<Map<String, Object>> presignedUpload(@RequestBody Map<String, String> body) {
        String bucket = body.getOrDefault("bucket", "telemetry");
        String objectName = body.get("objectName");
        if (objectName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "objectName 不能为空"));
        }
        String url = minioService.presignedUploadUrl(bucket, objectName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("method", "PUT");
        result.put("expiresIn", "10min");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "预签名下载 URL", description = "生成 MinIO 预签名下载 URL（有效期 1h），前端可直接下载文件")
    @PostMapping("/presigned-download")
    public ResponseEntity<Map<String, Object>> presignedDownload(@RequestBody Map<String, String> body) {
        String bucket = body.getOrDefault("bucket", "telemetry");
        String objectName = body.get("objectName");
        if (objectName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "objectName 不能为空"));
        }
        String url = minioService.presignedDownloadUrl(bucket, objectName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("method", "GET");
        result.put("expiresIn", "1h");
        return ResponseEntity.ok(result);
    }
}
