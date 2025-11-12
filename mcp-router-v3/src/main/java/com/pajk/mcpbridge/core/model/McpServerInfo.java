package com.pajk.mcpbridge.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MCP服务器信息
 * <p>
 * metadata字段支持自定义扩展，如：labels、env、capabilities、tags、gray、region等，
 * 可用于灰度、分组、能力路由等高级功能。
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
     * 服务IP地址
     */
    @JsonProperty("ip")
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
    @JsonProperty("port")
    private int port;
    
    /**
     * SSE端点路径
     */
    @JsonProperty("sseEndpoint")
    private String sseEndpoint;
    
    /**
     * 服务状态
     */
    @JsonProperty("status")
    private String status;
    
    /**
     * 服务组
     */
    @JsonProperty("serviceGroup")
    private String serviceGroup;
    
    /**
     * 服务描述
     */
    @JsonProperty("description")
    private String description;

    /**
     * 是否启用
     */
    @JsonProperty("enabled")
    private Boolean enabled;

    /**
     * 协议类型（如 mcp-sse、stdio 等）
     */
    @JsonProperty("protocol")
    private String protocol;

    /**
     * 远程服务配置（如 serviceRef、exportPath 等）
     */
    @JsonProperty("remoteServerConfig")
    private Object remoteServerConfig;

    /**
     * 工具描述配置ID（tools config ref）
     */
    @JsonProperty("toolsDescriptionRef")
    private String toolsDescriptionRef;

    /**
     * 工具元数据扩展（注册时可自定义，透传到 mcp-tools.json 的 toolsMeta 字段）
     */
    @JsonProperty("toolsMeta")
    private Map<String, Object> toolsMeta;

    /**
     * 服务唯一ID
     */
    @JsonProperty("id")
    private String id;

    /**
     * 命名空间ID
     */
    @JsonProperty("namespaceId")
    private String namespaceId;
    
    /**
     * 注册时间
     */
    @JsonProperty("registrationTime")
    private LocalDateTime registrationTime;
    
    /**
     * 最后心跳时间
     */
    @JsonProperty("lastHeartbeat")
    private LocalDateTime lastHeartbeat;
    
    /**
     * 服务元数据
     */
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    
    /**
     * 是否临时实例
     */
    @JsonProperty("ephemeral")
    private boolean ephemeral;
    
    /**
     * 健康状态
     */
    @JsonProperty("healthy")
    private boolean healthy;
    
    /**
     * 权重
     */
    @JsonProperty("weight")
    private double weight;
} 