package com.nacos.mcp.router.v3.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP消息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpMessage {
    
    /**
     * 消息ID
     */
    @JsonProperty("id")
    private String id;
    
    /**
     * 消息类型
     */
    @JsonProperty("method")
    private String method;
    
    /**
     * 消息参数
     */
    @JsonProperty("params")
    private Map<String, Object> params;
    
    /**
     * 消息结果
     */
    @JsonProperty("result")
    private Object result;
    
    /**
     * 错误信息
     */
    @JsonProperty("error")
    private McpError error;
    
    /**
     * 消息版本
     */
    @JsonProperty("jsonrpc")
    @Builder.Default
    private String jsonrpc = "2.0";
    
    /**
     * 消息元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 目标服务名
     */
    private String targetService;
    
    /**
     * 客户端ID
     */
    private String clientId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 消息时间戳
     */
    private Long timestamp;
    
    /**
     * MCP错误模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpError {
        @JsonProperty("code")
        private int code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("data")
        private Object data;
    }
} 