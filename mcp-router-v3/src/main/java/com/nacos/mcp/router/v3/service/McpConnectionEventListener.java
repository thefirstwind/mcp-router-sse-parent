package com.nacos.mcp.router.v3.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 连接事件监听器
 * 监听 Nacos 中 MCP Server 的连接状态变化，实现事件驱动的连接感知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectionEventListener {

    private final NamingService namingService;
    private final McpClientManager mcpClientManager;
    private final McpSseTransportProvider sseTransportProvider;
    
    // 连接状态缓存：connectionId -> connectionInfo
    private final Map<String, McpConnectionInfo> connectionCache = new ConcurrentHashMap<>();
    
    // 监听器缓存，用于清理
    private final Map<String, EventListener> eventListeners = new ConcurrentHashMap<>();
    
    private static final String CONNECTION_SERVICE_SUFFIX = "-connection";
    private static final String SERVICE_GROUP = "mcp-server";

    /**
     * 启动时订阅所有 MCP Server 连接事件
     */
    @PostConstruct
    public void startListening() {
        log.info("🔔 Starting MCP connection event listener...");
        
        // 发现现有的连接服务并订阅
        discoverAndSubscribeExistingConnections();
        
        // 定期检查新的连接服务
        setupPeriodicDiscovery();
        
        log.info("✅ MCP connection event listener started");
    }

    /**
     * 发现并订阅现有的连接服务
     */
    private void discoverAndSubscribeExistingConnections() {
        try {
            // 获取所有服务列表
            var servicesList = namingService.getServicesOfServer(1, Integer.MAX_VALUE, SERVICE_GROUP);
            
            if (servicesList != null && servicesList.getData() != null) {
                servicesList.getData().stream()
                        .filter(serviceName -> serviceName.endsWith(CONNECTION_SERVICE_SUFFIX))
                        .forEach(this::subscribeToConnectionService);
                        
                log.info("📋 Discovered {} existing connection services", 
                        servicesList.getData().size());
            }
        } catch (Exception e) {
            log.error("Failed to discover existing connection services", e);
        }
    }

    /**
     * 订阅指定的连接服务
     */
    private void subscribeToConnectionService(String connectionServiceName) {
        try {
            String subscriptionKey = connectionServiceName + "@" + SERVICE_GROUP;
            
            if (eventListeners.containsKey(subscriptionKey)) {
                log.debug("Already subscribed to: {}", subscriptionKey);
                return;
            }

            EventListener listener = new EventListener() {
                @Override
                public void onEvent(com.alibaba.nacos.api.naming.listener.Event event) {
                    if (event instanceof NamingEvent namingEvent) {
                        handleConnectionEvent(connectionServiceName, namingEvent);
                    }
                }
            };

            namingService.subscribe(connectionServiceName, SERVICE_GROUP, listener);
            eventListeners.put(subscriptionKey, listener);
            
            log.info("🔔 Subscribed to connection service: {}", connectionServiceName);
            
            // 立即获取当前状态
            var instances = namingService.selectInstances(connectionServiceName, SERVICE_GROUP, true);
            for (Instance instance : instances) {
                handleConnectionInstance(connectionServiceName, instance, true);
            }
            
        } catch (Exception e) {
            log.error("Failed to subscribe to connection service: {}", connectionServiceName, e);
        }
    }

    /**
     * 处理连接事件
     */
    private void handleConnectionEvent(String connectionServiceName, NamingEvent namingEvent) {
        log.info("🔔 Received connection event for service: {}, instances: {}", 
                connectionServiceName, namingEvent.getInstances().size());

        for (Instance instance : namingEvent.getInstances()) {
            boolean isConnected = instance.isHealthy() && instance.isEnabled();
            handleConnectionInstance(connectionServiceName, instance, isConnected);
        }
    }

    /**
     * 处理连接实例变化
     */
    private void handleConnectionInstance(String connectionServiceName, Instance instance, boolean isConnected) {
        Map<String, String> metadata = instance.getMetadata();
        if (metadata == null) {
            log.warn("⚠️ No metadata found for connection instance: {}", connectionServiceName);
            return;
        }

        String connectionId = metadata.get("connectionId");
        String serverName = metadata.get("serverName");
        String lastUpdate = metadata.get("lastUpdate");
        
        if (connectionId == null || serverName == null) {
            log.warn("⚠️ Missing required metadata for connection: {}", connectionServiceName);
            return;
        }

        McpConnectionInfo connectionInfo = McpConnectionInfo.builder()
                .connectionId(connectionId)
                .serverName(serverName)
                .serviceName(connectionServiceName)
                .ip(instance.getIp())
                .port(instance.getPort())
                .connected(isConnected)
                .lastUpdate(lastUpdate != null ? Long.parseLong(lastUpdate) : System.currentTimeMillis())
                .metadata(metadata)
                .build();

        // 处理连接状态变化
        handleConnectionStateChange(connectionInfo);
    }

    /**
     * 处理连接状态变化
     */
    private void handleConnectionStateChange(McpConnectionInfo connectionInfo) {
        String connectionId = connectionInfo.getConnectionId();
        McpConnectionInfo previousInfo = connectionCache.get(connectionId);
        
        if (previousInfo == null || previousInfo.isConnected() != connectionInfo.isConnected()) {
            // 连接状态发生变化
            if (connectionInfo.isConnected()) {
                handleConnectionEstablished(connectionInfo);
            } else {
                handleConnectionLost(connectionInfo);
            }
        } else {
            // 连接状态未变化，但更新元数据
            connectionCache.put(connectionId, connectionInfo);
            log.debug("🔄 Updated connection info for: {}", connectionInfo.getServerName());
        }
    }

    /**
     * 处理连接建立
     */
    private void handleConnectionEstablished(McpConnectionInfo connectionInfo) {
        log.info("🟢 MCP Server connected: {} ({}:{})", 
                connectionInfo.getServerName(), 
                connectionInfo.getIp(), 
                connectionInfo.getPort());

        connectionCache.put(connectionInfo.getConnectionId(), connectionInfo);
        
        // 通知相关组件连接已建立
        notifyConnectionEstablished(connectionInfo);
    }

    /**
     * 处理连接断开
     */
    private void handleConnectionLost(McpConnectionInfo connectionInfo) {
        log.info("🔴 MCP Server disconnected: {} ({}:{})", 
                connectionInfo.getServerName(), 
                connectionInfo.getIp(), 
                connectionInfo.getPort());

        McpConnectionInfo previousInfo = connectionCache.remove(connectionInfo.getConnectionId());
        
        // 通知相关组件连接已断开
        notifyConnectionLost(connectionInfo, previousInfo);
    }

    /**
     * 通知连接建立
     */
    private void notifyConnectionEstablished(McpConnectionInfo connectionInfo) {
        try {
            // 1. 清理可能存在的旧客户端连接
            mcpClientManager.closeClient(connectionInfo.getServerName());
            
            // 2. 发布连接建立事件（可以用于其他组件监听）
            log.debug("📡 Publishing connection established event for: {}", connectionInfo.getServerName());
            
            // 3. 如果需要，可以预热连接
            // 这里可以添加连接预热逻辑
            
        } catch (Exception e) {
            log.error("Error notifying connection established for: {}", connectionInfo.getServerName(), e);
        }
    }

    /**
     * 通知连接断开
     */
    private void notifyConnectionLost(McpConnectionInfo connectionInfo, McpConnectionInfo previousInfo) {
        try {
            // 1. 清理客户端连接
            mcpClientManager.closeClient(connectionInfo.getServerName());
            
            // 2. 清理相关的 SSE 会话
            String sessionPattern = connectionInfo.getServerName() + "-*";
            sseTransportProvider.getAllSessions().entrySet().stream()
                    .filter(entry -> entry.getValue().getClientId().startsWith(connectionInfo.getServerName()))
                    .forEach(entry -> {
                        try {
                            sseTransportProvider.closeSession(entry.getKey()).subscribe();
                            log.debug("🧹 Cleaned up SSE session: {}", entry.getKey());
                        } catch (Exception e) {
                            log.warn("Failed to close SSE session: {}", entry.getKey(), e);
                        }
                    });
            
            // 3. 发布连接断开事件
            log.debug("📡 Publishing connection lost event for: {}", connectionInfo.getServerName());
            
        } catch (Exception e) {
            log.error("Error notifying connection lost for: {}", connectionInfo.getServerName(), e);
        }
    }

    /**
     * 定期发现新的连接服务
     */
    private void setupPeriodicDiscovery() {
        reactor.core.publisher.Flux.interval(java.time.Duration.ofMinutes(1))
                .doOnNext(tick -> {
                    try {
                        discoverAndSubscribeExistingConnections();
                    } catch (Exception e) {
                        log.warn("Periodic discovery failed", e);
                    }
                })
                .subscribe();
    }

    /**
     * 获取当前所有连接信息
     */
    public Map<String, McpConnectionInfo> getAllConnections() {
        return new ConcurrentHashMap<>(connectionCache);
    }

    /**
     * 获取指定服务器的连接信息
     */
    public McpConnectionInfo getConnection(String serverName) {
        return connectionCache.values().stream()
                .filter(info -> serverName.equals(info.getServerName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查服务器是否已连接
     */
    public boolean isServerConnected(String serverName) {
        McpConnectionInfo info = getConnection(serverName);
        return info != null && info.isConnected();
    }

    /**
     * 应用关闭时清理监听器
     */
    @PreDestroy
    public void cleanup() {
        log.info("🧹 Cleaning up MCP connection event listeners...");
        
        eventListeners.forEach((subscriptionKey, listener) -> {
            try {
                String[] parts = subscriptionKey.split("@");
                if (parts.length == 2) {
                    namingService.unsubscribe(parts[0], parts[1], listener);
                    log.debug("✅ Unsubscribed from: {}", subscriptionKey);
                }
            } catch (Exception e) {
                log.warn("Failed to unsubscribe from: {}", subscriptionKey, e);
            }
        });
        
        eventListeners.clear();
        connectionCache.clear();
        
        log.info("✅ MCP connection event listeners cleaned up");
    }

    /**
     * MCP 连接信息
     */
    @lombok.Data
    @lombok.Builder
    public static class McpConnectionInfo {
        private String connectionId;
        private String serverName;
        private String serviceName;
        private String ip;
        private int port;
        private boolean connected;
        private long lastUpdate;
        private Map<String, String> metadata;
    }
} 