// 声明当前类所属的包路径，对应 src/main/java/com/syxagent/uavagentmain/service/ 目录
package com.syxagent.uavagentmain.service;

// Jackson 的核心序列化/反序列化类，用于将 Java 对象转为 JSON 字节数组
import com.fasterxml.jackson.databind.ObjectMapper;
// MinIO Java SDK 的所有核心类：MinioClient、PutObjectArgs、GetObjectArgs、ListObjectsArgs 等
import io.minio.*;
// MinIO 预签名 URL 支持的 HTTP 方法枚举（GET / PUT）
import io.minio.http.Method;
// Lombok 注解：自动生成包含所有 final 字段的构造函数，Spring 通过它注入 Bean
import lombok.RequiredArgsConstructor;
// Lombok 注解：自动生成 Slf4j Logger 对象（log.info / log.error）
import lombok.extern.slf4j.Slf4j;
// Spring 注解：将当前类声明为 Service Bean，由 Spring 容器管理生命周期
import org.springframework.stereotype.Service;

// 将字节数组包装为输入流，避免创建临时文件（纯内存操作）
import java.io.ByteArrayInputStream;
// Java 标准库的输入流抽象，用于从 MinIO 读取对象内容
import java.io.InputStream;
// 获取当前日期，用于遥测快照的按天分区命名
import java.time.LocalDate;
// 将 LocalDate 格式化为 ISO 标准日期字符串（如 "2026-05-16"）
import java.time.format.DateTimeFormatter;
// 用于存储 listObjects 返回的对象名列表
import java.util.ArrayList;
// 用于 listObjects 返回类型声明
import java.util.List;
// 遥测数据使用 Map<String, Object> 表示 JSON 键值对，Jackson 可直接序列化
import java.util.Map;
// 预签名 URL 过期时间使用的枚举常量（HOURS / MINUTES）
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储服务
 *
 * 功能：
 * - 上传遥测快照（JSON）→ bucket: telemetry/YYYY-MM-DD/
 * - 上传任务计划     → bucket: missions/
 * - 上传飞行日志     → bucket: flight-logs/
 * - 生成预签名 URL    → 用于前端直接下载（无需经过后端传输）
 * - 列出/删除文件
 *
 * 涉及面试知识点：
 * - 对象存储 vs 块存储 vs 文件存储的区别
 * - 预签名 URL（Pre-signed URL）的安全设计
 * - S3 兼容 API（MinIO 兼容 AWS S3 协议）
 */
@Slf4j                   // Lombok：自动生成 private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class)
@Service                 // Spring：将此类标记为 Service Bean，启动时自动扫描并注册到容器
@RequiredArgsConstructor // Lombok：生成包含 minioClient 和 objectMapper 的构造器，Spring 通过它注入两个 Bean
public class MinioStorageService {

    // MinIO 客户端（S3 兼容协议），由 MinioConfig 配置类创建并注入，底层使用 OkHttp 通信
    private final MinioClient minioClient;

    // Jackson ObjectMapper，用于将 Java 对象序列化为 JSON 字节数组后上传到 MinIO
    private final ObjectMapper objectMapper;

    // ========================================================================
    //  遥测快照（Telemetry Snapshot）— 定时保存无人机遥测数据到 MinIO
    // ========================================================================

    /**
     * 以 JSON 格式存储遥测快照，按日期分区
     *
     * 对象命名规则：telemetry/{yyyy-MM-dd}/snapshot-{timestamp}.json
     * 日期分区的好处：按天查询快照时只需扫描对应目录，避免全桶扫描。
     * 时间戳后缀保证同一秒内的多次快照不会互相覆盖。
     */
    public void uploadTelemetrySnapshot(Map<String, Object> telemetry) {
        // 获取当前日期并格式化为 ISO 标准字符串（如 "2026-05-16"），用于按天分区
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        // 拼接对象名：前缀(telemetry) + 日期分区 + 文件名(snapshot-毫秒时间戳.json)
        // System.currentTimeMillis() 保证同一秒内多次调用生成不同文件名
        String objectName = "telemetry/" + date + "/snapshot-" + System.currentTimeMillis() + ".json";
        // 调用内部通用方法，将遥测 Map 序列化为 JSON 并上传
        uploadJson("telemetry", objectName, telemetry);
    }

    /**
     * 列出指定日期的遥测快照文件名
     * @param date 日期字符串，格式如 "2026-05-16"
     * @return 该日期下所有遥测快照的对象名列表
     */
    public List<String> listTelemetrySnapshots(String date) {
        // 拼接前缀 "telemetry/2026-05-16/"，MinIO 会返回该前缀下的所有对象
        return listObjects("telemetry", "telemetry/" + date + "/");
    }

    /**
     * 下载指定遥测快照的完整 JSON 内容
     * @param objectName 对象完整路径名（如 "telemetry/2026-05-16/snapshot-1715849123456.json"）
     * @return 快照 JSON 字符串，失败返回 null
     */
    public String downloadTelemetrySnapshot(String objectName) {
        // 从 telemetry 桶中读取对象，转换为字符串返回
        return downloadString("telemetry", objectName);
    }

    // ========================================================================
    //  任务计划 & 飞行日志（Mission Plan & Flight Log）
    // ========================================================================

    /**
     * 保存 LLM Agent 生成的任务计划到 MinIO
     * @param missionId 任务唯一 ID（UUID），用于构建文件名
     * @param plan      任务计划内容（Map 格式，由 LLM 生成的结构化 JSON）
     */
    public void uploadMissionPlan(String missionId, Map<String, Object> plan) {
        // 对象名格式：missions/{uuid}-plan.json，方便按 missionId 精确查找
        String objectName = "missions/" + missionId + "-plan.json";
        uploadJson("missions", objectName, plan);
    }

    /**
     * 保存飞行日志到 MinIO（记录每次飞控操作的结果）
     * @param logId 日志唯一 ID
     * @param log   日志内容（Map 格式，包含时间戳、操作类型、结果等）
     */
    public void uploadFlightLog(String logId, Map<String, Object> log) {
        // 对象名格式：flight-logs/{logId}.json
        String objectName = "flight-logs/" + logId + ".json";
        uploadJson("flight-logs", objectName, log);
    }

    /**
     * 列出所有已保存的任务计划文件名
     * @return missions 桶中所有对象名列表
     */
    public List<String> listMissionPlans() {
        // prefix 为空字符串 = 列出整个 missions 桶
        return listObjects("missions", "");
    }

    /**
     * 列出所有已保存的飞行日志文件名
     * @return flight-logs 桶中所有对象名列表
     */
    public List<String> listFlightLogs() {
        // prefix 为空字符串 = 列出整个 flight-logs 桶
        return listObjects("flight-logs", "");
    }

    // ========================================================================
    //  预签名 URL — 前端直传 MinIO，无需经过后端中转
    // ========================================================================

    /**
     * 生成预签名下载 URL（有效期 1 小时）
     *
     * 预签名 URL 的安全机制：
     * 1. URL 中嵌入 HMAC-SHA256 签名，MinIO 收到请求后重新计算签名并比对
     * 2. 签名绑定了 HTTP Method（此处为 GET），攻击者即使拿到 URL 也无法用于上传
     * 3. 签名绑定了过期时间（1 小时），超时后 MinIO 自动拒绝
     * 4. 前端拿到 URL 后直接向 MinIO 发请求，数据流不经过 Spring Boot 后端，节省带宽
     *
     * @param bucket     MinIO 桶名
     * @param objectName 要下载的对象完整路径名
     * @return 预签名 URL 字符串，失败返回 null
     */
    public String presignedDownloadUrl(String bucket, String objectName) {
        try {
            // 调用 MinIO SDK 生成预签名 URL
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()  // 构建参数对象
                            .method(Method.GET)          // 签名为 HTTP GET 请求
                            .bucket(bucket)              // 目标桶
                            .object(objectName)          // 目标对象
                            .expiry(1, TimeUnit.HOURS)   // 1 小时后签名自动失效
                            .build()                      // 构建完成
            );
        } catch (Exception e) {
            // 网络不通 / MinIO 未启动 / 桶不存在等异常统一兜底
            log.error("生成预签名 URL 失败: {}", e.getMessage());
            return null;  // 返回 null 让调用方决定降级策略
        }
    }

    /**
     * 生成预签名上传 URL（有效期 10 分钟）
     *
     * 前端拿到此 URL 后可直接 PUT 文件到 MinIO，无需经过后端。
     * 上传有效期设为 10 分钟（比下载的 1 小时更短），缩小被滥用的时间窗口。
     *
     * @param bucket     MinIO 桶名
     * @param objectName 要上传的目标对象路径名
     * @return 预签名上传 URL 字符串，失败返回 null
     */
    public String presignedUploadUrl(String bucket, String objectName) {
        try {
            // 调用 MinIO SDK 生成预签名上传 URL
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()  // 构建参数对象
                            .method(Method.PUT)           // 签名为 HTTP PUT 请求（上传）
                            .bucket(bucket)               // 目标桶
                            .object(objectName)           // 目标对象路径
                            .expiry(10, TimeUnit.MINUTES) // 10 分钟后签名自动失效，比下载短以降低安全风险
                            .build()                       // 构建完成
            );
        } catch (Exception e) {
            log.error("生成预签名上传 URL 失败: {}", e.getMessage());
            return null;
        }
    }

    // ========================================================================
    //  内部工具方法 — 封装 MinIO SDK 的底层操作
    // ========================================================================

    /**
     * 将 Java 对象序列化为 JSON 字节数组并上传到 MinIO
     *
     * 数据流：Object → Jackson writeValueAsBytes() → byte[] → ByteArrayInputStream → MinIO putObject()
     * 全程在内存中操作，不产生临时文件，适合遥测快照等小 JSON（< 5MB）。
     *
     * @param bucket     MinIO 桶名（telemetry / missions / flight-logs）
     * @param objectName 对象完整路径名（含前缀，如 "telemetry/2026-05-16/snapshot-xxx.json"）
     * @param data       要上传的 Java 对象（Map 或 POJO），Jackson 自动递归序列化
     */
    private void uploadJson(String bucket, String objectName, Object data) {
        try {
            // 用 Jackson 将 Java 对象序列化为 JSON 字节数组（纯内存操作，不写磁盘）
            byte[] bytes = objectMapper.writeValueAsBytes(data);
            // 将字节数组包装为 ByteArrayInputStream（无需关闭，但用 try-with-resources 保持风格统一）
            // ByteArrayInputStream 不涉及 I/O 资源，close() 是空操作
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                // 调用 MinIO SDK 上传字节流
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)                      // 目标桶名
                        .object(objectName)                  // 目标对象路径
                        .stream(is, bytes.length, -1)       // 输入流 + 长度 + partSize(-1=不分片，一次上传)
                        .contentType("application/json")     // 设置 Content-Type 为 JSON
                        .build()                              // 构建请求
                );
            }  // try-with-resources 自动调用 is.close()（虽然是空操作）
        } catch (Exception e) {
            // 网络异常 / MinIO 不可达 / 桶不存在 等统一记录日志，不向上抛异常
            log.error("MinIO 上传失败 [{}/{}]: {}", bucket, objectName, e.getMessage());
        }
    }

    /**
     * 从 MinIO 下载文件内容并转为字符串
     *
     * 适用场景：遥测快照 / 任务计划 / 飞行日志 均为 JSON 文本，直接返回字符串给前端或 Agent。
     * 注意：is.readAllBytes() 会将整个文件一次性加载到 JVM 内存，仅适用于小文件（< 10MB）。
     *
     * @param bucket     MinIO 桶名
     * @param objectName 对象完整路径名
     * @return 文件文本内容（UTF-8），失败返回 null
     */
    @SuppressWarnings("unchecked")  // 抑制 MinIO SDK 泛型相关编译警告
    private String downloadString(String bucket, String objectName) {
        try {
            // 从 MinIO 获取对象输入流，try-with-resources 确保流在使用后被正确关闭
            try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)    // 桶名
                    .object(objectName) // 对象路径
                    .build()            // 构建请求
            )) {
                // 一次性读取全部字节并转为 UTF-8 字符串（仅适合小文件）
                return new String(is.readAllBytes());
            }  // try-with-resources 自动调用 is.close()，释放 MinIO HTTP 连接
        } catch (Exception e) {
            log.error("MinIO 下载失败 [{}/{}]: {}", bucket, objectName, e.getMessage());
            return null;  // 失败返回 null，调用方需判空
        }
    }

    /**
     * 列出 MinIO 桶中指定前缀下的所有对象名
     *
     * MinIO listObjects() 返回的是惰性迭代器（Lazy Iterator）：
     * 不会一次性拉取全部元数据，而是在每次迭代时从 MinIO 按需获取。
     * 好处是桶内对象数量很大时不会撑爆内存；代价是 .get() 可能因网络闪断抛异常。
     *
     * @param bucket MinIO 桶名
     * @param prefix 对象名前缀过滤（如 "telemetry/2026-05-16/"），空字符串 = 不过滤
     * @return 对象名列表（只包含成功获取的条目，单条失败会跳过）
     */
    private List<String> listObjects(String bucket, String prefix) {
        // 创建结果列表，初始容量 0，在迭代过程中动态扩容
        List<String> result = new ArrayList<>();
        try {
            // 调用 MinIO SDK 获取惰性迭代器（此时并未真正发起网络请求）
            var items = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)  // 桶名
                    .prefix(prefix)   // 前缀过滤（空字符串 = 全部）
                    .build()           // 构建请求
            );
            // 遍历迭代器：每次 forEach 回调时才从 MinIO 按需拉取一条元数据
            items.forEach(item -> {
                try {
                    // item.get() 真正发起 HTTP 请求获取单条对象元数据
                    // .objectName() 提取对象完整路径名
                    result.add(item.get().objectName());
                } catch (Exception ignored) {
                    // 单条元数据拉取失败（网络闪断 / 对象被并发删除）→ 跳过该条，不影响整体
                    // 不记录日志避免日志爆炸（桶内对象很多时个别失败是正常的）
                }
            });
        } catch (Exception e) {
            // 整个列表操作失败（如 MinIO 不可达、桶不存在）
            log.error("MinIO 列表失败 [{}/{}]: {}", bucket, prefix, e.getMessage());
        }
        return result;  // 返回成功获取的对象名列表（可能为空列表）
    }
}
