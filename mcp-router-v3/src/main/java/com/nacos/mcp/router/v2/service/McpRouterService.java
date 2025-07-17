package com.nacos.mcp.router.v2.service;

import com.nacos.mcp.router.v2.model.McpMessage;
import com.nacos.mcp.router.v2.model.McpServerInfo;
import com.nacos.mcp.router.v2.registry.McpServerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * MCP路由服务
 * 负责处理MCP消息路由和转发，使用标准MCP客户端库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpRouterService {
    
    private final McpServerRegistry serverRegistry;
    private final McpClientManager mcpClientManager;
    private final McpSseTransportProvider sseTransportProvider;
    
    // 默认超时时间
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    /**
     * 路由请求到指定服务
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message) {
        return routeRequest(serviceName, message, DEFAULT_TIMEOUT);
    }
    
    /**
     * 路由请求到指定服务 - 带超时
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message, Duration timeout) {
        log.info("Routing request to service: {}, method: {}", serviceName, message.getMethod());
        
        // 检查是否是工具调用
        if (!"tools/call".equals(message.getMethod())) {
            return Mono.error(new IllegalArgumentException("Only tools/call method is supported, got: " + message.getMethod()));
        }
        
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .collectList()
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        return Mono.just(
                                McpMessage.builder()
                                        .id(message.getId())
                                        .jsonrpc("2.0")
                                        .error(McpMessage.McpError.builder()
                                                .code(10001)
                                                .message("目标服务不可用，请稍后重试或联系管理员")
                                                .build())
                                        .timestamp(System.currentTimeMillis())
                                        .build()
                        );
                    }
                    McpServerInfo serverInfo = list.get(0);
                    String toolName = extractToolName(message);
                    Map<String, Object> arguments = extractToolArguments(message);
                    
                    log.info("Calling tool '{}' on server '{}' with arguments: {}", toolName, serverInfo.getName(), arguments);
                    
                    // 使用 MCP 客户端调用工具
                    return mcpClientManager.callTool(serverInfo, toolName, arguments)
                            .map(result -> {
                                // 构建成功响应
                                McpMessage response = McpMessage.builder()
                                        .id(message.getId())
                                        .jsonrpc("2.0")
                                        .result(result)
                                        .timestamp(System.currentTimeMillis())
                                        .build();
                                
                                log.info("Successfully routed request to service: {}, response: {}", serviceName, response);
                                return response;
                            })
                            .onErrorResume(error -> {
                                log.error("Failed to route request to service: {}", serviceName, error);
                                
                                // 构建错误响应
                                McpMessage.McpError mcpError = McpMessage.McpError.builder()
                                        .code(-1)
                                        .message("Tool call failed: " + error.getMessage())
                                        .build();
                                
                                McpMessage errorResponse = McpMessage.builder()
                                        .id(message.getId())
                                        .jsonrpc("2.0")
                                        .error(mcpError)
                                        .timestamp(System.currentTimeMillis())
                                        .build();
                                
                                return Mono.just(errorResponse);
                            });
                })
                .timeout(timeout)
                .doOnError(error -> log.error("Failed to route request to service: {}", serviceName, error));
    }
    
    /**
     * 获取服务器的可用工具列表
     */
    public Mono<Object> listServerTools(String serviceName) {
        log.info("Listing tools for service: {}", serviceName);
        
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .collectList()
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        return Mono.just("目标服务不可用，请稍后重试或联系管理员");
                    }
                    McpServerInfo serverInfo = list.get(0);
                    return mcpClientManager.listTools(serverInfo)
                            .map(result -> (Object) result.tools());
                })
                .doOnSuccess(tools -> log.info("Listed tools for service: {}", serviceName))
                .doOnError(error -> log.error("Failed to list tools for service: {}", serviceName, error));
    }
    
    /**
     * 检查服务器是否有指定工具
     */
    public Mono<Boolean> hasServerTool(String serviceName, String toolName) {
        log.info("Checking if service '{}' has tool '{}'", serviceName, toolName);
        
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .collectList()
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        return Mono.just(false);
                    }
                    McpServerInfo serverInfo = list.get(0);
                    return mcpClientManager.hasTool(serverInfo, toolName);
                })
                .doOnSuccess(hasTool -> log.info("Service '{}' {} tool '{}'", serviceName, hasTool ? "has" : "does not have", toolName))
                .doOnError(error -> log.error("Failed to check tool for service: {}", serviceName, error));
    }
    
    /**
     * 通过SSE发送消息
     */
    public Mono<Void> sendSseMessage(String sessionId, String eventType, McpMessage message) {
        return sseTransportProvider.sendMessage(sessionId, eventType, serializeMessage(message));
    }
    
    /**
     * 通过SSE广播消息
     */
    public Mono<Void> broadcastSseMessage(String eventType, McpMessage message) {
        return sseTransportProvider.broadcast(eventType, serializeMessage(message));
    }
    
    /**
     * 路由SSE消息
     */
    public Mono<McpMessage> routeSseMessage(String serviceName, String sessionId, McpMessage message) {
        log.info("Routing SSE message to service: {}, session: {}", serviceName, sessionId);
        
        return routeRequest(serviceName, message)
                .flatMap(response -> {
                    // 通过SSE发送响应
                    return sendSseMessage(sessionId, "response", response)
                            .then(Mono.just(response));
                })
                .doOnSuccess(response -> log.info("Successfully routed SSE message to service: {}", serviceName))
                .doOnError(error -> log.error("Failed to route SSE message to service: {}", serviceName, error));
    }
    
    /**
     * 广播消息到指定服务的所有健康实例
     */
    public Mono<Void> broadcastMessage(String serviceName, McpMessage message) {
        log.info("Broadcasting message to all instances of service: {}, method: {}", serviceName, message.getMethod());
        
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .collectList()
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        log.warn("No healthy instances available for service: {}", serviceName);
                        return Mono.error(new IllegalStateException("目标服务不可用，请稍后重试或联系管理员"));
                    }
                    return Flux.fromIterable(list)
                            .flatMap(serverInfo -> {
                                // 检查是否是工具调用
                                if (!"tools/call".equals(message.getMethod())) {
                                    return Mono.error(new IllegalArgumentException("Only tools/call method is supported for broadcast, got: " + message.getMethod()));
                                }
                                
                                // 解析工具调用参数
                                String toolName = extractToolName(message);
                                Map<String, Object> arguments = extractToolArguments(message);
                                
                                log.info("Broadcasting tool '{}' call to server '{}' with arguments: {}", toolName, serverInfo.getName(), arguments);
                                
                                // 使用 MCP 客户端调用工具，忽略结果
                                return mcpClientManager.callTool(serverInfo, toolName, arguments)
                                        .doOnSuccess(result -> log.info("Successfully broadcasted to server: {}", serverInfo.getName()))
                                        .doOnError(error -> log.error("Failed to broadcast to server: {}", serverInfo.getName(), error))
                                        .onErrorResume(error -> Mono.empty()); // 忽略单个实例的错误
                            })
                            .then();
                })
                .timeout(DEFAULT_TIMEOUT)
                .doOnSuccess(unused -> log.info("Successfully broadcasted message to all instances of service: {}", serviceName))
                .doOnError(error -> log.error("Failed to broadcast message to service: {}", serviceName, error));
    }
    
    /**
     * 检查服务是否健康（是否有健康的实例）
     */
    public Mono<Boolean> isServiceHealthy(String serviceName) {
        log.debug("Checking health status for service: {}", serviceName);
        
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .hasElements()
                .doOnSuccess(healthy -> log.debug("Service '{}' health status: {}", serviceName, healthy ? "healthy" : "unhealthy"))
                .doOnError(error -> log.error("Failed to check health for service: {}", serviceName, error));
    }
    
    /**
     * 获取服务的实例数量
     */
    public Mono<Integer> getServiceInstanceCount(String serviceName) {
        log.debug("Getting instance count for service: {}", serviceName);
        
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.debug("Service '{}' has {} healthy instances", serviceName, count))
                .doOnError(error -> log.error("Failed to get instance count for service: {}", serviceName, error));
    }

    /**
     * 从消息中提取工具名称
     */
    private String extractToolName(McpMessage message) {
        if (message.getParams() == null) {
            throw new IllegalArgumentException("Missing params in tools/call message");
        }
        
        Object nameObj = message.getParams().get("name");
        if (nameObj == null) {
            throw new IllegalArgumentException("Missing 'name' parameter in tools/call message");
        }
        
        return nameObj.toString();
    }
    
    /**
     * 从消息中提取工具参数
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractToolArguments(McpMessage message) {
        if (message.getParams() == null) {
            return Map.of();
        }
        
        Object argumentsObj = message.getParams().get("arguments");
        if (argumentsObj instanceof Map) {
            return (Map<String, Object>) argumentsObj;
        }
        
        return Map.of();
    }
    
    /**
     * 序列化消息
     */
    private String serializeMessage(McpMessage message) {
        try {
            // 简单的JSON序列化，实际项目中应该使用ObjectMapper
            return String.format("{\"id\":\"%s\",\"result\":%s}", 
                    message.getId(), 
                    message.getResult() != null ? message.getResult().toString() : "null");
        } catch (Exception e) {
            log.error("Failed to serialize message: {}", e.getMessage());
            return "{\"error\":\"Serialization failed\"}";
        }
    }
} 