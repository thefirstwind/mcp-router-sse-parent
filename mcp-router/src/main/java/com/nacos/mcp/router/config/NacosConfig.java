package com.nacos.mcp.router.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Properties;

@Configuration
public class NacosConfig {

    private static final Logger logger = LoggerFactory.getLogger(NacosConfig.class);

    @Value("${spring.cloud.nacos.discovery.server-addr}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.discovery.username:nacos}")
    private String username;

    @Value("${spring.cloud.nacos.discovery.password:nacos}")
    private String password;

    @Value("${spring.cloud.nacos.discovery.namespace:}")
    private String namespace;

    @Bean
    public NamingService namingService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("username", username);
        properties.setProperty("password", password);
        if (namespace != null && !namespace.isEmpty()) {
            properties.setProperty("namespace", namespace);
        }
        
        logger.info("Creating NamingService with serverAddr: {}, username: {}, namespace: {}", 
                serverAddr, username, namespace);
        
        return NacosFactory.createNamingService(properties);
    }


} 