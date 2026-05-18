package com.syxagent.uavagentmain.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置
 *
 * 用途：
 * 1. 遥测快照归档（JSON 文件，按日期分区）
 * 2. 飞行任务计划存储（结构化 JSON）
 * 3. 飞行日志持久化备份
 *
 * 设计：
 * - 启动时自动创建所需 Bucket（telemetry、missions、flight-logs）
 * - MinioClient 单例，线程安全
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /** 启动时自动创建 Bucket */
    @PostConstruct
    void initBuckets(MinioClient client) {
        String[] buckets = {"telemetry", "missions", "flight-logs"};
        for (String bucket : buckets) {
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO Bucket 已创建: {}", bucket);
                }
            } catch (Exception e) {
                log.warn("MinIO Bucket 初始化失败 ({}): {}", bucket, e.getMessage());
            }
        }
    }
}
