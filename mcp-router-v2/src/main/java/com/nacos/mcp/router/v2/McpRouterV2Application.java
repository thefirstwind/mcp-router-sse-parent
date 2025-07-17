package com.nacos.mcp.router.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * MCP Router V2 主应用类
 */
@SpringBootApplication
@EnableWebFlux
@EnableScheduling
public class McpRouterV2Application {
    
    public static void main(String[] args) {
        SpringApplication.run(McpRouterV2Application.class, args);
    }
} 