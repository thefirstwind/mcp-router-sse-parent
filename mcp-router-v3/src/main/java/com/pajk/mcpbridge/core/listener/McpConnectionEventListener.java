package com.pajk.mcpbridge.core.listener;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.pajk.mcpbridge.core.config.NacosMcpRegistryConfig;
import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.core.service.McpClientManager;
import com.pajk.mcpbridge.core.service.McpSseTransportProvider;
import com.pajk.mcpbridge.persistence.service.McpServerPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
@Service
@RequiredArgsConstructor
public class McpConnectionEventListener {

    private final static Logger log = LoggerFactory.getLogger(McpConnectionEventListener.class);

    private final NamingService namingService;
    private final McpClientManager mcpClientManager;
    private final McpSseTransportProvider sseTransportProvider;
    private final NacosMcpRegistryConfig.McpRegistryProperties registryProperties;
    
    // 持久化服务（可选依赖）
    @Autowired(required = false)
    private McpServerPersistenceService persistenceService;
    
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
        
        // 启动后同步一次 Nacos 和数据库的状态
        syncNacosStateToDatabase();
        
        log.info("✅ MCP router service discovery monitoring enabled");
    }
    
    /**
     * 同步 Nacos 状态到数据库
     * 启动时将 Nacos 中所有服务实例同步到数据库
     */
    private void syncNacosStateToDatabase() {
        if (persistenceService == null) {
            log.warn("⚠️ Persistence service is not available, skipping Nacos state sync");
            return;
        }
        
        log.info("🔄 Starting Nacos to database state synchronization...");
        
        try {
            int totalServices = 0;
            int totalInstances = 0;
            int healthyInstances = 0;
            
            // 遍历所有配置的服务组
            for (String serviceGroup : registryProperties.getServiceGroups()) {
                try {
                    com.alibaba.nacos.api.naming.pojo.ListView<String> servicesList = 
                        namingService.getServicesOfServer(1, Integer.MAX_VALUE, serviceGroup);
                    
                    if (servicesList != null && servicesList.getData() != null) {
                        totalServices += servicesList.getData().size();
                        log.info("📋 Found {} services in group {}", servicesList.getData().size(), serviceGroup);
                        
                        for (String serviceName : servicesList.getData()) {
                            try {
                                List<Instance> instances = namingService.getAllInstances(serviceName, serviceGroup);
                                
                                if (instances.isEmpty()) {
                                    log.info("📭 Service {} has no instances, marking ephemeral nodes as unhealthy", serviceName);
                                    persistenceService.markEphemeralInstancesUnhealthy(serviceName, serviceGroup);
                                } else {
                                    // 关键修复：将所有实例持久化到数据库
                                    log.info("💾 Syncing {} instances for service: {}@{}", instances.size(), serviceName, serviceGroup);
                                    
                                    // 收集当前 Nacos 中的所有实例
                                    java.util.Set<String> nacosInstanceKeys = new java.util.HashSet<>();
                                    
                                    for (Instance instance : instances) {
                                        String instanceKey = instance.getIp() + ":" + instance.getPort();
                                        nacosInstanceKeys.add(instanceKey);
                                        totalInstances++;
                                        
                                        if (instance.isHealthy() && instance.isEnabled()) {
                                            healthyInstances++;
                                        }
                                        
                                        // 持久化实例到数据库
                                        persistInstanceSyncToDatabase(serviceName, serviceGroup, instance);
                                        
                                        log.debug("  - Instance: {}:{} (healthy: {}, enabled: {}, ephemeral: {})",
                                            instance.getIp(), instance.getPort(), 
                                            instance.isHealthy(), instance.isEnabled(), instance.isEphemeral());
                                    }
                                    
                                    // 标记数据库中不在 Nacos 列表中的临时节点为不健康
                                    markOfflineEphemeralInstances(serviceName, serviceGroup, nacosInstanceKeys);
                                }
                            } catch (Exception e) {
                                log.warn("⚠️ Failed to sync service {}: {}", serviceName, e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to sync service group {}: {}", serviceGroup, e.getMessage());
                }
            }
            
            // 方法2: 从数据库侧检查 - 验证数据库中健康的临时节点是否还在 Nacos 中
            persistenceService.verifyAndMarkOfflineEphemeralNodes();
            
            log.info("✅ Nacos to database state synchronization completed");
            log.info("📊 Sync summary: {} services, {} instances ({} healthy)", totalServices, totalInstances, healthyInstances);
            
        } catch (Exception e) {
            log.error("❌ Failed to sync Nacos state to database", e);
        }
    }
    
    /**
     * 同步持久化实例信息到数据库（启动时同步，阻塞执行确保完整性）
     */
    private void persistInstanceSyncToDatabase(String serviceName, String serviceGroup, Instance instance) {
        try {
            // 构建 McpServerInfo
            McpServerInfo serverInfo = new McpServerInfo();
            serverInfo.setName(serviceName);
            serverInfo.setServiceGroup(serviceGroup);
            serverInfo.setIp(instance.getIp());
            serverInfo.setHost(instance.getIp());
            serverInfo.setPort(instance.getPort());
            serverInfo.setWeight(instance.getWeight());
            serverInfo.setHealthy(instance.isHealthy());
            serverInfo.setEnabled(instance.isEnabled());
            serverInfo.setEphemeral(instance.isEphemeral());
            serverInfo.setMetadata(instance.getMetadata());
            
            // 从metadata中提取SSE端点信息
            if (instance.getMetadata() != null) {
                String sseEndpoint = instance.getMetadata().getOrDefault("sseEndpoint", "/sse");
                serverInfo.setSseEndpoint(sseEndpoint);
            }
            
            // 同步持久化（启动时执行，确保完整性）
            persistenceService.persistServerRegistration(serverInfo);
            
        } catch (Exception e) {
            log.error("❌ Error syncing instance to database: {}:{}", 
                instance.getIp(), instance.getPort(), e);
        }
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
            
            if (instances.isEmpty()) {
                // 服务实例列表为空，可能是临时节点下线
                log.warn("⚠️ No instances found for service: {}@{} - all instances may be offline", serviceName, serviceGroup);
                
                // 标记数据库中该服务的临时节点为不健康（如果启用了持久化）
                handleAllInstancesOffline(serviceName, serviceGroup);
            } else {
                // 收集当前 Nacos 中的所有实例（用于对比）
                java.util.Set<String> nacosInstanceKeys = new java.util.HashSet<>();
                
                for (Instance instance : instances) {
                    String instanceKey = instance.getIp() + ":" + instance.getPort();
                    nacosInstanceKeys.add(instanceKey);
                    
                    // 持久化所有实例的状态到数据库（包括不健康的）
                    persistInstanceToDatabase(serviceName, serviceGroup, instance);
                    
                    if (instance.isHealthy() && instance.isEnabled()) {
                        healthyCount++;
                        log.info("✅ Healthy instance: {}:{} (weight: {}, ephemeral: {})",
                            instance.getIp(), instance.getPort(), instance.getWeight(), instance.isEphemeral());
                    } else {
                        log.info("❌ Unhealthy instance: {}:{} (healthy: {}, enabled: {})",
                            instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
                    }
                }
                
                // 标记数据库中不在 Nacos 列表中的临时节点为不健康
                markOfflineEphemeralInstances(serviceName, serviceGroup, nacosInstanceKeys);
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
     * 处理所有实例下线的情况（临时节点）
     */
    private void handleAllInstancesOffline(String serviceName, String serviceGroup) {
        if (persistenceService == null) {
            log.warn("⚠️ Persistence service is not available, skipping database update");
            return;
        }
        
        try {
            log.info("🗑️ Marking ephemeral instances as unhealthy for service: {}@{}", serviceName, serviceGroup);
            
            // 通知持久化服务标记该服务的临时节点为不健康
            Mono.fromRunnable(() -> persistenceService.markEphemeralInstancesUnhealthy(serviceName, serviceGroup))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    null,
                    error -> log.error("❌ Failed to mark ephemeral instances as unhealthy: {}@{} - {}", 
                        serviceName, serviceGroup, error.getMessage()),
                    () -> log.info("✅ Ephemeral instances marked as unhealthy: {}@{}", serviceName, serviceGroup)
                );
                
        } catch (Exception e) {
            log.error("❌ Failed to handle all instances offline: {}@{}", serviceName, serviceGroup, e);
        }
    }
    
    /**
     * 标记数据库中不在 Nacos 列表中的临时节点为不健康
     */
    private void markOfflineEphemeralInstances(String serviceName, String serviceGroup, java.util.Set<String> nacosInstanceKeys) {
        if (persistenceService == null) {
            return;
        }
        
        Mono.fromRunnable(() -> {
            try {
                persistenceService.markOfflineEphemeralInstancesNotInNacos(serviceName, serviceGroup, nacosInstanceKeys);
            } catch (Exception e) {
                log.error("❌ Failed to mark offline ephemeral instances: {}@{} - {}", 
                    serviceName, serviceGroup, e.getMessage());
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
    }
    
    /**
     * 持久化实例信息到数据库
     */
    private void persistInstanceToDatabase(String serviceName, String serviceGroup, Instance instance) {
        if (persistenceService == null) {
            log.warn("⚠️ Persistence service is not available, skipping database persistence");
            return;
        }
        
        try {
            // 构建 McpServerInfo
            McpServerInfo serverInfo = new McpServerInfo();
            serverInfo.setName(serviceName);
            serverInfo.setServiceGroup(serviceGroup);
            serverInfo.setIp(instance.getIp());
            serverInfo.setHost(instance.getIp());
            serverInfo.setPort(instance.getPort());
            serverInfo.setWeight(instance.getWeight());
            
            // 关键修复：对于临时节点（ephemeral=true），如果它出现在 Nacos 的实例列表中，
            // 就说明服务进程正在运行并已注册到 Nacos，应该被视为健康和启用的。
            // Nacos 可能会暂时报告 healthy=false（例如健康检查延迟），但只要实例在列表中，
            // 就说明服务是活跃的。
            boolean isEphemeral = instance.isEphemeral();
            if (isEphemeral) {
                // 临时节点：出现在列表中 = 服务在运行 = 应该是健康的
                // 使用 Nacos 的 enabled 状态，但强制 healthy=true
                serverInfo.setHealthy(true);  // 强制健康状态为 true
                serverInfo.setEnabled(instance.isEnabled());  // 保留 Nacos 的 enabled 状态
                log.info("✅ Ephemeral instance in Nacos list, marking as healthy: {}:{} (nacos_healthy={}, nacos_enabled={})",
                    instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
            } else {
                // 持久化节点：使用 Nacos 报告的原始状态
                serverInfo.setHealthy(instance.isHealthy());
                serverInfo.setEnabled(instance.isEnabled());
                log.info("ℹ️ Persistent instance, using Nacos status: {}:{} (healthy={}, enabled={})",
                    instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
            }
            
            serverInfo.setEphemeral(instance.isEphemeral());
            serverInfo.setMetadata(instance.getMetadata());
            
            // 从metadata中提取SSE端点信息
            if (instance.getMetadata() != null) {
                String sseEndpoint = instance.getMetadata().getOrDefault("sseEndpoint", "/sse");
                serverInfo.setSseEndpoint(sseEndpoint);
            }
            
            log.info("💾 Attempting to persist instance to database: {}@{} - {}:{} (healthy={}, enabled={}, ephemeral={})", 
                serviceName, serviceGroup, instance.getIp(), instance.getPort(), 
                serverInfo.isHealthy(), serverInfo.getEnabled(), serverInfo.isEphemeral());
            
            // 异步持久化到数据库
            Mono.fromRunnable(() -> persistenceService.persistServerRegistration(serverInfo))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    null,
                    error -> log.error("❌ Failed to persist instance to database: {}:{} - {}", 
                        instance.getIp(), instance.getPort(), error.getMessage()),
                    () -> log.info("✅ Instance persisted to database: {}:{} with healthy={}, enabled={}", 
                        instance.getIp(), instance.getPort(), serverInfo.isHealthy(), serverInfo.getEnabled())
                );
                
        } catch (Exception e) {
            log.error("❌ Error persisting instance to database: {}:{}", 
                instance.getIp(), instance.getPort(), e);
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