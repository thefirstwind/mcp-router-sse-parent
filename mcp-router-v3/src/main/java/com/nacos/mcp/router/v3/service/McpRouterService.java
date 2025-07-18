package com.nacos.mcp.router.v3.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.nacos.mcp.router.v3.model.McpMessage;
import com.nacos.mcp.router.v3.model.McpServerInfo;
import com.nacos.mcp.router.v3.registry.McpServerRegistry;
import io.modelcontextprotocol.client.McpAsyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 增强的MCP路由服务
 * 实现按需连接、智能负载均衡和性能监控
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpRouterService {

    private final McpServerRegistry serverRegistry;
    private final McpClientManager mcpClientManager;
    private final HealthCheckService healthCheckService;
    private final LoadBalancer loadBalancer;

    // 默认超时时间
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 路由请求到指定服务 - 默认超时
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message) {
        return routeRequest(serviceName, message, DEFAULT_TIMEOUT);
    }

    /**
     * 按需路由请求：发现服务 -> 健康检查 -> 智能负载均衡 -> 建立连接 -> 调用
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message, Duration timeout) {
        log.info("🔄 Starting intelligent routing for service: {}, method: {}", serviceName, message.getMethod());
        
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
                    
                    // Step 2: 智能负载均衡选择最优实例
                    McpServerInfo selectedServer = selectOptimalServerWithLoadBalancing(candidates);
                    log.info("🎯 Load balanced selected server: {} ({}:{}) from {} candidates", 
                            selectedServer.getName(), selectedServer.getIp(), selectedServer.getPort(), candidates.size());
                    
                    // Step 3: 按需建立连接并调用（带性能监控）
                    return routeToServerWithMonitoring(selectedServer, message, timeout);
                })
                .timeout(timeout)
                .onErrorResume(error -> {
                    log.error("❌ Intelligent routing failed for service: {}", serviceName, error);
                    return createErrorResponse(message, -1, "Routing failed: " + error.getMessage());
                });
    }

    /**
     * Step 1: 发现健康的服务实例 (使用与智能路由一致的逻辑)
     */
    private Mono<List<McpServerInfo>> discoverHealthyInstances(String serviceName) {
        log.debug("🔍 Discovering healthy instances for service: {}", serviceName);
        
        // 使用与智能路由相同的逻辑：直接使用 Nacos 健康状态
        return serverRegistry.getAllHealthyServers(serviceName, "mcp-server")
                .collectList()
                .doOnNext(healthyServers -> {
                    log.info("✅ Found {} healthy instances for service: {}", healthyServers.size(), serviceName);
                    healthyServers.forEach(server -> 
                        log.debug("   - Instance: {}:{} (weight: {})", server.getIp(), server.getPort(), server.getWeight())
                    );
                });
    }

    /**
     * Step 2: 智能负载均衡选择最优服务器
     */
    private McpServerInfo selectOptimalServerWithLoadBalancing(List<McpServerInfo> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        // 转换为Nacos Instance格式进行负载均衡
        List<Instance> instances = candidates.stream()
                .map(this::convertToNacosInstance)
                .toList();
        
        // 使用轮询策略确保负载均衡
        Instance selectedInstance = loadBalancer.selectServer(instances, LoadBalancer.Strategy.WEIGHTED_ROUND_ROBIN);
        
        if (selectedInstance == null) {
            log.warn("⚠️ Load balancer returned null, falling back to first server");
            return candidates.get(0);
        }
        
        // 根据选中的实例找回原始服务器信息
        String selectedKey = selectedInstance.getIp() + ":" + selectedInstance.getPort();
        return candidates.stream()
                .filter(server -> (server.getIp() + ":" + server.getPort()).equals(selectedKey))
                .findFirst()
                .orElse(candidates.get(0));
    }

    /**
     * Step 3: 路由到指定服务器（带性能监控）
     */
    private Mono<McpMessage> routeToServerWithMonitoring(McpServerInfo serverInfo, McpMessage message, Duration timeout) {
        log.debug("📡 Establishing monitored connection to server: {}", serverInfo.getName());
        
        String toolName = extractToolName(message);
        Map<String, Object> arguments = extractToolArguments(message);
        
        log.info("🔧 Calling tool '{}' on server '{}' with monitoring", toolName, serverInfo.getName());
        
        long startTime = System.currentTimeMillis();
        Instance instance = convertToNacosInstance(serverInfo);
        
        // 按需获取或创建MCP客户端连接
        return mcpClientManager.getOrCreateMcpClient(serverInfo)
                .flatMap(client -> {
                    log.debug("🔗 MCP client connection established for server: {}", serverInfo.getName());
                    
                    // 从 MCP 客户端获取真实的客户端信息
                    String realClientId = client.getClientInfo().name();  // 真实的 MCP 客户端名称
                    String clientVersion = client.getClientInfo().version(); // 客户端版本
                    
                    // sessionId 简化处理，不暴露
                    String requestSessionId = null;
                    
                    // 调用工具
                    return mcpClientManager.callTool(serverInfo, toolName, arguments)
                            .map(result -> {
                                // 记录成功指标
                                long responseTime = System.currentTimeMillis() - startTime;
                                loadBalancer.recordResponseTime(instance, responseTime);
                                loadBalancer.recordSuccess(instance);
                                
                                // 构建成功响应（使用真实的MCP客户端信息）
                                McpMessage response = McpMessage.builder()
                                        .id(message.getId())
                                        .method(message.getMethod())
                                        .params(message.getParams())
                                        .jsonrpc("2.0")
                                        .result(result)
                                        .targetService(serverInfo.getName())
                                        .clientId(realClientId)  // 使用 MCP 客户端的真实名称
                                        .sessionId(null)  // 不暴露 sessionId
                                        .metadata(buildResponseMetadata(serverInfo, responseTime, toolName, realClientId, clientVersion))
                                        .timestamp(System.currentTimeMillis())
                                        .build();
                                
                                log.info("✅ Successfully routed request to server: {} (response time: {}ms) [clientId: {}]", 
                                        serverInfo.getName(), responseTime, realClientId);
                                return response;
                            })
                            .doFinally(signal -> {
                                // 减少连接计数
                                loadBalancer.decrementConnectionCount(instance);
                            });
                })
                .timeout(timeout.dividedBy(2)) // 给连接建立留一半时间
                .onErrorResume(error -> {
                    // 记录错误指标
                    long responseTime = System.currentTimeMillis() - startTime;
                    loadBalancer.recordResponseTime(instance, responseTime);
                    loadBalancer.recordError(instance);
                    loadBalancer.decrementConnectionCount(instance);
                    
                    log.error("❌ Failed to route to server: {} - {} (response time: {}ms)", 
                            serverInfo.getName(), error.getMessage(), responseTime);
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
                    
                    // 选择最优服务（使用加权轮询确保负载均衡）
                    McpServerInfo selectedServer = selectOptimalServerWithLoadBalancing(candidates);
                    log.info("🎯 Smart routing selected server: {} for tool: {}", selectedServer.getName(), toolName);
                    
                    // 路由到选定的服务器
                    return routeToServerWithMonitoring(selectedServer, message, timeout);
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
                .doOnNext(servers -> {
                    log.info("✅ Found {} services providing tool: {}", servers.size(), toolName);
                });
    }

    /**
     * 检查服务器是否提供指定工具
     */
    private Mono<Boolean> checkServerHasTool(McpServerInfo serverInfo, String toolName) {
        return mcpClientManager.hasTool(serverInfo, toolName)
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
                            .map(result -> (Object) result);
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
     * 获取路由统计信息
     */
    public Map<String, Object> getRoutingStats() {
        Map<String, Object> stats = loadBalancer.getLoadBalancerStats();
        stats.put("routing_strategy", "intelligent");
        stats.put("features", List.of("smart_routing", "connection_pooling", "performance_monitoring"));
        return stats;
    }

    /**
     * 转换McpServerInfo为Nacos Instance
     */
    private Instance convertToNacosInstance(McpServerInfo serverInfo) {
        Instance instance = new Instance();
        instance.setIp(serverInfo.getIp());
        instance.setPort(serverInfo.getPort());
        instance.setWeight(serverInfo.getWeight() > 0 ? serverInfo.getWeight() : 1.0);
        instance.setHealthy(true);
        instance.setEnabled(true);
        return instance;
    }

    /**
     * 计算健康度评分
     */
    private double calculateHealthScore(HealthCheckService.HealthStatus status) {
        if (status.getConsecutiveFailures() == 0) {
            return 1.0; // 完全健康
        }
        
        // 基于连续失败次数计算评分
        double score = Math.max(0.0, 1.0 - (status.getConsecutiveFailures() * 0.2));
        return score;
    }

    /**
     * 从消息中提取工具名称
     */
    private String extractToolName(McpMessage message) {
        if (message.getParams() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            return (String) params.get("name");
        }
        return null;
    }

    /**
     * 从消息中提取工具参数
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractToolArguments(McpMessage message) {
        if (message.getParams() instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            Object args = params.get("arguments");
            if (args instanceof Map) {
                return (Map<String, Object>) args;
            }
        }
        return Map.of();
    }

    /**
     * 创建错误响应
     */
    private Mono<McpMessage> createErrorResponse(McpMessage originalMessage, int code, String errorMessage) {
        log.error("Creating error response for message: {}", originalMessage.getId());
        
        // 简化错误响应，不暴露 sessionId
        String errorClientId = "mcp-router-v3-client";
        
        return Mono.just(
                McpMessage.builder()
                        .id(originalMessage.getId())
                        .method(originalMessage.getMethod())
                        .params(originalMessage.getParams())
                        .jsonrpc("2.0")
                        .error(McpMessage.McpError.builder()
                                .code(code)
                                .message(errorMessage)
                                .build())
                        .clientId(errorClientId)
                        .sessionId(null)
                        .metadata(buildErrorMetadata(code, errorMessage, errorClientId))
                        .timestamp(System.currentTimeMillis())
                        .build()
        );
    }





    /**
     * 构建响应元数据
     */
    private Map<String, Object> buildResponseMetadata(McpServerInfo serverInfo, long responseTime, String toolName, String clientId, String clientVersion) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("routedAt", System.currentTimeMillis());
        metadata.put("responseTime", responseTime);
        metadata.put("targetServer", serverInfo.getName());
        metadata.put("targetHost", serverInfo.getIp() + ":" + serverInfo.getPort());
        metadata.put("toolName", toolName);
        metadata.put("routerVersion", "v3");
        metadata.put("routingStrategy", "intelligent");
        metadata.put("serverVersion", serverInfo.getVersion());
        metadata.put("serverGroup", serverInfo.getServiceGroup());
        
        // 添加客户端信息（不添加 sessionId）
        if (clientId != null) {
            metadata.put("clientId", clientId);
        }
        if (clientVersion != null) {
            metadata.put("clientVersion", clientVersion);
        }
        
        // 添加服务器元数据
        if (serverInfo.getMetadata() != null) {
            metadata.put("serverMetadata", serverInfo.getMetadata());
        }
        
        return metadata;
    }

    /**
     * 构建错误元数据
     */
    private Map<String, Object> buildErrorMetadata(int errorCode, String errorMessage, String clientId) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("errorOccurredAt", System.currentTimeMillis());
        metadata.put("errorCode", errorCode);
        metadata.put("errorMessage", errorMessage);
        metadata.put("routerVersion", "v3");
        metadata.put("errorType", "routing_error");
        
        // 添加客户端信息（不添加 sessionId）
        if (clientId != null) {
            metadata.put("clientId", clientId);
        }
        
        return metadata;
    }
} 