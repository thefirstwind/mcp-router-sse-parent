package com.nacos.mcp.router.v3.service;

import com.nacos.mcp.router.v3.model.McpMessage;
import com.nacos.mcp.router.v3.model.McpServerInfo;
import com.nacos.mcp.router.v3.registry.McpServerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 增强的MCP路由服务
 * 实现按需连接和智能服务发现机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpRouterService {

    private final McpServerRegistry serverRegistry;
    private final McpClientManager mcpClientManager;
    private final HealthCheckService healthCheckService;

    /**
     * 按需路由请求：发现服务 -> 健康检查 -> 建立连接 -> 调用
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message, Duration timeout) {
        log.info("🔄 Starting on-demand routing for service: {}, method: {}", serviceName, message.getMethod());
        
        // 检查是否是工具调用
        if (!"tools/call".equals(message.getMethod())) {
            return Mono.error(new IllegalArgumentException("Only tools/call method is supported, got: " + message.getMethod()));
        }
        
        // Step 1: 通过服务发现找到可用实例
        return discoverHealthyInstances(serviceName)
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        return createErrorResponse(message, 10001, "No healthy services found for: " + serviceName);
                    }
                    
                    // Step 2: 选择最优实例（负载均衡）
                    McpServerInfo selectedServer = selectOptimalServer(candidates);
                    log.info("🎯 Selected server: {} ({}:{})", selectedServer.getName(), selectedServer.getIp(), selectedServer.getPort());
                    
                    // Step 3: 按需建立连接并调用
                    return routeToServer(selectedServer, message, timeout);
                })
                .timeout(timeout)
                .onErrorResume(error -> {
                    log.error("❌ Routing failed for service: {}", serviceName, error);
                    return createErrorResponse(message, -1, "Routing failed: " + error.getMessage());
                });
    }

    /**
     * Step 1: 发现健康的服务实例
     */
    private Mono<List<McpServerInfo>> discoverHealthyInstances(String serviceName) {
        log.debug("🔍 Discovering healthy instances for service: {}", serviceName);
        
        return serverRegistry.getAllInstances(serviceName, "mcp-server")
                .collectList()
                .flatMap(allInstances -> {
                    if (allInstances.isEmpty()) {
                        log.warn("⚠️ No instances found for service: {}", serviceName);
                        return Mono.just(List.<McpServerInfo>of());
                    }
                    
                    log.debug("📋 Found {} instances for service: {}", allInstances.size(), serviceName);
                    
                    // 并行进行健康检查
                    return reactor.core.publisher.Flux.fromIterable(allInstances)
                            .flatMap(server -> 
                                healthCheckService.checkServerHealthLayered(server)
                                    .map(status -> status.isHealthy() ? server : null)
                                    .onErrorReturn(null)
                            )
                            .filter(server -> server != null)
                            .collectList();
                })
                .doOnNext(healthyServers -> 
                    log.info("✅ Found {} healthy instances for service: {}", healthyServers.size(), serviceName)
                );
    }

    /**
     * Step 2: 选择最优服务器（简单轮询，后续可扩展为更复杂的负载均衡）
     */
    private McpServerInfo selectOptimalServer(List<McpServerInfo> candidates) {
        // 简单策略：选择第一个（后续可以实现更智能的算法）
        McpServerInfo selected = candidates.get(0);
        log.debug("🎯 Selected server using simple strategy: {}", selected.getName());
        return selected;
    }

    /**
     * Step 3: 路由到指定服务器（按需连接）
     */
    private Mono<McpMessage> routeToServer(McpServerInfo serverInfo, McpMessage message, Duration timeout) {
        log.debug("📡 Establishing on-demand connection to server: {}", serverInfo.getName());
        
        String toolName = extractToolName(message);
        Map<String, Object> arguments = extractToolArguments(message);
        
        log.info("🔧 Calling tool '{}' on server '{}' with arguments: {}", toolName, serverInfo.getName(), arguments);
        
        // 按需获取或创建MCP客户端连接
        return mcpClientManager.getOrCreateMcpClient(serverInfo)
                .flatMap(client -> {
                    log.debug("🔗 MCP client connection established for server: {}", serverInfo.getName());
                    
                    // 调用工具
                    return mcpClientManager.callTool(serverInfo, toolName, arguments)
                            .map(result -> {
                                // 构建成功响应
                                McpMessage response = McpMessage.builder()
                                        .id(message.getId())
                                        .jsonrpc("2.0")
                                        .result(result)
                                        .timestamp(System.currentTimeMillis())
                                        .build();
                                
                                log.info("✅ Successfully routed request to server: {}", serverInfo.getName());
                                return response;
                            });
                })
                .timeout(timeout.dividedBy(2)) // 给连接建立留一半时间
                .onErrorResume(error -> {
                    log.error("❌ Failed to route to server: {} - {}", serverInfo.getName(), error.getMessage());
                    return createErrorResponse(message, -1, "Connection or tool call failed: " + error.getMessage());
                });
    }

    /**
     * 智能路由：自动发现服务并路由
     */
    public Mono<McpMessage> smartRoute(McpMessage message, Duration timeout) {
        log.info("🧠 Starting smart routing for message: {}", message.getMethod());
        
        if (!"tools/call".equals(message.getMethod())) {
            return createErrorResponse(message, -32601, "Method not supported: " + message.getMethod());
        }
        
        String toolName = extractToolName(message);
        if (toolName == null) {
            return createErrorResponse(message, -32602, "Tool name not found in request");
        }
        
        // 发现所有可能的服务
        return discoverServicesWithTool(toolName)
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        return createErrorResponse(message, 10002, "No services found that provide tool: " + toolName);
                    }
                    
                    // 选择最优服务
                    McpServerInfo selectedServer = selectOptimalServer(candidates);
                    log.info("🎯 Smart routing selected server: {} for tool: {}", selectedServer.getName(), toolName);
                    
                    // 路由到选定的服务器
                    return routeToServer(selectedServer, message, timeout);
                });
    }

    /**
     * 发现提供指定工具的服务
     */
    private Mono<List<McpServerInfo>> discoverServicesWithTool(String toolName) {
        log.debug("🔍 Discovering services that provide tool: {}", toolName);
        
        return serverRegistry.getAllHealthyServers("*", "mcp-server")
                .cast(McpServerInfo.class)
                .filterWhen(server -> checkServerHasTool(server, toolName))
                .collectList()
                .doOnNext(servers -> 
                    log.info("✅ Found {} services providing tool: {}", servers.size(), toolName)
                );
    }

    /**
     * 检查服务器是否提供指定工具
     */
    private Mono<Boolean> checkServerHasTool(McpServerInfo serverInfo, String toolName) {
        return mcpClientManager.getOrCreateMcpClient(serverInfo)
                .flatMap(client -> mcpClientManager.listTools(serverInfo))
                .map(tools -> tools.containsKey(toolName))
                .onErrorReturn(false);
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

    /**
     * 创建错误响应
     */
    private Mono<McpMessage> createErrorResponse(McpMessage originalMessage, int code, String errorMessage) {
        log.error("Creating error response for message: {}", originalMessage.getId());
        return Mono.just(
                McpMessage.builder()
                        .id(originalMessage.getId())
                        .jsonrpc("2.0")
                        .error(McpMessage.McpError.builder()
                                .code(code)
                                .message(errorMessage)
                                .build())
                        .timestamp(System.currentTimeMillis())
                        .build()
        );
    }
} 