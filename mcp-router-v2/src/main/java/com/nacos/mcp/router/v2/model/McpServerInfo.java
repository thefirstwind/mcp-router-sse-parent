package com.nacos.mcp.router.v2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MCP服务器信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerInfo {
    
    /**
     * 服务名称
     */
    private String name;
    
    /**
     * 服务版本
     */
    private String version;
    
    /**
     * 服务IP地址 - 支持host和ip两种字段名
     */
    @JsonProperty(value = "ip", access = JsonProperty.Access.WRITE_ONLY)
    private String ip;
    
    /**
     * 服务主机地址 - 映射到ip字段
     */
    @JsonProperty(value = "host")
    public String getHost() {
        return ip;
    }
    
    public void setHost(String host) {
        this.ip = host;
    }
    
    /**
     * 服务端口
     */
    private int port;
    
    /**
     * SSE端点路径
     */
    private String sseEndpoint;
    
    /**
     * 服务状态
     */
    private String status;
    
    /**
     * 服务组
     */
    private String serviceGroup;
    
    /**
     * 注册时间
     */
    private LocalDateTime registrationTime;
    
    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;
    
    /**
     * 服务元数据
     */
    private Map<String, String> metadata;
    
    /**
     * 是否临时实例
     */
    private boolean ephemeral;
    
    /**
     * 健康状态
     */
    private boolean healthy;
    
    /**
     * 权重
     */
    private double weight;
} 