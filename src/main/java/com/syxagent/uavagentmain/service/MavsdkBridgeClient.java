package com.syxagent.uavagentmain.service;

import com.syxagent.uavagentmain.config.MavsdkBridgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * MAVSDK Bridge HTTP 客户端 v2.0
 *
 * 架构定位：
 * Java (Spring Boot) --HTTP--> Python (FastAPI Bridge) --MAVSDK UDP--> PX4 SITL
 *
 * 为什么需要这个类？
 * MAVSDK 只有 Python / C++ 版本，Java 不能直接调用 PX4 飞控 API。
 * 因此在 WSL2 中运行一个 Python FastAPI Bridge，把 MAVSDK 的异步函数
 * 封装成同步的 REST 接口，Java 后端通过 RestTemplate 调用。
 *
 * 设计要点：
 * - 连接超时 3 秒、读取超时 5 秒：防止 Bridge 不可达时线程阻塞
 * - 每个方法独立 try-catch：单个飞控指令失败不影响其他操作
 * - 统一返回 String/Map：正常返回 "ok" 或遥测 Map，异常返回 error 信息
 *
 * 对应 Bridge 端点：
 * GET  /telemetry  → 遥测数据（GPS、高度、电量、速度）
 * POST /takeoff    → 起飞（携带 altitude 参数）
 * POST /land       → 降落
 * POST /hold       → 悬停
 * POST /arm        → 解锁电机
 * POST /disarm     → 锁定电机
 * POST /rtl        → 返航（Return To Launch）
 * POST /waypoint   → 飞往指定 GPS 航点
 * POST /velocity   → 速度控制（offboard 模式）
 * POST /reboot     → 重启飞控
 * GET  /status     → Bridge 状态（版本、运行时间、起飞高度）
 */
@Slf4j
@Service
public class MavsdkBridgeClient {

    /** MAVSDK Bridge 配置（URL 地址） */
    private final MavsdkBridgeProperties props;

    /** HTTP 客户端，构造时已设好连接/读取超时 */
    private final RestTemplate restTemplate;

    public MavsdkBridgeClient(MavsdkBridgeProperties props) {
        this.props = props;

        // 构造 RestTemplate 并设置超时，防止 Bridge 不可达时线程无限等待
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));  // TCP 三次握手超时
        factory.setReadTimeout(Duration.ofSeconds(5));      // 等待响应数据超时
        this.restTemplate = new RestTemplate(factory);
    }

    // ========================================================
    //  遥测查询
    // ========================================================

    /**
     * 查询无人机当前遥测数据
     * GET /telemetry
     *
     * @return GPS 坐标、相对高度、三轴速度、电量百分比、GPS 卫星数、飞行状态
     *         异常时返回 {"error": "bridge_unreachable"}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTelemetry() {
        try {
            // RestTemplate 自动将 JSON 反序列化为 Map（key=String, value=Object）
            return restTemplate.getForObject(props.getUrl() + "/telemetry", Map.class);
        } catch (Exception e) {
            // Bridge 不可达、超时、JSON 解析失败等所有异常统一兜底
            log.warn("无法获取遥测: {}", e.getMessage());
            return Map.of("error", "bridge_unreachable");
        }
    }

    // ========================================================
    //  基础飞控指令
    // ========================================================

    /**
     * 起飞到指定高度
     * POST /takeoff  body: {"altitude": 10.0}
     *
     * Bridge 收到后依次执行：
     * 1. drone.action.set_takeoff_altitude(altitude) — 设置起飞目标高度
     * 2. drone.action.arm()                        — 解锁电机
     * 3. drone.action.takeoff()                    — 执行起飞
     *
     * @param altitude 目标高度（相对地面，单位米），默认 10m，最大 120m
     * @return "ok" 或 "error: 具体原因"
     */
    public String takeoff(double altitude) {
        try {
            var body = Map.of("altitude", altitude);
            restTemplate.postForObject(props.getUrl() + "/takeoff", body, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("起飞失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /** 起飞（默认 10 米），兼容旧版本无参调用 */
    public String takeoff() {
        return takeoff(10.0);
    }

    /**
     * 降落并自动锁定电机
     * POST /land
     *
     * 无人机从当前高度逐渐下降到地面，落地后自动 disarm。
     * 这是最终步骤，调用后任务结束。
     */
    public String land() {
        try {
            restTemplate.postForObject(props.getUrl() + "/land", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("降落失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 紧急悬停 — 最高优先级指令
     * POST /hold
     *
     * 无人机立即停止水平移动，保持当前位置和高度。
     * 任何飞行状态下都可执行，后续需显式调用 land/rtl/gotoWaypoint。
     */
    public String hold() {
        try {
            restTemplate.postForObject(props.getUrl() + "/hold", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("悬停失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 解锁电机（ARM）
     * POST /arm
     *
     * 正常情况下 takeoff 会自动 ARM，无需手动调用。
     * 仅在 takeoff 失败后需要手动重试 ARM 时使用。
     */
    public String arm() {
        try {
            restTemplate.postForObject(props.getUrl() + "/arm", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("ARM 失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 锁定电机（DISARM）
     * POST /disarm
     *
     * 立即停止电机运转，通常在降落完成后自动执行。
     * 空中 disarm 会导致坠机，仅地面使用。
     */
    public String disarm() {
        try {
            restTemplate.postForObject(props.getUrl() + "/disarm", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("DISARM 失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 返航（Return To Launch）
     * POST /rtl
     *
     * 无人机自动飞回起飞点并降落。
     * 调用后无需再调用 land()，RTL 已包含降落。
     */
    public String rtl() {
        try {
            restTemplate.postForObject(props.getUrl() + "/rtl", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("RTL 失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    /**
     * 重启飞控（紧急恢复）
     * POST /reboot
     *
     * 软件重启 PX4 飞控，用于飞控异常时的恢复操作。
     * 注意：空中重启会导致无人机失控，仅地面使用。
     */
    public String reboot() {
        try {
            restTemplate.postForObject(props.getUrl() + "/reboot", null, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("Reboot 失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    // ========================================================
    //  航点导航
    // ========================================================

    /**
     * 飞往指定 GPS 航点
     * POST /waypoint  body: {"lat": 47.397, "lon": 8.545, "alt": 20.0}
     *
     * Bridge 调用 MAVSDK 的 drone.action.goto_location(lat, lon, alt, yaw)
     *
     * @param lat 目标纬度（度）
     * @param lon 目标经度（度）
     * @param alt 目标高度（相对地面，米）
     * @return "ok" 或错误信息
     */
    public String sendWaypoint(double lat, double lon, double alt) {
        try {
            var body = Map.of("lat", lat, "lon", lon, "alt", alt);
            restTemplate.postForObject(props.getUrl() + "/waypoint", body, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("航点失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    // ========================================================
    //  速度控制（Offboard 模式）
    // ========================================================

    /**
     * 设置无人机速度（Offboard 模式）
     * POST /velocity  body: {"vx": 0.5, "vy": 0, "vz": 0, "yaw": 0}
     *
     * Bridge 调用 MAVSDK 的 drone.offboard.set_velocity_ned()
     * NED 坐标系：vx=向北(前)、vy=向东(右)、vz=向下
     *
     * @param vx  北向速度（m/s），正值 = 前进
     * @param vy  东向速度（m/s），正值 = 右移
     * @param vz  下降速度（m/s），正值 = 下降
     * @param yaw 偏航角（度）
     */
    public String sendVelocity(double vx, double vy, double vz, double yaw) {
        try {
            var body = Map.of("vx", vx, "vy", vy, "vz", vz, "yaw", yaw);
            restTemplate.postForObject(props.getUrl() + "/velocity", body, Map.class);
            return "ok";
        } catch (Exception e) {
            log.warn("速度控制失败: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    // ========================================================
    //  Bridge 状态
    // ========================================================

    /**
     * 查询 MAVSDK Bridge 自身状态
     * GET /status
     *
     * @return 飞控固件版本、Bridge 版本、运行时间、当前起飞高度
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStatus() {
        try {
            return restTemplate.getForObject(props.getUrl() + "/status", Map.class);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
