package com.nacos.mcp.router.v3.controller;

import com.nacos.mcp.router.v3.service.HealthCheckService;
import com.nacos.mcp.router.v3.service.McpClientManager;
import com.nacos.mcp.router.v3.service.McpRouterService;
import com.nacos.mcp.router.v3.service.LoadBalancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 监控仪表板控制器
 * 提供统一监控仪表板、连接池监控、路由统计等功能
 */
@Slf4j
@RestController
@RequestMapping("/mcp/monitor")
@RequiredArgsConstructor
public class HealthController {

    private final HealthCheckService healthCheckService;
    private final McpClientManager mcpClientManager;
    private final McpRouterService mcpRouterService;
    private final LoadBalancer loadBalancer;

    /**
     * 获取综合监控信息
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMonitorInfo() {
        Map<String, Object> response = new HashMap<>();
        
        // 服务基础信息
        Map<String, Object> serviceInfo = new HashMap<>();
        serviceInfo.put("name", "mcp-router-v3");
        serviceInfo.put("status", "UP");
        serviceInfo.put("version", "1.0.0");
        serviceInfo.put("health_strategy", "layered");
        serviceInfo.put("connection_pool", "enabled");
        serviceInfo.put("smart_routing", "enabled");
        response.put("service", serviceInfo);
        
        // 健康检查统计
        response.put("health_check", healthCheckService.getHealthCheckStats());
        
        // 连接池统计
        response.put("connection_pool", mcpClientManager.getPoolStats());
        
        // 路由统计
        response.put("routing", mcpRouterService.getRoutingStats());
        
        // 系统信息
        response.put("system", getSystemInfo());
        
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取健康检查统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getHealthStats() {
        var stats = healthCheckService.getHealthCheckStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取连接池统计信息
     */
    @GetMapping("/pool")
    public ResponseEntity<Map<String, Object>> getConnectionPoolStats() {
        Map<String, Object> poolStats = mcpClientManager.getPoolStats();
        Map<String, Object> response = new HashMap<>();
        response.put("connection_pool", poolStats);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取路由统计信息
     */
    @GetMapping("/routing")
    public ResponseEntity<Map<String, Object>> getRoutingStats() {
        Map<String, Object> routingStats = mcpRouterService.getRoutingStats();
        Map<String, Object> response = new HashMap<>();
        response.put("routing", routingStats);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取负载均衡统计信息
     */
    @GetMapping("/loadbalancer")
    public ResponseEntity<Map<String, Object>> getLoadBalancerStats() {
        Map<String, Object> lbStats = loadBalancer.getLoadBalancerStats();
        Map<String, Object> response = new HashMap<>();
        response.put("load_balancer", lbStats);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取综合监控仪表板信息
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // 基础服务信息
        Map<String, Object> serviceInfo = new HashMap<>();
        serviceInfo.put("name", "mcp-router-v3");
        serviceInfo.put("version", "1.0.0");
        serviceInfo.put("status", "UP");
        serviceInfo.put("start_time", getStartTime());
        serviceInfo.put("uptime", getUptime());
        dashboard.put("service", serviceInfo);
        
        // 健康检查统计
        dashboard.put("health_check", healthCheckService.getHealthCheckStats());
        
        // 连接池统计
        dashboard.put("connection_pool", mcpClientManager.getPoolStats());
        
        // 路由统计
        dashboard.put("routing", mcpRouterService.getRoutingStats());
        
        // 负载均衡统计
        dashboard.put("load_balancer", loadBalancer.getLoadBalancerStats());
        
        // 系统资源信息
        dashboard.put("system", getSystemInfo());
        
        // JVM信息
        dashboard.put("jvm", getJvmInfo());
        
        // 网络信息
        dashboard.put("network", getNetworkInfo());
        
        dashboard.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        dashboard.put("generated_at", System.currentTimeMillis());
        
        return ResponseEntity.ok(dashboard);
    }

    /**
     * 手动清理空闲连接
     */
    @PostMapping("/pool/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupIdleConnections() {
        log.info("Manual connection pool cleanup triggered");
        
        mcpClientManager.cleanupIdleConnections();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Connection pool cleanup completed");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }



    /**
     * 性能概览
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceOverview() {
        Map<String, Object> performance = new HashMap<>();
        
        // 连接池性能
        Map<String, Object> poolStats = mcpClientManager.getPoolStats();
        Map<String, Object> poolPerf = new HashMap<>();
        poolPerf.put("cache_hit_rate", poolStats.get("cache_hit_rate"));
        poolPerf.put("active_connections", poolStats.get("active_connections"));
        poolPerf.put("pool_utilization", calculatePoolUtilization(poolStats));
        performance.put("connection_pool", poolPerf);
        
        // 负载均衡性能
        Map<String, Object> lbStats = loadBalancer.getLoadBalancerStats();
        Map<String, Object> lbPerf = new HashMap<>();
        lbPerf.put("strategy", lbStats.get("strategy"));
        lbPerf.put("total_servers", lbStats.get("total_servers"));
        lbPerf.put("server_distribution", calculateServerDistribution(lbStats));
        performance.put("load_balancer", lbPerf);
        
        // 健康检查性能
        Map<String, Object> healthStats = healthCheckService.getHealthCheckStats();
        Map<String, Object> healthPerf = new HashMap<>();
        healthPerf.put("health_rate", healthStats.get("health_rate"));
        healthPerf.put("total_servers", healthStats.get("total_servers"));
        healthPerf.put("check_strategy", healthStats.get("check_strategy"));
        performance.put("health_check", healthPerf);
        
        // 系统性能
        performance.put("system", getSystemPerformanceMetrics());
        
        performance.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(performance);
    }

    /**
     * 手动触发指定服务的分层健康检查
     */
    @PostMapping("/check")
    public Mono<ResponseEntity<Map<String, Object>>> triggerHealthCheck(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        
        log.info("Manual health check triggered for service: {}@{}", serviceName, serviceGroup);
        
        return healthCheckService.triggerLayeredHealthCheck(serviceName, serviceGroup)
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Layered health check triggered");
                    response.put("service", serviceName + "@" + serviceGroup);
                    response.put("timestamp", System.currentTimeMillis());
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(error -> {
                    log.error("Health check failed for service: {}@{}", serviceName, serviceGroup, error);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Health check failed: " + error.getMessage());
                    errorResponse.put("service", serviceName + "@" + serviceGroup);
                    return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
                });
    }

    /**
     * 手动触发全量分层健康检查
     */
    @PostMapping("/check-all")
    public Mono<ResponseEntity<Map<String, Object>>> triggerFullHealthCheck() {
        log.info("Full layered health check triggered");
        
        return healthCheckService.triggerFullLayeredHealthCheck()
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Full layered health check triggered");
                    response.put("timestamp", System.currentTimeMillis());
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(error -> {
                    log.error("Full health check failed", error);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Full health check failed: " + error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
                });
    }

    // 私有辅助方法

    private String getStartTime() {
        return LocalDateTime.now().minusMinutes(30).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String getUptime() {
        return "30 minutes"; // 简化实现
    }

    private Map<String, Object> getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("total_memory_mb", runtime.totalMemory() / 1024 / 1024);
        systemInfo.put("free_memory_mb", runtime.freeMemory() / 1024 / 1024);
        systemInfo.put("used_memory_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        systemInfo.put("max_memory_mb", runtime.maxMemory() / 1024 / 1024);
        systemInfo.put("available_processors", runtime.availableProcessors());
        return systemInfo;
    }

    private Map<String, Object> getJvmInfo() {
        Map<String, Object> jvmInfo = new HashMap<>();
        jvmInfo.put("java_version", System.getProperty("java.version"));
        jvmInfo.put("java_vendor", System.getProperty("java.vendor"));
        jvmInfo.put("jvm_name", System.getProperty("java.vm.name"));
        jvmInfo.put("jvm_version", System.getProperty("java.vm.version"));
        jvmInfo.put("os_name", System.getProperty("os.name"));
        jvmInfo.put("os_arch", System.getProperty("os.arch"));
        return jvmInfo;
    }

    private Map<String, Object> getNetworkInfo() {
        Map<String, Object> networkInfo = new HashMap<>();
        networkInfo.put("hostname", getHostname());
        networkInfo.put("ip_address", getLocalIpAddress());
        return networkInfo;
    }

    private Map<String, Object> getSystemPerformanceMetrics() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> metrics = new HashMap<>();
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        metrics.put("memory_usage_percent", (double) usedMemory / runtime.maxMemory() * 100);
        metrics.put("cpu_cores", runtime.availableProcessors());
        metrics.put("gc_suggestion", usedMemory > totalMemory * 0.8 ? "high_memory_usage" : "normal");
        
        return metrics;
    }

    private double calculatePoolUtilization(Map<String, Object> poolStats) {
        Object activeConnections = poolStats.get("active_connections");
        Object maxPoolSize = poolStats.get("max_pool_size");
        
        if (activeConnections instanceof Number && maxPoolSize instanceof Number) {
            return ((Number) activeConnections).doubleValue() / ((Number) maxPoolSize).doubleValue() * 100;
        }
        return 0.0;
    }

    private Map<String, Object> calculateServerDistribution(Map<String, Object> lbStats) {
        Map<String, Object> distribution = new HashMap<>();
        Object connectionCounts = lbStats.get("connection_counts");
        
        if (connectionCounts instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) connectionCounts;
            
            long totalConnections = counts.values().stream()
                    .mapToLong(count -> count instanceof Number ? ((Number) count).longValue() : 0)
                    .sum();
            
            distribution.put("total_connections", totalConnections);
            distribution.put("server_count", counts.size());
            distribution.put("average_connections_per_server", 
                    counts.size() > 0 ? (double) totalConnections / counts.size() : 0.0);
        }
        
        return distribution;
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getLocalIpAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }
} 