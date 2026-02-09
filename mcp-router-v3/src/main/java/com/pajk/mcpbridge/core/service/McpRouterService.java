package com.pajk.mcpbridge.core.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.config.NacosMcpRegistryConfig;
import com.pajk.mcpbridge.core.model.McpMessage;
import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.core.registry.McpServerRegistry;
import com.pajk.mcpbridge.persistence.entity.RoutingLog;
import com.pajk.mcpbridge.persistence.service.PersistenceEventPublisher;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 增强的MCP路由服务
 * 实现按需连接、智能负载均衡和性能监控
 */
@Service
@RequiredArgsConstructor
public class McpRouterService {

    private final static Logger log = LoggerFactory.getLogger(McpRouterService.class);

    private final McpServerRegistry serverRegistry;
    private final McpClientManager mcpClientManager;
    private final HealthCheckService healthCheckService;
    private final LoadBalancer loadBalancer;
    private final NacosMcpRegistryConfig.McpRegistryProperties registryProperties;
    private final McpSessionService sessionService;
    
    // 持久化事件发布器（可选依赖，不影响主流程）
    @Autowired(required = false)
    private PersistenceEventPublisher persistenceEventPublisher;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 默认超时时间（增加到60秒，以支持较慢的MCP操作如resources/list）
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 路由请求到指定服务 - 默认超时
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message) {
        return routeRequest(serviceName, message, DEFAULT_TIMEOUT, Map.of());
    }
    
    /**
     * 路由请求到指定服务 - 默认超时（带请求头）
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message, Map<String, String> headers) {
        return routeRequest(serviceName, message, DEFAULT_TIMEOUT, headers);
    }

    /**
     * 按需路由请求：发现服务 -> 健康检查 -> 智能负载均衡 -> 建立连接 -> 调用
     */
    public Mono<McpMessage> routeRequest(String serviceName, McpMessage message, Duration timeout, Map<String, String> headers) {
        // 记录 sessionId 信息用于调试
        String resolvedSessionId = resolveSessionId(message, headers);
        log.info("🔄 Starting intelligent routing for service: {}, method: {}, timeout: {}ms, resolvedSessionId: {}", 
                serviceName, message.getMethod(), timeout.toMillis(), resolvedSessionId != null ? resolvedSessionId : "null");
        
        // 创建路由日志对象（记录开始时间）
        String requestId = UUID.randomUUID().toString();
        RoutingLog routingLog = createRoutingLog(requestId, serviceName, message, headers);
        
        // 记录最终使用的 sessionId（用于调试）
        if (routingLog.getSessionId() != null && !routingLog.getSessionId().isEmpty()) {
            log.debug("📝 Routing log created with sessionId: {}", routingLog.getSessionId());
        } else {
            log.warn("⚠️ Routing log created with null/empty sessionId for requestId: {}, service: {}, method: {}. " +
                    "This may indicate: 1) RESTful request (expected), 2) Streamable client did not pass sessionId correctly (unexpected)", 
                    requestId, serviceName, message.getMethod());
        }
        long startTime = System.currentTimeMillis();
        
        // 检查是否是支持的方法
        String method = message.getMethod();
        if (!isSupportedMethod(method)) {
            return Mono.error(new IllegalArgumentException("Unsupported method: " + method + ". Supported methods: initialize, tools/list, tools/call, resources/list, resources/read, prompts/list, prompts/get, resources/templates/list"));
        }
        
        // initialize 由 router 本地处理，直接返回 router 的能力信息
        if ("initialize".equals(method)) {
            log.info("🖐 Handling 'initialize' locally in router (no backend routing)");
            return handleInitializeRequest(message)
                    .doOnSuccess(response -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        routingLog.markSuccess((int) responseTime);
                        setResponseBody(routingLog, response);
                        publishRoutingLog(routingLog);
                    })
                    .doOnError(error -> {
                        routingLog.markFailure(error.getMessage(), 500, "UNKNOWN", error.getClass().getSimpleName());
                        setErrorResponseBody(routingLog, error);
                        publishRoutingLog(routingLog);
                    });
        }
        
        // 其余方法按需路由至后端服务器
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
                    
                    // 记录目标服务器和路由策略
                    routingLog.setServerKey(selectedServer.getName() + ":" + selectedServer.getIp() + ":" + selectedServer.getPort());
                    routingLog.setServerName(selectedServer.getName());  // 设置服务器名称
                    routingLog.setLoadBalanceStrategy("WEIGHTED_ROUND_ROBIN");
                    
                    // Step 3: 按需建立连接并调用（带性能监控）
                    return routeToServerWithMonitoring(selectedServer, message, timeout, routingLog);
                })
                .doOnSuccess(response -> {
                    // 记录成功的路由日志
                    long responseTime = System.currentTimeMillis() - startTime;
                    routingLog.markSuccess((int) responseTime);
                    // 设置响应体
                    setResponseBody(routingLog, response);
                    publishRoutingLog(routingLog);
                })
                .doOnError(error -> {
                    // 记录失败的路由日志
                    routingLog.markFailure(error.getMessage(), 500, "UNKNOWN", error.getClass().getSimpleName());
                    // 设置错误响应体
                    setErrorResponseBody(routingLog, error);
                    publishRoutingLog(routingLog);
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
        
        // 使用与智能路由相同的逻辑：直接使用 Nacos 健康状态，支持多个服务组
        return serverRegistry.getAllHealthyServers(serviceName, registryProperties.getServiceGroups())
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
     * 支持 tools/call 和 tools/list 方法
     */
    private Mono<McpMessage> routeToServerWithMonitoring(McpServerInfo serverInfo, McpMessage message, Duration timeout, RoutingLog routingLog) {
        log.debug("📡 Establishing monitored connection to server: {}", serverInfo.getName());
        
        String method = message.getMethod();
        log.info("🔧 Processing method '{}' on server '{}' with monitoring", method, serverInfo.getName());
        
        long startTime = System.currentTimeMillis();
        Instance instance = convertToNacosInstance(serverInfo);
        
        // 按需获取或创建MCP客户端连接
        return mcpClientManager.getOrCreateMcpClient(serverInfo)
                .flatMap(client -> {
                    log.debug("🔗 MCP client connection established for server: {}", serverInfo.getName());
                    
                    // 从 MCP 客户端获取真实的客户端信息
                    String realClientId = client.getClientInfo().name();  // 真实的 MCP 客户端名称
                    String clientVersion = client.getClientInfo().version(); // 客户端版本
                    
                    // 根据方法类型调用不同的处理逻辑
                    // 注意：initialize 不应该走到这里，因为 routeRequest 已经拦截了
                    Mono<Object> resultMono;
                    if ("tools/list".equals(method)) {
                        // 处理 tools/list 请求
                        // 使用传入的timeout参数，不进行激进优化（RESTful接口需要正常超时）
                        resultMono = mcpClientManager.listTools(serverInfo, timeout)
                                .map(listToolsResult -> {
                                    // 将 ListToolsResult 转换为 Map 格式
                                    Map<String, Object> result = new java.util.HashMap<>();
                                    result.put("tools", listToolsResult.tools());
                                    // 添加空的 toolsMeta（如果 MCP 协议需要）
                                    result.put("toolsMeta", Map.of());
                                    return (Object) result;
                                });
                    } else if ("tools/call".equals(method)) {
                        // 处理 tools/call 请求
                        String toolName = extractToolName(message);
                        Map<String, Object> arguments = extractToolArguments(message);
                        resultMono = mcpClientManager.callTool(serverInfo, toolName, arguments);
                    } else if ("resources/list".equals(method)) {
                        // 处理 resources/list 请求
                        resultMono = mcpClientManager.listResources(serverInfo)
                                .map(listResourcesResult -> {
                                    Map<String, Object> result = new java.util.HashMap<>();
                                    result.put("resources", listResourcesResult.resources());
                                    return (Object) result;
                                });
                    } else if ("resources/read".equals(method)) {
                        // 处理 resources/read 请求
                        McpSchema.ReadResourceRequest readRequest = extractReadResourceRequest(message);
                        resultMono = mcpClientManager.readResource(serverInfo, readRequest)
                                .map(readResourceResult -> {
                                    Map<String, Object> result = new java.util.HashMap<>();
                                    result.put("contents", readResourceResult.contents());
                                    return (Object) result;
                                });
                    } else if ("prompts/list".equals(method)) {
                        // 处理 prompts/list 请求
                        resultMono = mcpClientManager.listPrompts(serverInfo)
                                .map(listPromptsResult -> {
                                    Map<String, Object> result = new java.util.HashMap<>();
                                    result.put("prompts", listPromptsResult.prompts());
                                    return (Object) result;
                                });
                    } else if ("prompts/get".equals(method)) {
                        // 处理 prompts/get 请求
                        McpSchema.GetPromptRequest getPromptRequest = extractGetPromptRequest(message);
                        resultMono = mcpClientManager.getPrompt(serverInfo, getPromptRequest)
                                .map(getPromptResult -> {
                                    Map<String, Object> result = new java.util.HashMap<>();
                                    result.put("description", getPromptResult.description());
                                    result.put("messages", getPromptResult.messages());
                                    return (Object) result;
                                });
                    } else if ("resources/templates/list".equals(method)) {
                        // 处理 resources/templates/list 请求
                        resultMono = mcpClientManager.listResourceTemplates(serverInfo)
                                .map(listResourceTemplatesResult -> {
                                    Map<String, Object> result = new java.util.HashMap<>();
                                    result.put("resourceTemplates", listResourceTemplatesResult.resourceTemplates());
                                    return (Object) result;
                                });
                    } else {
                        return Mono.error(new IllegalArgumentException("Unsupported method: " + method));
                    }
                    
                    return resultMono
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
                                        .metadata(buildResponseMetadata(serverInfo, responseTime, method, realClientId, clientVersion))
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
                // 修复：RESTful接口使用完整的超时时间，不缩短（SSE接口才需要激进优化）
                .timeout(timeout.multipliedBy(9).dividedBy(10))
                .onErrorResume(error -> {
                    // 记录错误指标
                    long responseTime = System.currentTimeMillis() - startTime;
                    loadBalancer.recordResponseTime(instance, responseTime);
                    loadBalancer.recordError(instance);
                    loadBalancer.decrementConnectionCount(instance);
                    
                    log.error("❌ Failed to route to server: {} - {} (response time: {}ms)", 
                            serverInfo.getName(), error.getMessage(), responseTime);
                    return createErrorResponse(message, -1, "Connection or request failed: " + error.getMessage());
                });
    }

    /**
     * 智能路由：自动发现服务并路由
     */
    public Mono<McpMessage> smartRoute(McpMessage message, Duration timeout, Map<String, String> headers) {
        log.info("🧠 Starting smart routing for message: {}", message.getMethod());
        
        // 支持的智能路由方法：
        // - tools/call：基于工具反向发现服务
        // - tools/list：选择任一健康 MCP 服务返回其工具列表
        // - resources/list：选择任一健康 MCP 服务返回其资源列表
        // - prompts/list：选择任一健康 MCP 服务返回其提示列表
        String method = message.getMethod();
        if ("tools/call".equals(method)) {
            String toolName = extractToolName(message);
            String sessionId = resolveSessionId(message, headers);
            if (toolName == null) {
                return createErrorResponse(message, -32602, "Tool name not found in request");
            }
            
            // 创建路由日志对象
            String requestId = UUID.randomUUID().toString();
            RoutingLog routingLog = createRoutingLog(requestId, "smart-route", message, headers);
            long startTime = System.currentTimeMillis();
            
            // 发现所有可能的服务（提供该工具）
            return discoverServicesWithTool(toolName)
                    .flatMap(candidates -> {
                        if (candidates.isEmpty()) {
                            return createErrorResponse(message, 10002, "No services found that provide tool: " + toolName);
                        }
                        
                        McpServerInfo selectedServer = selectOptimalServerWithLoadBalancing(candidates);
                        log.info("🎯 Smart routing selected server: {} for tool: {}", selectedServer.getName(), toolName);
                        
                        routingLog.setServerKey(selectedServer.getName() + ":" + selectedServer.getIp() + ":" + selectedServer.getPort());
                        routingLog.setServerName(selectedServer.getName());
                        routingLog.setLoadBalanceStrategy("WEIGHTED_ROUND_ROBIN");
                        
                        return routeToServerWithMonitoring(selectedServer, message, timeout, routingLog);
                    })
                    .doOnSuccess(response -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        routingLog.markSuccess((int) responseTime);
                        setResponseBody(routingLog, response);
                        publishRoutingLog(routingLog);
                    })
                    .doOnError(error -> {
                        routingLog.markFailure(error.getMessage(), 500, "UNKNOWN", error.getClass().getSimpleName());
                        setErrorResponseBody(routingLog, error);
                        publishRoutingLog(routingLog);
                    })
                    .timeout(timeout) // 移除5秒限制，使用完整的超时时间
                    .onErrorResume(err -> {
                        log.error("❌ Smart routing failed: {}", err.getMessage());
                        return createErrorResponse(message, -1, "Smart routing failed: " + err.getMessage());
                    });
        } else if ("tools/list".equals(method) || 
                   "resources/list".equals(method) || 
                   "prompts/list".equals(method) ||
                   "resources/templates/list".equals(method)) {
            // 对于这些列表方法，无需特定条件，选择任一健康的 MCP 服务即可
            String requestId = UUID.randomUUID().toString();
            RoutingLog routingLog = createRoutingLog(requestId, "smart-route", message, headers);
            long startTime = System.currentTimeMillis();
            
            // 仅在 MCP 服务器分组内发现服务，避免选择到非 MCP endpoint 服务
            // 使用配置的服务组，支持多个服务组（如 mcp-server 和 mcp-endpoints）
            return serverRegistry.getAllHealthyServers("*", registryProperties.getServiceGroups())
                    .collectList()
                    .flatMap(candidates -> {
                        // 过滤掉路由自身的实例，避免自调用
                        candidates = candidates.stream()
                                .filter(s -> s != null && s.getName() != null && !"mcp-router-v3".equals(s.getName()))
                                .toList();
                        if (candidates == null || candidates.isEmpty()) {
                            return createErrorResponse(message, 10001, "No healthy MCP services available for " + method);
                        }
                        McpServerInfo selectedServer = selectOptimalServerWithLoadBalancing(candidates);
                        log.info("🎯 Smart routing selected server: {} for method: {}", selectedServer.getName(), method);
                        
                        routingLog.setServerKey(selectedServer.getName() + ":" + selectedServer.getIp() + ":" + selectedServer.getPort());
                        routingLog.setServerName(selectedServer.getName());
                        routingLog.setLoadBalanceStrategy("WEIGHTED_ROUND_ROBIN");
                        
                        return routeToServerWithMonitoring(selectedServer, message, timeout, routingLog);
                    })
                    .doOnSuccess(response -> {
                        long responseTime = System.currentTimeMillis() - startTime;
                        routingLog.markSuccess((int) responseTime);
                        setResponseBody(routingLog, response);
                        publishRoutingLog(routingLog);
                    })
                    .doOnError(error -> {
                        routingLog.markFailure(error.getMessage(), 500, "UNKNOWN", error.getClass().getSimpleName());
                        setErrorResponseBody(routingLog, error);
                        publishRoutingLog(routingLog);
                    });
        } else {
            return createErrorResponse(message, -32601, "Method not supported: " + method);
        }
    }

    /**
     * 发现提供指定工具的服务
     */
    private Mono<List<McpServerInfo>> discoverServicesWithTool(String toolName) {
        log.debug("🔍 Discovering services that provide tool: {}", toolName);
        
        return serverRegistry.getAllHealthyServers("*", registryProperties.getServiceGroups())
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
        
        return serverRegistry.getAllHealthyServers(serviceName, registryProperties.getServiceGroups())
                .collectList()
                .timeout(Duration.ofSeconds(5)) // 服务发现超时：5秒
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        return Mono.just("目标服务不可用，请稍后重试或联系管理员");
                    }
                    McpServerInfo serverInfo = list.get(0);
                    // 使用10秒超时，避免长时间等待
                    return mcpClientManager.listTools(serverInfo, Duration.ofSeconds(10))
                            .map(result -> (Object) result)
                            .timeout(Duration.ofSeconds(10)) // 添加额外的超时保护
                            .onErrorResume(error -> {
                                log.error("Failed to list tools for service: {} (server: {})", 
                                        serviceName, serverInfo.getName(), error);
                                // 返回错误信息而不是抛出异常
                                return Mono.just(Map.of("error", "Failed to list tools: " + error.getMessage()));
                            });
                })
                .timeout(Duration.ofSeconds(15)) // 总超时：15秒（服务发现5秒 + 调用10秒）
                .doOnSuccess(tools -> log.info("Listed tools for service: {}", serviceName))
                .doOnError(error -> log.error("Failed to list tools for service: {}", serviceName, error))
                .onErrorReturn("目标服务不可用，请稍后重试或联系管理员");
    }

    /**
     * 检查服务器是否有指定工具
     */
    public Mono<Boolean> hasServerTool(String serviceName, String toolName) {
        log.info("Checking if service '{}' has tool '{}'", serviceName, toolName);
        
        return serverRegistry.getAllHealthyServers(serviceName, registryProperties.getServiceGroups())
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
     * 检查方法是否支持
     */
    private boolean isSupportedMethod(String method) {
        return "initialize".equals(method) ||
               "tools/list".equals(method) ||
               "tools/call".equals(method) ||
               "resources/list".equals(method) ||
               "resources/read".equals(method) ||
               "prompts/list".equals(method) ||
               "prompts/get".equals(method) ||
               "resources/templates/list".equals(method);
    }

    /**
     * 从消息中提取读取资源请求
     */
    @SuppressWarnings("unchecked")
    private McpSchema.ReadResourceRequest extractReadResourceRequest(McpMessage message) {
        if (message.getParams() instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String uri = (String) params.get("uri");
            if (uri != null) {
                return new McpSchema.ReadResourceRequest(uri);
            }
        }
        throw new IllegalArgumentException("Missing 'uri' parameter in resources/read request");
    }

    /**
     * 从消息中提取获取提示请求
     */
    @SuppressWarnings("unchecked")
    private McpSchema.GetPromptRequest extractGetPromptRequest(McpMessage message) {
        if (message.getParams() instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String name = (String) params.get("name");
            Map<String, Object> arguments = null;
            Object args = params.get("arguments");
            if (args instanceof Map) {
                arguments = (Map<String, Object>) args;
            }
            if (name != null) {
                return new McpSchema.GetPromptRequest(name, arguments != null ? arguments : Map.of());
            }
        }
        throw new IllegalArgumentException("Missing 'name' parameter in prompts/get request");
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
    private Map<String, Object> buildResponseMetadata(McpServerInfo serverInfo, long responseTime, String methodOrToolName, String clientId, String clientVersion) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("routedAt", System.currentTimeMillis());
        metadata.put("responseTime", responseTime);
        metadata.put("targetServer", serverInfo.getName());
        metadata.put("targetHost", serverInfo.getIp() + ":" + serverInfo.getPort());
        metadata.put("method", methodOrToolName); // 可以是方法名（如 tools/list）或工具名（如 tools/call）
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
     * 创建路由日志对象
     */
    private RoutingLog createRoutingLog(String requestId, String serviceName, McpMessage message, Map<String, String> headers) {
        try {
            String params = objectMapper.writeValueAsString(message.getParams());
            // 限制 params 大小为 10KB
            params = truncateIfNeeded(params, 10240);
            
            // 提取工具名称
            String toolName = extractToolName(message);
            String sessionId = resolveSessionId(message, headers);

            // 官方推荐：sessionId 应由客户端显式传递（Mcp-Session-Id 或 ?sessionId=）
            // 但为了兼容当前 MCP Inspector 等客户端在 Streamable 模式下未传 sessionId 的情况，
            // 在仅用于落库的场景下做一个"最佳努力"的推断：
            // 注意：为了性能，sessionId 推断已移除，避免阻塞主流程
            // 如果需要 sessionId，应该通过请求头或参数显式传递
            // 移除同步的 getSessionOverview() 调用，避免阻塞
            // 如果需要推断 sessionId，应该使用异步方式或通过其他机制（如请求头）传递

            // 序列化请求头
            String requestHeadersJson = "{}";
            if (headers != null && !headers.isEmpty()) {
                try {
                    requestHeadersJson = objectMapper.writeValueAsString(headers);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize request headers", e);
                }
            }
            
            // 提取请求来源信息
            RequestSourceInfo sourceInfo = extractRequestSourceInfo(headers);
            
            return RoutingLog.builder()
                .requestId(requestId)
                .method(message.getMethod())
                .path("/mcp/router/route/" + serviceName)  // 设置请求路径
                .mcpMethod(message.getMethod())  // 设置 MCP 方法
                .toolName(toolName != null ? toolName : "")  // 设置工具名称
                .requestHeaders(requestHeadersJson)  // 设置请求头
                .requestBody(params)
                .sessionId(sessionId)
                .serverName(serviceName)  // 设置服务器名称（初始值，后续会更新为实际选中的服务器）
                .startTime(java.time.LocalDateTime.now())
                .isSuccess(true)
                .isCached(false)
                .isRetry(false)
                .retryCount(0)
                // 设置请求来源信息
                .clientIp(sourceInfo.clientIp)
                .realIp(sourceInfo.realIp)
                .forwardedFor(sourceInfo.forwardedFor)
                .userAgent(sourceInfo.userAgent)
                .referer(sourceInfo.referer)
                .origin(sourceInfo.origin)
                .host(sourceInfo.host)
                .build();
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize request params", e);
            String params = truncateIfNeeded(String.valueOf(message.getParams()), 10240);
            
            // 提取工具名称
            String toolName = extractToolName(message);
            String sessionId = resolveSessionId(message, headers);
            
            // 序列化请求头
            String requestHeadersJson = "{}";
            if (headers != null && !headers.isEmpty()) {
                try {
                    requestHeadersJson = objectMapper.writeValueAsString(headers);
                } catch (JsonProcessingException ex) {
                    log.warn("Failed to serialize request headers", ex);
                }
            }
            
            // 提取请求来源信息
            RequestSourceInfo sourceInfo = extractRequestSourceInfo(headers);
            
            return RoutingLog.builder()
                .requestId(requestId)
                .method(message.getMethod())
                .path("/mcp/router/route/" + serviceName)  // 设置请求路径
                .mcpMethod(message.getMethod())  // 设置 MCP 方法
                .toolName(toolName != null ? toolName : "")  // 设置工具名称
                .requestHeaders(requestHeadersJson)  // 设置请求头
                .requestBody(params)
                .sessionId(sessionId)
                .serverName(serviceName)  // 设置服务器名称（初始值，后续会更新为实际选中的服务器）
                .startTime(java.time.LocalDateTime.now())
                .isSuccess(true)
                .isCached(false)
                .isRetry(false)
                .retryCount(0)
                // 设置请求来源信息
                .clientIp(sourceInfo.clientIp)
                .realIp(sourceInfo.realIp)
                .forwardedFor(sourceInfo.forwardedFor)
                .userAgent(sourceInfo.userAgent)
                .referer(sourceInfo.referer)
                .origin(sourceInfo.origin)
                .host(sourceInfo.host)
                .build();
        }
    }

    private String resolveSessionId(McpMessage message, Map<String, String> headers) {
        String sessionId = message.getSessionId();
        if ((sessionId == null || sessionId.isEmpty()) && message.getMetadata() != null) {
            Object value = message.getMetadata().get("sessionId");
            if (value != null) {
                sessionId = value.toString();
            }
        }
        if ((sessionId == null || sessionId.isEmpty()) && headers != null) {
            sessionId = headers.getOrDefault("sessionId",
                    headers.getOrDefault("Session-Id", headers.getOrDefault("X-Session-Id", null)));
        }
        return sessionId;
    }
    
    /**
     * 从请求头中提取客户端IP（考虑代理情况）
     * 优先级：X-Real-IP > X-Forwarded-For（第一个IP）> 其他代理头
     */
    private String extractClientIp(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        
        // 1. 优先使用 X-Real-IP（通常由反向代理设置）
        String realIp = headers.get("X-Real-IP");
        if (realIp == null || realIp.isEmpty()) {
            realIp = headers.get("x-real-ip");
        }
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim().split(",")[0].trim();
        }
        
        // 2. 使用 X-Forwarded-For（取第一个IP，即真实客户端IP）
        String forwardedFor = headers.get("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isEmpty()) {
            forwardedFor = headers.get("x-forwarded-for");
        }
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For 可能包含多个IP，用逗号分隔，第一个是真实客户端IP
            String[] ips = forwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }
        
        // 3. 尝试其他可能的代理头
        String cfConnectingIp = headers.get("CF-Connecting-IP"); // Cloudflare
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp.trim();
        }
        
        String trueClientIp = headers.get("True-Client-IP"); // Akamai
        if (trueClientIp != null && !trueClientIp.isEmpty()) {
            return trueClientIp.trim();
        }
        
        return null;
    }
    
    /**
     * 从请求头中提取完整的 X-Forwarded-For 值
     */
    private String extractForwardedFor(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        
        String forwardedFor = headers.get("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isEmpty()) {
            forwardedFor = headers.get("x-forwarded-for");
        }
        
        return forwardedFor != null && !forwardedFor.isEmpty() ? forwardedFor.trim() : null;
    }
    
    /**
     * 从请求头中提取请求来源信息
     */
    private RequestSourceInfo extractRequestSourceInfo(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new RequestSourceInfo(null, null, null, null, null, null, null);
        }
        
        String clientIp = extractClientIp(headers);
        String realIp = clientIp; // 真实IP就是提取的客户端IP
        String forwardedFor = extractForwardedFor(headers);
        String userAgent = headers.getOrDefault("User-Agent", 
                headers.getOrDefault("user-agent", null));
        String referer = headers.getOrDefault("Referer", 
                headers.getOrDefault("referer", null));
        String origin = headers.getOrDefault("Origin", 
                headers.getOrDefault("origin", null));
        String host = headers.getOrDefault("Host", 
                headers.getOrDefault("host", null));
        
        return new RequestSourceInfo(clientIp, realIp, forwardedFor, userAgent, referer, origin, host);
    }
    
    /**
     * 请求来源信息记录类
     */
    private static class RequestSourceInfo {
        final String clientIp;
        final String realIp;
        final String forwardedFor;
        final String userAgent;
        final String referer;
        final String origin;
        final String host;
        
        RequestSourceInfo(String clientIp, String realIp, String forwardedFor, 
                         String userAgent, String referer, String origin, String host) {
            this.clientIp = clientIp;
            this.realIp = realIp;
            this.forwardedFor = forwardedFor;
            this.userAgent = userAgent;
            this.referer = referer;
            this.origin = origin;
            this.host = host;
        }
    }
    
    /**
     * 本地处理 initialize 请求：返回 router 的能力信息（符合 MCP 标准）
     */
    private Mono<McpMessage> handleInitializeRequest(McpMessage message) {
        Map<String, Object> result = new java.util.HashMap<>();
        
        // protocolVersion
        result.put("protocolVersion", "2024-11-05");
        
        // capabilities
        Map<String, Object> capabilities = new java.util.HashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("resources", Map.of("subscribe", false, "listChanged", false));
        capabilities.put("prompts", Map.of("listChanged", false));
        capabilities.put("sampling", Map.of());
        result.put("capabilities", capabilities);
        
        // serverInfo
        Map<String, Object> serverInfo = new java.util.HashMap<>();
        serverInfo.put("name", "mcp-router-v3");
        serverInfo.put("version", "1.0.1");
        result.put("serverInfo", serverInfo);
        
        McpMessage response = McpMessage.builder()
                .id(message.getId())
                .method("initialize")
                .jsonrpc("2.0")
                .result(result)
                .timestamp(System.currentTimeMillis())
                .build();
        
        return Mono.just(response);
    }
    
    /**
     * 截断字符串到指定大小（如果超出）
     */
    private String truncateIfNeeded(String str, int maxBytes) {
        if (str == null) {
            return null;
        }
        
        byte[] bytes = str.getBytes();
        if (bytes.length <= maxBytes) {
            return str;
        }
        
        // 截断并添加标记
        String truncated = new String(bytes, 0, maxBytes - 20);
        return truncated + "... [TRUNCATED]";
    }
    
    /**
     * 设置响应体
     * 如果响应体超过 2048 字节，会自动压缩存储
     */
    private void setResponseBody(RoutingLog routingLog, McpMessage response) {
        try {
            String responseBody = objectMapper.writeValueAsString(response);
            // 限制响应体大小为 50KB，剩余部分交由 TypeHandler 截断
            responseBody = truncateIfNeeded(responseBody, 51200);
            routingLog.setResponseBody(responseBody);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize response body", e);
            String responseBody = truncateIfNeeded(String.valueOf(response), 51200);
            routingLog.setResponseBody(responseBody);
        }
    }
    
    /**
     * 设置错误响应体
     * 如果响应体超过 2048 字节，会自动压缩存储
     */
    private void setErrorResponseBody(RoutingLog routingLog, Throwable error) {
        try {
            Map<String, Object> errorResponse = Map.of(
                "error", error.getMessage(),
                "errorType", error.getClass().getSimpleName()
            );
            String responseBody = objectMapper.writeValueAsString(errorResponse);
            responseBody = truncateIfNeeded(responseBody, 51200);
            routingLog.setResponseBody(responseBody);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize error response body", e);
            String responseBody = truncateIfNeeded("{\"error\":\"" + error.getMessage() + "\"}", 51200);
            routingLog.setResponseBody(responseBody);
        }
    }
    
    /**
     * 发布路由日志（异步，不阻塞主流程）
     */
    private void publishRoutingLog(RoutingLog routingLog) {
        // 异步执行，避免阻塞响应式流
        if (persistenceEventPublisher != null) {
            Mono.fromRunnable(() -> {
                try {
                    log.debug("📝 Publishing routing log: requestId={}, isSuccess={}", 
                        routingLog.getRequestId(), routingLog.getIsSuccess());
                    persistenceEventPublisher.publishRoutingLog(routingLog);
                } catch (Exception e) {
                    // 持久化失败不应影响主流程
                    log.warn("Failed to publish routing log", e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(Duration.ofMillis(100)) // 100ms 超时，避免长时间阻塞
            .onErrorResume(error -> {
                log.debug("⚠️ Routing log publish timeout or error (non-blocking): {}", error.getMessage());
                return Mono.empty();
            })
            .subscribe(); // 异步执行，不等待结果
        } else {
            // 只在第一次出现时记录警告，避免日志刷屏
            if (!persistenceWarningLogged) {
                log.warn("⚠️ PersistenceEventPublisher is null, routing log not published. " +
                        "Check configuration: mcp.persistence.enabled must be true in application.yml. " +
                        "This warning will only be logged once.");
                persistenceWarningLogged = true;
            }
        }
    }
    
    // 用于控制警告日志只输出一次
    private static boolean persistenceWarningLogged = false;
    
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