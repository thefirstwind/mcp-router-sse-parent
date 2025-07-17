package com.nacos.mcp.router.v2.config;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos MCP注册配置类
 */
@Slf4j
@Configuration
public class NacosMcpRegistryConfig {

    @Value("${spring.ai.alibaba.mcp.nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;
    
    @Value("${spring.ai.alibaba.mcp.nacos.username:nacos}")
    private String username;
    
    @Value("${spring.ai.alibaba.mcp.nacos.password:nacos}")
    private String password;
    
    @Value("${spring.ai.alibaba.mcp.nacos.namespace:public}")
    private String namespace;

    /**
     * 创建Nacos命名服务
     */
    @Bean
    public NamingService namingService() {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            properties.setProperty("username", username);
            properties.setProperty("password", password);
            properties.setProperty("namespace", namespace);
            
            NamingService namingService = NamingFactory.createNamingService(properties);
            log.info("Nacos NamingService created successfully with server: {}", serverAddr);
            return namingService;
        } catch (Exception e) {
            log.error("Failed to create Nacos NamingService", e);
            throw new RuntimeException("Failed to create Nacos NamingService", e);
        }
    }

    /**
     * 创建Nacos配置服务
     */
    @Bean(name = "mcpRouterConfigService")
    public ConfigService configService() {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            properties.setProperty("username", username);
            properties.setProperty("password", password);
            properties.setProperty("namespace", namespace);
            // 设置appName，这样在配置历史查询时会显示
            properties.setProperty("appName", "mcp-router-v3");
            // 设置用户名，用于配置历史记录中的srcUser字段
            properties.setProperty("srcUser", "mcp-router-v3");
            // 添加应用标识
            properties.setProperty("app", "mcp-router-v3");
            properties.setProperty("applicationName", "mcp-router-v3");
            
            log.info("Creating Nacos ConfigService with properties: serverAddr={}, namespace={}, appName=mcp-router-v3", 
                    serverAddr, namespace);
            
            ConfigService configService = ConfigFactory.createConfigService(properties);
            log.info("Nacos ConfigService created successfully with server:{} and appName: mcp-router-v3", serverAddr);
            return configService;
        } catch (Exception e) {
            log.error("Failed to create Nacos ConfigService", e);
            throw new RuntimeException("Failed to create Nacos ConfigService", e);
        }
    }
    
    /**
     * 创建ObjectMapper Bean
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
} 