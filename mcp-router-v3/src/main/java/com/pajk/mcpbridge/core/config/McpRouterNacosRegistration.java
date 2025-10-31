package com.pajk.mcpbridge.core.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP Router Nacos 实例注册
 * 将 mcp-router-v3 注册到 Nacos，让 mcp-server 能够通过服务发现找到它
 * 同时 mcp-router-v3 也会监听 Nacos 事件来感知其他服务的状态变化
 */
@Component
@RequiredArgsConstructor
public class McpRouterNacosRegistration {

    private final static Logger log = LoggerFactory.getLogger(McpRouterNacosRegistration.class);
    private final NamingService namingService;

    @Value("${server.port:8052}")
    private int serverPort;

    @Value("${spring.application.name:mcp-router-v3}")
    private String applicationName;

    @Value("${spring.ai.alibaba.mcp.nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    /**
     * 应用启动完成后注册到 Nacos
     * 让其他 MCP 服务能够通过服务发现找到这个路由器
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerToNacos() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            
            log.info("🚀 Registering mcp-router-v3 to Nacos for service discovery: {}:{}", localIp, serverPort);
            
            // 创建实例
            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(serverPort);
            instance.setHealthy(true);
            instance.setEnabled(true);
            instance.setEphemeral(true);  // 设置为临时实例，崩溃后自动清理
            
            // 设置元数据，标识这是一个路由器
            Map<String, String> metadata = new HashMap<>();
            metadata.put("type", "mcp-router");
            metadata.put("version", "v3");
            metadata.put("capabilities", "routing,load-balancing,event-driven");
            metadata.put("role", "router");
            metadata.put("acceptConnections", "true");
            metadata.put("startTime", String.valueOf(System.currentTimeMillis()));
            
            instance.setMetadata(metadata);
            
            // 注册实例 - 使用标准化的服务名，让 mcp-server 能够发现
            String routerServiceName = "mcp-router-v3";
            namingService.registerInstance(routerServiceName, serviceGroup, instance);
            
            log.info("✅ Successfully registered mcp-router-v3 to Nacos: service={}, group={}, ip={}, port={}", 
                    routerServiceName, serviceGroup, localIp, serverPort);
            log.info("📢 MCP servers can now discover this router via Nacos service discovery");
                    
        } catch (Exception e) {
            log.error("❌ Failed to register mcp-router-v3 to Nacos", e);
        }
    }

    /**
     * 应用关闭时注销实例
     */
    @EventListener(ContextClosedEvent.class)
    public void deregisterFromNacos() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String routerServiceName = "mcp-router-v3";
            
            namingService.deregisterInstance(routerServiceName, serviceGroup, localIp, serverPort);
            
            log.info("✅ Successfully deregistered mcp-router-v3 from Nacos");
            
        } catch (Exception e) {
            log.error("❌ Failed to deregister mcp-router-v3 from Nacos", e);
        }
    }
} 