package com.pajk.mcpbridge.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP服务器配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {
    
    /**
     * 服务器ID
     */
    private String id;
    
    /**
     * 服务器名称
     */
    private String name;
    
    /**
     * 服务器版本
     */
    private String version;
    
    /**
     * 协议类型
     */
    private String protocol;
    
    /**
     * 前端协议
     */
    private String frontProtocol;
    
    /**
     * 服务器描述
     */
    private String description;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 服务器能力
     */
    private List<String> capabilities;
    
    /**
     * 最新发布版本
     */
    private String latestPublishedVersion;
    
    /**
     * 服务器IP地址
     */
    private String ip;
    
    /**
     * 服务器端口
     */
    private int port;
    
    /**
     * SSE端点路径
     */
    private String sseEndpoint;
    
    /**
     * 服务器状态
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
     * 服务器元数据
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

    /**
     * 版本详情（含版本号和发布时间）
     */
    private VersionDetail versionDetail;

    /**
     * 远程服务配置（嵌套 serviceRef、exportPath）
     */
    private RemoteServerConfig remoteServerConfig;

    /**
     * 工具描述配置ID（完整 dataId）
     */
    private String toolsDescriptionRef;

    /**
     * 命名空间ID
     */
    private String namespaceId;

    /**
     * 提示描述配置ID（可选扩展）
     */
    private String promptDescriptionRef;

    /**
     * 资源描述配置ID（可选扩展）
     */
    private String resourceDescriptionRef;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionDetail {
        private String version;
        private String release_date;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoteServerConfig {
        private ServiceRef serviceRef;
        private String exportPath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceRef {
        private String namespaceId;
        private String groupName;
        private String serviceName;
    }
} 