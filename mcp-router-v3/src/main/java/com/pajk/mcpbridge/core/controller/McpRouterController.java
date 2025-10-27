package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.config.NacosMcpRegistryConfig;
import com.pajk.mcpbridge.core.registry.McpServerRegistry;
import com.pajk.mcpbridge.core.model.McpMessage;
import com.pajk.mcpbridge.core.service.McpRouterService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * MCP路由控制器
 * 处理MCP消息路由相关的HTTP请求，适配智能路由服务
 */
@RestController
@RequestMapping("/mcp/router")
@RequiredArgsConstructor
public class McpRouterController {

    private final static Logger log = LoggerFactory.getLogger(McpRouterController.class);
    private final McpRouterService routerService;
    private final McpServerRegistry serverRegistry;
    private final NacosMcpRegistryConfig.McpRegistryProperties registryProperties;
    
    /**
     * 路由消息到指定服务
     */
    @PostMapping("/route/{serviceName}")
    public Mono<McpMessage> routeMessage(@PathVariable String serviceName, 
                                       @RequestBody McpMessage message) {
        log.info("Received route request for service: {}", serviceName);
        return routerService.routeRequest(serviceName, message);
    }
    
    /**
     * 路由消息到指定服务（带超时）
     */
    @PostMapping("/route/{serviceName}/timeout/{timeoutSeconds}")
    public Mono<McpMessage> routeMessageWithTimeout(@PathVariable String serviceName,
                                                   @PathVariable int timeoutSeconds,
                                                   @RequestBody McpMessage message) {
        log.info("Received route request for service: {} with timeout: {}s", serviceName, timeoutSeconds);
        return routerService.routeRequest(serviceName, message, Duration.ofSeconds(timeoutSeconds));
    }
    
    /**
     * 智能路由 - 自动发现和路由
     */
    @PostMapping("/smart-route")
    public Mono<McpMessage> smartRoute(@RequestBody McpMessage message,
                                     @RequestParam(defaultValue = "30") int timeoutSeconds) {
        log.info("Received smart route request");
        return routerService.smartRoute(message, Duration.ofSeconds(timeoutSeconds));
    }
    
    /**
     * 获取服务的工具列表
     */
    @GetMapping("/tools/{serviceName}")
    public Mono<Object> getServiceTools(@PathVariable String serviceName) {
        log.info("Getting tools for service: {}", serviceName);
        return routerService.listServerTools(serviceName);
    }
    
    /**
     * 检查服务是否有指定工具
     */
    @GetMapping("/tools/{serviceName}/has/{toolName}")
    public Mono<Map<String, Object>> hasServiceTool(@PathVariable String serviceName,
                                                   @PathVariable String toolName) {
        log.info("Checking if service: {} has tool: {}", serviceName, toolName);
        return routerService.hasServerTool(serviceName, toolName)
                .map(hasTool -> Map.of(
                        "serviceName", serviceName,
                        "toolName", toolName,
                        "hasTool", hasTool,
                        "timestamp", System.currentTimeMillis()
                ));
    }
    
    /**
     * 获取服务健康状态
     */
    @GetMapping("/health/{serviceName}")
    public Mono<Map<String, Object>> getServiceHealth(@PathVariable String serviceName) {
        log.info("Checking health for service: {}", serviceName);
        
        return Mono.zip(
                getServiceHealthStatus(serviceName),
                getServiceInstanceCount(serviceName)
        ).map(tuple -> Map.of(
                "serviceName", serviceName,
                "healthy", tuple.getT1(),
                "instanceCount", tuple.getT2(),
                "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * 获取路由统计信息
     */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> getRouterStats() {
        log.info("Getting router statistics");
        
        Map<String, Object> stats = routerService.getRoutingStats();
        stats.put("timestamp", System.currentTimeMillis());
        return Mono.just(stats);
    }
    
    /**
     * 获取所有可用服务
     */
    @GetMapping("/services")
    public Mono<Map<String, Object>> getAllServices(@RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Getting all services for group: {}", serviceGroup);
        
        return serverRegistry.getAllHealthyServers("*", serviceGroup)
                .collectList()
                .map(servers -> Map.of(
                        "serviceGroup", serviceGroup,
                        "servers", servers,
                        "count", servers.size(),
                        "timestamp", System.currentTimeMillis()
                ));
    }
    
    /**
     * 获取指定服务的实例列表
     */
    @GetMapping("/services/{serviceName}/instances")
    public Mono<Map<String, Object>> getServiceInstances(@PathVariable String serviceName,
                                                        @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Getting instances for service: {}@{}", serviceName, serviceGroup);
        
        return serverRegistry.getAllInstances(serviceName, serviceGroup)
                .collectList()
                .map(instances -> Map.of(
                        "serviceName", serviceName,
                        "serviceGroup", serviceGroup,
                        "instances", instances,
                        "count", instances.size(),
                        "timestamp", System.currentTimeMillis()
                ));
    }
    
    // 私有辅助方法
    
    private Mono<Boolean> getServiceHealthStatus(String serviceName) {
        return serverRegistry.getAllHealthyServers(serviceName, registryProperties.getServiceGroups())
                .hasElements();
    }
    
    private Mono<Integer> getServiceInstanceCount(String serviceName) {
        // 注意：getAllInstances 方法目前只支持单个服务组，这里使用第一个配置的服务组
        String serviceGroup = registryProperties.getServiceGroups().isEmpty() ? 
                "mcp-server" : registryProperties.getServiceGroups().get(0);
        return serverRegistry.getAllInstances(serviceName, serviceGroup)
                .count()
                .map(Long::intValue);
    }
} 