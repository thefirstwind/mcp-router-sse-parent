package com.pajk.mcpbridge.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 路由日志实体类
 * 对应数据库表: routing_logs
 * 
 * 记录每一次路由请求的完整信息，用于:
 * 1. 请求追踪和问题排查
 * 2. 性能分析和优化
 * 3. 业务统计和报表
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingLog {
    
    /**
     * 主键ID（自增）
     */
    private Long id;
    
    /**
     * 请求唯一标识（用于关联请求的所有日志）
     */
    private String requestId;
    
    /**
     * 请求方法（如: tools/list, tools/call）
     */
    private String method;
    
    /**
     * 请求参数（JSON格式）
     */
    private String params;
    
    /**
     * 路由策略（ROUND_ROBIN, WEIGHTED, LEAST_CONNECTIONS）
     */
    private String routingStrategy;
    
    /**
     * 目标服务器标识
     */
    private String targetServer;
    
    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;
    
    /**
     * HTTP状态码
     */
    private Integer statusCode;
    
    /**
     * 是否成功（true/false）
     */
    private Boolean success;
    
    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
    
    /**
     * 请求时间
     */
    private LocalDateTime requestTime;
    
    /**
     * 响应时间
     */
    private LocalDateTime responseTimestamp;
    
    /**
     * 创建时间（数据库记录创建时间）
     */
    private LocalDateTime createdAt;
    
    /**
     * 创建一个新的路由日志构建器，自动设置请求时间
     */
    public static RoutingLogBuilder newBuilder() {
        return RoutingLog.builder()
            .requestTime(LocalDateTime.now())
            .success(true); // 默认成功，失败时再更新
    }
    
    /**
     * 标记为成功并设置响应时间
     */
    public void markSuccess(long responseTimeMs) {
        this.success = true;
        this.responseTime = responseTimeMs;
        this.responseTimestamp = LocalDateTime.now();
        this.statusCode = 200;
    }
    
    /**
     * 标记为失败并设置错误信息
     */
    public void markFailure(String errorMessage, Integer statusCode) {
        this.success = false;
        this.errorMessage = errorMessage;
        this.responseTimestamp = LocalDateTime.now();
        this.statusCode = statusCode;
        
        // 计算响应时间
        if (this.requestTime != null) {
            this.responseTime = java.time.Duration.between(
                this.requestTime,
                this.responseTimestamp
            ).toMillis();
        }
    }
}


