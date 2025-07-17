package com.nacos.mcp.router.v2.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP版本配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpVersionConfig {
    
    /**
     * 服务器ID
     */
    private String id;
    
    /**
     * 服务器名称
     */
    private String name;
    
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
     * 版本历史
     */
    private List<VersionDetail> versionHistory;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
    
    /**
     * 版本详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionDetail {
        
        /**
         * 版本号
         */
        private String version;
        
        /**
         * 发布时间
         */
        private LocalDateTime publishTime;
        
        /**
         * 版本描述
         */
        private String description;
        
        /**
         * 版本状态
         */
        private String status;
        
        /**
         * 版本元数据
         */
        private Map<String, Object> metadata;
    }
} 