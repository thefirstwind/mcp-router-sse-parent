-- MCP Bridge 数据库表结构
USE `mcp-bridge`;

-- 1. MCP 服务器实例表
CREATE TABLE IF NOT EXISTS `mcp_servers` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `server_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '服务器唯一标识',
    `server_name` VARCHAR(100) NOT NULL COMMENT '服务器名称',
    `server_group` VARCHAR(50) NOT NULL DEFAULT 'mcp-server' COMMENT '服务组',
    `namespace_id` VARCHAR(50) NOT NULL DEFAULT 'public' COMMENT 'Nacos命名空间',
    `host` VARCHAR(100) NOT NULL COMMENT '服务器主机地址',
    `port` INT NOT NULL COMMENT '服务器端口',
    `sse_endpoint` VARCHAR(200) NOT NULL DEFAULT '/sse' COMMENT 'SSE端点路径',
    `health_endpoint` VARCHAR(200) DEFAULT '/health' COMMENT '健康检查端点',
    `metadata` JSON COMMENT '服务器元数据',
    `healthy` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '健康状态',
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `weight` DOUBLE NOT NULL DEFAULT 1.0 COMMENT '权重',
    `ephemeral` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否临时实例',
    `cluster_name` VARCHAR(50) DEFAULT 'DEFAULT' COMMENT '集群名称',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_server_key` (`server_key`),
    INDEX `idx_server_group` (`server_group`),
    INDEX `idx_namespace` (`namespace_id`),
    INDEX `idx_healthy` (`healthy`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器实例表';

-- 2. 健康检查记录表
CREATE TABLE IF NOT EXISTS `health_check_records` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `server_key` VARCHAR(100) NOT NULL COMMENT '服务器标识',
    `check_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检查时间',
    `status` VARCHAR(20) NOT NULL COMMENT '健康状态: HEALTHY, UNHEALTHY, UNKNOWN',
    `response_time` INT COMMENT '响应时间(毫秒)',
    `error_message` TEXT COMMENT '错误信息',
    `check_type` VARCHAR(20) NOT NULL DEFAULT 'HTTP' COMMENT '检查类型: HTTP, TCP, MCP',
    `details` JSON COMMENT '检查详情',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_server_key` (`server_key`),
    INDEX `idx_check_time` (`check_time`),
    INDEX `idx_status` (`status`),
    INDEX `idx_check_type` (`check_type`),
    FOREIGN KEY (`server_key`) REFERENCES `mcp_servers`(`server_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='健康检查记录表';

-- 3. 路由请求日志表
CREATE TABLE IF NOT EXISTS `routing_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `request_id` VARCHAR(100) NOT NULL COMMENT '请求ID',
    `server_key` VARCHAR(100) COMMENT '目标服务器标识',
    `method` VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
    `path` VARCHAR(500) NOT NULL COMMENT '请求路径',
    `query_params` TEXT COMMENT '查询参数',
    `request_headers` JSON COMMENT '请求头',
    `request_body` LONGTEXT COMMENT '请求体',
    `response_status` INT COMMENT '响应状态码',
    `response_headers` JSON COMMENT '响应头',
    `response_body` LONGTEXT COMMENT '响应体',
    `start_time` TIMESTAMP NOT NULL COMMENT '请求开始时间',
    `end_time` TIMESTAMP COMMENT '请求结束时间',
    `duration` INT COMMENT '请求耗时(毫秒)',
    `client_ip` VARCHAR(50) COMMENT '客户端IP',
    `user_agent` VARCHAR(500) COMMENT '用户代理',
    `error_message` TEXT COMMENT '错误信息',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_request_id` (`request_id`),
    INDEX `idx_server_key` (`server_key`),
    INDEX `idx_start_time` (`start_time`),
    INDEX `idx_response_status` (`response_status`),
    INDEX `idx_client_ip` (`client_ip`),
    FOREIGN KEY (`server_key`) REFERENCES `mcp_servers`(`server_key`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路由请求日志表';

-- 4. MCP 工具配置表
CREATE TABLE IF NOT EXISTS `mcp_tools` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `server_key` VARCHAR(100) NOT NULL COMMENT '服务器标识',
    `tool_name` VARCHAR(100) NOT NULL COMMENT '工具名称',
    `tool_description` TEXT COMMENT '工具描述',
    `input_schema` JSON COMMENT '输入参数schema',
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    `version` VARCHAR(20) DEFAULT '1.0' COMMENT '工具版本',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_server_tool` (`server_key`, `tool_name`),
    INDEX `idx_server_key` (`server_key`),
    INDEX `idx_tool_name` (`tool_name`),
    INDEX `idx_enabled` (`enabled`),
    FOREIGN KEY (`server_key`) REFERENCES `mcp_servers`(`server_key`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP工具配置表';

-- 5. 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` TEXT NOT NULL COMMENT '配置值',
    `config_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '配置类型: STRING, JSON, NUMBER, BOOLEAN',
    `description` VARCHAR(500) COMMENT '配置描述',
    `editable` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否可编辑',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_config_key` (`config_key`),
    INDEX `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 插入默认系统配置
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `config_type`, `description`, `editable`) VALUES
('health_check.interval', '30', 'NUMBER', '健康检查间隔(秒)', TRUE),
('health_check.timeout', '5', 'NUMBER', '健康检查超时时间(秒)', TRUE),
('health_check.failure_threshold', '3', 'NUMBER', '健康检查失败阈值', TRUE),
('load_balancer.algorithm', 'ROUND_ROBIN', 'STRING', '负载均衡算法: ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, RANDOM', TRUE),
('circuit_breaker.enabled', 'true', 'BOOLEAN', '是否启用熔断器', TRUE),
('circuit_breaker.failure_threshold', '5', 'NUMBER', '熔断器失败阈值', TRUE),
('circuit_breaker.timeout', '60', 'NUMBER', '熔断器超时时间(秒)', TRUE),
('logging.request_body', 'false', 'BOOLEAN', '是否记录请求体', TRUE),
('logging.response_body', 'false', 'BOOLEAN', '是否记录响应体', TRUE),
('logging.retention_days', '7', 'NUMBER', '日志保留天数', TRUE);