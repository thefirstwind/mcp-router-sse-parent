package com.nacos.mcp.server.v1.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.server.v1.tools.PersonManagementTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server配置类
 * 使用Spring AI的标准MCP Server配置
 */
@Configuration
public class McpServerConfig {

    /**
     * 创建工具回调提供者
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(PersonManagementTool personManagementTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(personManagementTool)
                .build();
    }

    /**
     * 创建ObjectMapper Bean
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
} 