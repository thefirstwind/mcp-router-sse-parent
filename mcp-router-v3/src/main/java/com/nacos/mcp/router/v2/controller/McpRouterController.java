package com.nacos.mcp.router.v2.controller;

import com.nacos.mcp.router.v2.model.McpMessage;
import com.nacos.mcp.router.v2.service.LoadBalancer;
import com.nacos.mcp.router.v2.service.McpRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MCP路由控制器
 * 处理MCP消息路由相关的HTTP请求
 */
@Slf4j
@RestController
@RequestMapping("/mcp/router")
@RequiredArgsConstructor
public class McpRouterController {
    
    private final McpRouterService routerService;
    
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
     * 广播消息到所有服务实例
     */
    @PostMapping("/broadcast/{serviceName}")
    public Mono<Void> broadcastMessage(@PathVariable String serviceName,
                                     @RequestBody McpMessage message) {
        log.info("Received broadcast request for service: {}", serviceName);
        return routerService.broadcastMessage(serviceName, message);
    }
    
    /**
     * 通过SSE发送消息
     */
    @PostMapping("/sse/send/{sessionId}")
    public Mono<Void> sendSseMessage(@PathVariable String sessionId,
                                   @RequestParam String eventType,
                                   @RequestBody McpMessage message) {
        log.info("Sending SSE message to session: {}, event: {}", sessionId, eventType);
        return routerService.sendSseMessage(sessionId, eventType, message);
    }
    
    /**
     * 通过SSE广播消息
     */
    @PostMapping("/sse/broadcast")
    public Mono<Void> broadcastSseMessage(@RequestParam String eventType,
                                        @RequestBody McpMessage message) {
        log.info("Broadcasting SSE message, event: {}", eventType);
        return routerService.broadcastSseMessage(eventType, message);
    }
    
    /**
     * 路由SSE消息
     */
    @PostMapping("/sse/route/{serviceName}/{sessionId}")
    public Mono<McpMessage> routeSseMessage(@PathVariable String serviceName,
                                          @PathVariable String sessionId,
                                          @RequestBody McpMessage message) {
        log.info("Routing SSE message to service: {}, session: {}", serviceName, sessionId);
        return routerService.routeSseMessage(serviceName, sessionId, message);
    }
    
    /**
     * 获取服务健康状态
     */
    @GetMapping("/health/{serviceName}")
    public Mono<Map<String, Object>> getServiceHealth(@PathVariable String serviceName) {
        log.info("Checking health for service: {}", serviceName);
        
        return Mono.zip(
                routerService.isServiceHealthy(serviceName),
                routerService.getServiceInstanceCount(serviceName)
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
        
        return Mono.fromCallable(() -> Map.of(
                "timestamp", System.currentTimeMillis(),
                "status", "active",
                "version", "2.0.0"
        ));
    }
} 