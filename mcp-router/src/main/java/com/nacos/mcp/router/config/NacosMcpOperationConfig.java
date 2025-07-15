package com.nacos.mcp.router.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.nacos.mcp.router.service.McpServerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

/**
 * Nacos MCP 操作配置类
 */
@Slf4j
@Configuration
@EnableScheduling
public class NacosMcpOperationConfig implements InitializingBean {

    private final NamingService namingService;
    private final McpServerRegistry mcpServerRegistry;
    private static final String SERVICE_NAME = "mcp-server-v2";
    private static final String GROUP_NAME = "mcp-server";

    public NacosMcpOperationConfig(NamingService namingService, McpServerRegistry mcpServerRegistry) {
        this.namingService = namingService;
        this.mcpServerRegistry = mcpServerRegistry;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化时获取当前所有实例
        List<Instance> instances = namingService.selectInstances(SERVICE_NAME, GROUP_NAME, true);
        instances.forEach(mcpServerRegistry::registerInstance);

        // 添加服务变更监听器
        EventListener listener = event -> {
            if (event instanceof com.alibaba.nacos.api.naming.listener.NamingEvent) {
                com.alibaba.nacos.api.naming.listener.NamingEvent namingEvent = 
                    (com.alibaba.nacos.api.naming.listener.NamingEvent) event;
                mcpServerRegistry.handleInstanceChange(namingEvent.getInstances());
            }
        };

        namingService.subscribe(SERVICE_NAME, GROUP_NAME, listener);
        log.info("Subscribed to MCP Server changes: {}, group: {}", SERVICE_NAME, GROUP_NAME);
    }

    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void checkServerHealth() {
        try {
            List<Instance> instances = namingService.selectInstances(SERVICE_NAME, GROUP_NAME, true);
            mcpServerRegistry.handleInstanceChange(instances);
            log.debug("Completed health check for MCP servers, current healthy instances: {}", instances.size());
        } catch (Exception e) {
            log.error("Failed to check MCP server health", e);
        }
    }
} 