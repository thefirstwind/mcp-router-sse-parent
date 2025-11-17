package com.pajk.mcpbridge.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 健康检查记录实体类
 * 对应数据库表: health_check_records
 * 
 * 用于记录服务器健康检查结果，支持:
 * 1. 健康状态追踪
 * 2. 故障诊断
 * 3. SLA 统计
 * 
 * 采样策略: 
 * - 失败检查: 100% 记录
 * - 成功检查: 10% 采样
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckRecord {
    
    /**
     * 主键ID（自增）
     */
    private Long id;
    
    /**
     * 服务器标识（name:ip:port）
     */
    private String serverKey;
    
    /**
     * 检查时间
     */
    private LocalDateTime checkTime;
    
    /**
     * 检查类型（NACOS, MCP, COMBINED）
     */
    private String checkType;
    
    /**
     * 检查层级（LEVEL1=Nacos, LEVEL2=MCP）
     */
    private String checkLevel;
    
    /**
     * 健康状态（HEALTHY, UNHEALTHY, UNKNOWN, DEGRADED）
     */
    private String status;
    
    /**
     * 健康度评分（0.00-1.00）
     */
    private java.math.BigDecimal healthScore;
    
    /**
     * 响应时间（毫秒）
     */
    private Integer responseTime;
    
    /**
     * 连接时间（毫秒）
     */
    private Integer connectionTime;
    
    /**
     * 检查总耗时（毫秒）
     */
    private Integer checkDuration;
    
    /**
     * 连续成功次数
     */
    private Integer consecutiveSuccesses;
    
    /**
     * 连续失败次数
     */
    private Integer consecutiveFailures;
    
    /**
     * 累计检查次数
     */
    private Integer totalChecks;
    
    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 检查详情（JSON格式）
     */
    private String details;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 标记为健康
     */
    public void markHealthy(long responseTimeMs, Integer statusCode) {
        this.status = "HEALTHY";
        this.healthScore = java.math.BigDecimal.valueOf(1.00);
        this.responseTime = (int) responseTimeMs;
        if (this.consecutiveSuccesses == null) {
            this.consecutiveSuccesses = 0;
        }
        this.consecutiveSuccesses++;
        if (this.totalChecks == null) {
            this.totalChecks = 0;
        }
        this.totalChecks++;
    }
    
    /**
     * 标记为不健康
     */
    public void markUnhealthy(String errorMessage, String errorType) {
        this.status = "UNHEALTHY";
        this.healthScore = java.math.BigDecimal.valueOf(0.00);
        this.errorMessage = errorMessage;
        this.errorCode = errorType;
        if (this.consecutiveFailures == null) {
            this.consecutiveFailures = 0;
        }
        this.consecutiveFailures++;
        if (this.totalChecks == null) {
            this.totalChecks = 0;
        }
        this.totalChecks++;
    }
}





























