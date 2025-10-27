package com.pajk.mcpbridge.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final static Logger log = LoggerFactory.getLogger(McpRouterV3Application.class);
    public static void main(String[] args) {
        SpringApplication.run(McpRouterV3Application.class, args);
        log.info("Application server started!!!");
    }
} 