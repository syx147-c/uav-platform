# UAV 飞行控制平台 — 简历项目描述

---

## 一句话概述

基于 **Spring Boot 3 + LangChain4j + RabbitMQ + Redis + Resilience4j + MinIO + PyTorch 视觉感知 + PX4 仿真** 的无人机飞行控制平台，支持自然语言飞控指令，集成 LLM Agent（Function Calling）、YOLOv11 + ByteTrack + Depth Anything V2 视觉感知引擎、RabbitMQ 事件驱动架构、Resilience4j 熔断器保护、Redis 限流+缓存+幂等、JWT 无状态认证、WebSocket 实时遥测、MinIO 对象存储、Micrometer + Prometheus + Grafana 可观测性、闭环安全监控与 Docker Compose 一键部署。

---

## 项目亮点（面试官一眼看完）

| 亮点 | 关键词 |
|------|--------|
| LLM Agent 自主决策执行飞控指令 | LangChain4j、AiServices、@Tool、Prompt Engineering、Function Calling |
| SOTA 视觉感知引擎 | YOLOv11 目标检测 + ByteTrack v2 多目标跟踪 + Depth Anything V2 单目深度估计 + 势场法避障 |
| RabbitMQ 事件驱动架构 | Topic Exchange + 死信队列 + Publisher Confirm + 手动 ACK + TTL |
| Resilience4j 弹性防护 | 熔断器（Circuit Breaker）+ 重试（指数退避）+ Fallback 降级 |
| Redis 五层应用 | Token 黑名单、遥测缓存、滑动窗口限流(ZSET)、指令幂等(SETNX)、会话隔离 |
| MinIO 对象存储（S3 兼容） | 遥测归档（按日期分区）、任务计划存储、预签名 URL 直传 |
| 可观测性三件套 | Micrometer 自定义指标 + Prometheus 抓取 + Grafana 8 面板仪表盘 |
| 感知-决策-执行封闭环 | LLM 高层决策 + Python 感知实时推理 + MAVSDK 底层执行 |
| 自然语言 → 结构化工具调用 | Tool Orchestration（工具编排）、状态机校验、复合指令拆解 |
| 实时遥测 WebSocket 推送 | WebSocket、服务端主动推送、三层存储（MySQL + Redis + MinIO） |
| JWT 无状态认证 + 黑名单 | HS256、Bearer Token、Redis 黑名单（登出即时失效）、Security 过滤器链 |
| 闭环安全监控线程 | @Scheduled 定时检测、异常阈值（电量/GPS/航点偏离）、自动干预 |
| Docker Compose 7 服务编排 | MySQL、Redis、MinIO、RabbitMQ、Prometheus、Grafana、前后端 |
| 跨语言通信（Java ↔ Python） | HTTP REST、MAVSDK Bridge（FastAPI）、UDP 飞控协议 |
| SpringDoc OpenAPI 3.0 | Swagger UI 交互式 API 文档，5 个 Tag 分组，30+ 接口注解 |

---

## 简历正文（可直接放入“项目经历”）

### UAVAgent — 基于 LLM Agent 的无人机飞行控制平台

**技术栈**：Spring Boot 3.5、Spring Security、MyBatis-Plus、LangChain4j 1.0-beta3（LLM Agent 框架）、RabbitMQ 3.12、Redis 7、Resilience4j 2.3、MinIO（S3 对象存储）、WebSocket、JWT（HS256）、SpringDoc OpenAPI 3.0、Micrometer + Prometheus + Grafana、PyTorch + YOLOv11 + ByteTrack v2 + Depth Anything V2（视觉感知引擎）、Vue 3 + Vite、Cesium.js、Docker Compose、Python FastAPI、PX4 SITL + Gazebo

**项目描述**：
- 设计并实现了 **LLM Agent 驱动的无人机飞控系统**，基于 **LangChain4j** 的 `@Tool` 注解 + `AiServices` 动态代理，将 14 个工具（8 个飞控 + 6 个视觉感知）注册为 LLM 可调用方法，实现**自然语言 → Function Calling → 飞控执行/视觉搜索**的完整链路。Agent 外层集成 **Resilience4j 熔断器**（失败率 50% 触发熔断、30s 半开探测）+ 指数退避重试（1s→2s→4s）
- 构建 **SOTA 视觉感知引擎**：集成 **YOLOv11**（目标检测）+ **ByteTrack v2**（多目标跟踪，两阶段关联 + Kalman 滤波）+ **Depth Anything V2**（单目深度估计，NeurIPS 2024），通过 `@Tool` 注解将 6 个感知能力注册给 LLM，实现**自然语言驱动的视觉搜索与目标跟随**
- 实现 **深度势场法实时避障**：5 扇区深度分区 + 势场法速度合成，<100ms 实时响应，三级响应策略（Clear→直飞、Obstacle→转向、Danger→后退+爬升），避障指令通过 MAVSDK offboard 模式执行
- 编写 **154 行结构化系统提示词**（Prompt Engineering），定义 PX4 飞行阶段状态机、复合指令拆解模板（GPS 偏移计算）、10 条安全硬约束（遥测不可达禁止飞控、电量<10% 自动降落、坐标(0,0)拒绝执行）
- 构建 **RabbitMQ 事件驱动架构**：Topic 交换机 + 3 个业务队列（mission.events / telemetry.stream / log.ingest）+ 死信队列，Publisher Confirm + 手动 ACK 保证消息不丢失，遥测队列 TTL 60s 自动清理过期数据
- 集成 **Resilience4j 熔断器**保护 LLM API 调用：滑动窗口 10 次、失败率 50% 触发熔断、30s 后半开探测、熔断时 Fallback 降级为手动快通道，配合指数退避重试（1s→2s→4s，最多 3 次）
- 实现 **Redis 五层应用**：① Token 黑名单（JWT 登出即时失效）② 遥测缓存（TTL 5s，减少 Bridge HTTP 压力）③ 滑动窗口频率限制（ZSET，60s 窗口）④ 指令幂等守卫（SETNX，防重复执行）⑤ 会话状态隔离（ConcurrentHashMap 按 sessionId）
- 实现 **可观测性体系**：Micrometer 自定义 8 个指标（Agent QPS/P95 延迟/飞控指令计数/异常检测次数/熔断状态），Prometheus 抓取 + Grafana 8 面板预置仪表盘
- 实现 **MinIO 对象存储**（S3 兼容协议）：遥测快照按日期分区归档、任务计划持久化、预签名 URL（前端直传 MinIO 绕过 Java 后端，PUT 10min / GET 1h）
- 基于 **PX4 SITL + Gazebo 仿真环境**，搭建 Python MAVSDK Bridge（FastAPI，11 个 REST 端点），Java 后端通过 HTTP 调用 Bridge，实现跨语言（Java ↔ Python ↔ C++ PX4）三层通信架构
- 实现 **JWT 无状态认证**：Spring Security 过滤器链 + HS256 Token + Redis 黑名单软撤销，前端导航守卫 + HTTP 拦截器自动携带 Bearer Token
- 设计 **WebSocket 实时遥测推送**，`@Scheduled(fixedRate=2000)` 定时拉取 Bridge 遥测 → WebSocket 广播 → 前端 Cesium 3D 地图，三层持久化（MySQL 10s + Redis 缓存 + MinIO 60s 归档）
- 设计 **闭环安全监控模块**（`ClosedLoopMonitor`），`@Scheduled(fixedRate=3000)` 每 3 秒检测电量/GPS/航点偏离，超阈值自动 hold/land/RTL（即使 LLM 决策失误）
- 使用 **SpringDoc OpenAPI 3.0** 为 5 个 API 分组、30+ 接口添加 Swagger 注解，支持 Swagger UI 交互式测试
- 使用 **Docker Compose 编排 7 个服务**（MySQL、Redis、MinIO、RabbitMQ、Prometheus、Grafana），Grafana 预置 UAV 飞行控制平台专用仪表盘

**个人职责**：
- 全栈独立开发：后端 80%（Spring Boot、Agent、安全、监控、Bridge）+ 前端 20%（登录页、路由守卫、API 封装）
- Agent 系统提示词设计与迭代优化（5 版迭代，从基础飞控到仿真环境感知 + 安全规则闭环）
- 跨进程通信架构设计（Windows IDEA → localhost → WSL2 Bridge → PX4 SITL）

**项目成果**：
- 成功实现"查询状态""起飞""飞行到指定坐标悬停后返航""紧急降落"等 10+ 种飞控场景
- Agent 工具调用成功率 > 90%（在有遥测反馈的情况下）
- 闭环安全监控在电量异常、GPS 丢失等场景下正确触发干预

---

## 面试官可能看重的技术点

### 1. LLM Agent 实战经验
- 不是简单地调用 ChatGPT API，而是用 **LangChain4j 的 @Tool 注解** 将业务能力注册给 LLM，LLM 自主决策调用哪些工具、传什么参数、按什么顺序
- 用 **Prompt Engineering** 约束 LLM 行为（安全规则、飞行阶段、复合指令拆解），而非硬编码 if-else

### 2. 状态机设计
- `MissionStateMachine` 用 **状态转移表** 校验飞控操作合法性（例如禁止未起飞就飞航点、禁止 RTL 后飞航点）
- 并发场景用 `ConcurrentHashMap<String, MissionState>` 隔离不同会话的状态

### 3. 安全闭环
- JWT 鉴权 + Spring Security 过滤器链（**无状态 Session 设计**，每次请求独立验证 Token）
- 运行时安全监控（`ClosedLoopMonitor`）——即使 LLM 决策失误，独立线程也能强制干预

### 4. 计算机视觉实战
- 不是调 API 做图片分类，而是从**论文算法到工程落地**：ByteTrack 从零实现（Kalman + Hungarian + 两阶段关联），Depth Anything 用 transformers pipeline 做单目深度估计
- LLM + CV 融合：LLM 负责高层决策（"跟踪那个车"），CV 负责实时感知（检测+跟踪+避障），形成**感知-决策-执行封闭环**

### 5. 系统集成能力
- Java ↔ Python 跨语言通信（Spring Boot REST → FastAPI → MAVSDK → PX4 UDP）
- Docker 多服务编排（MySQL + Prometheus + Grafana + 前后端）
- 端口转发（WSL2 → Windows，netsh interface portproxy）

---

## 适合投递的岗位方向

- **后台开发实习生**（Spring Boot + 中间件 + 微服务思想）
- **Java 开发实习生**（Spring 生态 + MyBatis + 安全 + 性能）
- **AI 应用开发实习生**（LLM Agent + Function Calling + CV 感知融合）
- **AI 算法/计算机视觉实习生**（YOLOv11 + ByteTrack + Depth Anything 实战）
- **基础架构实习生**（Docker + 监控 + 多服务编排）
- **物联网/嵌入式后端**（无人机通信 + 实时数据 + 仿真环境）

---

## 简历一句话版（精简）

> 独立开发基于 LLM Agent + SOTA 视觉感知的无人机飞行控制平台，Spring Boot + LangChain4j 实现自然语言飞控指令解析与工具调用，集成 YOLOv11 + ByteTrack + Depth Anything V2 感知引擎（检测/跟踪/避障），JWT 认证、WebSocket 实时遥测、RabbitMQ 事件驱动、闭环安全监控、Docker 容器化部署。

---

## STAR 法则版（面试口述用）

**Situation**：仿真无人机（PX4 SITL）飞控操作复杂，需要手动输入坐标和参数，普通用户难以使用。

**Task**：设计一个系统，让用户能用自然语言控制无人机（如"飞到天安门上空 50 米悬停 30 秒后返航"），且保证安全性。

**Action**：
- 用 LangChain4j 集成 DeepSeek LLM，编写 154 行系统提示词定义飞行规则和安全边界
- 用 @Tool 注解将 14 个工具（8 飞控 + 6 视觉感知）注册给 Agent，实现自然语言飞控+视觉搜索
- 集成 YOLOv11 + ByteTrack + Depth Anything V2 视觉感知引擎，实现目标检测/跟踪/避障
- 实现深度势场法避障算法（5 扇区分区 + <100ms 实时响应），通过 MAVSDK offboard 执行
- 实现状态机校验 + 闭环监控线程防止 LLM 误操作
- 设计 JWT 认证 + RabbitMQ 事件驱动 + WebSocket 实时遥测 + Docker 编排部署

**Result**：
- 支持 10+ 种飞控场景 + 6 种视觉感知能力，Agent 工具调用成功率 > 90%
- 视觉感知引擎测试 15/15 通过，GPU 加速可达 15 FPS (CPU) ~ 30 FPS (GPU)
- 闭环安全监控在异常场景下正确触发干预

---

## 面试必问：项目中遇到的最大困难及解决方案

> 面试官 90% 会问"你遇到的最大困难是什么"，以下 3 个案例任选 1-2 个回答。
> 回答结构统一用 **STAR 法则**：Situation → Task → Action → Result。

---

### 案例一：Python Bridge 遥测接口阻塞导致 Agent 卡死（已解决）

**S — 背景**：
系统架构是 Java 通过 HTTP 调用 WSL2 中的 Python MAVSDK Bridge，Bridge 再通过 UDP 与 PX4 通信。Agent 调用 `queryTelemetry()` → Bridge `/telemetry` → PX4 遥测流。

**T — 问题**：
测试时发现 Agent 偶尔卡死，整个请求超时 30 秒。排查发现 Python Bridge 的 `/telemetry` 接口用了 `async for pos in drone.telemetry.position()` —— 这是一个**无限异步迭代器**，会等待 PX4 推送下一条遥测数据。如果 Gazebo 暂停或 PX4 未发数据，协程**无限阻塞**，FastAPI 线程池被耗尽。

**A — 解决过程**：
1. 首先用 `curl -m 5 http://localhost:8000/telemetry` 复现了超时问题
2. 读了 MAVSDK Python 文档，发现 `telemetry.position()` 返回 Python `AsyncGenerator`
3. 改用 `asyncio.wait_for(stream.__anext__(), timeout=1.0)` —— 只取第一条数据，超时 1 秒抛异常
4. 超时时返回 `{"error": "telemetry_timeout"}`，Java 端识别后推送前端"Bridge 不可达"
5. 同时在 Java 版 Agent 提示词增加安全规则第 9 条："遥测不可达时禁止所有飞控指令"

**R — 结果**：
- 每次遥测请求最多 1 秒返回，不再阻塞
- 前端能实时感知 Bridge 连接状态
- Agent 在遥测失败时正确中止任务，不盲目执行飞控

**R — 反思**：
这个 bug 让我深刻理解了**异步编程中的边界问题**——从同步 Java 世界调用异步 Python 世界时，必须设置超时兜底，不能假设远程服务永远正常响应。

---

### 案例二：LLM Agent 因 GPS=0 反复拒绝执行合法飞控指令（已解决）

**S — 背景**：
GAzebo 仿真环境中，PX4 的 GPS 插件可能不加载，导致 `gps_fix` 值为 0。在真实场景中 GPS=0 意味着"无卫星信号"，应该禁止飞行。

**T — 问题**：
用户在 AI 控制台输入"向前飞 20 米"，Agent 调用 `queryTelemetry()` 获得 `gps_fix=0`，然后 LLM 判断"GPS 信号丢失，禁止航点飞行"并拒绝执行。但实际上 `takeoff` 和 `land` 不需要 GPS，仿真环境下 `gps_fix=0` 是正常的。

我最初在提示词里写了"GPS < 4 禁止飞航点"，导致 LLM 在 gps_fix=0 时反复拒绝 `gotoWaypoint()`。

**A — 解决过程**：
1. 在提示词中显式区分"仿真环境"和"真实环境"的 GPS 语义
2. 分析所有 8 个飞控工具对 GPS 的依赖程度：`takeoff/land/hold/rtl/arm/disarm` **不依赖 GPS**，只有 `gotoWaypoint` 需要有效 GPS
3. 修改提示词规则："仿真环境中 gps_fix 可能为 0 或 8，均为正常值。仅当用户需要 `gotoWaypoint` 且 GPS 坐标为 (0,0) 时拒绝执行"
4. 把有效范围从"gps_fix >= 4"改为"仅坐标 (0,0) 为非法"

**R — 结果**：
- Agent 不再因 GPS 值拒绝基本飞控操作
- 同时保留了真正的安全兜底（坐标 (0,0) 拒绝）
- 用户输入"起飞""降落""悬停"全部正常响应

**R — 反思**：
这个问题的根因是**我直接把真实世界的规则搬到了仿真环境**。仿真环境有它的特殊性（GPS 插件可能不加载），提示词需要显式告知 LLM 这些差异，不能假设 LLM 有外部上下文知识。

---

### 案例三：多用户并发导致任务状态互相覆盖（已解决）

**S — 背景**：
AgentService 最初用 `volatile MissionState currentState` 作为全局单状态字段。

**T — 问题**：
并发测试时发现：用户 A 的指令"起飞到 50 米"正在执行（状态 = EXECUTING），用户 B 输入"查询状态"后，用户 A 的状态被覆盖为 IDLE，导致 A 的任务状态混乱。

**A — 解决过程**：
1. 分析根因：`volatile` 只保证**可见性**（多线程读同一变量不会看到旧值），不保证**隔离性**（多线程写会互相覆盖）
2. 改用 `ConcurrentHashMap<String, MissionState> sessionStates` —— 按 sessionId 隔离
3. 同时修改 Agent 缓存：`ConcurrentHashMap<String, UavAgent> agentCache`，每个会话独立持有自己的 Agent 实例和 ChatMemory
4. 状态机校验从 `currentState == XXX` 改为 `sessionStates.get(sessionId) == XXX`
5. 添加 `@PreDestroy` 清理：应用关闭时遍历所有活跃会话，标记为 FAILED

**R — 结果**：
- 多用户并发操作完全隔离，互不影响
- 每个会话独立持有 Memory，对话历史不串扰
- 代码改动集中在一处（ConcurrentHashMap 替换 volatile），风险可控

**R — 反思**：
`ConcurrentHashMap` 不是万能药——它解决的是并发读写问题，但如果多个线程同时修改同一个 sessionId 的状态，仍然需要加锁。当前设计是每个 sessionId 同一时刻只有一个 Agent 线程在操作（LLM 调用是串行的），所以不需要额外锁。未来如果支持同一会话内并发工具调用，需要考虑加锁。

---

### 面试话术模板（30 秒版）

> "我遇到的最大挑战是 **Python Bridge 的遥测接口在仿真暂停时会无限阻塞**。原因是 Python 用了 async for 等待异步迭代器推送数据，没有超时机制。
>
> 我通过三步解决：第一，用 asyncio.wait_for 包装迭代器，设 1 秒超时；第二，超时后返回 error JSON 而不是挂起；第三，在 Java Agent 的提示词里加了安全规则——遥测不可达时禁止飞控指令。
>
> 这个 bug 让我意识到了**跨语言异步通信中必须设置超时兜底**，不能假设远程服务永远正常。"

---

---
## 简历精简版（后台开发实习生）

### 技术栈

`Spring Boot 3.5` `Spring Security` `MyBatis-Plus` `LangChain4j 1.0` `RabbitMQ` `Redis` `Resilience4j` `MinIO` `WebSocket` `JWT` `YOLOv11` `ByteTrack` `Depth Anything V2` `PyTorch` `Micrometer + Prometheus + Grafana` `Docker Compose`

---

### 项目经历

**UAVAgent — 基于 LLM Agent 的无人机飞行控制平台**（后端独立开发）

- **LLM Agent 智能编排层**：基于 **LangChain4j** `@Tool` 注解 + `AiServices` 动态代理，将 14 个工具（8 飞控 + 6 视觉感知）注册为 LLM 可调用工具，实现**自然语言 → Function Calling → 飞控执行/视觉搜索**的完整链路。Agent 外层集成 **Resilience4j 熔断器**（失败率 50% / 30s 半开）+ 指数退避重试，熔断时 Fallback 降级
- **SOTA 视觉感知引擎**：集成 **YOLOv11** 目标检测 + **ByteTrack v2** 多目标跟踪（Kalman + Hungarian 匹配）+ **Depth Anything V2** 单目深度估计（NeurIPS 2024）+ 深度势场法实时避障（<100ms），通过 `@Tool` 注册给 LLM，实现自然语言驱动视觉搜索与目标跟随
- **三层安全防护架构**：① Prompt 约束层 —— 154 行系统提示词，定义状态机 + 10 条硬约束；② 状态机校验层 —— 状态转移表 + `ConcurrentHashMap` 会话隔离；③ 闭环监控层 —— `@Scheduled` 独立线程每 3 秒异常检测，LLM 失误也能强制干预
- **RabbitMQ 事件驱动架构**：Topic 交换机 + 3 个业务队列（指令 / 遥测 / 日志）+ 死信队列，Publisher Confirm + 手动 ACK 保证消息可靠投递，遥测队列 TTL 60s 自动清理
- **Redis 多层应用**：① 滑动窗口限流（ZSET，60s 窗口）② 指令幂等（SETNX）③ Token 黑名单（JWT 登出即时失效）④ 遥测热缓存（TTL 5s）⑤ 会话状态隔离
- **可观测性体系**：Micrometer 自定义 8 个业务指标（Agent QPS / P95 延迟 / 飞控指令计数 / 熔断状态），Prometheus 抓取 + Grafana 预置仪表盘
- **JWT 无状态认证**：Spring Security 过滤器链 + HS256 + Redis 黑名单软撤销
- **MinIO 对象存储**：遥测按日期分区归档 + 预签名 URL 直传（绕过 Java 后端）
- **Docker Compose** 编排 MySQL、Redis、RabbitMQ、MinIO、Prometheus、Grafana 共 7 个服务，一键部署

**个人职责**：
- 独立负责后端架构设计及 80% 代码开发（Agent 服务、消息队列、安全模块、监控体系）
- Agent 系统提示词设计与 5 版迭代优化，工具调用成功率 > 90%
- 设计消息可靠性方案（死信队列 + ACK + TTL）与接口防护策略（限流 + 熔断 + 幂等）

---

### 面试官追问预判（星号标注 = 高频）

> **Q★：LangChain4j 的 @Tool 是怎么工作的？**
>
> A：`@Tool` 注解标记方法后，LangChain4j 通过 `AiServices` 生成动态代理，自动提取方法签名 + 参数描述 + 返回值结构，序列化为 OpenAI Function Calling 的 JSON Schema 格式。当用户输入自然语言，LLM 返回一个 function_call（工具名 + 参数 JSON），代理层反序列化为 Java 方法调用。本质上和 Spring AOP 的代理模式一样，只是切面逻辑换成了 LLM 决策。

> **Q★：Agent 调用链路怎么保证稳定性？**
>
> A：四层防护——① Resilience4j 熔断器保护 LLM API（失败率 50% 触发熔断，30s 后半开）；② 熔断时 Fallback 走手动快通道，不丢请求；③ Agent 决策结果经过状态机校验（禁止非法状态转移）；④ 闭环监控独立线程兜底（LLM 决策失误也能强制干预）。LLM 是"建议者"而不是"执行者"，最终控制权在状态机和监控线程。

> **Q：提示词里为什么要写状态机和硬约束，而不是代码里 if-else？**
>
> A：LLM 需要上下文才能做出正确决策——如果不在 Prompt 里告诉它"gps_fix=0 时禁止 gotoWaypoint"，它可能会在仿真环境下反复拒绝合法指令。本质上 Prompt 就是 LLM 的"运行环境"，和 JVM 的 -Xmx 一样决定了行为边界。硬约束写在 Prompt 里是因为 LLM 在 Function Calling 之前就会做意图判断，这个阶段代码还拦截不到。

---

### 面试官可能看重的技术点

**Q：这些困难听起来都是 Bug 修复，有没有架构层面的困难？**

**A**：有的。最大的架构决策是 **LLM Agent 的自治边界怎么划分**。LLM 可以自主决策工具调用，但如果 LLM 失误了怎么办？我的方案是"三层防护"：Prompt 约束（事前）→ 状态机校验（事中）→ 闭环监控（事后），而不是让 LLM 拥有完全自主权。这个架构折中了灵活性和安全性。

**Q：如果用 Java 线程池管理 Agent 任务，你考虑过吗？**

**A**：当前是 Tomcat 请求线程直接执行 Agent 调用（同步阻塞），没有额外线程池。如果未来并发量上来，可以用 `ExecutorService` 管理 Agent 任务，每个任务 Future.get() 设超时，超时后 cancel(true) 中断。但这会带来新问题——飞控指令中断后无人机状态不确定，需要配合 ClosedLoopMonitor 兜底。

> **Q★：为什么选 YOLOv11 + ByteTrack 这个组合？有没有对比过其他方案？**

> A：检测选了 YOLOv11 nano（2.6M 参数，CPU 30FPS）——对比 RT-DETR v2（精度高但推理慢 2-3x）和 D-FINE（部署复杂），无人机上**实时性 > 精度**。跟踪选了 ByteTrack——对比 DeepSORT（ReID 特征网络 +30ms 开销）和 SORT（无低置信度恢复），ByteTrack 的两阶段关联在**遮挡场景下 ID Switch 减少 50%+**，且只需 IoU 匹配，不增加推理负担。

> **Q：LLM Agent 和视觉感知是怎么联动的？**

> A：通过 `@Tool` 注解，LLM 可以调用 6 个感知工具（detectObjects / trackTarget / enableAvoidance / followTarget 等）。关键是**分工**：LLM 负责高层语义决策（"搜索前方车辆"），避障放在 Python 端硬实时执行（<100ms，不经过 LLM 推理）。LLM 是"建议者"，避障是"保镖"——遇到障碍物时避障子系统直接 override 飞控指令。这个架构折中了 LLM 的灵活性和飞控的安全性。
