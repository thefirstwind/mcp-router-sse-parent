package com.pajk.mcpbridge.core;

import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * MCP Router V3 主应用类
 * 
 * 注意：排除 MybatisAutoConfiguration，使用我们自定义的条件化配置
 */
@SpringBootApplication(exclude = {MybatisAutoConfiguration.class})
@EnableWebFlux
@EnableScheduling
@ComponentScan(basePackages = {
    "com.pajk.mcpbridge.core",
    "com.pajk.mcpbridge.persistence"
})
public class McpRouterV3Application {

    private final static Logger log = LoggerFactory.getLogger(McpRouterV3Application.class);
    public static void main(String[] args) {
        SpringApplication.run(McpRouterV3Application.class, args);
        log.info("Application server started!!!");
    }
} 