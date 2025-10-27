package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.service.CircuitBreakerService;
import com.pajk.mcpbridge.core.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 健康检查控制器
 * 提供健康检查和熔断器状态的API
 */
@RestController
@RequestMapping("/mcp/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final static Logger log = LoggerFactory.getLogger(HealthCheckController.class);
    private final HealthCheckService healthCheckService;
    private final CircuitBreakerService circuitBreakerService;
    
    /**
     * 获取所有服务的健康状态
     */
    @GetMapping("/status")
    public Mono<Map<String, Object>> getAllHealthStatus() {
        log.info("Getting all health status");
        
        return Mono.fromCallable(() -> {
            Map<String, HealthCheckService.HealthStatus> healthStatuses = 
                    healthCheckService.getAllHealthStatus();
            
            return Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "healthStatuses", healthStatuses.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> Map.of(
                                            "serverId", entry.getValue().getServerId(),
                                            "serviceName", entry.getValue().getServiceName(),
                                            "healthy", entry.getValue().isHealthy(),
                                            "successCount", entry.getValue().getSuccessCount(),
                                            "failureCount", entry.getValue().getFailureCount(),
                                            "consecutiveFailures", entry.getValue().getConsecutiveFailures(),
                                            "lastCheckTime", entry.getValue().getLastCheckTime()
                                    )
                            ))
            );
        });
    }
    
    /**
     * 获取指定服务的健康状态
     */
    @GetMapping("/status/{serviceName}")
    public Mono<Map<String, Object>> getServiceHealthStatus(@PathVariable String serviceName) {
        log.info("Getting health status for service: {}", serviceName);
        
        return Mono.fromCallable(() -> {
            HealthCheckService.HealthStatus status = 
                    healthCheckService.getServiceHealthStatus(serviceName);
            
            if (status == null) {
                return Map.of(
                        "serviceName", serviceName,
                        "found", false,
                        "timestamp", System.currentTimeMillis()
                );
            }
            
            return Map.of(
                    "serviceName", serviceName,
                    "found", true,
                    "serverId", status.getServerId(),
                    "healthy", status.isHealthy(),
                    "successCount", status.getSuccessCount(),
                    "failureCount", status.getFailureCount(),
                    "consecutiveFailures", status.getConsecutiveFailures(),
                    "lastCheckTime", status.getLastCheckTime(),
                    "timestamp", System.currentTimeMillis()
            );
        });
    }
    
    /**
     * 获取所有熔断器状态
     */
    @GetMapping("/circuit-breakers")
    public Mono<Map<String, Object>> getAllCircuitBreakerStatus() {
        log.info("Getting all circuit breaker status");
        
        return Mono.fromCallable(() -> {
            Map<String, CircuitBreakerService.CircuitBreakerState> states = 
                    circuitBreakerService.getAllCircuitBreakerStates();
            
            return Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "circuitBreakers", states.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> {
                                        CircuitBreakerService.CircuitBreakerState state = entry.getValue();
                                        Map<String, Object> stateMap = new HashMap<>();
                                        stateMap.put("serviceName", state.getServiceName());
                                        stateMap.put("state", state.getState().name());
                                        stateMap.put("failureCount", state.getFailureCount());
                                        stateMap.put("successCount", state.getSuccessCount());
                                        stateMap.put("consecutiveFailures", state.getConsecutiveFailures());
                                        stateMap.put("consecutiveSuccesses", state.getConsecutiveSuccesses());
                                        stateMap.put("failureThreshold", state.getFailureThreshold());
                                        stateMap.put("successThreshold", state.getSuccessThreshold());
                                        stateMap.put("timeout", state.getTimeout().toSeconds());
                                        stateMap.put("lastFailureTime", state.getLastFailureTime());
                                        stateMap.put("lastSuccessTime", state.getLastSuccessTime());
                                        stateMap.put("stateChangeTime", state.getStateChangeTime());
                                        return stateMap;
                                    }
                            ))
            );
        });
    }
    
    /**
     * 获取指定服务的熔断器状态
     */
    @GetMapping("/circuit-breakers/{serviceName}")
    public Mono<Map<String, Object>> getCircuitBreakerStatus(@PathVariable String serviceName) {
        log.info("Getting circuit breaker status for service: {}", serviceName);
        
        return Mono.fromCallable(() -> {
            CircuitBreakerService.CircuitBreakerState state = 
                    circuitBreakerService.getCircuitBreakerState(serviceName);
            
            if (state == null) {
                return Map.of(
                        "serviceName", serviceName,
                        "found", false,
                        "timestamp", System.currentTimeMillis()
                );
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("serviceName", serviceName);
            result.put("found", true);
            result.put("state", state.getState().name());
            result.put("failureCount", state.getFailureCount());
            result.put("successCount", state.getSuccessCount());
            result.put("consecutiveFailures", state.getConsecutiveFailures());
            result.put("consecutiveSuccesses", state.getConsecutiveSuccesses());
            result.put("failureThreshold", state.getFailureThreshold());
            result.put("successThreshold", state.getSuccessThreshold());
            result.put("timeout", state.getTimeout().toSeconds());
            result.put("lastFailureTime", state.getLastFailureTime());
            result.put("lastSuccessTime", state.getLastSuccessTime());
            result.put("stateChangeTime", state.getStateChangeTime());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        });
    }
    
    /**
     * 重置熔断器
     */
    @PostMapping("/circuit-breakers/{serviceName}/reset")
    public Mono<Map<String, Object>> resetCircuitBreaker(@PathVariable String serviceName) {
        log.info("Resetting circuit breaker for service: {}", serviceName);
        
        return Mono.fromRunnable(() -> {
            circuitBreakerService.resetCircuitBreaker(serviceName);
        }).then(Mono.fromCallable(() -> Map.of(
                "serviceName", serviceName,
                "action", "reset",
                "timestamp", System.currentTimeMillis(),
                "success", true
        )));
    }
    
    /**
     * 手动开启熔断器
     */
    @PostMapping("/circuit-breakers/{serviceName}/open")
    public Mono<Map<String, Object>> openCircuitBreaker(@PathVariable String serviceName) {
        log.info("Manually opening circuit breaker for service: {}", serviceName);
        
        return Mono.fromRunnable(() -> {
            circuitBreakerService.openCircuit(serviceName);
        }).then(Mono.fromCallable(() -> Map.of(
                "serviceName", serviceName,
                "action", "open",
                "timestamp", System.currentTimeMillis(),
                "success", true
        )));
    }
    
    /**
     * 手动关闭熔断器
     */
    @PostMapping("/circuit-breakers/{serviceName}/close")
    public Mono<Map<String, Object>> closeCircuitBreaker(@PathVariable String serviceName) {
        log.info("Manually closing circuit breaker for service: {}", serviceName);
        
        return Mono.fromRunnable(() -> {
            circuitBreakerService.closeCircuit(serviceName);
        }).then(Mono.fromCallable(() -> Map.of(
                "serviceName", serviceName,
                "action", "close",
                "timestamp", System.currentTimeMillis(),
                "success", true
        )));
    }
    
    /**
     * 获取健康检查统计信息
     */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> getHealthCheckStats() {
        log.info("Getting health check statistics");
        
        return Mono.fromCallable(() -> {
            Map<String, HealthCheckService.HealthStatus> healthStatuses = 
                    healthCheckService.getAllHealthStatus();
            
            Map<String, CircuitBreakerService.CircuitBreakerState> circuitBreakers = 
                    circuitBreakerService.getAllCircuitBreakerStates();
            
            long totalServices = healthStatuses.size();
            long healthyServices = healthStatuses.values().stream()
                    .mapToLong(status -> status.isHealthy() ? 1 : 0)
                    .sum();
            long unhealthyServices = totalServices - healthyServices;
            
            long openCircuits = circuitBreakers.values().stream()
                    .mapToLong(state -> state.isOpen() ? 1 : 0)
                    .sum();
            long halfOpenCircuits = circuitBreakers.values().stream()
                    .mapToLong(state -> state.isHalfOpen() ? 1 : 0)
                    .sum();
            long closedCircuits = circuitBreakers.size() - openCircuits - halfOpenCircuits;
            
            return Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "healthCheck", Map.of(
                            "totalServices", totalServices,
                            "healthyServices", healthyServices,
                            "unhealthyServices", unhealthyServices,
                            "healthyRate", totalServices > 0 ? (double) healthyServices / totalServices : 0.0
                    ),
                    "circuitBreakers", Map.of(
                            "totalCircuits", circuitBreakers.size(),
                            "openCircuits", openCircuits,
                            "halfOpenCircuits", halfOpenCircuits,
                            "closedCircuits", closedCircuits
                    )
            );
        });
    }
    
    /**
     * 手动触发指定服务的健康检查
     */
    @PostMapping("/check/{serviceName}")
    public Mono<Map<String, Object>> triggerHealthCheck(@PathVariable String serviceName,
                                                        @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Manual MCP health check triggered for service: {}", serviceName);
        
        return healthCheckService.triggerHealthCheck(serviceName, serviceGroup)
                .then(Mono.fromCallable(() -> {
                    HealthCheckService.HealthStatus status = 
                            healthCheckService.getServiceHealthStatus(serviceName);
                    
                    return Map.of(
                            "serviceName", serviceName,
                            "triggered", true,
                            "timestamp", System.currentTimeMillis(),
                            "currentStatus", status != null ? Map.of(
                                    "healthy", status.isHealthy(),
                                    "successCount", status.getSuccessCount(),
                                    "failureCount", status.getFailureCount()
                            ) : "No status available",
                            "healthCheckType", "MCP_PROTOCOL"
                    );
                }));
    }
    
    /**
     * 手动触发全量健康检查
     */
    @PostMapping("/trigger-full-check")
    public Mono<Map<String, Object>> triggerFullHealthCheck() {
        log.info("Triggering full health check manually");
        
        return healthCheckService.triggerFullHealthCheck()
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("message", "Full health check triggered successfully");
                    result.put("status", "completed");
                    result.put("timestamp", System.currentTimeMillis());
                    return result;
                }))
                .onErrorResume(error -> {
                    log.error("Failed to trigger full health check", error);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("message", "Failed to trigger full health check: " + error.getMessage());
                    errorResult.put("status", "failed");
                    errorResult.put("timestamp", System.currentTimeMillis());
                    return Mono.just(errorResult);
                });
    }

    /**
     * 清理过期的健康状态
     */
    @PostMapping("/cleanup")
    public Mono<Map<String, Object>> cleanupExpiredHealthStatus() {
        log.info("Cleaning up expired health status");
        
        return Mono.fromCallable(() -> {
            healthCheckService.cleanupExpiredHealthStatus();
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Expired health status cleaned up successfully");
            result.put("timestamp", System.currentTimeMillis());
            return result;
        });
    }
} 