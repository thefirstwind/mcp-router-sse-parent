package com.nacos.mcp.server.v3.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Properties;

/**
 * Nacos配置类
 * 提供NamingService Bean，支持新的事件驱动连接架构
 */
@Configuration
public class NacosConfig {

    private static final Logger logger = LoggerFactory.getLogger(NacosConfig.class);

    @Value("${spring.ai.alibaba.mcp.nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${spring.ai.alibaba.mcp.nacos.namespace:public}")
    private String namespace;

    @Value("${spring.ai.alibaba.mcp.nacos.username:nacos}")
    private String username;

    @Value("${spring.ai.alibaba.mcp.nacos.password:nacos}")
    private String password;

    /**
     * 创建NamingService Bean
     */
    @Bean
    public NamingService namingService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("namespace", namespace);
        properties.setProperty("username", username);
        properties.setProperty("password", password);

        logger.info("Initializing Nacos NamingService with serverAddr: {}, namespace: {}, username: {}", 
                serverAddr, namespace, username);
        
        NamingService namingService = NacosFactory.createNamingService(properties);
        logger.info("✅ Nacos NamingService created successfully");
        return namingService;
    }

    /**
     * 创建 WebClient.Builder Bean，用于连接到 mcp-router-v3
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024));
    }
}