package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.service.CircuitBreakerService;
import com.pajk.mcpbridge.core.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 提供健康状态查询、熔断器状态查询等功能
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
    public ResponseEntity<Map<String, Object>> getAllHealthStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("healthStatuses", healthCheckService.getAllHealthStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定服务的健康状态
     */
    @GetMapping("/status/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServiceHealthStatus(@PathVariable String serviceName) {
        HealthCheckService.HealthStatus status = healthCheckService.getServiceHealthStatus(serviceName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("serviceName", serviceName);
        
        if (status != null) {
            response.put("found", true);
            response.put("healthy", status.isHealthy());
            response.put("serverId", status.getServerId());
            response.put("lastCheckTime", status.getLastCheckTime());
            response.put("successCount", status.getSuccessCount());
            response.put("failureCount", status.getFailureCount());
        } else {
            response.put("found", false);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有熔断器状态
     */
    @GetMapping("/circuit-breakers")
    public ResponseEntity<Map<String, Object>> getAllCircuitBreakerStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("circuitBreakers", circuitBreakerService.getAllCircuitBreakerStates());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定服务的熔断器状态
     */
    @GetMapping("/circuit-breakers/{serviceName}")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus(@PathVariable String serviceName) {
        CircuitBreakerService.CircuitBreakerState state = circuitBreakerService.getCircuitBreakerState(serviceName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("serviceName", serviceName);
        
        if (state != null) {
            response.put("found", true);
            response.put("state", state.getState().name());
            response.put("failureThreshold", state.getFailureThreshold());
            response.put("successThreshold", state.getSuccessThreshold());
            response.put("failureCount", state.getFailureCount());
            response.put("successCount", state.getSuccessCount());
            response.put("lastFailureTime", state.getLastFailureTime());
            response.put("lastSuccessTime", state.getLastSuccessTime());
        } else {
            response.put("found", false);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 重置熔断器
     */
    @PostMapping("/circuit-breakers/{serviceName}/reset")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker(@PathVariable String serviceName) {
        circuitBreakerService.resetCircuitBreaker(serviceName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("serviceName", serviceName);
        response.put("action", "reset");
        response.put("success", true);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 打开熔断器
     */
    @PostMapping("/circuit-breakers/{serviceName}/open")
    public ResponseEntity<Map<String, Object>> openCircuitBreaker(@PathVariable String serviceName) {
        circuitBreakerService.openCircuit(serviceName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("serviceName", serviceName);
        response.put("action", "open");
        response.put("success", true);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 关闭熔断器
     */
    @PostMapping("/circuit-breakers/{serviceName}/close")
    public ResponseEntity<Map<String, Object>> closeCircuitBreaker(@PathVariable String serviceName) {
        circuitBreakerService.closeCircuit(serviceName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("serviceName", serviceName);
        response.put("action", "close");
        response.put("success", true);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取健康检查和熔断器统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getHealthCheckStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("healthCheck", healthCheckService.getHealthCheckStats());
        
        // 计算熔断器统计
        Map<String, CircuitBreakerService.CircuitBreakerState> circuitBreakers = 
            circuitBreakerService.getAllCircuitBreakerStates();
        int totalCircuits = circuitBreakers.size();
        long openCircuits = circuitBreakers.values().stream()
            .filter(state -> state.getState() == CircuitBreakerService.State.OPEN)
            .count();
        long closedCircuits = circuitBreakers.values().stream()
            .filter(state -> state.getState() == CircuitBreakerService.State.CLOSED)
            .count();
        long halfOpenCircuits = circuitBreakers.values().stream()
            .filter(state -> state.getState() == CircuitBreakerService.State.HALF_OPEN)
            .count();
        
        Map<String, Object> circuitBreakerStats = new HashMap<>();
        circuitBreakerStats.put("totalCircuits", totalCircuits);
        circuitBreakerStats.put("openCircuits", openCircuits);
        circuitBreakerStats.put("closedCircuits", closedCircuits);
        circuitBreakerStats.put("halfOpenCircuits", halfOpenCircuits);
        response.put("circuitBreakers", circuitBreakerStats);
        
        return ResponseEntity.ok(response);
    }
}


















