-- ============================================================================
-- MCP Router V3 - Optimized Database Schema v2.0
-- ============================================================================
-- 数据库: mcp_bridge
-- 字符集: utf8mb4
-- 排序规则: utf8mb4_unicode_ci
-- 引擎: InnoDB
-- 版本: 2.0
-- 创建日期: 2025-03-01
-- 
-- 优化要点:
-- 1. 修复分区表主键问题（主键必须包含分区键）
-- 2. 修改外键策略（避免级联删除导致数据丢失）
-- 3. 添加唯一索引支持 UPSERT 操作
-- 4. 优化索引设计（覆盖索引）
-- 5. 添加性能优化配置
-- ============================================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `mcp_bridge` 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

USE `mcp_bridge`;

-- 设置会话变量
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. 核心业务表
-- ============================================================================

-- 1.1 MCP服务器实例表（优化版）
-- ============================================================================
DROP TABLE IF EXISTS `mcp_servers`;
CREATE TABLE `mcp_servers` (
  `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
  `server_key` VARCHAR(100) NOT NULL COMMENT '服务器唯一标识 (name:ip:port)',
  `server_name` VARCHAR(100) NOT NULL COMMENT '服务器名称',
  `server_group` VARCHAR(50) NOT NULL DEFAULT 'mcp-server' COMMENT '服务组',
  `namespace_id` VARCHAR(50) NOT NULL DEFAULT 'public' COMMENT 'Nacos命名空间',
  
  -- 网络配置
  `host` VARCHAR(100) NOT NULL COMMENT '服务器主机地址',
  `port` INT NOT NULL COMMENT '服务器端口',
  `sse_endpoint` VARCHAR(200) NOT NULL DEFAULT '/sse' COMMENT 'SSE端点路径',
  `health_endpoint` VARCHAR(200) DEFAULT '/health' COMMENT '健康检查端点',
  
  -- 状态信息
  `healthy` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '健康状态',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `weight` DOUBLE NOT NULL DEFAULT 1.0 COMMENT '权重',
  `ephemeral` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否临时实例',
  `cluster_name` VARCHAR(50) DEFAULT 'DEFAULT' COMMENT '集群名称',
  
  -- 扩展信息
  `version` VARCHAR(20) DEFAULT '1.0.0' COMMENT '服务版本',
  `protocol` VARCHAR(20) DEFAULT 'mcp-sse' COMMENT '协议类型',
  `metadata` JSON COMMENT '服务器元数据',
  `tags` JSON COMMENT '标签',
  
  -- 统计信息
  `total_requests` BIGINT DEFAULT 0 COMMENT '总请求数',
  `total_errors` BIGINT DEFAULT 0 COMMENT '总错误数',
  `last_request_time` DATETIME NULL COMMENT '最后请求时间',
  `last_health_check` DATETIME NULL COMMENT '最后健康检查时间',
  
  -- 时间戳
  `registered_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` DATETIME NULL COMMENT '删除时间（软删除）',
  
  -- 主键和唯一约束
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_key` (`server_key`),
  
  -- 索引
  KEY `idx_server_name` (`server_name`),
  KEY `idx_server_group` (`server_group`),
  KEY `idx_namespace` (`namespace_id`),
  KEY `idx_healthy_enabled` (`healthy`, `enabled`, `deleted_at`),
  KEY `idx_host_port` (`host`, `port`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器实例表';

-- 1.2 健康检查记录表（优化版 - 修复分区主键问题）
-- ============================================================================
DROP TABLE IF EXISTS `health_check_records`;
CREATE TABLE `health_check_records` (
  `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
  `server_key` VARCHAR(100) NOT NULL COMMENT '服务器标识',
  
  -- 检查信息
  `check_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检查时间',
  `check_type` VARCHAR(20) NOT NULL DEFAULT 'MCP' COMMENT '检查类型: NACOS, MCP, COMBINED',
  `check_level` VARCHAR(20) DEFAULT 'LEVEL2' COMMENT '检查层级: LEVEL1(Nacos), LEVEL2(MCP)',
  
  -- 状态信息
  `status` VARCHAR(20) NOT NULL COMMENT '健康状态: HEALTHY, UNHEALTHY, UNKNOWN, DEGRADED',
  `health_score` DECIMAL(3,2) DEFAULT 1.00 COMMENT '健康度评分 0.00-1.00',
  
  -- 性能指标
  `response_time` INT COMMENT '响应时间(毫秒)',
  `connection_time` INT COMMENT '连接时间(毫秒)',
  `check_duration` INT COMMENT '检查总耗时(毫秒)',
  
  -- 计数器
  `consecutive_successes` INT DEFAULT 0 COMMENT '连续成功次数',
  `consecutive_failures` INT DEFAULT 0 COMMENT '连续失败次数',
  `total_checks` INT DEFAULT 0 COMMENT '累计检查次数',
  
  -- 详细信息
  `error_message` TEXT COMMENT '错误信息',
  `error_code` VARCHAR(50) COMMENT '错误代码',
  `details` JSON COMMENT '检查详情',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  -- 主键必须包含分区键
  PRIMARY KEY (`id`, `check_time`),
  
  -- 索引
  KEY `idx_server_key` (`server_key`),
  KEY `idx_check_time` (`check_time`),
  KEY `idx_status` (`status`),
  KEY `idx_check_type` (`check_type`),
  KEY `idx_server_time` (`server_key`, `check_time`)
  
  -- 注意：外键约束无法在分区表上使用
  -- CONSTRAINT `fk_health_server` FOREIGN KEY (`server_key`) 
  --     REFERENCES `mcp_servers`(`server_key`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康检查记录表'
-- 按月分区
PARTITION BY RANGE (TO_DAYS(check_time)) (
  PARTITION p_2025_01 VALUES LESS THAN (TO_DAYS('2025-02-01')),
  PARTITION p_2025_02 VALUES LESS THAN (TO_DAYS('2025-03-01')),
  PARTITION p_2025_03 VALUES LESS THAN (TO_DAYS('2025-04-01')),
  PARTITION p_2025_04 VALUES LESS THAN (TO_DAYS('2025-05-01')),
  PARTITION p_2025_05 VALUES LESS THAN (TO_DAYS('2025-06-01')),
  PARTITION p_2025_06 VALUES LESS THAN (TO_DAYS('2025-07-01')),
  PARTITION p_2025_07 VALUES LESS THAN (TO_DAYS('2025-08-01')),
  PARTITION p_2025_08 VALUES LESS THAN (TO_DAYS('2025-09-01')),
  PARTITION p_2025_09 VALUES LESS THAN (TO_DAYS('2025-10-01')),
  PARTITION p_2025_10 VALUES LESS THAN (TO_DAYS('2025-11-01')),
  PARTITION p_2025_11 VALUES LESS THAN (TO_DAYS('2025-12-01')),
  PARTITION p_2025_12 VALUES LESS THAN (TO_DAYS('2026-01-01')),
  PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 1.3 路由请求日志表（优化版 - 修复分区主键问题）
-- ============================================================================
DROP TABLE IF EXISTS `routing_logs`;
CREATE TABLE `routing_logs` (
  `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
  
  -- 请求标识
  `request_id` VARCHAR(100) NOT NULL COMMENT '请求ID',
  `trace_id` VARCHAR(100) COMMENT '追踪ID（用于分布式追踪）',
  `parent_id` VARCHAR(100) COMMENT '父请求ID',
  
  -- 路由信息
  `server_key` VARCHAR(100) COMMENT '目标服务器标识',
  `server_name` VARCHAR(100) COMMENT '目标服务器名称',
  `load_balance_strategy` VARCHAR(30) COMMENT '负载均衡策略',
  
  -- 请求信息
  `method` VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
  `path` VARCHAR(500) NOT NULL COMMENT '请求路径',
  `mcp_method` VARCHAR(50) COMMENT 'MCP方法（如 tools/call）',
  `tool_name` VARCHAR(100) COMMENT '工具名称',
  `query_params` TEXT COMMENT '查询参数',
  
  -- 请求内容（考虑存储开销，可选择性存储）
  `request_headers` JSON COMMENT '请求头',
  `request_body` LONGTEXT COMMENT '请求体',
  `request_size` INT COMMENT '请求体大小(字节)',
  
  -- 响应信息
  `response_status` INT COMMENT '响应状态码',
  `response_headers` JSON COMMENT '响应头',
  `response_body` LONGTEXT COMMENT '响应体',
  `response_size` INT COMMENT '响应体大小(字节)',
  
  -- 时间信息
  `start_time` DATETIME NOT NULL COMMENT '请求开始时间',
  `end_time` DATETIME COMMENT '请求结束时间',
  `duration` INT COMMENT '请求耗时(毫秒)',
  `queue_time` INT COMMENT '排队时间(毫秒)',
  `connect_time` INT COMMENT '连接时间(毫秒)',
  `process_time` INT COMMENT '处理时间(毫秒)',
  
  -- 客户端信息
  `client_id` VARCHAR(100) COMMENT '客户端ID',
  `client_ip` VARCHAR(50) COMMENT '客户端IP',
  `real_ip` VARCHAR(50) COMMENT '真实IP（考虑代理后的真实客户端IP）',
  `forwarded_for` VARCHAR(500) COMMENT 'X-Forwarded-For头（完整的代理链）',
  `user_agent` VARCHAR(500) COMMENT '用户代理',
  `referer` VARCHAR(1000) COMMENT 'Referer头（请求来源页面）',
  `origin` VARCHAR(500) COMMENT 'Origin头（请求来源域名）',
  `host` VARCHAR(255) COMMENT 'Host头（请求的主机名和端口）',
  `session_id` VARCHAR(100) COMMENT '会话ID',
  
  -- 状态信息
  `is_success` TINYINT(1) DEFAULT 0 COMMENT '是否成功',
  `is_cached` TINYINT(1) DEFAULT 0 COMMENT '是否命中缓存',
  `is_retry` TINYINT(1) DEFAULT 0 COMMENT '是否重试请求',
  `retry_count` INT DEFAULT 0 COMMENT '重试次数',
  
  -- 错误信息
  `error_message` TEXT COMMENT '错误信息',
  `error_code` VARCHAR(50) COMMENT '错误代码',
  `error_type` VARCHAR(50) COMMENT '错误类型',
  
  -- 元数据
  `metadata` JSON COMMENT '扩展元数据',
  `tags` JSON COMMENT '标签',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  -- 主键必须包含分区键
  PRIMARY KEY (`id`, `start_time`),
  
  -- 唯一索引支持 UPSERT（request_id 必须包含分区键）
  UNIQUE KEY `uk_request_time` (`request_id`, `start_time`),
  
  -- 索引
  KEY `idx_server_key` (`server_key`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_response_status` (`response_status`),
  KEY `idx_client_ip` (`client_ip`),
  KEY `idx_is_success` (`is_success`),
  KEY `idx_tool_name` (`tool_name`),
  KEY `idx_server_time` (`server_key`, `start_time`),
  KEY `idx_client_time` (`client_id`, `start_time`),
  
  -- 覆盖索引：常用查询字段
  KEY `idx_cover_summary` (`server_key`, `start_time`, `is_success`, `duration`, `tool_name`)
  
  -- 注意：外键约束无法在分区表上使用
  -- CONSTRAINT `fk_routing_server` FOREIGN KEY (`server_key`) 
  --     REFERENCES `mcp_servers`(`server_key`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路由请求日志表'
-- 按天分区，便于数据归档和清理
PARTITION BY RANGE (TO_DAYS(start_time)) (
  PARTITION p_2025_03_01 VALUES LESS THAN (TO_DAYS('2025-03-02')),
  PARTITION p_2025_03_02 VALUES LESS THAN (TO_DAYS('2025-03-03')),
  PARTITION p_2025_03_03 VALUES LESS THAN (TO_DAYS('2025-03-04')),
  PARTITION p_2025_03_04 VALUES LESS THAN (TO_DAYS('2025-03-05')),
  PARTITION p_2025_03_05 VALUES LESS THAN (TO_DAYS('2025-03-06')),
  PARTITION p_2025_03_06 VALUES LESS THAN (TO_DAYS('2025-03-07')),
  PARTITION p_2025_03_07 VALUES LESS THAN (TO_DAYS('2025-03-08')),
  PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- 1.4 路由日志归档表（与主表结构相同，用于存储历史数据）
-- ============================================================================
DROP TABLE IF EXISTS `routing_logs_archive`;
CREATE TABLE `routing_logs_archive` LIKE `routing_logs`;

-- ============================================================================
-- 2. 扩展功能表
-- ============================================================================

-- 2.1 负载均衡指标表
-- ============================================================================
DROP TABLE IF EXISTS `load_balancer_metrics`;
CREATE TABLE `load_balancer_metrics` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `server_key` VARCHAR(100) COMMENT '服务器标识（可空以支持外键SET NULL）',
  `metric_time` DATETIME NOT NULL COMMENT '指标时间',
  
  -- 请求统计
  `total_requests` BIGINT DEFAULT 0 COMMENT '总请求数',
  `successful_requests` BIGINT DEFAULT 0 COMMENT '成功请求数',
  `error_requests` BIGINT DEFAULT 0 COMMENT '错误请求数',
  `success_rate` DECIMAL(5,4) COMMENT '成功率',
  
  -- 性能指标
  `total_response_time` BIGINT DEFAULT 0 COMMENT '总响应时间(ms)',
  `avg_response_time` INT COMMENT '平均响应时间(ms)',
  `min_response_time` INT COMMENT '最小响应时间(ms)',
  `max_response_time` INT COMMENT '最大响应时间(ms)',
  `p50_response_time` INT COMMENT 'P50响应时间(ms)',
  `p95_response_time` INT COMMENT 'P95响应时间(ms)',
  `p99_response_time` INT COMMENT 'P99响应时间(ms)',
  
  -- 连接统计
  `active_connections` INT DEFAULT 0 COMMENT '活跃连接数',
  `peak_connections` INT DEFAULT 0 COMMENT '峰值连接数',
  
  -- 健康评分
  `health_score` DECIMAL(3,2) DEFAULT 1.00 COMMENT '健康评分',
  
  -- 负载指标
  `requests_per_second` DECIMAL(10,2) COMMENT '每秒请求数',
  `load_score` DECIMAL(5,2) COMMENT '负载评分',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  -- 索引
  KEY `idx_server_key` (`server_key`),
  KEY `idx_metric_time` (`metric_time`),
  KEY `idx_server_time` (`server_key`, `metric_time`),
  
  -- 外键约束（使用 SET NULL 而非 CASCADE）
  CONSTRAINT `fk_metrics_server` FOREIGN KEY (`server_key`) 
    REFERENCES `mcp_servers`(`server_key`) 
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='负载均衡指标表';

-- 2.2 熔断器状态历史表
-- ============================================================================
DROP TABLE IF EXISTS `circuit_breaker_history`;
CREATE TABLE `circuit_breaker_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `service_name` VARCHAR(100) NOT NULL COMMENT '服务名称',
  
  -- 状态信息
  `state` VARCHAR(20) NOT NULL COMMENT '熔断器状态: OPEN, CLOSED, HALF_OPEN',
  `previous_state` VARCHAR(20) COMMENT '前一状态',
  `state_change_time` DATETIME NOT NULL COMMENT '状态变更时间',
  `state_duration` INT COMMENT '状态持续时间(秒)',
  
  -- 计数器
  `failure_count` INT DEFAULT 0 COMMENT '失败计数',
  `success_count` INT DEFAULT 0 COMMENT '成功计数',
  `consecutive_failures` INT DEFAULT 0 COMMENT '连续失败次数',
  `consecutive_successes` INT DEFAULT 0 COMMENT '连续成功次数',
  
  -- 阈值配置
  `failure_threshold` INT COMMENT '失败阈值',
  `success_threshold` INT COMMENT '成功阈值',
  `timeout_seconds` INT COMMENT '超时时间(秒)',
  
  -- 触发信息
  `trigger_reason` TEXT COMMENT '触发原因',
  `last_error` TEXT COMMENT '最后错误信息',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  -- 索引
  KEY `idx_service` (`service_name`),
  KEY `idx_time` (`state_change_time`),
  KEY `idx_state` (`state`),
  KEY `idx_service_time` (`service_name`, `state_change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='熔断器状态历史表';

-- 2.3 连接池统计表
-- ============================================================================
DROP TABLE IF EXISTS `connection_pool_stats`;
CREATE TABLE `connection_pool_stats` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `stat_time` DATETIME NOT NULL COMMENT '统计时间',
  
  -- 连接统计
  `active_connections` INT DEFAULT 0 COMMENT '活跃连接数',
  `idle_connections` INT DEFAULT 0 COMMENT '空闲连接数',
  `total_connections` INT DEFAULT 0 COMMENT '总连接数',
  
  -- 生命周期统计
  `total_created` BIGINT DEFAULT 0 COMMENT '累计创建连接数',
  `total_closed` BIGINT DEFAULT 0 COMMENT '累计关闭连接数',
  `total_expired` BIGINT DEFAULT 0 COMMENT '累计过期连接数',
  
  -- 请求统计
  `total_requests` BIGINT DEFAULT 0 COMMENT '总请求数',
  `cache_hits` BIGINT DEFAULT 0 COMMENT '缓存命中数',
  `cache_misses` BIGINT DEFAULT 0 COMMENT '缓存未命中数',
  `cache_hit_rate` DECIMAL(5,4) COMMENT '缓存命中率',
  
  -- 性能指标
  `avg_wait_time` INT COMMENT '平均等待时间(ms)',
  `max_wait_time` INT COMMENT '最大等待时间(ms)',
  `avg_connection_age` INT COMMENT '平均连接年龄(秒)',
  
  -- 池配置
  `max_pool_size` INT COMMENT '最大池大小',
  `idle_timeout_minutes` INT COMMENT '空闲超时(分钟)',
  `max_lifetime_hours` INT COMMENT '最大生命周期(小时)',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  -- 索引
  KEY `idx_stat_time` (`stat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='连接池统计表';

-- 2.4 SSE会话记录表
-- ============================================================================
DROP TABLE IF EXISTS `sse_session_records`;
CREATE TABLE `sse_session_records` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  
  -- 会话标识
  `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
  `client_id` VARCHAR(100) NOT NULL COMMENT '客户端ID',
  
  -- 状态信息
  `status` VARCHAR(20) NOT NULL COMMENT '会话状态: CONNECTING, CONNECTED, DISCONNECTED, ERROR, TIMEOUT',
  `disconnect_reason` VARCHAR(50) COMMENT '断开原因',
  
  -- 时间信息
  `created_time` DATETIME NOT NULL COMMENT '创建时间',
  `last_active_time` DATETIME COMMENT '最后活跃时间',
  `disconnected_time` DATETIME COMMENT '断开时间',
  `session_duration` INT COMMENT '会话时长(秒)',
  `idle_duration` INT COMMENT '空闲时长(秒)',
  
  -- 消息统计
  `message_count` BIGINT DEFAULT 0 COMMENT '消息数量',
  `error_count` BIGINT DEFAULT 0 COMMENT '错误数量',
  `bytes_sent` BIGINT DEFAULT 0 COMMENT '发送字节数',
  `bytes_received` BIGINT DEFAULT 0 COMMENT '接收字节数',
  
  -- 质量指标
  `avg_message_interval` INT COMMENT '平均消息间隔(ms)',
  `max_idle_time` INT COMMENT '最大空闲时间(秒)',
  `reconnect_count` INT DEFAULT 0 COMMENT '重连次数',
  
  -- 客户端信息
  `client_ip` VARCHAR(50) COMMENT '客户端IP',
  `user_agent` VARCHAR(500) COMMENT '用户代理',
  
  -- 元数据
  `metadata` JSON COMMENT '元数据',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  
  -- 索引
  KEY `idx_session_id` (`session_id`),
  KEY `idx_client_id` (`client_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SSE会话记录表';

-- ============================================================================
-- 3. 辅助配置表
-- ============================================================================

-- 3.1 MCP工具配置表
-- ============================================================================
DROP TABLE IF EXISTS `mcp_tools`;
CREATE TABLE `mcp_tools` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `server_key` VARCHAR(100) NOT NULL COMMENT '服务器标识',
  `tool_name` VARCHAR(100) NOT NULL COMMENT '工具名称',
  `tool_description` TEXT COMMENT '工具描述',
  `input_schema` JSON COMMENT '输入参数schema',
  `output_schema` JSON COMMENT '输出参数schema',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `version` VARCHAR(20) DEFAULT '1.0' COMMENT '工具版本',
  
  -- 统计信息
  `call_count` BIGINT DEFAULT 0 COMMENT '调用次数',
  `error_count` BIGINT DEFAULT 0 COMMENT '错误次数',
  `avg_response_time` INT COMMENT '平均响应时间(ms)',
  `last_call_time` DATETIME NULL COMMENT '最后调用时间',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  -- 索引
  UNIQUE KEY `uk_server_tool` (`server_key`, `tool_name`),
  KEY `idx_server_key` (`server_key`),
  KEY `idx_tool_name` (`tool_name`),
  KEY `idx_enabled` (`enabled`),
  
  -- 外键约束
  CONSTRAINT `fk_tools_server` FOREIGN KEY (`server_key`) 
    REFERENCES `mcp_servers`(`server_key`) 
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具配置表';

-- 3.2 系统配置表
-- ============================================================================
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` TEXT NOT NULL COMMENT '配置值',
  `config_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '配置类型: STRING, JSON, NUMBER, BOOLEAN',
  `category` VARCHAR(50) DEFAULT 'GENERAL' COMMENT '配置分类',
  `description` VARCHAR(500) COMMENT '配置描述',
  `default_value` TEXT COMMENT '默认值',
  `editable` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可编辑',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_config_type` (`config_type`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================================================
-- 4. 聚合统计表
-- ============================================================================

-- 4.1 服务器日统计表
-- ============================================================================
DROP TABLE IF EXISTS `server_daily_stats`;
CREATE TABLE `server_daily_stats` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `server_key` VARCHAR(100) NOT NULL COMMENT '服务器标识',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  
  -- 请求统计
  `total_requests` BIGINT DEFAULT 0 COMMENT '总请求数',
  `successful_requests` BIGINT DEFAULT 0 COMMENT '成功请求数',
  `error_requests` BIGINT DEFAULT 0 COMMENT '错误请求数',
  `success_rate` DECIMAL(5,4) COMMENT '成功率',
  
  -- 性能统计
  `avg_response_time` INT COMMENT '平均响应时间(ms)',
  `p50_response_time` INT COMMENT 'P50响应时间(ms)',
  `p95_response_time` INT COMMENT 'P95响应时间(ms)',
  `p99_response_time` INT COMMENT 'P99响应时间(ms)',
  
  -- 健康统计
  `total_health_checks` INT DEFAULT 0 COMMENT '健康检查总数',
  `successful_health_checks` INT DEFAULT 0 COMMENT '成功健康检查数',
  `avg_health_score` DECIMAL(3,2) COMMENT '平均健康评分',
  
  -- 可用性统计
  `uptime_seconds` INT COMMENT '可用时间(秒)',
  `downtime_seconds` INT COMMENT '不可用时间(秒)',
  `availability` DECIMAL(5,4) COMMENT '可用性',
  
  -- 热点工具
  `top_tools` JSON COMMENT '热点工具统计 [{tool: xx, count: xx}]',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  -- 索引
  UNIQUE KEY `uk_server_date` (`server_key`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`),
  
  -- 外键约束
  CONSTRAINT `fk_daily_server` FOREIGN KEY (`server_key`) 
    REFERENCES `mcp_servers`(`server_key`) 
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器日统计表';

-- 4.2 工具调用统计表
-- ============================================================================
DROP TABLE IF EXISTS `tool_call_stats`;
CREATE TABLE `tool_call_stats` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `tool_name` VARCHAR(100) NOT NULL COMMENT '工具名称',
  `server_key` VARCHAR(100) NOT NULL COMMENT '服务器标识',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  
  -- 调用统计
  `total_calls` BIGINT DEFAULT 0 COMMENT '总调用次数',
  `successful_calls` BIGINT DEFAULT 0 COMMENT '成功调用次数',
  `error_calls` BIGINT DEFAULT 0 COMMENT '错误调用次数',
  `success_rate` DECIMAL(5,4) COMMENT '成功率',
  
  -- 性能统计
  `avg_response_time` INT COMMENT '平均响应时间(ms)',
  `min_response_time` INT COMMENT '最小响应时间(ms)',
  `max_response_time` INT COMMENT '最大响应时间(ms)',
  
  -- 用户统计
  `unique_clients` INT DEFAULT 0 COMMENT '唯一客户端数',
  
  -- 时间戳
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  -- 索引
  UNIQUE KEY `uk_tool_server_date` (`tool_name`, `server_key`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_tool_name` (`tool_name`),
  
  -- 外键约束
  CONSTRAINT `fk_tool_stats_server` FOREIGN KEY (`server_key`) 
    REFERENCES `mcp_servers`(`server_key`) 
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用统计表';

-- ============================================================================
-- 5. 初始化数据
-- ============================================================================

-- 5.1 插入默认系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `config_type`, `category`, `description`, `editable`) VALUES
-- 健康检查配置
('health_check.interval', '30', 'NUMBER', 'HEALTH', '健康检查间隔(秒)', 1),
('health_check.timeout', '5', 'NUMBER', 'HEALTH', '健康检查超时时间(秒)', 1),
('health_check.failure_threshold', '3', 'NUMBER', 'HEALTH', '健康检查失败阈值', 1),
('health_check.success_threshold', '2', 'NUMBER', 'HEALTH', '健康恢复成功阈值', 1),

-- 负载均衡配置
('load_balancer.algorithm', 'SMART_ROUTING', 'STRING', 'ROUTING', '负载均衡算法', 1),
('load_balancer.weight_enabled', 'true', 'BOOLEAN', 'ROUTING', '是否启用权重', 1),

-- 熔断器配置
('circuit_breaker.enabled', 'true', 'BOOLEAN', 'CIRCUIT_BREAKER', '是否启用熔断器', 1),
('circuit_breaker.failure_threshold', '5', 'NUMBER', 'CIRCUIT_BREAKER', '熔断器失败阈值', 1),
('circuit_breaker.timeout', '60', 'NUMBER', 'CIRCUIT_BREAKER', '熔断器超时时间(秒)', 1),

-- 连接池配置
('connection_pool.max_size', '20', 'NUMBER', 'CONNECTION', '连接池最大大小', 1),
('connection_pool.idle_timeout', '10', 'NUMBER', 'CONNECTION', '连接空闲超时(分钟)', 1),

-- 持久化配置
('persistence.enabled', 'true', 'BOOLEAN', 'PERSISTENCE', '是否启用持久化', 1),
('persistence.async_write', 'true', 'BOOLEAN', 'PERSISTENCE', '是否启用异步写入', 1),
('persistence.batch_size', '500', 'NUMBER', 'PERSISTENCE', '批量写入大小', 1),
('persistence.log_retention_days', '7', 'NUMBER', 'PERSISTENCE', '日志保留天数', 1)
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 6. 存储过程
-- ============================================================================

-- 6.1 自动创建路由日志分区
DELIMITER $$

DROP PROCEDURE IF EXISTS `create_routing_log_partition`$$
CREATE PROCEDURE `create_routing_log_partition`()
BEGIN
    DECLARE v_date DATE;
    DECLARE v_partition_name VARCHAR(20);
    DECLARE v_partition_value INT;
    
    SET v_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY);
    SET v_partition_name = CONCAT('p_', DATE_FORMAT(v_date, '%Y_%m_%d'));
    SET v_partition_value = TO_DAYS(DATE_ADD(v_date, INTERVAL 1 DAY));
    
    -- 检查分区是否已存在
    SET @partition_exists = (
        SELECT COUNT(*) 
        FROM information_schema.PARTITIONS 
        WHERE TABLE_SCHEMA = 'mcp_bridge' 
        AND TABLE_NAME = 'routing_logs' 
        AND PARTITION_NAME = v_partition_name
    );
    
    IF @partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE routing_logs REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', v_partition_name, ' VALUES LESS THAN (', v_partition_value, '),',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        
        SELECT CONCAT('✅ Created partition: ', v_partition_name) AS result;
    ELSE
        SELECT CONCAT('ℹ️  Partition already exists: ', v_partition_name) AS result;
    END IF;
END$$

DELIMITER ;

-- ============================================================================
-- 7. 视图
-- ============================================================================

-- 7.1 服务器概览视图
CREATE OR REPLACE VIEW `v_server_overview` AS
SELECT 
    s.server_key,
    s.server_name,
    s.server_group,
    s.host,
    s.port,
    s.healthy,
    s.enabled,
    s.weight,
    s.total_requests,
    s.total_errors,
    s.last_request_time,
    s.last_health_check,
    COUNT(DISTINCT t.tool_name) as tool_count
FROM mcp_servers s
LEFT JOIN mcp_tools t ON s.server_key = t.server_key AND t.enabled = 1
WHERE s.deleted_at IS NULL
GROUP BY s.server_key, s.server_name, s.server_group, s.host, s.port, 
         s.healthy, s.enabled, s.weight, s.total_requests, s.total_errors,
         s.last_request_time, s.last_health_check;

-- 7.2 最近24小时请求统计视图
CREATE OR REPLACE VIEW `v_recent_24h_stats` AS
SELECT 
    server_key,
    server_name,
    COUNT(*) as total_requests,
    SUM(CASE WHEN is_success = 1 THEN 1 ELSE 0 END) as successful_requests,
    SUM(CASE WHEN is_success = 0 THEN 1 ELSE 0 END) as error_requests,
    CAST(AVG(duration) AS SIGNED) as avg_response_time,
    MAX(duration) as max_response_time,
    MIN(duration) as min_response_time
FROM routing_logs
WHERE start_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY server_key, server_name;

-- ============================================================================
-- 恢复外键检查
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 完成
-- ============================================================================

SELECT '✅ MCP Router V3 优化版数据库 schema v2.0 创建完成！' as status;

SELECT CONCAT('📊 共创建 ', COUNT(*), ' 张表') as table_count 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'mcp_bridge' AND TABLE_TYPE = 'BASE TABLE';

SELECT CONCAT('👁️  共创建 ', COUNT(*), ' 个视图') as view_count 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'mcp_bridge' AND TABLE_TYPE = 'VIEW';

SELECT CONCAT('🔧 共创建 ', COUNT(*), ' 个存储过程') as procedure_count 
FROM information_schema.ROUTINES 
WHERE ROUTINE_SCHEMA = 'mcp_bridge' AND ROUTINE_TYPE = 'PROCEDURE';

-- 显示分区信息
SELECT 
    TABLE_NAME,
    PARTITION_NAME,
    PARTITION_METHOD,
    PARTITION_EXPRESSION,
    TABLE_ROWS
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = 'mcp_bridge'
  AND PARTITION_NAME IS NOT NULL
ORDER BY TABLE_NAME, PARTITION_ORDINAL_POSITION;

