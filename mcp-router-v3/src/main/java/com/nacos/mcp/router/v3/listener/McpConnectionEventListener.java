package com.nacos.mcp.router.v3.listener;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.nacos.mcp.router.v3.config.NacosMcpRegistryConfig;
import com.nacos.mcp.router.v3.service.McpClientManager;
import com.nacos.mcp.router.v3.service.McpSseTransportProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接事件监听器 - 已禁用
 * 由于架构简化为纯Nacos服务发现，不再需要监听主动连接事件
 * mcp-router现在通过标准服务发现找到mcp-server并按需建立连接
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectionEventListener {

    private final NamingService namingService;
    private final McpClientManager mcpClientManager;
    private final McpSseTransportProvider sseTransportProvider;
    private final NacosMcpRegistryConfig.McpRegistryProperties registryProperties;
    
    // 连接状态缓存：connectionId -> connectionInfo
    private final Map<String, McpConnectionInfo> connectionCache = new ConcurrentHashMap<>();
    
    // 监听器缓存，用于清理
    private final Map<String, EventListener> eventListeners = new ConcurrentHashMap<>();
    
    private static final String CONNECTION_SERVICE_SUFFIX = "-connection";

    /**
     * 启动时主动订阅所有可能的MCP服务，确保能实时监听服务注册
     */
    @PostConstruct
    public void startListening() {
        log.info("🔔 MCP connection event listener - Starting real-time service discovery mode");
        log.info("ℹ️ mcp-router will discover mcp-servers and monitor real-time service registration");
        
        // Subscribe to MCP services based on configured service groups and actual Nacos instances
        subscribeConfiguredMcpServices();
        
        log.info("✅ MCP router service discovery monitoring enabled");
    }
    
    /**
     * Subscribe to MCP services based on configured service groups and query actual instances from Nacos
     */
    private void subscribeConfiguredMcpServices() {
        List<String> serviceGroups = registryProperties.getServiceGroups();
        if (serviceGroups == null || serviceGroups.isEmpty()) {
            log.warn("⚠️ No service groups configured, falling back to default group: mcp-server");
            serviceGroups = List.of("mcp-server");
        }
        
        log.info("📋 Configured service groups: {}", serviceGroups);
        
        for (String serviceGroup : serviceGroups) {
            try {
                subscribeServiceGroup(serviceGroup);
            } catch (Exception e) {
                log.error("❌ Failed to subscribe to service group: {}", serviceGroup, e);
            }
        }
    }
    
    /**
     * Subscribe to all services in a specific service group by querying Nacos
     */
    private void subscribeServiceGroup(String serviceGroup) {
        try {
            log.info("🔍 Querying all services in group: {}", serviceGroup);
            
            // Query all services in the service group from Nacos
            com.alibaba.nacos.api.naming.pojo.ListView<String> servicesList = 
                namingService.getServicesOfServer(1, Integer.MAX_VALUE, serviceGroup);
            
            if (servicesList == null || servicesList.getData() == null || servicesList.getData().isEmpty()) {
                log.info("📭 No services found in group: {}", serviceGroup);
                return;
            }
            
            List<String> services = servicesList.getData();
            log.info("📋 Found {} services in group {}: {}", services.size(), serviceGroup, services);
            
            // Subscribe to each service in the group
            for (String serviceName : services) {
                try {
                    subscribeServiceChanges(serviceName, serviceGroup);
                    log.info("📡 Successfully subscribed to service changes: {}@{}", serviceName, serviceGroup);
                } catch (Exception e) {
                    log.warn("⚠️ Failed to subscribe to service: {}@{} - {}", serviceName, serviceGroup, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to query services in group: {}", serviceGroup, e);
        }
    }
    
    /**
     * Subscribe to service change events for the specified service
     */
    private void subscribeServiceChanges(String serviceName, String serviceGroup) {
        String subscriptionKey = serviceName + "@" + serviceGroup;
        
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(com.alibaba.nacos.api.naming.listener.Event event) {
                if (event instanceof NamingEvent namingEvent) {
                    handleServiceChangeEvent(serviceName, serviceGroup, namingEvent);
                }
            }
        };
        
        try {
            namingService.subscribe(serviceName, serviceGroup, listener);
            eventListeners.put(subscriptionKey, listener);
            log.info("✅ Successfully subscribed to service changes: {}", subscriptionKey);
        } catch (NacosException e) {
            log.error("❌ Failed to subscribe to service changes: {}", subscriptionKey, e);
            throw new RuntimeException("Failed to subscribe to service changes", e);
        }
    }
    
    /**
     * Handle service change events
     */
    private void handleServiceChangeEvent(String serviceName, String serviceGroup, NamingEvent namingEvent) {
        try {
            List<Instance> instances = namingEvent.getInstances();
            int healthyCount = 0;
            int totalCount = instances.size();
            
            log.info("🔄 [Nacos Service Change] Service: {}@{}", serviceName, serviceGroup);
            
            for (Instance instance : instances) {
                if (instance.isHealthy() && instance.isEnabled()) {
                    healthyCount++;
                    log.info("✅ Healthy instance: {}:{} (weight: {}, ephemeral: {})",
                        instance.getIp(), instance.getPort(), instance.getWeight(), instance.isEphemeral());
                } else {
                    log.info("❌ Unhealthy instance: {}:{} (healthy: {}, enabled: {})",
                        instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
                }
            }
            
            log.info("📊 [Service Statistics] {}@{} - Total instances: {}, Healthy instances: {}", 
                serviceName, serviceGroup, totalCount, healthyCount);
                
            // Log service change, connections will be established on-demand for next request
            if (healthyCount > 0) {
                log.info("🔄 Service change recorded, connections will be established on-demand for next request: {}@{}", serviceName, serviceGroup);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to handle service change event: {}@{}", serviceName, serviceGroup, e);
        }
    }

    /**
     * 清理资源
     */
    @PreDestroy
    public void cleanup() {
        log.info("🧹 Cleaning up MCP connection event listener...");
        
        // 取消所有订阅
        eventListeners.forEach((key, listener) -> {
            try {
                String[] parts = key.split("@");
                if (parts.length == 2) {
                    namingService.unsubscribe(parts[0], parts[1], listener);
                    log.debug("Unsubscribed from: {}", key);
                }
            } catch (Exception e) {
                log.warn("Failed to unsubscribe from: {}", key, e);
            }
        });
        
        eventListeners.clear();
        connectionCache.clear();
        
        log.info("✅ MCP connection event listener cleanup completed");
    }

    // 保留内部类定义以避免编译错误
    public static class McpConnectionInfo {
        private final String connectionId;
        private final String serverName;
        private final String serverIp;
        private final int serverPort;
        private final boolean connected;
        private final long lastUpdate;
        
        public McpConnectionInfo(String connectionId, String serverName, String serverIp, 
                               int serverPort, boolean connected, long lastUpdate) {
            this.connectionId = connectionId;
            this.serverName = serverName;
            this.serverIp = serverIp;
            this.serverPort = serverPort;
            this.connected = connected;
            this.lastUpdate = lastUpdate;
        }
        
        // Getters
        public String getConnectionId() { return connectionId; }
        public String getServerName() { return serverName; }
        public String getServerIp() { return serverIp; }
        public int getServerPort() { return serverPort; }
        public boolean isConnected() { return connected; }
        public long getLastUpdate() { return lastUpdate; }
    }
} 