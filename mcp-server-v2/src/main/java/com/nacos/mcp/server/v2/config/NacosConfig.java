package com.nacos.mcp.server.v2.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos配置类
 * 提供NamingService Bean
 */
@Configuration
public class NacosConfig {

    private static final Logger logger = LoggerFactory.getLogger(NacosConfig.class);

    @Value("${spring.ai.mcp.server.nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${spring.ai.mcp.server.nacos.namespace:public}")
    private String namespace;

    @Value("${spring.ai.mcp.server.nacos.group:mcp-server}")
    private String group;

    /**
     * 创建NamingService Bean
     */
    @Bean
    public NamingService namingService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        // properties.setProperty("namespace", namespace);
        
        logger.info("Initializing Nacos NamingService with serverAddr: {}, namespace: {}", serverAddr, namespace);
        return NacosFactory.createNamingService(properties);
    }
} 