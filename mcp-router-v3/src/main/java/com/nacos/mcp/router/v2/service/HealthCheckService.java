package com.nacos.mcp.router.v2.service;

import com.nacos.mcp.router.v2.model.McpServerInfo;
import com.nacos.mcp.router.v2.registry.McpServerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 健康检查服务
 * 定期检查MCP服务器的健康状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {
    
    private final McpServerRegistry serverRegistry;
    private final WebClient webClient;
    private final CircuitBreakerService circuitBreakerService;
    
    // 健康检查结果缓存
    private final Map<String, HealthStatus> healthStatusCache = new ConcurrentHashMap<>();
    
    // 健康检查超时时间
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);
    
    /**
     * 定时健康检查 - 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void performHealthCheck() {
        log.debug("Starting scheduled health check");
        
        // 首先从服务注册中心发现所有已注册的MCP服务
        discoverAndCheckAllMcpServices()
                .doOnError(error -> log.error("Failed to discover and check MCP services", error))
                .subscribe();
    }
    
    /**
     * 发现并检查所有MCP服务
     */
    private Mono<Void> discoverAndCheckAllMcpServices() {
        // 使用通配符查询所有配置的服务组中的 MCP 服务
        // "*" 表示查询所有服务名，"*" 表示查询所有配置的服务组
        return serverRegistry.getAllHealthyServers("*", "*")
                .cast(McpServerInfo.class)
                .flatMap(this::checkServerHealth)
                .doOnNext(this::updateHealthStatus)
                .doOnError(error -> log.error("Health check failed during discovery", error))
                .then()
                .doOnSuccess(unused -> {
                    if (healthStatusCache.isEmpty()) {
                        log.debug("No active MCP services found to check");
                    } else {
                        log.debug("Health check completed for {} services", healthStatusCache.size());
                    }
                });
    }
    
    /**
     * 手动触发健康检查
     */
    public Mono<Void> triggerHealthCheck(String serviceName, String serviceGroup) {
        log.info("Triggering health check for service: {}", serviceName);
        
        return serverRegistry.getAllInstances(serviceName, serviceGroup)
                .flatMap(this::checkServerHealth)
                .doOnNext(this::updateHealthStatus)
                .doOnError(error -> log.error("Health check failed for service: {}", serviceName, error))
                .then();
    }
    
    /**
     * 手动触发全量健康检查
     */
    public Mono<Void> triggerFullHealthCheck() {
        log.info("Triggering full health check for all MCP services");
        
        return discoverAndCheckAllMcpServices()
                .doOnSuccess(unused -> log.info("Full health check completed"))
                .doOnError(error -> log.error("Full health check failed", error));
    }
    
    /**
     * 检查单个服务器健康状态
     */
    public Mono<HealthStatus> checkServerHealth(McpServerInfo serverInfo) {
        String serverId = buildServerId(serverInfo);
        
        return performHealthCheck(serverInfo)
                .map(healthy -> {
                    HealthStatus status = healthStatusCache.computeIfAbsent(serverId, 
                            k -> new HealthStatus(serverId, serverInfo.getName()));
                    
                    if (healthy) {
                        status.recordSuccess();
                    } else {
                        status.recordFailure();
                    }
                    
                    return status;
                })
                .doOnNext(status -> log.debug("Health check result for {}: {}", 
                        serverId, status.isHealthy()));
    }
    
    /**
     * 执行健康检查 - 使用心跳请求到 MCP 服务的 SSE 端点
     */
    private Mono<Boolean> performHealthCheck(McpServerInfo serverInfo) {
        String heartbeatUrl = buildHeartbeatUrl(serverInfo);
        
        // 构建 MCP 心跳请求
        String heartbeatPayload = buildMcpHeartbeatPayload();
        
        return webClient.post()
                .uri(heartbeatUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .bodyValue(heartbeatPayload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(response -> {
                    // 检查 MCP 响应是否有效
                    if (response != null && (
                            response.contains("\"jsonrpc\"") || 
                            response.contains("\"result\"") ||
                            response.contains("pong") ||
                            response.contains("success"))) {
                        return true;
                    }
                    return false;
                })
                .onErrorResume(error -> {
                    log.debug("MCP heartbeat failed for {}:{} - {}", 
                            serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                            serverInfo.getPort(), 
                            error.getMessage());
                    
                    // 如果心跳失败，尝试简单的 SSE 连接检查
                    return attemptSseConnectivityCheck(serverInfo);
                })
                .doOnNext(healthy -> {
                    if (healthy) {
                        log.debug("✅ Health check passed for {}:{}", 
                                serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                                serverInfo.getPort());
                    } else {
                        log.debug("❌ Health check failed for {}:{}", 
                                serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                                serverInfo.getPort());
                    }
                });
    }
    
    /**
     * 构建 MCP 心跳请求 payload
     */
    private String buildMcpHeartbeatPayload() {
        return """
                {
                    "jsonrpc": "2.0",
                    "id": "health-check-%d",
                    "method": "ping"
                }
                """.formatted(System.currentTimeMillis());
    }
    
    /**
     * 尝试简单的 SSE 连接检查
     */
    private Mono<Boolean> attemptSseConnectivityCheck(McpServerInfo serverInfo) {
        String sseUrl = buildSseUrl(serverInfo);
        
        return webClient.get()
                .uri(sseUrl)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> {
                    // 检查是否是有效的 SSE 响应
                    return response != null && (
                            response.startsWith("data:") || 
                            response.contains("event:") ||
                            response.contains("retry:") ||
                            !response.trim().isEmpty());
                })
                .onErrorResume(error -> {
                    // 最后尝试基础连接检查
                    return attemptBasicConnectivityCheck(serverInfo);
                })
                .doOnNext(connected -> {
                    if (connected) {
                        log.debug("🔗 SSE connectivity check passed for {}:{}", 
                                serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                                serverInfo.getPort());
                    }
                });
    }
    
    /**
     * 尝试基础连接检查（最后的回退方案）
     */
    private Mono<Boolean> attemptBasicConnectivityCheck(McpServerInfo serverInfo) {
        // 尝试连接服务器的基础端口
        String baseUrl = String.format("http://%s:%d/", 
                serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                serverInfo.getPort());
        
        return webClient.get()
                .uri(baseUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(response -> true)
                .onErrorReturn(false)
                .doOnNext(connected -> {
                    if (connected) {
                        log.debug("🔗 Basic connectivity check passed for {}:{}", 
                                serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                                serverInfo.getPort());
                    }
                });
    }
    
    /**
     * 构建心跳检查URL（使用 MCP 服务的 SSE 端点）
     */
    private String buildHeartbeatUrl(McpServerInfo serverInfo) {
        String sseEndpoint = serverInfo.getSseEndpoint();
        if (sseEndpoint == null || sseEndpoint.trim().isEmpty()) {
            // 从元数据获取 SSE 端点
            if (serverInfo.getMetadata() != null && serverInfo.getMetadata().containsKey("sseEndpoint")) {
                sseEndpoint = serverInfo.getMetadata().get("sseEndpoint");
            } else {
                // 默认 SSE 端点
                sseEndpoint = "/sse";
            }
        }
        
        // 确保端点以 / 开头
        if (!sseEndpoint.startsWith("/")) {
            sseEndpoint = "/" + sseEndpoint;
        }
        
        String baseUrl = String.format("http://%s:%d%s", 
                serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(), 
                serverInfo.getPort(), 
                sseEndpoint);
        
        log.debug("Built heartbeat URL for {}: {}", serverInfo.getName(), baseUrl);
        return baseUrl;
    }
    
    /**
     * 构建 SSE 连接检查 URL
     */
    private String buildSseUrl(McpServerInfo serverInfo) {
        return buildHeartbeatUrl(serverInfo); // 使用相同的 SSE 端点
    }
    
    /**
     * 更新健康状态
     */
    private void updateHealthStatus(HealthStatus status) {
        healthStatusCache.put(status.getServerId(), status);
        
        // 更新熔断器状态
        if (status.shouldOpenCircuit()) {
            circuitBreakerService.openCircuit(status.getServiceName());
            log.warn("Circuit breaker opened for service: {}", status.getServiceName());
        } else if (status.shouldCloseCircuit()) {
            circuitBreakerService.closeCircuit(status.getServiceName());
            log.info("Circuit breaker closed for service: {}", status.getServiceName());
        }
        // 同步健康状态到Nacos
        try {
            String[] parts = status.getServerId().split(":");
            if (parts.length == 3) {
                String serviceName = parts[0];
                String ip = parts[1];
                int port = Integer.parseInt(parts[2]);
                // 若健康则enabled/healthy都为true，否则都为false
                serverRegistry.updateInstanceHealth(serviceName, "mcp-server", ip, port, status.isHealthy(), status.isHealthy());
                // 刷新本地健康实例缓存
                serverRegistry.getAllHealthyServers(serviceName, "mcp-server").collectList().subscribe(healthyList -> {
                    String cacheKey = serviceName + "@mcp-server";
                    serverRegistry.healthyInstanceCache.put(cacheKey, healthyList);
                    serverRegistry.healthyCacheTimestamp.put(cacheKey, System.currentTimeMillis());
                    log.debug("[健康检查] 刷新本地健康实例缓存: {}，健康实例数：{}", cacheKey, healthyList.size());
                });
            }
        } catch (Exception e) {
            log.error("[健康同步] updateHealthStatus同步到Nacos失败", e);
        }
    }
    
    /**
     * 获取健康状态
     */
    public HealthStatus getHealthStatus(String serverId) {
        return healthStatusCache.get(serverId);
    }
    
    /**
     * 获取服务健康状态
     */
    public HealthStatus getServiceHealthStatus(String serviceName) {
        return healthStatusCache.values().stream()
                .filter(status -> serviceName.equals(status.getServiceName()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取所有健康状态
     */
    public Map<String, HealthStatus> getAllHealthStatus() {
        return new ConcurrentHashMap<>(healthStatusCache);
    }
    
    /**
     * 清理过期的健康状态记录
     */
    @Scheduled(fixedRate = 300000) // 5分钟清理一次
    public void cleanupExpiredHealthStatus() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        
        healthStatusCache.entrySet().removeIf(entry -> {
            HealthStatus status = entry.getValue();
            return status.getLastCheckTime().isBefore(cutoff);
        });
        
        log.debug("Cleaned up expired health status records");
    }
    
    /**
     * 构建服务器ID
     */
    private String buildServerId(McpServerInfo serverInfo) {
        return String.format("%s:%s:%d", serverInfo.getName(), serverInfo.getIp(), serverInfo.getPort());
    }
    
    /**
     * 从缓存创建服务器信息
     */
    private McpServerInfo createServerInfoFromCache(HealthStatus cachedStatus) {
        // 从服务器ID解析出IP和端口
        String[] parts = cachedStatus.getServerId().split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid server ID format: " + cachedStatus.getServerId());
        }
        
        String serviceName = parts[0];
        String ip = parts[1];
        int port = Integer.parseInt(parts[2]);
        
        return McpServerInfo.builder()
                .name(serviceName)
                .ip(ip)
                .port(port)
                .healthy(cachedStatus.isHealthy())
                .metadata(Map.of("healthEndpoint", "/actuator/health"))
                .build();
    }
    
    /**
     * 健康状态模型
     */
    public static class HealthStatus {
        private final String serverId;
        private final String serviceName;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private LocalDateTime lastCheckTime = LocalDateTime.now();
        private boolean healthy = true;
        
        // 熔断器阈值
        private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
        private static final int CIRCUIT_BREAKER_RESET_THRESHOLD = 3;
        
        public HealthStatus(String serverId, String serviceName) {
            this.serverId = serverId;
            this.serviceName = serviceName;
        }
        
        public void recordSuccess() {
            successCount.incrementAndGet();
            consecutiveFailures.set(0);
            lastCheckTime = LocalDateTime.now();
            healthy = true;
        }
        
        public void recordFailure() {
            failureCount.incrementAndGet();
            consecutiveFailures.incrementAndGet();
            lastCheckTime = LocalDateTime.now();
            healthy = false;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public boolean shouldOpenCircuit() {
            return consecutiveFailures.get() >= CIRCUIT_BREAKER_THRESHOLD;
        }
        
        public boolean shouldCloseCircuit() {
            return consecutiveFailures.get() == 0 && successCount.get() >= CIRCUIT_BREAKER_RESET_THRESHOLD;
        }
        
        // Getters
        public String getServerId() {
            return serverId;
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public int getSuccessCount() {
            return successCount.get();
        }
        
        public int getFailureCount() {
            return failureCount.get();
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures.get();
        }
        
        public LocalDateTime getLastCheckTime() {
            return lastCheckTime;
        }
    }
} 