# UAV 飞行控制平台 — 面试题与答案

> 目标岗位：后台开发实习生  
> 共计 44 题，覆盖：项目概况、LLM Agent、Spring Boot 架构、JWT 安全、数据库、并发性能、Redis 三层应用、MinIO 对象存储、LangChain4j 深入、Docker 部署、困难解决、计算机视觉感知、基础速记  
> 使用方式：先看问题自问自答，再对照答案补充，重点记忆 **加粗关键词** 用于面试输出

---

## 一、项目概况

### Q1：简单介绍一下这个项目？

**答**：这是一个**无人机飞行控制平台**，核心功能是让用户通过**自然语言**控制仿真无人机。

用户在前端输入"起飞到 50 米悬停 30 秒后返航"，后端 **LLM Agent**（LangChain4j + DeepSeek）会自主拆解为原子飞控操作——先查遥测、再起飞、飞航点、悬停、返航——每一步调用对应的工具方法。飞行数据通过 **WebSocket** 实时推送到前端 **Cesium 3D 地图**上渲染。

系统还做了 **JWT 认证**、**闭环安全监控**（电量 < 10% 自动降落）、**Docker 一键部署**。

---

### Q2：项目中你主要负责哪些部分？

**答**：全栈独立开发。后端占比约 80%，前端约 20%。

后端核心我做了：
- **LLM Agent 编排层**（AgentService + FlightControlTools + 提示词模板）
- **飞行状态机**（MissionStateMachine，校验操作合法性）
- **JWT 认证体系**（Token 生成/验证/过滤器 + Spring Security 配置）
- **闭环安全监控**（@Scheduled 定时检测异常并自动干预）
- **Python MAVSDK Bridge**（FastAPI，连接 PX4 仿真环境）

前端我做了：**登录页**（天蓝色主题）、**路由守卫**、**HTTP 拦截器**（自动携带 Token）。

---

## 二、LLM Agent 相关（高频考点）

### Q3：LLM Agent 是怎么实现自然语言控制无人机的？

**答**：核心思路是 **Function Calling（工具调用）**。

我用 **LangChain4j** 框架，通过 `@Tool` 注解把飞控操作（查询遥测、起飞、降落、飞航点、悬停、返航等）声明为工具方法。LLM 收到用户的自然语言输入后，结合**系统提示词**（154 行，定义了飞行规则、安全约束、复合指令拆解模板），自主决策：
1. 需要调用哪些工具
2. 按什么顺序调用
3. 每个工具传什么参数

我**不写任何 if-else 分支**判断用户意图，全部由 LLM 推理完成。每次工具调用后，把结果反馈给 LLM，让它决定下一步，形成**感知-决策-执行**的闭环。

---

### Q4：LangChain4j 的核心组件有哪些？你是怎么用的？

**答**：我用到的核心组件：

| 组件 | 作用 |
|------|------|
| **ChatLanguageModel** | 对接 DeepSeek API（OpenAI 兼容协议） |
| **@Tool 注解** | 将 Java 方法注册为 LLM 可调用的工具 |
| **@P 注解** | 描述工具参数，帮助 LLM 理解参数含义 |
| **AiServices** | 动态创建 Agent 代理对象，自动处理工具调用循环 |
| **SystemMessage** | 系统提示词，定义 Agent 角色和行为规则 |

工作流程：
1. `AiServices.builder()` 配置 LLM 模型 + 工具对象
2. 构建时注入 **system prompt**（角色 + 规则 + 安全约束）
3. 调用 `agent.chat(message)` → LangChain4j 自动完成：发送消息 → LLM 决策 → 工具调用 → 结果反馈 → 下一轮决策，直到 LLM 输出最终回复

---

### Q5：Agent 怎么保证不会做出危险操作？

**答**：三层层层防护：

**第一层：Prompt 约束（事前）**。系统提示词定义了：
- 任何操作前必须先 `queryTelemetry` 检查状态
- 电量 < 20% 建议降落，< 10% 自动执行 `land()`
- 坐标 (0, 0) 为非法值拒绝执行
- 遥测不可达时**禁止所有飞控指令**
- GPS 值（0 或 8）在仿真环境均正常，不影响基本操作

**第二层：状态机校验（事中）**。`MissionStateMachine` 用**状态转移表**校验每一步操作：
- 禁止从 IDLE 直接飞航点（必须经过 TAKEOFF）
- 禁止 RTL 后飞航点
- 禁止降落中起飞

不合法的状态转移直接拒绝，不调用 MAVSDK。

**第三层：闭环监控（事后兜底）**。`ClosedLoopMonitor` 每 3 秒检查：
- 电量 < 10% → 自动 RTL
- GPS 丢失 > 10 秒 → 自动悬停
- 偏离航点 > 50m → 记录告警日志

即使 LLM 决策失误，独立线程也能强制干预。

---

### Q6：你的系统提示词（Prompt）是怎么设计的？

**答**：提示词 154 行，分层结构化设计：

```
# 角色（定义 Agent 是谁、运行在什么环境）
→ "你是 PX4 无人机飞行控制智能体"

# 仿真环境（告知环境参数）
→ 机型、起飞点 GPS、通信延迟

# 可用工具（每个工具的功能、参数、调用规则）
→ queryTelemetry、takeoff(alt)、land()、gotoWaypoint(lat,lon,alt) 等

# PX4 飞行阶段（状态转移顺序）
→ IDLE → TAKEOFF → WAYPOINT → HOVER → RTL → LAND → IDLE

# 复合指令处理规则（自然语言模板 → 工具序列映射）
→ "飞到X上空Y米，悬停Z秒后返航" = takeoff(Y) → gotoWaypoint → hover(Z) → RTL
→ "向前飞X米" = GPS偏移计算（纬度每度≈111320m）

# 安全规则（10条硬约束）
→ 遥测失败立即停止、电量阈值、坐标合法性等

# 回复格式（输出规范化）
→ 【工具名】→ 结果简述
```

关键设计理念：**不是让 LLM 自由发挥，而是给它一组精确的操作模板和硬约束边界**。

---

## 三、Spring Boot 后端架构

### Q7：后端整体分层架构是怎样的？

**答**：标准的分层架构 + Agent 模块：

```
controller/      → REST API 层（DroneController、AuthController、AgentController）
agent/controller → Agent 专用控制器
agent/service/   → AgentService（LLM 编排）、ClosedLoopMonitor（安全监控）
agent/tool/      → FlightControlTools（@Tool 方法，LLM 可调用的工具集）
agent/state/     → MissionStateMachine（飞行状态机）
agent/prompt/    → FlightPromptTemplate（系统提示词常量）
config/          → SecurityConfig、JwtTokenProvider、JwtAuthenticationFilter
service/         → MavsdkBridgeClient（HTTP 调用 Python Bridge）、TelemetryPusher
entity/mapper/   → MyBatis-Plus 数据层
```

---

### Q8：为什么用 MavsdkBridgeClient 而不是直接调用 MAVSDK？

**答**：因为 **MAVSDK 只有 Python/C++ 版本，不支持 Java**。

我的方案：
```
Java (Spring Boot) --HTTP--> Python (FastAPI Bridge) --MAVSDK UDP--> PX4 SITL
```

Python MAVSDK Bridge 把 MAVSDK 的异步 API（`drone.action.takeoff()`、`drone.action.goto_location()` 等）封装成 **同步 HTTP REST 接口**（POST /takeoff、POST /waypoint、GET /telemetry）。Java 后端用 `RestTemplate` 调用。

这是一个典型的**跨语言通信**设计——用 HTTP 作传输层，用 JSON 作数据格式，解耦语言依赖。

---

### Q9：TelemetryPusher 的 WebSocket 推送机制是怎样的？

**答**：`@Scheduled(fixedRate = 1000)` 每秒执行一次：
1. 调用 `MavsdkBridgeClient.getTelemetry()` 获取遥测
2. 更新 `FlightControlTools` 的缓存（Agent 下次 queryTelemetry 时直接返回缓存，减少 Bridge 调用）
3. 通过 `SimpMessagingTemplate` 广播到 `/topic/telemetry`
4. 每 5 次额外写入一次数据库（`uav_telemetry_snapshot`）
5. 如果 Bridge 不可达，推送 `{"connected": false, "error": true}` 通知前端

前端 `useWebSocket.js` 连接 `ws://localhost:8080/ws/telemetry`，收到消息后更新响应式 ref，Vue 自动刷新 DOM。

---

## 四、JWT 认证与安全

### Q10：JWT 认证流程是怎样的？

**答**：无状态 Session 设计，每次请求携带 Token。

**登录流程**：
1. 前端 `POST /api/auth/login` 发送用户名 + 密码
2. `AuthController` 调用 BCryptPasswordEncoder 验证密码
3. 验证通过 → `JwtTokenProvider.generateToken(username, role)` 生成 HS256 Token
4. 返回 `{token, username, role}` 给前端
5. 前端存入 localStorage，后续所有请求通过 `Authorization: Bearer <token>` 携带

**请求验证流程**：
1. `JwtAuthenticationFilter`（继承 OncePerRequestFilter）拦截所有请求
2. 从 Authorization 头提取 Bearer token
3. `JwtTokenProvider.validateToken()` 验证签名 + 过期时间
4. 验证通过 → 构造 `UsernamePasswordAuthenticationToken` 放入 SecurityContext
5. Spring Security 根据 `authorizeHttpRequests` 配置检查该 URL 是否需要认证

---

### Q11：为什么用 HS256 而不是 RS256？

**答**：对称加密（HS256）适合**单体应用**场景——签名和验证使用同一个密钥，部署简单，不需要管理公私钥对。我的项目是单体 Spring Boot 应用，HS256 足够。如果是微服务架构（多个服务需要验证 Token），改用 RS256 更好，因为只有认证服务持有私钥，其他服务可以用公钥验证。

---

### Q12：SecurityConfig 里 permitAll 和 authenticated 怎么划分的？

**答**：

| URL 模式 | 权限 | 原因 |
|----------|------|------|
| `/api/auth/**` | permitAll | 登录/注册接口，用户还未获得 Token |
| `/ws/**` | permitAll | 浏览器 WebSocket API 不支持自定义请求头，JWT 无法在握手时携带 |
| `/actuator/**` | permitAll | Prometheus 刮取指标、Docker 健康检查不需要认证 |
| `/api/system/**` | permitAll | 系统健康检查 |
| `/api/**` | authenticated | 所有业务 API 需要有效 JWT |

---

## 五、数据库设计

### Q13：数据库表怎么设计的？

**答**：4 张表：

| 表 | 用途 |
|----|------|
| `sys_user` | 用户表，存储 BCrypt 加密密码 + 角色 |
| `uav_mission` | 飞行任务表，存储自然语言描述 + LLM 生成的任务计划（JSON）+ 状态 |
| `uav_flight_log` | 飞行日志表，记录每次飞控操作（ARM/TAKEOFF/WAYPOINT/...）+ 来源（Agent/Manual/Emergency） |
| `uav_telemetry_snapshot` | 遥测快照表，定时存储 GPS/高度/速度/电量，支持历史轨迹回溯 |

索引设计：`uav_telemetry_snapshot` 建了 `(mission_id, created_at)` 复合索引，用于按任务查询遥测历史。

---

## 六、并发与性能

### Q14：多用户同时发指令，状态怎么隔离？

**答**：最开始我用 `volatile MissionState currentState`，这是一个**全局单状态**。后来发现多用户会互相覆盖状态——用户 A 的指令会覆盖用户 B 的状态。

改用 `ConcurrentHashMap<String, MissionState> sessionStates`，**按 sessionId 隔离状态**。每个前端会话有独立的 sessionId，Agent 操作只影响当前会话。Ll Agent 代理也做了缓存：`ConcurrentHashMap<String, UavAgent> agentCache`，按 sessionId 复用。

`@PreDestroy` 时遍历清理所有活跃会话，标记为 FAILED。

---

### Q15：项目中有哪些性能优化点？

**答**：
- **Agent 代理缓存**：`ConcurrentHashMap` 按 sessionId 缓存 `UavAgent` 代理对象，避免每次请求重建 AiServices（创建代理涉及反射+代理类生成，有开销）
- **遥测缓存**：`FlightControlTools` 缓存最新遥测，Agent 的 `queryTelemetry()` 直接返回缓存，减少对 Python Bridge 的 HTTP 调用
- **RestTemplate 超时**：connect 3s、read 5s，避免 Bridge 不可达时线程阻塞
- **前端懒加载路由**：`() => import('../views/...')`，首屏只加载 Dashboard
- **数据库连接池**：HikariCP，最大 10 连接，最小 5 空闲

---

## 七、Redis 应用（高频考点）

### Q16：项目中 Redis 用在哪些场景？

**答**：三个场景，复杂度递进：

**① Token 黑名单（最简单的 KV 操作）**
- 问题：JWT 签发后无法撤销，用户登出了 Token 仍然有效
- 解决：登出时将 Token 写入 Redis，Key = `jwt:blacklist:<token>`，Value = "1"，TTL = Token 剩余有效时间
- JwtAuthenticationFilter 验证时先查黑名单，命中则拒绝
- key 自动过期，不产生垃圾数据

**② 遥测数据缓存（对象缓存 + TTL）**
- 问题：LLM Agent 频繁调用 `queryTelemetry()`，每次都走 Bridge HTTP 浪费资源
- 解决：Redis 缓存最新遥测快照，TTL 5 秒，确保数据新鲜度的同时减少 Bridge 调用
- 使用 `RedisTemplate<String, Object>` + Jackson 序列化
- 相比 in-memory `volatile` 字段：支持未来多实例共享、自动过期、可监控命中率

**③ API 频率限制（滑动窗口算法 + ZSET 数据结构）**
- 问题：LLM API 调用成本高，需要限制 /api/agent/chat 的频率
- 解决：Redis ZSET（Sorted Set）实现滑动窗口
- 算法：
  1. 每个请求以毫秒时间戳作为 Score 加入 ZSET
  2. 删除窗口外（60 秒前）的记录
  3. 统计剩余数量 → 超阈值返回 429
- 复杂度 O(log N)，原子操作，支持分布式

---

### Q17：Redis 的持久化策略了解吗？项目中怎么选的？

**答**：Redis 有两种持久化：**RDB（快照）**和 **AOF（追加日志）**。

项目中用的是 **AOF（appendonly yes）**——每次写操作追加到日志文件，重启后重放恢复数据。适合 Token 黑名单场景（不能丢数据，登出后 Token 必须失效）。

同时配置了 **maxmemory 128mb + allkeys-lru 淘汰策略**——内存满时淘汰最近最少使用的 Key，保护内存不溢出。

---

### Q18：频率限制用 Redis 而不是用 Guava RateLimiter 有什么考虑？

**答**：
- **Guava RateLimiter**：JVM 内存级，单机有效，重启丢失，无法跨实例共享
- **Redis**：独立进程，所有实例共享同一份计数，适合未来水平扩展；而且 Token 黑名单和缓存已经在 Redis 里，不需要引入额外的组件

这是典型的**分布式 vs 单机**的权衡——项目当前是单体，但选型上优先考虑分布式方案。

---

## 八、MinIO 对象存储

### Q19：MinIO 是什么？为什么选 MinIO 而不是云服务（OSS/S3）？

**答**：MinIO 是**开源的对象存储服务**，完全兼容 **AWS S3 API**。

选择理由：
1. **本地开发不需要联网**——OSS/S3 需要注册云账号，成本高
2. **S3 兼容协议**——代码写的 `PutObjectArgs`/`GetObjectArgs` 跟 S3 SDK 一模一样，未来迁移到云只需改 endpoint
3. **Docker 一键部署**——`docker run minio/minio` 即可，没有额外依赖
4. **预签名 URL**——MinIO 支持 `getPresignedObjectUrl()`，前端可以直接上传/下载，不经过后端，节省带宽

---

### Q20：项目中 MinIO 存了什么？数据怎么组织？

**答**：3 个 Bucket：

| Bucket | 存储内容 | 分区策略 |
|--------|----------|----------|
| `telemetry` | 遥测快照（JSON） | 按日期：`telemetry/2026-05-14/snapshot-1715664123456.json` |
| `missions` | 任务计划（JSON） | `missions/{missionId}-plan.json` |
| `flight-logs` | 飞行日志（JSON） | `flight-logs/{logId}.json` |

设计考虑：
- 按日期分区方便按时间范围查询和清理
- JSON 格式可直接被大数据工具（Spark/Flink）消费
- 预签名 URL 让前端直接下载，不需要后端做流式转发

---

### Q21：对象存储 vs 块存储 vs 文件存储 的区别？

**答**：

| 类型 | 代表 | 特点 | 适用场景 |
|------|------|------|----------|
| **块存储** | EBS/Ceph RBD | 低延迟、随机读写、无目录结构 | 数据库、虚拟机磁盘 |
| **文件存储** | NFS/NAS | 目录树、权限管理、多机共享 | 共享文件、代码仓库 |
| **对象存储** | MinIO/S3/OSS | 扁平命名空间、HTTP API 访问、无限扩展、元数据丰富 | 图片/视频/日志/备份 |

项目中遥测快照和任务计划是典型的**非结构化数据**，扁平命名空间 + HTTP 访问最合适，所以用对象存储。

---

## 九、LangChain4j 深入

### Q22：LangChain4j 的 @Tool 注解是怎么工作的？

**答**：`@Tool` 注解标记一个 Java 方法为 LLM 可调用的工具。

当 LLM 决定调用工具时，LangChain4j 通过**反射**找到对应方法，构造参数并执行。返回值以 JSON 格式回传给 LLM，LLM 根据返回值决定下一步操作。

关键细节：
- `@Tool("工具名")` 定义工具名称（LLM 看到的函数名）
- `@P("参数描述")` 注解在每个参数上，帮助 LLM 理解参数含义
- LLM 本身**不执行代码**——它输出一个 "function_call" 标记，LangChain4j 框架拦截这个标记，调用对应的 Java 方法，把结果返回给 LLM

这就是 **Function Calling** 的完整循环：LLM 决策 → 框架执行 → 结果反馈 → LLM 再决策。

---

### Q23：AiServices 和直接调用 ChatLanguageModel 有什么区别？

**答**：

- **直接调用**：`model.chat("起飞")` 只返回文本，需要手动解析 LLM 响应中的 function_call 标记，然后手动调用工具方法
- **AiServices**：创建动态代理对象，定义接口方法 → 框架自动处理工具调用循环（planning → executing → observing → re-planning）

AiServices 的好处：
1. 代码量少 80%——不用写 function_call 解析逻辑
2. 自动重试——工具调用失败后 LLM 会尝试修正（最多 2 次）
3. 类型安全——接口方法定义输入输出类型
4. 会话管理——内置 ChatMemory 支持多轮对话上下文

---

## 十、Docker 与部署

### Q24：Docker Compose 怎么编排的？

**答**：5 个服务：MySQL 8.0、Prometheus、Grafana、后端（Spring Boot）、前端（Vite/Nginx）。

服务间通过 Docker 内部网络通信（`uav-net`）。MySQL 数据持久化到 `./docker-data/mysql`，Grafana 仪表盘预置到 `./docker/grafana/`。

后端暴露 8080 端口，Prometheus 每 15s 刮取 `/actuator/prometheus` 指标，Grafana 预配置了 JVM 内存、HTTP 请求延迟、飞控操作计数器三张仪表盘。

---

### Q25：WSL2 到 Windows 的端口转发怎么做的？

**答**：PX4 SITL 在 WSL2 内运行，MAVSDK Bridge（FastAPI，端口 8000）也在 WSL2。Windows 上的 Java 后端需要通过 `localhost:8000` 访问 Bridge。

WSL2 默认可以**从 Windows 访问 WSL 的服务**（通过 localhost），但 WSL2 的 IP 地址在重启后会变化。我的做法：
1. Bridge 监听 `0.0.0.0:8000`（所有网络接口）
2. Java 后端配置 `mavsdk.bridge.url=http://localhost:8000`
3. 如果 localhost 转发失效（某些网络环境），用 `netsh interface portproxy` 建立 14540 (UDP) 和 8000 (TCP) 端口转发

---

## 十一、困难与解决方案（行为面试高频）

### Q26：你在项目中遇到的最大困难是什么？

**答**：最大的困难是 **为什么有些问题用传统 if-else 很难解决**。

具体场景：PX4 的 GPS `gps_fix` 值在仿真环境下可能是 0 也可能是 8（取决于 Gazebo 是否加载 GPS 插件）。如果按真实场景理解，GPS 为 0 意味着"无卫星信号"，应该禁止飞行。但在仿真中这是正常值。

我最初在 Prompt 里写了"GPS < 4 禁止飞航点"，导致 LLM 在 gps_fix=0 时反复拒绝执行 `gotoWaypoint()`，而 `takeoff()` 和 `land()` 其实不需要 GPS。

**解决方案**：修改 Prompt 规则——明确告知 LLM "这是仿真环境，GPS 值由仿真决定，0 或 8 均为正常，不影响 takeoff/land/hold/rtl"。同时保留坐标 (0, 0) 作为真异常值拒绝。

**反思**：这让我明白**仿真/测试环境与生产环境的差异需要显式告知 AI 系统**，不能假设 AI 有外部上下文。

---

### Q27：Python Bridge 的遥测接口为什么会超时？

**答**：最初 Bridge 的 `/telemetry` 用 `async for pos in drone.telemetry.position()` 等待下一个遥测更新。如果 PX4 停止推送（如 Gazebo 暂停），这个协程会无限阻塞。

**解决方案**：改用 `asyncio.wait_for(stream.__anext__(), timeout=1.0)` 取第一条数据，超时 1 秒后抛异常返回 error。这样每次请求最多耗时 1 秒，不会阻塞 FastAPI 的线程池。

---

## 十二、高频基础题速记

### Q30：Spring Boot 自动配置原理？

**答**：`@SpringBootApplication` 包含 `@EnableAutoConfiguration`，启动时扫描 classpath 下的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，根据条件注解（`@ConditionalOnClass`、`@ConditionalOnMissingBean` 等）决定是否加载某个自动配置类。

### Q31：MyBatis 和 MyBatis-Plus 的区别？

**答**：MyBatis-Plus 在 MyBatis 的基础上提供了**内置 CRUD 方法**（通过继承 `BaseMapper`），**分页插件**、**乐观锁**、**逻辑删除**等开箱即用功能。开发者不需要写 XML 也能完成基本 CRUD，复杂查询仍可自定义 SQL。

### Q32：Spring Security 过滤器链执行顺序？

**答**：大致顺序：`SecurityContextPersistenceFilter` → `UsernamePasswordAuthenticationFilter` → `ExceptionTranslationFilter` → `FilterSecurityInterceptor`。自定义的 `JwtAuthenticationFilter` 插入在 `UsernamePasswordAuthenticationFilter` 之前，提取 Token 并设置 SecurityContext。

### Q33：BCrypt 比 MD5/SHA 好在哪里？

**答**：BCrypt 有 **salt（盐值）**——每次加密会随机生成 16 字节盐值并嵌入结果中，相同密码每次加密结果不同。还有 **cost factor（工作因子）**——可以通过增加迭代次数对抗硬件性能提升（摩尔定律）。MD5/SHA 是通用哈希，可以并行暴力破解。

---

## 十一、RabbitMQ 事件驱动架构（新增亮点）

### Q27：项目中 RabbitMQ 是怎么用的？为什么不用更简单的 @Async？

**答**：我用 RabbitMQ 构建了**事件驱动架构**，解耦核心业务与日志/通知/异常检测等非核心逻辑。

**Exchange/Queue 拓扑**：
```
uav.topic (Topic Exchange)
    ├── Routing Key: mission.status.changed → Queue: mission.events
    │     消费者: MissionEventConsumer (异步写飞行日志)
    ├── Routing Key: telemetry.raw / telemetry.anomaly → Queue: telemetry.stream
    │     消费者: TelemetryStreamConsumer (批量持久化 + 异常检测)
    │     TTL: 60s (遥测有时效性，过期自动丢弃)
    └── Routing Key: log.operation → Queue: log.ingest
          消费者: LogIngestConsumer (批量写入 ElasticSearch)
          模式: lazy (直接写磁盘，节省内存)

uav.dlx (Dead Letter Exchange)
    └── uav.dlq (死信队列，人工排查失败消息)
```

**为什么不用 @Async？**
- `@Async` 是**进程内异步**，应用重启后任务丢失
- RabbitMQ 消息**持久化到磁盘**，Broker 重启不丢
- RabbitMQ 有 **死信队列**，失败消息可追溯
- RabbitMQ 支持**消费端手动 ACK**，确保消息被成功处理

**可靠性保证**（面试官一定追问）：
1. Publisher Confirm — 发送端确认消息已到达 Broker
2. 手动 ACK — 消费成功后 `channel.basicAck()`，失败后 `basicNack()` 重试
3. 持久化队列 + 持久化消息
4. 失败 3 次入死信队列，不无限重试
5. 遥测队列 TTL 60s — 过期数据自动丢弃（Redis 已有最新缓存兜底）

---

### Q28：Publisher Confirm 和手动 ACK 是怎么配合的？能保证不丢消息吗？

**答**：

**Publisher Confirm（发送端确认）**：
- RabbitTemplate 开启 ConfirmCallback
- 消息发送后，Broker 返回 ACK/NACK
- NACK 时回调记录日志，后续可通过定时任务补偿重发

**手动 ACK（消费端确认）**：
- 配置 `acknowledge-mode: manual`
- 消费者处理成功后显式调用 `channel.basicAck(deliveryTag, false)`
- 处理失败 → `channel.basicNack(deliveryTag, false, true)` 重新入队
- 连续失败 3 次 → `basicNack(deliveryTag, false, false)` 不重新入队 → 进入死信队列

**能保证不丢消息吗？**
- 正常情况：Confirm + 手动 ACK + 持久化 → **不丢**
- Broker 宕机前未持久化：极小概率丢失（可配置 `mandatory: true` + ReturnCallback 兜底）
- 消费端宕机：未 ACK 的消息自动重新入队（RabbitMQ 有 consumer timeout）

**项目中的权衡**：遥测队列的 TTL 是 60s，意味着如果消费者宕机 > 60s，旧遥测数据会过期丢弃。这是**有意的设计选择**——遥测是时序数据（每秒产生），过时数据价值低，Redis 已有最新缓存兜底。

---

### Q29：RabbitMQ 的 Exchange 类型有哪些？为什么选 Topic？

**答**：4 种：

| 类型 | 路由规则 | 适用场景 |
|------|---------|---------|
| Direct | 完全匹配 routing key | 点对点 |
| Fanout | 广播到所有绑定队列 | 发布/订阅 |
| **Topic** | 通配符匹配（`*` 一个词，`#` 零或多个词） | 灵活的多消费者路由 |
| Headers | 按消息 Header 匹配 | 特殊路由需求 |

选 Topic 的理由：可以用一个 exchange 按 `mission.*`、`telemetry.*`、`log.*` 分组路由，**语义清晰、扩展方便**。未来如果加 `notification.*` 路由，只需绑定新队列，不改代码。

---

### Q30：消息积压了怎么办？

**答**：

**事前预防**：
- 遥测队列 TTL 60s，自动清理过期数据
- 日志队列 lazy 模式，消息直接写磁盘（节省内存）
- 消费者并发：`concurrency = "1-3"`，根据消息量动态扩展线程

**事后处理**：
- 扩容消费者实例
- 如果 MySQL 写入是瓶颈 → 改为批量写入（攒 100 条或 5s）
- 临时增加消费者并发数

---

## 十二、Resilience4j 熔断器（新增亮点）

### Q31：Resilience4j 是什么？项目中怎么用的？

**答**：Resilience4j 是 Java 的**弹性防护库**，提供熔断器、重试、限流、隔离舱等模块。

我在项目中用它保护 LLM API 调用：

```java
// 熔断器配置
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .failureRateThreshold(50)          // 失败率 50% → 熔断
    .slowCallDurationThreshold(10s)    // 响应 > 10s = 慢调用
    .waitDurationInOpenState(30s)      // 熔断后 30s 进入半开
    .permittedNumberOfCallsInHalfOpenState(3) // 半开状态允许 3 次探测
    .slidingWindowSize(10)             // 10 次请求的滑动窗口
    .build();

// 使用方式：装饰 LLM 调用
String result = circuitBreaker.executeWithProtection(
    () -> agent.chat(message),         // 正常调用
    () -> "AI 服务暂时不可达（已熔断）" // fallback
);
```

**熔断器状态机**：
```
CLOSED (正常) → 失败率达阈值 → OPEN (快速失败，等 30s)
OPEN → 30s 后 → HALF_OPEN (允许 3 次探测)
HALF_OPEN → 成功 → CLOSED | 失败 → OPEN
```

**为什么需要熔断器？**
- DeepSeek API 可能限流/欠费/故障
- 没有熔断器：每个请求等 60s 超时 → 线程池耗尽 → 整个系统不可用（**雪崩**）
- 有熔断器：检测到 50% 失败 → 快速返回 fallback → 保护系统资源

---

### Q32：熔断、降级、限流怎么区分？

**答**：

| 概念 | 含义 | 项目中实现 |
|------|------|-----------|
| **熔断 (Circuit Breaker)** | 检测到下游故障后快速失败，保护调用方 | Resilience4j CircuitBreaker 保护 LLM 调用 |
| **降级 (Fallback)** | 主逻辑失败后返回备选结果 | 熔断后返回"AI 暂不可达，请用手动快通道" |
| **限流 (Rate Limiting)** | 限制请求频率，保护被调用方 | Redis ZSET 滑动窗口限制 /api/agent/chat 60s 20 次 |

三者配合关系：**先限流（保护 DeepSeek API）→ 仍失败则熔断（保护自己）→ 熔断时走降级（保护用户体验）**

---

## 十三、SSE 与实时推送（新增深度题）

### Q33：为什么选 WebSocket 而不用 SSE？

**答**：我的项目中**同时用了 WebSocket 和 SSE**，但场景不同：

| | WebSocket | SSE |
|------|------|------|
| 方向 | 双向 | 单向（服务端→客户端） |
| 协议 | 独立 ws:// 协议 | 标准 HTTP |
| 重连 | 需手动实现 | 浏览器原生自动重连 |
| 代理兼容 | Nginx 需特殊配置 | HTTP 代理直接透传 |
| 二进制支持 | 支持 | 仅文本 |
| 实现复杂度 | 较高 | 低 |

**项目中**：
- **WebSocket**：遥测推送（4Hz 高频、双向可能用——未来可能需要前端发指令）
- **SSE**：Agent 聊天流式响应（单向推送、自动重连、实现更简单）

**面试话术**：
> 我选择了 WebSocket 而不是 SSE 做遥测推送，因为飞控场景下前端未来可能需要通过同一连接发送即时指令（如"紧急悬停"），需要双向通信。但如果只是单向推送遥测，SSE 更简单、更省资源。

---

### Q34：WebSocket 长连接怎么管理？断线了怎么办？

**答**：

**连接管理**（TelemetryWebSocketHandler）：
- `CopyOnWriteArrayList<WebSocketSession>` 维护所有在线连接
- 连接建立：`afterConnectionEstablished` → 加入列表 → 推送欢迎消息
- 连接关闭：`afterConnectionClosed` → 从列表移除 → 清理资源
- 广播消息：遍历列表 → `session.sendMessage()` → 失败则移除该 session

**心跳机制**：
- TelemetryPusher 每 2s 定时推送（天然心跳）
- 前端 `useWebSocket.js` 监听 `onclose` → 5s 后自动重连
- 指数退避重连：5s → 10s → 20s → 30s max

**前端重连逻辑**：
```javascript
ws.onclose = () => {
  if (reconnectAttempts < maxReconnect) {
    setTimeout(connect, Math.min(5000 * Math.pow(2, reconnectAttempts), 30000));
    reconnectAttempts++;
  }
};
```

---

## 十四、幂等性设计（新增亮点）

### Q35：怎么防止用户或 Agent 重复发送同一条飞控指令？

**答**：用 Redis SETNX 实现**指令幂等守卫**。

**场景**：
- 用户网络超时，点了两次"起飞"
- LLM 幻觉，两次调用 `takeoff()` 工具
- 网络重传导致 Bridge 收到重复 HTTP 请求

**实现** (`CommandIdempotencyGuard`)：
```java
public boolean allow(String commandType, String contentHash) {
    String key = "idempotency:" + commandType + ":" + contentHash;
    // SETNX: 仅当 key 不存在时设置，原子操作
    Boolean success = redis.setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    if (!success) {
        log.warn("幂等拒绝: {} 已发送（10s内）", commandType);
        return false; // 重复调用
    }
    return true; // 首次调用
}
```

**为什么 TTL = 10s？**
- 飞控指令执行很快（起飞 ~2s、降落 ~3s）
- 10s 覆盖正常执行 + 网络延迟
- 过期后允许重发（如 RTL 失败后手动再次 RTL）

**为什么用 Redis 而不是 synchronized？**
- Redis 是分布式锁，即使应用重启也保持状态
- synchronized 只在单 JVM 有效，重启丢失
- Redis Key 自动过期，不占内存

---

## 十五、可观测性（新增亮点）

### Q36：项目怎么监控和排查问题？

**答**：三件套：Spring Boot Actuator + Prometheus + Grafana。

**1. 暴露指标**：
- Actuator 端点：`/actuator/prometheus`，Prometheus 每 15s 刮取
- 自定义指标（Micrometer）：
  - `uav_agent_calls_total`：Agent 调用次数
  - `uav_agent_call_duration`：Agent 调用耗时（P50/P95/P99）
  - `uav_drone_commands_total`：飞控指令计数
  - `uav_anomaly_detected_total`：异常检测次数
  - `uav_circuit_breaker_transitions`：熔断器状态变更

**2. Grafana 仪表盘**（预置 8 个面板）：
- LLM Agent QPS + P95 延迟
- 飞控指令分布饼图
- 遥测推送频率
- 异常检测触发计数 + 告警阈值
- JVM 内存使用率
- HTTP QPS

**3. 日志检索**（设计）：
- AOP 切面自动记录操作日志 → RabbitMQ → ElasticSearch
- ES 按时间 + 操作类型 + 用户创建索引
- `/api/logs/search?q=xxx` 全文检索

**告警规则**（设计中）：
- 熔断器 OPEN > 5min → 触发告警
- Agent 调用成功率 < 80% → 触发告警
- 异常检测触发 > 10 次/h → 触发告警

---

### Q37：SpringDoc OpenAPI 你怎么用的？

**答**：在所有 Controller 上添加了 Swagger 3.0 注解：
- `@Tag` 标注 Controller 分组（AI Agent、无人机快通道、认证、对象存储、任务与日志）
- `@Operation` 标注每个接口的用途和描述
- 自定义 OpenAPI Bean 配置 API 文档标题、版本、联系方式

**访问**：
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

**用途**：前端团队可以直接在 Swagger UI 上测试接口、生成 Postman 集合，不需要翻代码看接口文档。

---

## 十六、技术决策速记表（面试快速回顾）

| 决策 | 选项 A | 选项 B | 选择 | 理由 |
|------|--------|--------|------|------|
| LLM 框架 | LangChain4j | Spring AI | LangChain4j | Function Calling 更成熟, 社区更大 |
| 实时推送 | WebSocket | SSE | WebSocket | 未来需双向通信（前端发紧急指令） |
| 消息队列 | RabbitMQ | Kafka | RabbitMQ | 项目规模小, 灵活路由, 运维简单 |
| 熔断器 | Resilience4j | Hystrix | Resilience4j | Hystrix 已停维护 |
| 异步处理 | RabbitMQ | @Async | RabbitMQ | 持久化, 死信, 手动ACK, 不丢消息 |
| 缓存 | Redis | Caffeine | Redis | 分布式, 持久化, 支持集群 |
| 对象存储 | MinIO | OSS/S3 | MinIO | 本地开发不联网, S3 兼容, Docker 部署 |
| 认证 | JWT | Session | JWT | 无状态, 适合分布式, 不需要服务端存储 |
| 密码加密 | BCrypt | MD5/SHA | BCrypt | 盐值 + 可调成本因子, 抗暴力破解 |
| API 文档 | SpringDoc | 手写 | SpringDoc | 自动生成, Swagger UI 交互式测试 |

---

## XVII: 计算机视觉感知（v3.0 Perception Engine，新增亮点）

### Q38：项目中如何实现了无人机的目标检测和避障？

**答**：在 v3.0 中新增了 **Perception Engine**，集成四大 SOTA 算法：

1. **YOLOv11** (Ultralytics, 2025) — 实时目标检测，nano 模型 2.6M 参数，CPU 可达 30FPS
2. **ByteTrack v2** (ECCV 2024) — 多目标跟踪，两阶段关联（高置信度→低置信度），恢复被遮挡目标
3. **Depth Anything V2** (NeurIPS 2024) — 单目深度估计，基于 DINOv2 编码器，**不需要双目摄像头或激光雷达**
4. **EGO-Planner 启发式避障** — 深度分区 + 势场法，<100ms 实时生成避障速度指令

架构：摄像头帧 → YOLOv11 检测 → ByteTrack 跟踪 → Depth Anything 深度估计 → 势场法避障规划 → MAVSDK 速度指令 → PX4 执行。

---
		
### Q39：为什么选 YOLOv11 而不是其他检测模型？

**答**：三个理由：
1. **实时性**：nano 版本仅 2.6M 参数，CPU 推理 30FPS+，适合无人机边缘计算
2. **生态成熟**：Ultralytics 提供完整的训练/导出/部署流水线，Python API 简洁
3. **精度足够**：COCO mAP 39.5 (nano)，无人机场景主要检测人/车/建筑，nano 完全够用

备选方案对比：
- **RT-DETR v2**：Transformer-based，小目标更优但推理慢 2-3x
- **D-FINE**：精度更高但部署复杂
- 选型原则：**无人机上实时性 > 精度**，YOLOv11 nano 是最佳折中

---

### Q40：ByteTrack 的核心创新是什么？为什么不直接用 SORT 或 DeepSORT？

**答**：ByteTrack 的核心创新是 **"保留低置信度检测框"**：

传统 SORT/DeepSORT 的做法：只保留高置信度（>0.5）的检测框，丢弃低置信度的。  
问题：被遮挡/模糊的目标正好是低置信度的，丢弃它们 → ID Switch → 跟踪断裂。

ByteTrack 的做法（两阶段关联）：
1. **第一次关联**：高置信度检测 ↔ 已有轨迹（标准匈牙利匹配）
2. **第二次关联**：低置信度检测 ↔ 未匹配的轨迹（更宽松的 IoU 阈值）
3. 效果：被短暂遮挡的目标重新出现时，低置信度框能恢复跟踪 → **ID Switch 减少 50%+**

为什么不选 DeepSORT：DeepSORT 依赖额外的 ReID 特征提取网络，**增加 30-50ms 推理开销**，无人机场景对实时性要求高，ByteTrack 只用 IoU 匹配，更快。

---

### Q41：单目深度估计（Depth Anything V2）相比双目的优势是什么？

**答**：核心区别：
- **双目深度**：需要两个摄像头 + 已知基线距离 + 立体匹配算法（SGBM），硬件成本高、标定复杂
- **单目深度**：一个普通 RGB 摄像头，通过深度学习直接回归深度图

Depth Anything V2 的三大优势：
1. **硬件简单**：消费级摄像头即可，无需双目标定
2. **泛化极强**：在合成+真实混合数据上训练，对任意场景都有效
3. **三档模型**：Small (24M) / Base (97M) / Large (335M)，可按算力选择

局限性（面试时诚实说明）：
- 输出的是**相对深度**（0=远 1=近），不是绝对深度（多少米）
- 尺度模糊 (scale ambiguity)：无法直接知道"前方障碍物 3 米"
- 解决方案：对于避障来说，相对深度已经足够（知道哪边更近/更远即可选方向）

---

### Q42：避障算法具体怎么实现的？从深度图到速度指令的全过程？

**答**：分为 4 步，使用**势场法 (Potential Field)**：

**Step 1 — 扇区划分**：将深度图分为 5 个垂直扇区（Left / Center-Left / Center / Center-Right / Right）

**Step 2 — 障碍评估**：计算每个扇区中"近像素"的比例（深度值 > 0.6 的像素占比）
- 中心扇区近像素 > 15% → 前方有障碍物
- 中心扇区最小深度 > 0.8 → 危险区域（<2m），紧急制动

**Step 3 — 方向选择**：按扇区平均深度排序，选择最"空"的方向

**Step 4 — 速度合成**：
- 无障碍：直飞 (vx=3.0 m/s, vy=0)
- 有障碍：转向最空扇区 (vx=1.8 m/s, vy=±2.0 m/s)
- 危险区：后退 + 爬升 (vx=-1.5, vz=-0.5)

时间复杂度：O(W×H) 深度图遍历，640×480 约 1-2ms

---

### Q43：LLM Agent 怎么和视觉感知联动？能给一个具体场景吗？

**答**：通过 `@Tool` 注解，LLM 可以调用视觉感知能力。场景示例：

**用户输入**："搜索前方区域，如果有车辆就跟着它，同时注意避开障碍物"

**Agent 执行流程**：
1. LLM 理解：需要检测 + 跟踪 + 避障
2. 调用 `detectObjects(image)` → "检测到 1 个物体: car (置信度 0.92)"
3. 调用 `enableObstacleAvoidance()` → "避障已启用，安全距离 5m"
4. 循环调用 `followTarget(image)` → 每帧更新跟随速度指令
5. 避障子系统独立运行，如果突然出现障碍物 → 自动 override 跟随速度

**关键设计**：避障是**硬实时子系统**，不经过 LLM 推理（延迟太高），而是由 Python 端直接执行。LLM 负责**高层决策**（跟踪哪个目标），避障负责**底层安全**（不撞东西）。

---

### Q44：视觉感知模块的性能如何？有什么优化策略？

**答**：

**当前性能（CPU, i7-13700H）**：
- YOLOv11 nano 检测: ~30ms/帧 (33 FPS)
- Depth Anything V2 Small: ~500ms/帧 (2 FPS)
- ByteTrack: ~1ms/帧 (关联+卡尔曼滤波)
- 端到端 Pipeline: 检测每 2 帧 + 深度每 5 帧 → 综合 ~15 FPS

**优化策略**（面试时展示性能意识）：
1. **帧跳过 (Frame Skip)**：检测每 2 帧、深度每 5 帧，跟踪逐帧（Kalman 预测补帧）
2. **GPU 加速 (CUDA)**：深度估计可获 10-50x 加速 → 30 FPS+
3. **模型量化 (INT8/FP16)**：模型体积减半，推理速度 2-3x
4. **TensorRT 部署**：NVIDIA Jetson 边缘设备专用优化
5. **分辨率缩放**：深度估计 518→256，精度略降但速度 4x

---

## 面试准备建议

1. **先背项目介绍**（Q1—Q2），面试官 90% 会先让你介绍项目
2. **重点准备 Agent 部分**（Q3—Q6），这是你和其他候选人最大的差异化竞争力
3. **RabbitMQ 事件驱动**（Q27—Q30）是新增亮点，后台岗必问消息队列
4. **Resilience4j 熔断器**（Q31—Q32）展示你的生产环境意识和弹性设计能力
5. **视觉感知**（Q38—Q44）是 v3.0 最新亮点，展示 CV+Agent 融合能力，面 AI 相关岗必问
6. **Redis 三层应用**（Q16—Q18）是亮点，后台岗必问缓存和数据结构
7. **MinIO 对象存储**（Q19—Q21）展示你的存储选型能力和 S3 协议理解
8. **SSE vs WebSocket**（Q33—Q34）展示你对推送技术的深入理解
9. **幂等性设计**（Q35）展示你的分布式系统安全意识
10. **可观测性**（Q36）展示你的运维思维和监控能力
11. **LangChain4j 深入**（Q22—Q23）证明你不是只调 API，理解框架原理
12. **JWT + Security**（Q10—Q12）是后台岗位基本功，必须对答如流
13. **困难与解决方案**（原 Q26—Q27）展示问题解决能力，准备 2-3 个具体案例
14. **基础题**（Q30—Q33）如果答不上说明基础不扎实，建议备熟
15. **感知算法**（Q38—Q44）准备 1-2 个最熟悉的算法深入讲，不需要全部
