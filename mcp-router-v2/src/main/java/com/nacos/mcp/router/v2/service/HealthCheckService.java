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
        
        // 对已缓存的服务进行健康检查
        if (healthStatusCache.isEmpty()) {
            log.debug("No services in cache to check");
            return;
        }
        
        // 遍历已缓存的服务进行健康检查
        healthStatusCache.values().forEach(cachedStatus -> {
            try {
                // 创建一个简化的服务器信息用于健康检查
                McpServerInfo serverInfo = createServerInfoFromCache(cachedStatus);
                checkServerHealth(serverInfo)
                        .doOnNext(this::updateHealthStatus)
                        .doOnError(error -> log.error("Health check failed for service: {}", 
                                cachedStatus.getServiceName(), error))
                        .subscribe();
            } catch (Exception e) {
                log.error("Failed to check health for service: {}", cachedStatus.getServiceName(), e);
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
     * 执行健康检查
     */
    private Mono<Boolean> performHealthCheck(McpServerInfo serverInfo) {
        String healthUrl = buildHealthUrl(serverInfo);
        
        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(response -> true)
                .onErrorReturn(false)
                .doOnNext(healthy -> {
                    if (healthy) {
                        log.debug("Health check passed for {}:{}", serverInfo.getIp(), serverInfo.getPort());
                    } else {
                        log.warn("Health check failed for {}:{}", serverInfo.getIp(), serverInfo.getPort());
                    }
                });
    }
    
    /**
     * 构建健康检查URL
     */
    private String buildHealthUrl(McpServerInfo serverInfo) {
        // 从元数据获取健康检查端点，默认为 /actuator/health
        String healthEndpoint = serverInfo.getMetadata().getOrDefault("healthEndpoint", "/actuator/health");
        return String.format("http://%s:%d%s", serverInfo.getIp(), serverInfo.getPort(), healthEndpoint);
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