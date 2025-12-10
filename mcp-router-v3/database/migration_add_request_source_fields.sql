-- ============================================================================
-- 数据库迁移脚本：为 routing_logs 表添加请求来源信息字段
-- 执行日期：2025-01-XX
-- 说明：扩展路由日志表，记录更多请求来源信息，便于追踪和分析
-- ============================================================================

-- 注意：MySQL 8.0.19+ 支持 IF NOT EXISTS 语法
-- 如果使用较低版本的 MySQL，请先检查字段是否存在，或使用存储过程

-- 添加 real_ip 字段（真实IP，考虑代理后的真实客户端IP）
-- MySQL 8.0.19+ 可以使用 IF NOT EXISTS
ALTER TABLE `routing_logs` 
ADD COLUMN `real_ip` VARCHAR(50) COMMENT '真实IP（考虑代理后的真实客户端IP）' AFTER `client_ip`;

-- 添加 forwarded_for 字段（X-Forwarded-For头，完整的代理链）
ALTER TABLE `routing_logs` 
ADD COLUMN `forwarded_for` VARCHAR(500) COMMENT 'X-Forwarded-For头（完整的代理链）' AFTER `real_ip`;

-- 添加 referer 字段（Referer头，请求来源页面）
ALTER TABLE `routing_logs` 
ADD COLUMN `referer` VARCHAR(1000) COMMENT 'Referer头（请求来源页面）' AFTER `user_agent`;

-- 添加 origin 字段（Origin头，请求来源域名）
ALTER TABLE `routing_logs` 
ADD COLUMN `origin` VARCHAR(500) COMMENT 'Origin头（请求来源域名）' AFTER `referer`;

-- 添加 host 字段（Host头，请求的主机名和端口）
ALTER TABLE `routing_logs` 
ADD COLUMN `host` VARCHAR(255) COMMENT 'Host头（请求的主机名和端口）' AFTER `origin`;

-- ============================================================================
-- 如果字段已存在，可以使用以下存储过程来安全添加字段
-- ============================================================================
/*
DELIMITER $$

CREATE PROCEDURE AddColumnIfNotExists(
    IN tableName VARCHAR(64),
    IN columnName VARCHAR(64),
    IN columnDefinition TEXT
)
BEGIN
    DECLARE columnExists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO columnExists
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tableName
      AND COLUMN_NAME = columnName;
    
    IF columnExists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', tableName, '` ADD COLUMN `', columnName, '` ', columnDefinition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 使用存储过程添加字段
CALL AddColumnIfNotExists('routing_logs', 'real_ip', 'VARCHAR(50) COMMENT ''真实IP（考虑代理后的真实客户端IP）'' AFTER `client_ip`');
CALL AddColumnIfNotExists('routing_logs', 'forwarded_for', 'VARCHAR(500) COMMENT ''X-Forwarded-For头（完整的代理链）'' AFTER `real_ip`');
CALL AddColumnIfNotExists('routing_logs', 'referer', 'VARCHAR(1000) COMMENT ''Referer头（请求来源页面）'' AFTER `user_agent`');
CALL AddColumnIfNotExists('routing_logs', 'origin', 'VARCHAR(500) COMMENT ''Origin头（请求来源域名）'' AFTER `referer`');
CALL AddColumnIfNotExists('routing_logs', 'host', 'VARCHAR(255) COMMENT ''Host头（请求的主机名和端口）'' AFTER `origin`');

-- 删除存储过程
DROP PROCEDURE IF EXISTS AddColumnIfNotExists;
*/

-- ============================================================================
-- 验证脚本：检查字段是否添加成功
-- ============================================================================
SELECT 
    COLUMN_NAME, 
    DATA_TYPE, 
    CHARACTER_MAXIMUM_LENGTH, 
    COLUMN_COMMENT,
    ORDINAL_POSITION
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'routing_logs'
  AND COLUMN_NAME IN ('real_ip', 'forwarded_for', 'referer', 'origin', 'host')
ORDER BY ORDINAL_POSITION;

