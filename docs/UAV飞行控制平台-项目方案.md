# UAV 飞行控制平台 — 项目方案

## 一、项目概述

打造一个基于大语言模型（LLM）的无人机飞行控制平台。用户通过自然语言向智能体下达飞行任务，智能体自动拆解意图、编排任务、下发指令，经由地面站控制仿真环境中的无人机，并将飞行状态实时回传至 Web 管理面板。

---

## 二、核心目标

1. **自然语言操控无人机** — 用户用中文/英文描述飞行任务，系统自动转换为飞控指令
2. **实时 3D 可视化** — 前端集成三维仿真引擎，展示无人机姿态、轨迹与环境
3. **任务编排与状态管理** — 支持复杂多步骤飞行任务，具备异常中断与恢复能力
4. **安全双通道控制** — 紧急指令绕过 LLM 与消息队列直连飞控，保障安全响应
5. **感知—决策闭环** — 无人机根据实时遥测与图像反馈动态调整飞行策略

---

## 三、总体架构

### 3.1 架构图

+------------------------------------------------------------+
|                     Windows 宿主机                          |
|                                                            |
|  +-----------------+    WebSocket     +------------------+ |
|  |  Vue3/React     |<--------------->|  Spring Boot 3   | |
|  |  Web 管理面板   |    + REST API   |  (后端主服务)    | |
|  |                 |                |                  | |
|  |  - 自然语言输入 |                |  - LangChain4j   | |
|  |  - 3D 地图/遥测 |                |  - Spring State  | |
|  |  - 紧急控制面板 |                |    Machine       | |
|  |  - 任务编排界面 |                |  - WebSocket     | |
|  +-----------------+                +--------+---------+ |
|                                               |           |
|                          +--------------------+-----+     |
|                          |     Docker Compose 环境  |     |
|                          |                          |     |
|  +--------+  +--------+  |  +--------+  +--------+  |     |
|  | MySQL  |  | Redis  |  |  | MinIO  |  |RabbitMQ|  |     |
|  |(业务库)|  |(缓存)  |  |  |(图像)  |  |(消息)  |  |     |
|  +--------+  +--------+  |  +--------+  +---+----+  |     |
|                          +--------------------+-----+     |
|                                               |           |
|  +-------------------------------------------+-----+     |
|  |                  WSL2 环境                  |     |     |
|  |                                             |     |     |
|  |  +------------------+    +------------------v---+ |     |
|  |  |  Python MAVSDK   |<---|  地面站服务          | |     |
|  |  |  桥接 (FastAPI)  |    |  (MQ消费+遥测上报)   | |     |
|  |  +--------+---------+    +---------------------+ |     |
|  |           |                                      |     |
|  |  +--------v---------+                            |     |
|  |  |   PX4 SITL       |                            |     |
|  |  |   (飞控仿真)     |                            |     |
|  |  +--------+---------+                            |     |
|  |           |                                      |     |
|  |  +--------v---------+                            |     |
|  |  |   Gazebo   |                            |     |
|  |  |   (3D 仿真渲染)  |                            |     |
|  |  +------------------+                            |     |
|  +--------------------------------------------------+     |
+------------------------------------------------------------+

### 3.2 双通道指令体系

| 通道 | 路径 | 延迟 | 适用场景 |
|------|------|------|----------|
| **慢通道**（智能通道） | Web -> LLM Agent -> RabbitMQ -> 地面站 -> PX4 | 3~6s | 高层任务规划（"飞到坐标 X 并悬停"） |
| **快通道**（安全通道） | Web -> WebSocket -> 地面站直连 -> PX4 | <200ms | 紧急指令（"立即悬停""紧急返航""切断动力"） |

---

## 四、技术选型

### 4.1 技术栈总览

| 层级 | 技术 | 说明 |
|------|------|------|
| **前端框架** | Vue 3 + Vite（或 React + Next.js） | SPA 管理面板 |
| **前端 3D** | Cesium.js / Three.js + 仿真画面推流 | 飞行轨迹与姿态可视化 |
| **后端框架** | Spring Boot 3 + JDK 17+ | 主服务 |
| **ORM** | MyBatis-Plus | 数据库访问 |
| **消息队列** | RabbitMQ | 指令异步下发、遥测回传解耦 |
| **LLM 框架** | LangChain4j | 大模型接入与工具调用 |
| **状态机** | Spring State Machine | 飞行任务状态编排 |
| **实时通信** | Spring WebSocket (STOMP) | 前后端双向实时推送 |
| **安全框架** | Spring Security + JWT（可选） | 认证授权 |
| **缓存** | Redis | Agent 会话状态、实时遥测缓存 |
| **数据库** | MySQL 8 | 用户、任务、日志等业务数据 |
| **对象存储** | MinIO | 仿真截图、日志文件等非结构化数据 |
| **MAVLink 接入** | Python MAVSDK + FastAPI（桥接） | WSL2 内与 PX4 通信 |
| **飞控仿真** | PX4 SITL | 软件在环飞控 |
| **3D 仿真** | Gazebo Harmonic | 三维场景渲染 |
| **监控** | Prometheus + Grafana | 指标采集与可视化 |
| **日志** | Loki + Promtail（或 ELK） | 日志收集与检索 |
| **容器编排** | Docker Compose | 后端服务一键部署 |

### 4.2 关键选型说明

#### 仿真引擎：为何放弃 AirSim？

AirSim 已于 2023 年被 Microsoft 归档停止维护，最高仅支持 UE 4.27，社区不再修 bug。替代方案：

| 方案 | 推荐度 | 适用场景 |
|------|--------|----------|
| **UE5 + PX4 SITL** | ★★★☆☆ | 需要顶级视觉效果，有 UE 开发能力 |
| **Gazebo Harmonic + PX4 SITL** | ★★★★★ | 侧重飞控算法验证，PX4 官方首选，社区最活跃 |
| **jMAVSim** | ★★★☆☆ | 快速原型验证，无 3D 视觉需求 |

> **本项目推荐 Gazebo Harmonic + PX4 SITL**，PX4 官方首选仿真器，社区最活跃，集成路径最成熟。

#### LLM 编排：为何不用 langgraph4j？

langgraph4j 是社区个人项目，非 LangChain 官方出品，成熟度低。替代策略：

- **LangChain4j** 负责 LLM 接入、Tool 定义与调用
- **Spring State Machine** 负责飞行任务状态管理
- LLM 只做"理解意图 + 生成指令"，不直接管理状态转移

#### MAVLink 接入：为何引入 Python 桥接？

- MAVSDK-Python 是官方维护最完善的 SDK，Java 版功能滞后
- 通过在 WSL2 内部署 FastAPI 服务提供 HTTP/gRPC 接口，Java 后端通过 HTTP 调用
- 桥接层轻量、可控、易于调试

---

## 五、模块划分

### 5.1 后端模块

```
uav-platform/
├── uav-common/            # 公共模块：DTO、枚举、工具类
├── uav-security/          # 安全模块：认证、授权、JWT
├── uav-agent/             # LLM Agent 模块
│   ├── llm/               #   LangChain4j 配置与大模型接入
│   ├── tools/             #   Tool 定义（起飞、降落、航点、返航等）
│   └── state/             #   Spring State Machine 状态定义与转移
├── uav-groundstation/     # 地面站模块
│   ├── mavlink/           #   与 Python MAVSDK 桥接的 HTTP 客户端
│   ├── telemetry/         #   遥测数据采集与上报
│   └── command/           #   指令消费者（RabbitMQ Listener）
├── uav-push/              # 实时推送模块
│   └── websocket/         #   STOMP WebSocket 配置与消息路由
├── uav-mission/           # 任务管理模块
│   ├── mission/           #   任务 CRUD
│   └── log/               #   飞行日志
└── uav-storage/           # 存储模块
    ├── minio/             #   MinIO 文件上传/下载
    └── redis/             #   Redis 缓存服务
```

### 5.2 前端模块

```
uav-web/
├── src/
│   ├── views/
│   │   ├── Dashboard.vue         # 驾驶舱主页（3D 视图 + 遥测仪表盘）
│   │   ├── MissionPlanner.vue    # 任务编排界面
│   │   ├── FlightLog.vue         # 飞行日志回放
│   │   └── Settings.vue          # 系统设置
│   ├── components/
│   │   ├── TelemetryPanel.vue    # 实时遥测面板
│   │   ├── EmergencyControl.vue  # 紧急控制按钮组
│   │   ├── CesiumViewer.vue      # 3D 地图组件
│   │   └── ChatInput.vue         # 自然语言输入框
│   ├── composables/
│   │   ├── useWebSocket.ts       # WebSocket 连接管理
│   │   └── useTelemetry.ts       # 遥测数据订阅
│   └── stores/
│       ├── flight.ts             # 飞行状态 Pinia Store
│       └── mission.ts            # 任务状态 Pinia Store
```

---

## 六、核心业务流程

### 6.1 自然语言飞行任务流程

```
用户输入："飞到坐标(30.5,120.3)上空50米，悬停30秒后返航"

  1. Web 前端 -> HTTP POST /api/agent/chat
  2. LangChain4j Agent 处理
     a) LLM 理解意图 -> "起飞 -> 航点飞行 -> 悬停 -> 返航"
     b) 调用 Tools: planMission(waypoint, hover, rtl)
     c) 返回结构化 TaskPlan
  3. Spring State Machine 接管
     IDLE -> ARMING -> TAKEOFF -> WAYPOINT -> HOVER -> RTL
     逐状态执行，每步发送对应 MAVLink 指令
  4. 指令发往 RabbitMQ
     exchange: uav.command
     routing_key: uav.command.mission
  5. 地面站服务消费指令
     -> HTTP 调用 Python MAVSDK Bridge
     -> Bridge 通过 MAVSDK 发送 MAVLink 消息给 PX4
  6. PX4 执行飞行动作，Gazebo 渲染
     遥测数据 -> MAVSDK Bridge -> 地面站 ->
     RabbitMQ (uav.telemetry) -> Spring Boot ->
     Redis (实时缓存) + WebSocket -> 前端实时更新
```

### 6.2 紧急指令流程（快通道）

```
用户点击"紧急悬停"

  Web 前端 -> WebSocket /app/emergency
    { "command": "HOLD", "priority": "CRITICAL" }
  地面站服务直接处理（绕过 LLM、绕过 MQ）
  -> 立即调用 Python MAVSDK Bridge -> PX4 HOLD 指令
  -> 响应延迟 < 200ms
```

### 6.3 感知—决策闭环

```
循环执行（每个周期 ~2s）：

  1. 读取 Redis 中最新的遥测数据
     - GPS 坐标、高度、速度、姿态、电量
     - 最新仿真截图（MinIO URL）

  2. LLM Agent 分析
     - 是否偏离航线？-> 生成修正航点
     - 电量是否过低？-> 触发自动返航
     - 前方是否有障碍物？-> 调整高度绕行

  3. 如有干预必要：
     - 暂停当前任务
     - 插入紧急航点
     - 通知前端（WebSocket 推送）

  4. 如无异常：继续监控
```

---

## 七、飞行任务状态机设计

```
                          +---------+
                          |  IDLE   |
                          +----+----+
                               | 收到起飞指令
                               v
                          +---------+
                          | ARMING  |
                          +----+----+
                               | 解锁完成
                               v
                          +---------+
                   +------| TAKEOFF |------+
                   |      +----+----+      |
                   |           |           | 异常/紧急
                   |           v           |
                   |      +---------+      |
                   |      |WAYPOINT |      |
                   |      +----+----+      |
                   |           |           |
                   |           v           |
                   |      +---------+      |
                   |      |  HOVER  |      |
                   |      +----+----+      |
                   |           |           |
                   |           v           v
                   |      +---------+  +---------+
                   |      |   RTL   |  |  HOLD   |
                   |      +----+----+  +----+----+
                   |           |           |
                   |           v           | 恢复
                   |      +---------+      |
                   +----->|  LAND   |<-----+
                          +----+----+
                               | 降落完成
                               v
                          +---------+
                          |  IDLE   |
                          +---------+
```

---

## 八、Agent 工具定义

LLM Agent 通过 LangChain4j 的 @Tool 注解暴露以下飞行控制原语：

| Tool 名称 | 功能 | 关键参数 |
|-----------|------|----------|
| armDrone | 解锁无人机 | — |
| takeoff | 起飞至指定高度 | altitude |
| gotoWaypoint | 飞至指定航点 | lat, lon, alt |
| hover | 悬停指定时长 | duration |
| returnToLaunch | 返航至起飞点 | — |
| land | 降落 | — |
| hold | 紧急悬停（立即） | — |
| setFlightMode | 切换飞行模式 | mode |
| queryTelemetry | 查询当前遥测 | fields |
| captureImage | 拍摄仿真截图 | — |

---

## 九、WebSocket 通信协议

### 9.1 推送通道（服务端 -> 前端）

| 目标地址 | 消息类型 | 频率 |
|----------|----------|------|
| /topic/telemetry | 遥测数据（GPS、姿态、速度、电量） | 10Hz |
| /topic/alert | 系统告警（低电量、链路断开等） | 按事件 |
| /topic/mission-state | 任务状态变更 | 按事件 |
| /topic/image | 仿真截图 URL | 1Hz |

### 9.2 发送通道（前端 -> 服务端）

| 目标地址 | 用途 |
|----------|------|
| /app/chat | 发送自然语言指令 |
| /app/emergency | 发送紧急控制指令 |
| /app/mission | 创建/修改飞行任务 |

---

## 十、数据库核心表设计

```sql
-- 用户表（可选，如需多用户）
CREATE TABLE uav_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) DEFAULT 'USER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 飞行任务表
CREATE TABLE uav_mission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    title VARCHAR(256),
    description TEXT,
    task_plan JSON,
    state VARCHAR(32) DEFAULT 'CREATED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 飞行日志表
CREATE TABLE uav_flight_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mission_id BIGINT,
    event_type VARCHAR(64),
    event_data JSON,
    source VARCHAR(32) DEFAULT 'AGENT',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 遥测快照表（近实时数据存 Redis，历史数据定期归档到 MySQL）
CREATE TABLE uav_telemetry_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mission_id BIGINT,
    latitude DOUBLE,
    longitude DOUBLE,
    altitude DOUBLE,
    velocity_x DOUBLE,
    velocity_y DOUBLE,
    velocity_z DOUBLE,
    roll DOUBLE,
    pitch DOUBLE,
    yaw DOUBLE,
    battery_voltage DOUBLE,
    gps_fix INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mission_time (mission_id, created_at)
);
```

---

## 十一、监控与告警

| 监控维度 | 工具 | 关键指标 |
|----------|------|----------|
| 服务健康 | Prometheus + Actuator | JVM 内存、GC、线程数、HTTP 延迟 |
| 消息队列 | RabbitMQ Exporter | 队列深度、消费速率、死信数量 |
| MAVLink 链路 | 自建指标 | 心跳丢失次数、MAVLink 消息延迟 |
| 飞行安全 | 自建指标 | 电量百分比、GPS 卫星数、当前高度 |
| 告警通知 | Grafana Alerting | 低电量(<20%)、链路断开、MQ 积压>100 |

---

## 十二、部署方案

### 12.1 环境分布

```
+-------------------------------------------------------+
|                  Docker Compose                         |
|                                                         |
|  +----------+  +----------+  +----------+             |
|  | spring-  |  |  MySQL   |  |  Redis   |             |
|  | boot     |  |  (3306)  |  |  (6379)  |             |
|  | (8080)   |  +----------+  +----------+             |
|  +----------+                                          |
|  +----------+  +----------+  +----------+             |
|  | RabbitMQ |  |  MinIO   |  | Grafana  |             |
|  | (5672)   |  | (9000)   |  | (3000)   |             |
|  +----------+  +----------+  +----------+             |
|                                                         |
+-------------------------------------------------------+

+-------------------------------------------------------+
|                  WSL2 (非 Docker)                       |
|                                                         |
|  +----------+  +----------+  +----------+             |
|  | FastAPI  |  | PX4 SITL |  |  Gazebo  |             |
|  | MAVSDK   |  |          |  | Harmonic |             |
|  | Bridge   |  |          |  |          |             |
|  | (8000)   |  |          |  |          |             |
|  +----------+  +----------+  +----------+             |
|                                                         |
+-------------------------------------------------------+
```

### 12.2 WSL2 网络要点

- 使用 Windows 11 的 WSL2 镜像网络模式，或启动脚本自动配置端口转发
- FastAPI Bridge 监听 0.0.0.0:8000，Java 后端通过 localhost:8000 访问
- PX4 SITL 默认使用 UDP 14540（Offboard 控制端口）

---

## 十三、分阶段实施路线

### Phase 1 — 跑通仿真链路（2~3 周）

**目标：** 能在仿真环境中手动控制无人机起降

- [ ] 搭建 WSL2 环境，安装 PX4 SITL
- [ ] 部署 Gazebo Harmonic 仿真环境
- [ ] 编写 Python MAVSDK Bridge 基础服务
- [ ] 实现基本 MAVLink 指令：解锁、起飞、降落
- [ ] 验证仿真画面渲染正常

### Phase 2 — 后端核心 + 前端面板（3~4 周）

**目标：** Web 面板能显示实时遥测并下发基础指令

- [ ] Spring Boot 项目初始化，模块划分
- [ ] 数据库表设计与初始化
- [ ] 地面站服务：MQ 消费、指令转发、遥测采集
- [ ] RabbitMQ 配置与集成
- [ ] WebSocket 实时遥测推送
- [ ] Vue3 前端框架搭建
- [ ] 遥测仪表盘、Cesium.js 3D 地图、紧急控制面板
- [ ] Redis 会话与遥测缓存

### Phase 3 — LLM 智能体集成（3~4 周）

**目标：** 用户通过自然语言控制无人机

- [ ] LangChain4j 接入大模型（GPT-4 / Claude / 国产模型）
- [ ] 飞行控制 Tool 定义与注册
- [ ] Spring State Machine 任务状态编排
- [ ] 自然语言 -> 结构化 TaskPlan 转换
- [ ] 前端 ChatInput 组件对接
- [ ] 飞行日志存储与回放

### Phase 4 — 闭环控制与生产加固（3~4 周）

**目标：** 系统完整、安全、可观测

- [ ] 感知—决策闭环（ReAct 循环）
- [ ] 紧急快通道优化与压测
- [ ] Prometheus + Grafana 监控面板
- [ ] 日志收集（Loki）
- [ ] Docker Compose 一键编排
- [ ] 安全认证（Spring Security + JWT）
- [ ] 异常场景测试（链路断开、电量耗尽、GPS 丢失）
- [ ] 性能测试与优化

**总工期估算：11~15 周**

---

## 十四、风险与对策

| 风险 | 影响 | 概率 | 对策 |
|------|------|------|------|
| AirSim 停止维护 | 高 | 100% | 已替换为 Gazebo + PX4 SITL |
| langgraph4j 不稳定 | 中 | 高 | 已替换为 Spring State Machine |
| Java MAVSDK 功能不足 | 中 | 中 | 引入 Python MAVSDK 桥接层 |
| LLM 推理延迟过高 | 中 | 中 | 紧急指令走快通道；考虑使用流式输出 |
| WSL2 网络不通 | 中 | 低 | 编写自动化端口转发脚本 |
| PX4 与 Gazebo 版本兼容 | 中 | 低 | 锁定 Gazebo 版本与 PX4 兼容矩阵，持续关注 release note |
| LLM 生成危险指令 | 高 | 低 | 前端紧急按钮直连地面站；指令安全校验层 |
| 仿真与真机差异 | 中 | 高 | 架构预留真机切换接口；仿真仅用于验证逻辑 |

---

## 十五、附录

### A. 环境依赖清单

| 软件 | 版本要求 | 用途 |
|------|----------|------|
| JDK | 17+ | Spring Boot 3 运行环境 |
| Python | 3.10+ | MAVSDK Bridge |
| WSL2 | Ubuntu 22.04 | PX4 + 仿真运行环境 |
| PX4-Autopilot | main 分支 | 飞控固件 |
| Gazebo Harmonic | latest | 3D 仿真渲染 |
| Docker | 24+ | 容器化部署 |
| MySQL | 8.0 | 业务数据存储 |
| Redis | 7.x | 缓存 |
| RabbitMQ | 3.12+ | 消息队列 |
| MinIO | latest | 对象存储 |

### B. 后续扩展方向

1. **多机编队** — 同时控制多架无人机协同执行任务
2. **真机对接** — 仿真验证通过后，MAVLink 接口直接对接真实飞控硬件
3. **语音输入** — 集成 ASR（语音识别），实现语音操控无人机
4. **AI 视觉** — 接入 YOLO/检测模型，实现目标识别与跟踪
5. **数字孪生** — 仿真环境与真实环境同步映射
