package com.nacos.mcp.router.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.nacos.mcp.router.service.McpServerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 注册配置类
 */
@Configuration
public class McpServerRegistryConfig {

    @Bean
    public McpServerRegistry mcpServerRegistry(NamingService namingService) {
        return new McpServerRegistry(namingService);
    }
} 