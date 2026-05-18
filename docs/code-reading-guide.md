# UAV 飞行控制平台 — 代码阅读指南

> 目标：以最高效的顺序理解整个项目，每一阶段只看核心内容，逐步建立全局认知。

---

## 阅读总览（8 个阶段）

| 阶段 | 标题 | 文件数 | 目标 |
|------|------|--------|------|
| 1 | 项目骨架 | 3 | 知道项目长什么样，依赖了哪些技术 |
| 2 | 通信链路 | 3 | 理解 Java → Python → PX4 三层通信 |
| 3 | 数据管道 | 4 | 理解遥测数据从 PX4 到前端的完整路径 |
| 4 | Agent 核心 | 5 | 理解 LLM 如何把自然语言变成飞控指令 |
| 5 | 安全与认证 | 5 | 理解 JWT + 黑名单 + 频率限制的防护体系 |
| 6 | 前端 | 5 | 理解前端页面结构、路由守卫、API 封装 |
| 7 | 消息队列与弹性防护 | 9 | 理解 RabbitMQ 事件驱动 + Resilience4j 熔断 + 幂等 + 指标监控 |
| 8 | 部署运维 | 4 | 理解 Docker 编排和监控 |
| 9 | 视觉感知 (v3.0) | 8 | 理解 YOLOv11 + ByteTrack + Depth Anything V2 感知引擎 |

---

## 第一阶段：项目骨架（先看全局）

> 目的：知道项目用了哪些技术，Spring Boot 怎么启动的，有哪些模块。

### 阅读顺序

**1. `pom.xml`**（根目录）
- 看 `<dependencies>` 部分：Spring Boot、MyBatis-Plus、LangChain4j、MinIO、Redis、JWT、WebSocket、Lombok
- 不用细看每个依赖版本，快速扫一遍知道有哪些技术栈即可
- 重点关注：`langchain4j`、`minio`、`spring-boot-starter-websocket`、`jjwt` 这几个非标准依赖

**2. `src/main/resources/application.yml`**
- 看有哪些配置段：`server`、`spring.datasource`、`mavsdk.bridge`、`minio`、`spring.data.redis`、`jwt`
- 每个配置段的 key 名暗示了项目集成了哪些外部服务
- 重点记住 `mavsdk.bridge.url`——这是 Java 后端调用 Python Bridge 的地址

**3. `src/main/java/.../UavAgentMainApplication.java`**
- Spring Boot 入口，`@SpringBootApplication` + `main()` 方法
- 看有没有 `@EnableScheduling`（定时任务）、`@EnableScheduling` 等额外注解
- 这个文件只需要 10 秒扫一眼，确认是标准 Spring Boot 启动类

---

## 第二阶段：通信链路（最核心的架构）

> 目的：理解 Java 后端怎么控制 PX4 仿真无人机——这是整个项目的通信骨架。

### 阅读顺序

**4. `src/main/java/.../config/MavsdkBridgeProperties.java`**
- Spring Boot `@ConfigurationProperties`，读取 `application.yml` 中的 `mavsdk.bridge.url`
- 只有一个字段 `url`，非常简单，看完知道 Bridge 地址怎么注入的

**5. `src/main/java/.../service/MavsdkBridgeClient.java`**
- 11 个方法，对应 Python Bridge 的 11 个 REST 端点
- 看 `getTelemetry()` 理解遥测查询：GET 请求 → 返回 Map
- 看 `takeoff()` 理解飞控指令：POST 请求 → 携带 JSON body
- 每一个方法的 try-catch 模式都一样：成功返回 "ok"，失败返回 "error: ..."
- 理解 RestTemplate 的超时设置（连接 3s、读取 5s）为什么这样设计
- **这个文件是通信层的核心，建议精读**

**6. `scripts/bridge.py`**
- Python FastAPI 服务，运行在 WSL2 中
- 看 11 个 `@app.post("/xxx")` 端点如何调用 MAVSDK Python 库
- 看懂 `async for` 协程问题（已在简历文档中记录）
- 不需要逐行读懂 Python，重点看端点名称和 MavsdkBridgeClient 的方法一一对应

---

## 第三阶段：数据管道（遥测数据的完整旅程）

> 目的：理解一次遥测数据从 PX4 → Python Bridge → Java 后端 → 前端页面的完整路径。

### 阅读顺序

**7. `src/main/java/.../handler/TelemetryWebSocketHandler.java`**
- WebSocket 处理器：管理客户端连接/断开、广播消息
- 看 `afterConnectionEstablished()` 和 `afterConnectionClosed()` 理解会话管理
- 看 `broadcast()` 方法理解如何向所有前端客户端推送数据
- 使用 `CopyOnWriteArraySet` 存储 WebSocket session，线程安全

**8. `src/main/java/.../service/TelemetryPusher.java`**
- `@Scheduled(fixedRate=2000)` 每 2 秒执行一次
- 核心流程：`bridge.getTelemetry()` → `wsHandler.broadcast()` → 前端实时渲染
- 四层存储：WebSocket 广播 → Redis 缓存（5s TTL） → MySQL（10s） → MinIO（60s）
- 注入了 `TelemetryCacheService` 和 `MinioStorageService`

**9. `src/main/java/.../service/TelemetryCacheService.java`**
- Redis 遥测缓存，TTL 5 秒
- 方法简单：`cacheLatest()` 写入、`getLatest()` 读取
- 理解为什么用 5 秒 TTL：减少 Python Bridge 的 HTTP 压力

**10. `src/main/java/.../service/MinioStorageService.java`**
- MinIO 对象存储（S3 兼容）
- 3 个桶：telemetry（按日期分区）、missions（任务计划）、flight-logs（飞行日志）
- 重点看 `presignedDownloadUrl()` 和 `presignedUploadUrl()`——前端直传 MinIO 绕过后端

---

## 第四阶段：Agent 核心（LLM 飞控大脑）

> 目的：理解自然语言如何变成飞控指令——这是项目最大的亮点。

### 前置知识
看代码前，先读一下 `docs/langchain4j-interview.md` 的第 6 题（Function Calling 完整流程），5 分钟快速理解 `@Tool` 注解和 `AiServices` 代理模式。

### 阅读顺序

**11. `src/main/java/.../agent/tool/FlightControlTools.java`**
- 8 个 `@Tool` 注解方法：`takeoff`、`land`、`hold`、`arm`、`disarm`、`rtl`、`gotoWaypoint`、`queryTelemetry`
- 每一个 `@Tool` 方法的 `@P` 注解定义了参数描述（LLM 只读这个，不读 Java 代码）
- 理解 `@Tool` 方法内部怎么调用 `MavsdkBridgeClient` 的对应方法
- 重点看 `queryTelemetry()`——LLM 通过它获取无人机实时状态来决策

**12. `src/main/java/.../agent/state/MissionState.java`**
- 枚举：`IDLE → TAKEOFF → WAYPOINT → HOVER → RTL → LAND`
- 6 个飞行阶段，理解每个状态的含义

**13. `src/main/java/.../agent/state/MissionStateMachine.java`**
- 状态转移表：定义了哪些状态转换是合法的
- 例如：禁止从 IDLE 直接跳到 WAYPOINT（必须先 TAKEOFF）
- 使用 `ConcurrentHashMap<String, MissionState>` 按 sessionId 隔离多用户状态
- **面试高频考点：为什么从 volatile 改为 ConcurrentHashMap**

**14. `src/main/java/.../agent/service/AgentService.java`**
- 核心编排层：接收用户消息 → 调用 LLM Agent → 执行工具 → 返回结果
- 看 `chat(sessionId, userMessage)` 方法：这是 Agent 对话的入口
- 理解 `AiServices` 代理的构建过程：`.chatLanguageModel()` + `.tools()` + `.chatMemory()`
- 按 sessionId 缓存 Agent 实例，每个会话独立持有 Memory

**15. `src/main/java/.../agent/prompt/FlightPromptTemplate.java`**
- 154 行系统提示词，定义 LLM 的角色、规则、安全约束
- 包含：PX4 飞行阶段状态机、复合指令拆解模板、GPS 偏移计算、10 条安全硬约束
- **不需要逐行背，但要理解提示词的结构层次**

---

## 第五阶段：安全与认证（生产加固）

> 目的：理解请求从前端到后端经历了哪些安全检查。

### 阅读顺序

**16. `src/main/java/.../config/JwtTokenProvider.java`**
- JWT HS256 生成、验证、解析
- `generateToken(username, role)` → 生成 Token
- `validateToken(token)` → 验证签名 + 过期时间
- `getUsername(token)` / `getRole(token)` → 从 Token 中提取信息

**17. `src/main/java/.../config/JwtAuthenticationFilter.java`**
- Spring Security 过滤器，每个 HTTP 请求都会经过它
- 流程：提取 Authorization 头 → 解析 Bearer Token → 验证 JWT → 查黑名单 → 设置 SecurityContext
- 理解 `OncePerRequestFilter`：保证每个请求只过滤一次

**18. `src/main/java/.../service/TokenBlacklistService.java`**
- Redis 黑名单：登出时将 Token 加入 `jwt:blacklist:{token}`，TTL 对齐 Token 剩余有效期
- `isBlacklisted(token)` → 查 Redis
- 为什么不用数据库存黑名单？——Redis 查询更快，且 TTL 自动过期

**19. `src/main/java/.../config/SecurityConfig.java`**
- Spring Security 配置中心
- `/api/auth/**` 放通（登录注册不需要 Token）
- `/ws/**` 放通（WebSocket 握手不在 HTTP 头带 Token）
- `/api/**` 需要认证
- 注册 JwtAuthenticationFilter

**20. `src/main/java/.../config/RateLimiterInterceptor.java`**
- Redis ZSET 滑动窗口频率限制
- `/api/agent/chat` 60 秒内最多 20 次
- 理解 ZSET 算法：`ZADD + ZREMRANGEBYSCORE + ZCARD`

---

## 第六阶段：前端（用户界面）

> 目的：理解前端页面结构、路由跳转、API 调用和 WebSocket 连接。

### 阅读顺序

**21. `uav-web/src/router/index.js`**
- 路由表：`/login`（公开）、`/` Dashboard、`/mission` 任务、`/logs` 日志、`/charts` 图表、`/chat` AI 对话
- `beforeEach` 导航守卫：没 Token → 强制跳转 `/login`
- 理解哪些路由是公开的，哪些需要登录

**22. `uav-web/src/App.vue`**
- 根组件：根据路由决定渲染 `AppLayout`（需登录）还是纯 `<router-view>`（公开页）
- 非常简短，30 秒扫完

**23. `uav-web/src/layout/AppLayout.vue`**
- 主布局：侧边栏 + 顶栏（用户信息 + 登出按钮）
- 理解侧边栏菜单项和路由的对应关系

**24. `uav-web/src/api/http.js`**
- Fetch 封装：自动在请求头附加 `Authorization: Bearer {token}`
- 401 响应自动清除凭证并跳转登录页
- 理解为什么 `/api/auth/**` 不自动附加 Token（登录时还没有 Token）

**25. `uav-web/src/composables/useAuth.js`**
- 全局单例认证状态（`window.__AUTH_STORE__`）
- `login()` / `register()` / `logout()` 三个方法
- 理解为什么用全局单例：避免 App.vue 和 AppLayout 状态不同步

**26. `uav-web/src/composables/useWebSocket.js`**
- WebSocket 连接管理：自动连接、断线重连
- 遥测数据通过 `onmessage` 回调推送给组件

**27. `uav-web/src/views/LoginView.vue`**
- 登录/注册双 Tab 切换
- 天蓝色渐变背景 + 毛玻璃卡片
- 理解登录成功后 Token 怎么存储的（`saveAuth()` → localStorage）

**28. `uav-web/src/views/Dashboard.vue`**
- 主仪表盘：Cesium 3D 地球 + 无人机实时位置
- 遥测数据面板（电量、高度、GPS、速度）

---

## 第七阶段：消息队列与弹性防护（新增 v2.0）

> 目的：理解 RabbitMQ 事件驱动架构和 Resilience4j 熔断器如何保护系统。

### 阅读顺序

**29. `mq/RabbitMQConfig.java`**
- Topic 交换机 + 3 个业务队列 + 死信队列的拓扑定义
- Publisher Confirm + Jackson 消息转换器配置
- 理解持久化队列 + TTL + lazy 模式的配置用途

**30. `mq/MissionEvent.java`**
- 事件体（Record 类）：eventId（幂等去重）、eventType、missionId、payload
- 工厂方法：statusChanged / commandSent / anomaly / operationLog

**31. `mq/MissionEventPublisher.java`**
- RabbitTemplate 发布事件，按 Routing Key 投递到不同队列
- 理解哪些业务场景会发布事件（AgentService 状态变更 → publishStatusChanged）

**32. `mq/MissionEventConsumer.java`**
- `@RabbitListener` 监听 mission.events 队列
- 手动 ACK / NACK：成功 → ACK，失败 → NACK 重试，3 次失败 → DLQ
- 理解并发消费 `concurrency = "1-3"`

**33. `mq/TelemetryStreamConsumer.java`**
- 遥测数据异步处理，60s TTL 过期自动丢弃
- 与 TelemetryPusher 同步写入互补的关系

**34. `resilience/LLMCircuitBreaker.java`**
- Resilience4j 熔断器 + 重试组合：
  - 熔断器：50% 失败率触发，30s 半开探测
  - 重试：3 次，指数退避（1s→2s→4s）
- `executeWithProtection(supplier, fallback)` 装饰器方法
- 理解熔断器状态机 CLOSED → OPEN → HALF_OPEN

**35. `service/CommandIdempotencyGuard.java`**
- Redis SETNX 实现指令去重
- Key = `idempotency:{commandType}:{hash}`, TTL = 10s
- 理解幂等性设计的必要性（用户双击、LLM 幻觉、网络重传）

**36. `config/UavMetrics.java`**
- Micrometer 自定义 7 个业务指标
- P50/P95/P99 分位数 —— 面试时能解释为什么需要分位数而非平均值

**37. `config/OpenApiConfig.java`**
- Swagger / OpenAPI 3.0 配置
- 5 个 API 分组：AI Agent、无人机快通道、认证、对象存储、任务与日志

---

## 第八阶段：部署运维（Docker + 监控）

> 目的：理解系统怎么一键部署，怎么监控运行状态。

### 阅读顺序

**29. `docker-compose.yml`**（根目录）
- 7 个服务：MySQL、Redis、MinIO、RabbitMQ、Prometheus、Grafana、Spring Boot
- 看 `depends_on`、`healthcheck`、`ports`、`volumes`
- 理解服务之间的依赖关系

**30. `Dockerfile`**（根目录）
- 多阶段构建：Maven 编译 → JRE 运行镜像
- 非常简短，2 分钟扫完

**31. `docker/prometheus.yml`**
- Prometheus 刮取配置：`/actuator/prometheus` 端点
- 刮取间隔 15 秒

**32. `docker/grafana/`**
- 数据源配置 + 预置仪表盘
- 了解有哪些预置监控面板

**33. `scripts/start-all.bat`**
- Windows 一键启动脚本
- 依次启动：Docker 服务 → WSL2 PX4 → Python Bridge → Spring Boot → 前端

---

## 第九阶段：视觉感知引擎（v3.0 新增亮点）

> 目的：理解 YOLOv11 + ByteTrack + Depth Anything V2 + 避障算法如何赋予无人机自主感知能力。

### 阅读顺序

**34. `perception/config.py`**
- 所有可调参数：检测模型、深度模型、置信度阈值、跟踪 buffer、避障安全距离
- 理解 `@dataclass` 配置模式：一次配置，全局注入

**35. `perception/detector.py`**
- YOLOv11 目标检测封装：懒加载模型、GPU/CPU 自动适配、类别过滤
- `Detector.detect(frame)` → `List[Detection]`
- **面试重点**：YOLOv11 相比 YOLOv8 的改进（C3k2 模块）、NMS 原理

**36. `perception/tracker.py`**
- ByteTrack v2 完整实现：Kalman 滤波预测 + 匈牙利算法关联 + 两阶段匹配
- **ByteTrack 核心创新**：保留低置信度框，第二次关联恢复被遮挡目标
- **面试重点**：为什么 ByteTrack 优于 SORT/DeepSORT、Hungarian 算法 O(n³)

**37. `perception/depth_estimator.py`**
- Depth Anything V2 单目深度估计：transformers pipeline 封装
- `depth_estimator.estimate(frame)` → 深度图 → `detect_obstacles()` → 扇区分析
- **面试重点**：单目 vs 双目的优劣、相对深度 vs 绝对深度、scale ambiguity

**38. `perception/obstacle_avoider.py`**
- 势场法避障：5 扇区深度分区 → 找最空方向 → 合成速度指令（NED 坐标系）
- `compute(depth_map, target_direction)` → `AvoidanceCommand(vx, vy, vz, yaw)`
- 三级响应：Clear → 直飞, Obstacle → 转向, Danger → 后退+爬升
- **面试重点**：为什么不用 A*/RRT*（实时性 <100ms）、势场法原理

**39. `perception/perception_engine.py`**
- 全 Pipeline 编排：`process_frame(frame)` 依次执行检测→跟踪→深度→避障
- Builder 模式启用功能：`.enable_detection().enable_tracking().enable_depth().enable_avoidance()`
- 线程安全：`threading.Lock` 保护最新结果

**40. `scripts/bridge.py`（perception 部分）**
- 7 个新增感知端点：`/perception/detect` `/track` `/depth` `/avoid/*` `/follow`
- 理解感知端点如何与 MAVSDK 飞控指令联动（避障指令直接调用 `drone.offboard.set_velocity_ned`）

**41. `agent/tool/PerceptionTools.java`**
- 6 个 `@Tool` 方法：`detectObjects` `trackTarget` `enableObstacleAvoidance` `disableObstacleAvoidance` `followTarget` `queryPerceptionStatus`
- LLM 可通过自然语言调用视觉感知能力（如"搜索前方车辆并跟随"）

---

## 推荐学习节奏

| 天数 | 阶段 | 预计时间 | 重点 |
|------|------|----------|------|
| Day 1 | 第1-2阶段 | 2h | 理解通信链路，能画出 Java→Python→PX4 的图 |
| Day 2 | 第3阶段 | 1.5h | 理解遥测数据的完整旅程（4 层存储） |
| Day 3 | 第4阶段 | 2.5h | Agent 核心，理解 @Tool + AiServices + 状态机 |
| Day 4 | 第5阶段 | 1.5h | JWT + 黑名单 + 频率限制 + Security 过滤器链 |
| Day 5 | 第6阶段 | 2h | 前端路由 + API 封装 + WebSocket |
| Day 6 | 第7阶段 | 2h | RabbitMQ + Resilience4j 弹性防护 + 幂等设计 |
| Day 7 | 第8阶段 | 1h | Docker 编排 + Prometheus + Grafana |
| Day 8 | 第9阶段 | 2.5h | YOLOv11 + ByteTrack + Depth Anything V2 + 避障算法 |

| 天数 | 阶段 | 预计时间 | 重点 |
|------|------|----------|------|
| Day 1 | 第1-2阶段 | 2h | 理解通信链路，能画出 Java→Python→PX4 的图 |
| Day 2 | 第3阶段 | 1.5h | 理解遥测数据的完整旅程（4 层存储） |
| Day 3 | 第4阶段 | 2.5h | Agent 核心，理解 @Tool + AiServices + 状态机 |
| Day 4 | 第5阶段 | 1.5h | JWT + 黑名单 + 频率限制 + Security 过滤器链 |
| Day 5 | 第6阶段 | 2h | 前端路由 + API 封装 + WebSocket |
| Day 6 | 第7阶段 | 2h | RabbitMQ + Resilience4j 弹性防护 + 幂等设计 |
| Day 7 | 第8阶段 | 1h | Docker 编排 + Prometheus + Grafana |

---

## 面试最容易问到的 15 个文件（必精读）

| 优先级 | 文件 | 面试考点 |
|--------|------|----------|
| 1 | `agent/tool/FlightControlTools.java` | `@Tool` 注解原理、Function Calling |
| 2 | `agent/service/AgentService.java` | AiServices 代理、Memory 管理、并发隔离、熔断器+感知工具集成 |
| 3 | `perception/detector.py` | YOLOv11 目标检测、NMS、mAP 评估 |
| 4 | `perception/tracker.py` | ByteTrack 两阶段关联、Kalman 滤波、Hungarian 匹配 |
| 5 | `perception/depth_estimator.py` | Depth Anything V2、单目深度估计、相对深度 |
| 6 | `perception/obstacle_avoider.py` | 势场法避障、扇区分析、NED 速度合成 |
| 7 | `service/MavsdkBridgeClient.java` | 跨语言通信、超时设计、异常兜底 |
| 8 | `mq/RabbitMQConfig.java` | Topic 交换机、死信队列、Publisher Confirm、消息持久化 |
| 9 | `resilience/LLMCircuitBreaker.java` | 熔断器状态机、降级策略、指数退避重试 |
| 10 | `agent/state/MissionStateMachine.java` | 状态机设计、ConcurrentHashMap 并发 |
| 11 | `agent/tool/PerceptionTools.java` | 视觉 @Tool、LLM + CV 联动、感知-决策-执行闭环 |
| 12 | `config/SecurityConfig.java` + `JwtAuthenticationFilter.java` | 无状态认证、过滤器链 |
| 13 | `service/CommandIdempotencyGuard.java` | Redis SETNX 幂等、分布式去重设计 |
| 14 | `config/RateLimiterInterceptor.java` | Redis ZSET 滑动窗口算法 |
| 15 | `config/UavMetrics.java` | Micrometer 自定义指标、P95/P99 分位数 |
