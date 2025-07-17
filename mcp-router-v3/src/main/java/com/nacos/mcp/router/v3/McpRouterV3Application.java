package com.nacos.mcp.router.v3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * MCP Router V3 主应用类
 */
@SpringBootApplication
@EnableWebFlux
@EnableScheduling
public class McpRouterV3Application {
    
    public static void main(String[] args) {
        SpringApplication.run(McpRouterV3Application.class, args);
    }
} 