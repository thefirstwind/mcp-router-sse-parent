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
    
    // ============================================================================
    // 主键和标识
    // ============================================================================
    
    /**
     * 主键ID（自增）
     */
    private Long id;
    
    /**
     * 请求唯一标识（用于关联请求的所有日志）
     */
    private String requestId;
    
    /**
     * 追踪ID（用于分布式追踪）
     */
    private String traceId;
    
    /**
     * 父请求ID（用于请求链路追踪）
     */
    private String parentId;
    
    // ============================================================================
    // 路由信息
    // ============================================================================
    
    /**
     * 目标服务器标识 (server_key)
     */
    private String serverKey;
    
    /**
     * 目标服务器名称 (server_name)
     */
    private String serverName;
    
    /**
     * 负载均衡策略 (load_balance_strategy)
     * 如: ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, LEAST_CONNECTIONS, SMART_ROUTING
     */
    private String loadBalanceStrategy;
    
    // ============================================================================
    // 请求信息
    // ============================================================================
    
    /**
     * HTTP方法 (method)
     * 如: GET, POST, PUT, DELETE
     */
    private String method;
    
    /**
     * 请求路径 (path)
     * 如: /mcp/router/route/mcp-server-v6
     */
    private String path;
    
    /**
     * MCP方法 (mcp_method)
     * 如: tools/call, tools/list, resources/list
     */
    private String mcpMethod;
    
    /**
     * 工具名称 (tool_name)
     * 如: getPersonById, getAllPersons
     */
    private String toolName;
    
    /**
     * 查询参数 (query_params)
     * URL查询字符串
     */
    private String queryParams;
    
    /**
     * 请求头 (request_headers)
     * JSON格式
     */
    private String requestHeaders;
    
    /**
     * 请求体 (request_body)
     * 完整的请求体内容
     */
    private String requestBody;
    
    /**
     * 请求体大小 (request_size)
     * 单位：字节
     */
    private Integer requestSize;
    
    // ============================================================================
    // 响应信息
    // ============================================================================
    
    /**
     * 响应状态码 (response_status)
     * 如: 200, 404, 500
     */
    private Integer responseStatus;
    
    /**
     * 响应头 (response_headers)
     * JSON格式
     */
    private String responseHeaders;
    
    /**
     * 响应体 (response_body)
     * 完整的响应体内容
     */
    private String responseBody;
    
    /**
     * 响应体大小 (response_size)
     * 单位：字节
     */
    private Integer responseSize;
    
    // ============================================================================
    // 时间信息
    // ============================================================================
    
    /**
     * 请求开始时间 (start_time)
     */
    private LocalDateTime startTime;
    
    /**
     * 请求结束时间 (end_time)
     */
    private LocalDateTime endTime;
    
    /**
     * 请求总耗时 (duration)
     * 单位：毫秒
     */
    private Integer duration;
    
    /**
     * 排队时间 (queue_time)
     * 单位：毫秒
     */
    private Integer queueTime;
    
    /**
     * 连接时间 (connect_time)
     * 单位：毫秒
     */
    private Integer connectTime;
    
    /**
     * 处理时间 (process_time)
     * 单位：毫秒
     */
    private Integer processTime;
    
    // ============================================================================
    // 客户端信息
    // ============================================================================
    
    /**
     * 客户端ID (client_id)
     */
    private String clientId;
    
    /**
     * 客户端IP (client_ip)
     */
    private String clientIp;
    
    /**
     * 用户代理 (user_agent)
     */
    private String userAgent;
    
    /**
     * 会话ID (session_id)
     */
    private String sessionId;
    
    // ============================================================================
    // 状态标识
    // ============================================================================
    
    /**
     * 是否成功 (is_success)
     */
    private Boolean isSuccess;
    
    /**
     * 是否命中缓存 (is_cached)
     */
    private Boolean isCached;
    
    /**
     * 是否重试请求 (is_retry)
     */
    private Boolean isRetry;
    
    /**
     * 重试次数 (retry_count)
     */
    private Integer retryCount;
    
    // ============================================================================
    // 错误信息
    // ============================================================================
    
    /**
     * 错误信息 (error_message)
     */
    private String errorMessage;
    
    /**
     * 错误代码 (error_code)
     */
    private String errorCode;
    
    /**
     * 错误类型 (error_type)
     * 如: TIMEOUT, CONNECTION_ERROR, SERVER_ERROR
     */
    private String errorType;
    
    // ============================================================================
    // 元数据
    // ============================================================================
    
    /**
     * 扩展元数据 (metadata)
     * JSON格式，存储其他业务相关信息
     */
    private String metadata;
    
    /**
     * 标签 (tags)
     * JSON格式，用于分类和过滤
     */
    private String tags;
    
    // ============================================================================
    // 时间戳
    // ============================================================================
    
    /**
     * 创建时间（数据库记录创建时间）(created_at)
     */
    private LocalDateTime createdAt;
    
    // ============================================================================
    // 便捷方法
    // ============================================================================
    
    /**
     * 标记为成功并设置响应时间
     */
    public void markSuccess(int durationMs) {
        this.isSuccess = true;
        this.duration = durationMs;
        this.endTime = LocalDateTime.now();
        this.responseStatus = 200;
    }
    
    /**
     * 标记为失败并设置错误信息
     */
    public void markFailure(String errorMessage, Integer statusCode, String errorCode, String errorType) {
        this.isSuccess = false;
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.endTime = LocalDateTime.now();
        this.responseStatus = statusCode;
        
        // 计算耗时
        if (this.startTime != null && this.endTime != null) {
            this.duration = (int) java.time.Duration.between(
                this.startTime,
                this.endTime
            ).toMillis();
        }
    }
    
    /**
     * 计算并设置耗时
     */
    public void calculateDuration() {
        if (this.startTime != null && this.endTime != null) {
            this.duration = (int) java.time.Duration.between(
                this.startTime,
                this.endTime
            ).toMillis();
        }
    }
}


