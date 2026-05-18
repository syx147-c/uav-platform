-- UAV 飞行控制平台 数据库初始化脚本
-- 使用 Navicat 连接 localhost:3307, 用户 uav, 密码 uav123, 数据库 uav_platform

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    role VARCHAR(32) DEFAULT 'USER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 默认管理员账号 admin / admin123
INSERT IGNORE INTO sys_user (username, password, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', 'ADMIN');

-- 飞行任务表
CREATE TABLE IF NOT EXISTS uav_mission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(256),
    description TEXT COMMENT '原始自然语言描述',
    task_plan JSON COMMENT 'LLM生成的结构化任务计划',
    state VARCHAR(32) DEFAULT 'CREATED' COMMENT 'CREATED/EXECUTING/PAUSED/COMPLETED/FAILED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 飞行日志表
CREATE TABLE IF NOT EXISTS uav_flight_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mission_id BIGINT,
    event_type VARCHAR(64) COMMENT 'ARM/TAKEOFF/WAYPOINT/HOVER/RTL/LAND/HOLD',
    event_data JSON COMMENT '事件详情',
    source VARCHAR(32) DEFAULT 'AGENT' COMMENT 'AGENT/MANUAL/EMERGENCY',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 遥测快照表
CREATE TABLE IF NOT EXISTS uav_telemetry_snapshot (
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
