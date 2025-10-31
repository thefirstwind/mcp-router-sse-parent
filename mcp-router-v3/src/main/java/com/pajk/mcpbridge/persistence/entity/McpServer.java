package com.pajk.mcpbridge.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP服务器注册信息实体
 * 记录服务器的注册信息、健康状态和元数据
 * 映射到表: mcp_servers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServer {
    
    /**
     * 数据库主键ID
     */
    private Long id;
    
    /**
     * 服务器唯一标识: serviceName:host:port
     * 例如: "mcp-server-v3:192.168.1.100:8063"
     */
    private String serverKey;
    
    /**
     * 服务名称
     */
    private String serverName;
    
    /**
     * 服务分组
     */
    private String serverGroup;
    
    /**
     * Nacos命名空间
     */
    private String namespaceId;
    
    /**
     * 服务器主机地址（IP或域名）
     */
    private String host;
    
    /**
     * 服务器端口
     */
    private Integer port;
    
    /**
     * SSE连接端点
     */
    private String sseEndpoint;
    
    /**
     * 健康检查端点
     */
    private String healthEndpoint;
    
    /**
     * 是否健康
     */
    private Boolean healthy;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 权重（用于负载均衡）
     */
    private Double weight;
    
    /**
     * 是否为临时实例
     */
    private Boolean ephemeral;
    
    /**
     * 集群名称
     */
    private String clusterName;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 协议类型（如 mcp-sse）
     */
    private String protocol;
    
    /**
     * 元数据JSON (存储工具列表、版本号等)
     */
    private String metadata;
    
    /**
     * 标签JSON
     */
    private String tags;
    
    /**
     * 总请求数
     */
    private Long totalRequests;
    
    /**
     * 总错误数
     */
    private Long totalErrors;
    
    /**
     * 最后请求时间
     */
    private LocalDateTime lastRequestTime;
    
    /**
     * 最后健康检查时间
     */
    private LocalDateTime lastHealthCheck;
    
    /**
     * 注册时间
     */
    private LocalDateTime registeredAt;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 删除时间（软删除）
     */
    private LocalDateTime deletedAt;
    
    /**
     * 构建器模式 - 从服务注册信息创建
     */
    public static McpServer fromRegistration(String serverKey, String serverName, String serviceGroup,
                                             String host, Integer port, String sseEndpoint, 
                                             String healthEndpoint, String metadata) {
        return fromRegistration(serverKey, serverName, serviceGroup, host, port, sseEndpoint, 
                               healthEndpoint, metadata, true, true, 1.0, true);
    }
    
    /**
     * 构建器模式 - 从服务注册信息创建（完整参数版本）
     */
    public static McpServer fromRegistration(String serverKey, String serverName, String serviceGroup,
                                             String host, Integer port, String sseEndpoint, 
                                             String healthEndpoint, String metadata,
                                             Boolean healthy, Boolean enabled, Double weight, Boolean ephemeral) {
        return McpServer.builder()
            .serverKey(serverKey)
            .serverName(serverName)
            .serverGroup(serviceGroup != null ? serviceGroup : "mcp-server")
            .namespaceId("public")
            .host(host)
            .port(port)
            .sseEndpoint(sseEndpoint != null ? sseEndpoint : "/sse")
            .healthEndpoint(healthEndpoint != null ? healthEndpoint : "/health")
            .healthy(healthy != null ? healthy : true)
            .enabled(enabled != null ? enabled : true)
            .weight(weight != null ? weight : 1.0)
            .ephemeral(ephemeral != null ? ephemeral : true)
            .clusterName("DEFAULT")
            .version("1.0.0")
            .protocol("mcp-sse")
            .metadata(metadata)
            .totalRequests(0L)
            .totalErrors(0L)
            .lastHealthCheck(LocalDateTime.now())
            .registeredAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * 更新健康状态
     */
    public void updateHealthStatus(boolean healthy) {
        this.healthy = healthy;
        this.lastHealthCheck = LocalDateTime.now();
    }
    
    /**
     * 标记为离线（软删除）
     */
    public void markOffline() {
        this.healthy = false;
        this.enabled = false;
        this.deletedAt = LocalDateTime.now();
    }
    
    /**
     * 更新健康检查时间
     */
    public void updateHealthCheck() {
        this.lastHealthCheck = LocalDateTime.now();
        if (!this.healthy) {
            this.healthy = true;
        }
    }
}

