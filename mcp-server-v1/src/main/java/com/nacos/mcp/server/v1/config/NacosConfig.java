package com.nacos.mcp.server.v1.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos配置类
 */
@Configuration
public class NacosConfig {

    @Value("${spring.cloud.nacos.discovery.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.discovery.username:nacos}")
    private String username;

    @Value("${spring.cloud.nacos.discovery.password:nacos}")
    private String password;

    @Value("${spring.cloud.nacos.discovery.namespace:}")
    private String namespace;

    @Bean
    public NamingService namingService() throws Exception {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("username", username);
        properties.put("password", password);
        if (namespace != null && !namespace.isEmpty()) {
            properties.put("namespace", namespace);
        }
        
        return NacosFactory.createNamingService(properties);
    }
} 