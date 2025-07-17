package com.nacos.mcp.router.v3.config;

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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Properties;
import java.util.List;
import java.util.Arrays;

/**
 * Nacos MCP注册配置类
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NacosMcpRegistryConfig.McpRegistryProperties.class)
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
     * MCP 注册属性配置
     */
    @ConfigurationProperties(prefix = "spring.ai.alibaba.mcp.nacos.registry")
    public static class McpRegistryProperties {
        /**
         * 是否启用注册
         */
        private boolean enabled = true;
        
        /**
         * 服务组列表 - 用于服务发现时查询多个组
         */
        private List<String> serviceGroups = Arrays.asList("mcp-server", "DEFAULT_GROUP");
        
        /**
         * 当前服务注册到的组
         */
        private String serviceGroup = "mcp-server";
        
        /**
         * 服务名称
         */
        private String serviceName;
        
        /**
         * 应用名称
         */
        private String appName;
        
        /**
         * 源用户
         */
        private String srcUser;

        // Getter 和 Setter 方法
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getServiceGroups() {
            return serviceGroups;
        }

        public void setServiceGroups(List<String> serviceGroups) {
            this.serviceGroups = serviceGroups;
        }

        public String getServiceGroup() {
            return serviceGroup;
        }

        public void setServiceGroup(String serviceGroup) {
            this.serviceGroup = serviceGroup;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getSrcUser() {
            return srcUser;
        }

        public void setSrcUser(String srcUser) {
            this.srcUser = srcUser;
        }
    }

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
    @Bean
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