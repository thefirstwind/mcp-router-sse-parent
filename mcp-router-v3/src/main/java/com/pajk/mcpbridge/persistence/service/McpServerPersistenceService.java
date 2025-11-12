package com.pajk.mcpbridge.persistence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.persistence.entity.McpServer;
import com.pajk.mcpbridge.persistence.mapper.McpServerMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP服务器注册信息持久化服务
 * 
 * 功能：
 * 1. 同步持久化服务器注册/注销信息
 * 2. 定期更新服务器心跳状态
 * 3. 自动清理过期的离线服务器
 * 4. 提供服务器信息查询接口
 */
@Service
@RequiredArgsConstructor
public class McpServerPersistenceService {
    
    private static final Logger log = LoggerFactory.getLogger(McpServerPersistenceService.class);
    
    private final McpServerMapper mcpServerMapper;
    private final ObjectMapper objectMapper;
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("✅ McpServerPersistenceService initialized successfully");
        log.info("📊 Database persistence is ENABLED for MCP server registration");
    }
    
    // 统计指标
    private final AtomicLong totalRegistrations = new AtomicLong(0);
    private final AtomicLong totalDeregistrations = new AtomicLong(0);
    private final AtomicLong totalHeartbeats = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    
    /**
     * 持久化服务器注册信息（同步操作）
     * 注册操作频率低，可以同步持久化确保一致性
     */
    public void persistServerRegistration(McpServerInfo serverInfo) {
        try {
            String serverKey = buildServerKey(serverInfo);
            String metadata = serializeMetadata(serverInfo.getMetadata());
            
            log.info("🔍 Building McpServer entity for: {} ({}:{})", 
                serverInfo.getName(), serverInfo.getHost(), serverInfo.getPort());
            
            McpServer server = McpServer.builder()
                .serverKey(serverKey)
                .serverName(serverInfo.getName())
                .serverGroup(serverInfo.getServiceGroup() != null ? serverInfo.getServiceGroup() : "mcp-server")
                .namespaceId(serverInfo.getNamespaceId() != null ? serverInfo.getNamespaceId() : "public")
                .host(serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp())
                .port(serverInfo.getPort())
                .sseEndpoint(serverInfo.getSseEndpoint() != null ? serverInfo.getSseEndpoint() : "/sse")
                .healthEndpoint("/health")  // healthEndpoint
                .metadata(metadata)
                .healthy(serverInfo.isHealthy())
                .enabled(serverInfo.getEnabled() != null ? serverInfo.getEnabled() : true)
                .weight(serverInfo.getWeight())
                .ephemeral(serverInfo.isEphemeral())
                .clusterName("DEFAULT")
                .version(serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0")
                .protocol(serverInfo.getProtocol() != null ? serverInfo.getProtocol() : "mcp-sse")
                .totalRequests(0L)
                .totalErrors(0L)
                .lastHealthCheck(LocalDateTime.now())
                .registeredAt(LocalDateTime.now())
                .build();
            
            int rows = mcpServerMapper.insertOrUpdate(server);
            
            if (rows > 0) {
                totalRegistrations.incrementAndGet();
                log.info("✅ Server persisted to database: {} ({}:{}) - healthy={}, enabled={}, rows={}", 
                    serverInfo.getName(), server.getHost(), server.getPort(), 
                    serverInfo.isHealthy(), serverInfo.getEnabled(), rows);
            } else {
                log.warn("⚠️ InsertOrUpdate returned 0 rows for: {} ({}:{})", 
                    serverInfo.getName(), server.getHost(), server.getPort());
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to persist server registration: {} - {}", 
                serverInfo.getName(), e.getMessage(), e);
        }
    }
    
    /**
     * 持久化服务器注销信息
     */
    public void persistServerDeregistration(String serverKey) {
        try {
            int rows = mcpServerMapper.markOffline(serverKey, LocalDateTime.now());
            
            if (rows > 0) {
                totalDeregistrations.incrementAndGet();
                log.debug("✅ Server deregistration persisted: {}", serverKey);
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to persist server deregistration: {} - {}", 
                serverKey, e.getMessage());
        }
    }
    
    /**
     * 更新服务器健康检查时间
     */
    public void updateServerHealthCheck(String serverKey) {
        try {
            int rows = mcpServerMapper.updateHealthCheck(serverKey, LocalDateTime.now());
            
            if (rows > 0) {
                totalHeartbeats.incrementAndGet();
                log.trace("🫀 Server health check updated: {}", serverKey);
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.debug("Failed to update server health check: {} - {}", 
                serverKey, e.getMessage());
        }
    }
    
    /**
     * 更新服务器健康状态
     */
    public void updateServerHealthStatus(String serverKey, boolean healthy) {
        try {
            int rows = mcpServerMapper.updateHealthStatus(serverKey, healthy, LocalDateTime.now());
            
            if (rows > 0) {
                log.debug("✅ Server health status updated: {} -> {}", 
                    serverKey, healthy ? "HEALTHY" : "UNHEALTHY");
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to update server health status: {} - {}", 
                serverKey, e.getMessage());
        }
    }
    
    /**
     * 标记服务的所有临时节点为不健康
     * 当 Nacos 检测到服务的所有实例都下线时调用（临时节点被完全移除）
     */
    public void markEphemeralInstancesUnhealthy(String serviceName, String serviceGroup) {
        try {
            int rows = mcpServerMapper.markEphemeralInstancesUnhealthyByService(serviceName, LocalDateTime.now());
            
            if (rows > 0) {
                log.info("✅ Marked {} ephemeral instances as unhealthy for service: {}@{}", 
                    rows, serviceName, serviceGroup);
            } else {
                log.debug("ℹ️ No ephemeral instances found to mark as unhealthy for service: {}@{}", 
                    serviceName, serviceGroup);
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to mark ephemeral instances as unhealthy: {}@{} - {}", 
                serviceName, serviceGroup, e.getMessage());
        }
    }
    
    /**
     * 标记数据库中不在 Nacos 列表中的临时节点为不健康
     * 当 Nacos 中某些实例下线但还有其他实例在线时调用
     */
    public void markOfflineEphemeralInstancesNotInNacos(String serviceName, String serviceGroup, java.util.Set<String> nacosInstanceKeys) {
        try {
            // 1. 查询数据库中该服务的所有临时节点
            List<McpServer> dbServers = mcpServerMapper.selectByServiceNameAndGroup(serviceName, serviceGroup);
            
            int markedCount = 0;
            for (McpServer server : dbServers) {
                // 只处理临时节点
                if (server.getEphemeral() != null && server.getEphemeral()) {
                    String instanceKey = server.getHost() + ":" + server.getPort();
                    
                    // 如果该实例不在 Nacos 列表中，标记为不健康
                    if (!nacosInstanceKeys.contains(instanceKey)) {
                        log.info("📉 Marking offline ephemeral instance as unhealthy: {}@{} - {}", 
                            serviceName, serviceGroup, instanceKey);
                        
                        int rows = mcpServerMapper.updateHealthStatus(
                            server.getServerKey(), 
                            false, 
                            LocalDateTime.now()
                        );
                        
                        if (rows > 0) {
                            markedCount++;
                        }
                    }
                }
            }
            
            if (markedCount > 0) {
                log.info("✅ Marked {} offline ephemeral instances as unhealthy for service: {}@{}", 
                    markedCount, serviceName, serviceGroup);
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to mark offline ephemeral instances: {}@{} - {}", 
                serviceName, serviceGroup, e.getMessage());
        }
    }
    
    /**
     * 验证并标记离线的临时节点
     * 检查数据库中所有 healthy=1 且 ephemeral=1 的服务，
     * 如果它们的 updated_at 时间超过 5 分钟，则标记为不健康
     */
    public void verifyAndMarkOfflineEphemeralNodes() {
        try {
            int rows = mcpServerMapper.markStaleEphemeralInstancesUnhealthy(5, LocalDateTime.now());
            
            if (rows > 0) {
                log.info("✅ Marked {} stale ephemeral instances as unhealthy (not updated for >5 minutes)", rows);
            } else {
                log.debug("ℹ️ No stale ephemeral instances found");
            }
            
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            log.error("❌ Failed to verify and mark offline ephemeral nodes: {}", e.getMessage());
        }
    }
    
    /**
     * 查询服务器信息
     */
    public McpServer getServerInfo(String serverKey) {
        try {
            return mcpServerMapper.selectByServerKey(serverKey);
        } catch (Exception e) {
            log.error("Failed to query server info: {} - {}", serverKey, e.getMessage());
            return null;
        }
    }
    
    /**
     * 查询所有在线服务器
     */
    public List<McpServer> getAllOnlineServers() {
        try {
            return mcpServerMapper.selectAllOnlineServers();
        } catch (Exception e) {
            log.error("Failed to query online servers: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 查询所有健康服务器
     */
    public List<McpServer> getAllHealthyServers() {
        try {
            return mcpServerMapper.selectAllHealthyServers();
        } catch (Exception e) {
            log.error("Failed to query healthy servers: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 定期检查并标记健康检查超时的服务器为离线
     * 每2分钟执行一次，标记超过5分钟未健康检查的服务器
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void checkAndMarkTimeoutServers() {
        try {
            // 查询超过5分钟未健康检查的服务器
            List<McpServer> timeoutServers = mcpServerMapper.selectServersByHealthCheckTimeout(5);
            
            if (!timeoutServers.isEmpty()) {
                List<String> serverKeys = timeoutServers.stream()
                    .map(McpServer::getServerKey)
                    .toList();
                
                int rows = mcpServerMapper.batchMarkOffline(serverKeys, LocalDateTime.now());
                
                log.warn("⚠️ Marked {} servers as offline due to health check timeout", rows);
                totalDeregistrations.addAndGet(rows);
            }
            
        } catch (Exception e) {
            log.error("Failed to check and mark timeout servers: {}", e.getMessage());
        }
    }
    
    /**
     * 定期清理过期的离线服务器记录
     * 每天凌晨3点执行，删除7天前离线的服务器记录
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredOfflineServers() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(7);
            int deleted = mcpServerMapper.deleteOfflineServersBefore(beforeTime);
            
            if (deleted > 0) {
                log.info("🧹 Cleaned up {} expired offline server records", deleted);
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired offline servers: {}", e.getMessage());
        }
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        try {
            int onlineCount = mcpServerMapper.countOnlineServers();
            int healthyCount = mcpServerMapper.countHealthyServers();
            
            return Map.of(
                "total_registrations", totalRegistrations.get(),
                "total_deregistrations", totalDeregistrations.get(),
                "total_heartbeats", totalHeartbeats.get(),
                "failed_operations", failedOperations.get(),
                "online_servers", onlineCount,
                "healthy_servers", healthyCount
            );
        } catch (Exception e) {
            log.error("Failed to get statistics: {}", e.getMessage());
            return Map.of();
        }
    }
    
    /**
     * 构建服务器唯一标识
     */
    private String buildServerKey(McpServerInfo serverInfo) {
        String host = serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp();
        return String.format("%s:%s:%d", 
            serverInfo.getName(), host, serverInfo.getPort());
    }
    
    /**
     * 序列化元数据为JSON
     */
    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata, using empty JSON: {}", e.getMessage());
            return "{}";
        }
    }
}