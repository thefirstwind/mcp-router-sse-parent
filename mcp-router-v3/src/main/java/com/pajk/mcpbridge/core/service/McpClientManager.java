package com.pajk.mcpbridge.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.model.McpServerInfo;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的MCP客户端管理器 - 连接池版本
 * 负责管理到远程MCP服务器的连接池，支持连接复用、空闲回收、生命周期管理
 */
@Service
@RequiredArgsConstructor
public class McpClientManager {

    private final static Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // 连接池：server key -> connection wrapper
    private final Map<String, McpConnectionWrapper> connectionPool = new ConcurrentHashMap<>();
    
    // 连接池配置
    private static final int MAX_POOL_SIZE = 20;
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10); // 10分钟空闲超时
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(60); // 增加到60秒以支持较慢的MCP操作
    private static final Duration MAX_LIFETIME = Duration.ofHours(1); // 连接最大生命周期
    
    // 统计信息
    private final AtomicLong totalConnectionsCreated = new AtomicLong(0);
    private final AtomicLong totalConnectionsClosed = new AtomicLong(0);
    private final AtomicLong totalConnectionRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    /**
     * 启动连接池管理
     */
    @PostConstruct
    public void startConnectionPoolManager() {
        log.info("🏊 Starting MCP connection pool manager...");
        log.info("📊 Pool configuration - Max size: {}, Idle timeout: {}, Max lifetime: {}", 
                MAX_POOL_SIZE, IDLE_TIMEOUT, MAX_LIFETIME);
        
        // 启动定期清理任务
        reactor.core.publisher.Flux.interval(Duration.ofMinutes(1))
                .doOnNext(tick -> cleanupIdleConnections())
                .doOnError(error -> log.error("Connection cleanup task failed", error))
                .subscribe();
                
        log.info("✅ MCP connection pool manager started");
    }

    /**
     * 获取或创建 MCP 客户端（连接池版本）
     */
    public Mono<McpAsyncClient> getOrCreateMcpClient(McpServerInfo serverInfo) {
        if (serverInfo == null) {
            return Mono.error(new IllegalArgumentException("ServerInfo cannot be null"));
        }
        
        String serverKey = buildServerKey(serverInfo);
        if (serverKey == null || serverKey.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Server key cannot be null or empty"));
        }
        
        totalConnectionRequests.incrementAndGet();
        
        // 检查连接池中是否有可用连接
        McpConnectionWrapper existingWrapper = connectionPool.get(serverKey);
        if (existingWrapper != null && existingWrapper.isValid()) {
            log.debug("🎯 Using pooled connection for server: {}", serverKey);
            existingWrapper.updateLastUsed();
            cacheHits.incrementAndGet();
            return Mono.just(existingWrapper.getClient());
        }

        // 连接池已满检查
        if (connectionPool.size() >= MAX_POOL_SIZE) {
            log.warn("⚠️ Connection pool is full ({}/{}), cleaning up expired connections", 
                    connectionPool.size(), MAX_POOL_SIZE);
            cleanupIdleConnections();
            
            if (connectionPool.size() >= MAX_POOL_SIZE) {
                return Mono.error(new RuntimeException("Connection pool exhausted"));
            }
        }

        // 创建新连接
        // 激进优化：缩短连接创建超时到300ms（初始化200ms + 缓冲100ms）
        return createNewConnection(serverInfo)
                .timeout(Duration.ofMillis(3000)) // 优化：放宽到3s，适应生产环境网络波动
                .map(client -> {
                    McpConnectionWrapper wrapper = new McpConnectionWrapper(
                            client, serverInfo, LocalDateTime.now());
                    connectionPool.put(serverKey, wrapper);
                    totalConnectionsCreated.incrementAndGet();
                    
                    log.info("🔗 Created new pooled connection for server: {} (pool size: {}/{})", 
                            serverKey, connectionPool.size(), MAX_POOL_SIZE);
                    return client;
                });
    }

    /**
     * 创建新的MCP连接
     */
    private Mono<McpAsyncClient> createNewConnection(McpServerInfo serverInfo) {
        return Mono.defer(() -> {
            log.debug("🔧 Creating new MCP connection for server: {}", serverInfo.getName());
 
            String serverBaseUrl = buildServerUrl(serverInfo);
            log.debug("Using server URL: {}", serverBaseUrl);
 
            // 创建WebClient Builder
            WebClient.Builder clientBuilder = webClientBuilder
                    .clone()
                    .baseUrl(serverBaseUrl)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024));
 
            // 创建SSE传输，使用从 Nacos 元数据获取的自定义 SSE 端点和消息端点
            WebFluxSseClientTransport transport = new WebFluxSseClientTransport(clientBuilder, objectMapper);
 
            // 创建客户端信息
            McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                    "mcp-router-v3-client",
                    "2.0.0"
            );
 
            // 构建异步MCP客户端
            McpAsyncClient client = McpClient.async(transport)
                    .clientInfo(clientInfo)
                    .requestTimeout(CONNECTION_TIMEOUT)
                    .build();
 
            // 激进优化：缩短初始化超时到200ms，确保快速响应
            // 注意：如果初始化失败，连接仍可使用，只是可能无法立即使用某些功能
            return client.initialize()
                    .timeout(Duration.ofMillis(2000)) // 优化：放宽到2s
                    .thenReturn(client)
                    .doOnSuccess(c -> log.debug("✅ MCP connection created and initialized for server: {}", serverInfo.getName()))
                    .onErrorResume(error -> {
                        // 即使初始化失败，也返回客户端（可能仍可使用）
                        log.warn("⚠️ MCP client initialization timeout/failed for server: {}, but connection may still be usable: {}", 
                                serverInfo.getName(), error.getMessage());
                        return Mono.just(client);
                    });
        })
        .subscribeOn(Schedulers.boundedElastic());
    }



    /**
     * 调用远程 MCP 服务器的工具
     */
    public Mono<Object> callTool(McpServerInfo serverInfo, String toolName, Map<String, Object> arguments) {
        log.debug("🔧 Calling tool '{}' on server '{}' via connection pool", toolName, serverInfo.getName());

        // 对于虚拟项目（virtual-*），直接使用 HTTP POST 调用 RESTful 接口
        if (serverInfo.getName() != null && serverInfo.getName().startsWith("virtual-")) {
            return callToolViaHttp(serverInfo, toolName, arguments);
        }

        return getOrCreateMcpClient(serverInfo)
                .flatMap(client -> {
                    // 构建工具调用请求
                    McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                            toolName, 
                            arguments != null ? arguments : Map.of()
                    );
                    
                    log.debug("📤 Sending tool call request via pooled connection: {}", request);
                    
                    return client.callTool(request)
                            .map(result -> {
                                log.debug("📥 Received tool call result via pooled connection");
                                
                                if (result.isError() != null && result.isError()) {
                                    throw new RuntimeException("Tool execution error: " + result.content());
                                }
                                
                                return parseToolResult(result.content());
                            })
                            .doOnSuccess(result -> log.debug("✅ Tool call successful via pool for '{}'", toolName))
                            .doOnError(error -> {
                                log.error("❌ Tool call failed via pool for '{}': {}", toolName, error.getMessage());
                                // 连接出错时，移除该连接
                                invalidateConnection(serverInfo);
                            });
                })
                .timeout(Duration.ofSeconds(60))
                .onErrorMap(e -> new RuntimeException("MCP call failed for tool '" + toolName + "' on server '" +
                        serverInfo.getName() + "': " + e.getMessage()));
    }

    /**
     * 发送 initialize 请求到后端服务器
     */
    public Mono<Map<String, Object>> initialize(McpServerInfo serverInfo, com.pajk.mcpbridge.core.model.McpMessage message) {
        log.debug("🔧 Sending initialize request to server via connection pool: {}", serverInfo.getName());
        
        // 从消息中提取 initialize 参数
        Object params = message.getParams();
        if (params == null) {
            return Mono.error(new IllegalArgumentException("Initialize params is required"));
        }
        
        // 通过 HTTP 直接发送 initialize 请求
        String serverBaseUrl = buildServerUrl(serverInfo);
        String sessionId = java.util.UUID.randomUUID().toString(); // 生成临时 sessionId
        
        // 构建请求体
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", message.getId());
        requestBody.put("method", "initialize");
        requestBody.put("params", params);
        
        // 通过 WebClient 发送请求
        // 注意：对于虚拟项目（virtual-*），需要在请求头中传递 X-Service-Name，以便 zkInfo 识别 endpoint
        return webClientBuilder
                .baseUrl(serverBaseUrl)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/mcp/message")
                        .queryParam("sessionId", sessionId)
                        .build())
                .header("X-Service-Name", serverInfo.getName()) // 传递 serviceName 以便 zkInfo 识别虚拟项目
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // 解析响应，返回 result Map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.get("result");
                    if (result == null) {
                        throw new RuntimeException("Invalid initialize response: no result");
                    }
                    return result;
                })
                .doOnSuccess(result -> log.debug("✅ Initialize request successful via pool for server: {}", serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to initialize via pool for server: {}", serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                })
                .timeout(Duration.ofSeconds(60))
                .onErrorMap(e -> new RuntimeException("MCP initialize failed for server '" + serverInfo.getName() + "': " + e.getMessage()));
    }

    /**
     * 获取服务器的可用工具列表
     */
    /**
     * 获取服务器的可用工具列表
     * 修复：RESTful接口使用正常超时，SSE接口才需要激进优化
     */
    public Mono<McpSchema.ListToolsResult> listTools(McpServerInfo serverInfo) {
        return listTools(serverInfo, Duration.ofSeconds(60)); // 默认60秒超时
    }
    
    /**
     * 获取服务器的可用工具列表（带超时参数）
     * 对于虚拟项目（virtual-*），直接使用 HTTP POST 调用 RESTful 接口
     * 对于其他服务，使用 SSE 客户端
     */
    public Mono<McpSchema.ListToolsResult> listTools(McpServerInfo serverInfo, Duration timeout) {
        log.debug("📋 Listing tools for server: {}", serverInfo.getName());

        // 对于虚拟项目（virtual-*），直接使用 HTTP POST 调用 RESTful 接口
        if (serverInfo.getName() != null && serverInfo.getName().startsWith("virtual-")) {
            return listToolsViaHttp(serverInfo, timeout);
        }

        // 检查是否是激进优化模式（超时时间 < 1秒）
        boolean aggressiveMode = timeout.toMillis() < 1000;
        
        // 先检查连接池，如果没有连接，使用更短的超时创建连接
        String serverKey = buildServerKey(serverInfo);
        McpConnectionWrapper existingWrapper = connectionPool.get(serverKey);
        if (existingWrapper != null && existingWrapper.isValid()) {
            // 连接池中有连接，直接使用
            log.debug("🎯 Using pooled connection for tools/list: {}", serverKey);
            existingWrapper.updateLastUsed();
            cacheHits.incrementAndGet();
            return existingWrapper.getClient()
                    .listTools()
                    .timeout(aggressiveMode ? Duration.ofMillis(2000) : timeout); // 激进模式2s，否则使用传入的超时
        }
        
        // 连接池中没有连接，需要创建
        Duration connectionTimeout = aggressiveMode ? Duration.ofMillis(3000) : Duration.ofSeconds(10);
        Duration callTimeout = aggressiveMode ? Duration.ofMillis(2000) : timeout;

        return getOrCreateMcpClient(serverInfo)
                .timeout(connectionTimeout) // 连接创建和初始化超时
                .flatMap(client -> {
                    // 立即调用
                    return client.listTools()
                            .timeout(callTimeout); // listTools调用超时
                })
                .doOnSuccess(tools -> log.debug("✅ Listed {} tools via pool for server: {}", 
                        tools.tools().size(), serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to list tools via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 通过 HTTP POST 直接调用 RESTful 接口获取工具列表（用于虚拟项目）
     */
    private Mono<McpSchema.ListToolsResult> listToolsViaHttp(McpServerInfo serverInfo, Duration timeout) {
        log.debug("📋 Listing tools via HTTP for virtual project: {}", serverInfo.getName());
        
        String serverBaseUrl = buildServerUrl(serverInfo);
        String sessionId = java.util.UUID.randomUUID().toString(); // 生成临时 sessionId
        
        // 构建请求体
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", "tools-list-" + System.currentTimeMillis());
        requestBody.put("method", "tools/list");
        requestBody.put("params", Map.of());
        
        // 通过 WebClient 发送请求
        // 注意：对于虚拟项目（virtual-*），需要在请求头中传递 X-Service-Name，以便 zkInfo 识别 endpoint
        return webClientBuilder
                .baseUrl(serverBaseUrl)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/mcp/message")
                        .queryParam("sessionId", sessionId)
                        .build())
                .header("X-Service-Name", serverInfo.getName()) // 传递 serviceName 以便 zkInfo 识别虚拟项目
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout) // 使用传入的超时时间
                .map(response -> {
                    // 解析响应，返回 ListToolsResult
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.get("result");
                    if (result == null) {
                        throw new RuntimeException("Invalid tools/list response: no result");
                    }
                    
                    // 使用 ObjectMapper 直接将 result Map 转换为 ListToolsResult
                    try {
                        return objectMapper.convertValue(result, McpSchema.ListToolsResult.class);
                    } catch (Exception e) {
                        log.error("Failed to convert result to ListToolsResult: {}", result, e);
                        throw new RuntimeException("Failed to convert tools/list response: " + e.getMessage(), e);
                    }
                })
                .doOnSuccess(tools -> log.debug("✅ Tools/list request successful via HTTP for server: {}", serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to list tools via HTTP for server: {}", serverInfo.getName(), error);
                })
                .onErrorMap(e -> new RuntimeException("MCP tools/list failed for server '" + serverInfo.getName() + "': " + e.getMessage()));
    }

    /**
     * 通过 HTTP POST 直接调用 RESTful 接口执行工具调用（用于虚拟项目）
     */
    private Mono<Object> callToolViaHttp(McpServerInfo serverInfo, String toolName, Map<String, Object> arguments) {
        log.debug("🔧 Calling tool '{}' via HTTP for virtual project: {}", toolName, serverInfo.getName());
        
        String serverBaseUrl = buildServerUrl(serverInfo);
        String sessionId = java.util.UUID.randomUUID().toString(); // 生成临时 sessionId
        
        // 构建请求体
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", "tools-call-" + System.currentTimeMillis());
        requestBody.put("method", "tools/call");
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Map.of());
        requestBody.put("params", params);
        
        // 通过 WebClient 发送请求
        // 注意：对于虚拟项目（virtual-*），需要在请求头中传递 X-Service-Name，以便 zkInfo 识别 endpoint
        return webClientBuilder
                .baseUrl(serverBaseUrl)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/mcp/message")
                        .queryParam("sessionId", sessionId)
                        .build())
                .header("X-Service-Name", serverInfo.getName()) // 传递 serviceName 以便 zkInfo 识别虚拟项目
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(60)) // 工具调用可能需要更长时间
                .map(response -> {
                    // 检查是否有错误
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) response.get("error");
                    if (error != null) {
                        String errorMessage = (String) error.get("message");
                        throw new RuntimeException("Tool execution error: " + errorMessage);
                    }
                    
                    // 解析响应，返回 result
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.get("result");
                    if (result == null) {
                        throw new RuntimeException("Invalid tools/call response: no result");
                    }
                    
                    // 解析工具调用结果（zkInfo 返回的 result 格式可能不同，需要适配）
                    // zkInfo 的 tools/call 响应格式：{"jsonrpc":"2.0","id":"...","result":{...}}
                    // 其中 result 可能包含 content 数组或其他格式
                    Object contentObj = result.get("content");
                    if (contentObj != null) {
                        // 如果有 content 字段，使用 parseToolResult 解析
                        if (contentObj instanceof java.util.List) {
                            @SuppressWarnings("unchecked")
                            java.util.List<Map<String, Object>> contentList = (java.util.List<Map<String, Object>>) contentObj;
                            // 转换为 McpSchema.Content 格式
                            java.util.List<McpSchema.Content> contents = contentList.stream()
                                    .map(contentMap -> {
                                        String type = (String) contentMap.get("type");
                                        if ("text".equals(type)) {
                                            String text = (String) contentMap.get("text");
                                            return new McpSchema.TextContent(text);
                                        }
                                        return new McpSchema.TextContent(contentMap.toString());
                                    })
                                    .collect(java.util.stream.Collectors.toList());
                            return parseToolResult(contents);
                        }
                    }
                    
                    // 如果没有 content 字段，直接返回 result
                    return result;
                })
                .doOnSuccess(result -> log.debug("✅ Tools/call request successful via HTTP for server: {}", serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to call tool via HTTP for server: {}", serverInfo.getName(), error);
                })
                .onErrorMap(e -> new RuntimeException("MCP tools/call failed for tool '" + toolName + "' on server '" + serverInfo.getName() + "': " + e.getMessage()));
    }

    /**
     * 检查服务器是否有指定工具
     */
    public Mono<Boolean> hasTool(McpServerInfo serverInfo, String toolName) {
        return listTools(serverInfo)
                .map(result -> result.tools().stream()
                        .anyMatch(tool -> tool.name().equals(toolName)))
                .onErrorReturn(false);
    }

    /**
     * 获取服务器的可用资源列表
     */
    public Mono<McpSchema.ListResourcesResult> listResources(McpServerInfo serverInfo) {
        log.debug("📋 Listing resources for server via connection pool: {}", serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(McpAsyncClient::listResources)
                .timeout(Duration.ofMillis(5000)) // 优化：放宽到5s
                .doOnSuccess(resources -> log.debug("✅ Listed {} resources via pool for server: {}", 
                        resources.resources() != null ? resources.resources().size() : 0, serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to list resources via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 读取资源内容
     */
    public Mono<McpSchema.ReadResourceResult> readResource(McpServerInfo serverInfo, McpSchema.Resource resource) {
        log.debug("📖 Reading resource '{}' from server via connection pool: {}", 
                resource.uri(), serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(client -> client.readResource(resource))
                .doOnSuccess(result -> log.debug("✅ Read resource successfully via pool"))
                .doOnError(error -> {
                    log.error("❌ Failed to read resource via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 读取资源内容（使用请求对象）
     */
    public Mono<McpSchema.ReadResourceResult> readResource(McpServerInfo serverInfo, McpSchema.ReadResourceRequest request) {
        log.debug("📖 Reading resource '{}' from server via connection pool: {}", 
                request.uri(), serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(client -> client.readResource(request))
                .doOnSuccess(result -> log.debug("✅ Read resource successfully via pool"))
                .doOnError(error -> {
                    log.error("❌ Failed to read resource via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 获取服务器的可用提示列表
     */
    public Mono<McpSchema.ListPromptsResult> listPrompts(McpServerInfo serverInfo) {
        log.debug("📋 Listing prompts for server via connection pool: {}", serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(McpAsyncClient::listPrompts)
                .timeout(Duration.ofMillis(5000)) // 优化：放宽到5s
                .doOnSuccess(prompts -> log.debug("✅ Listed {} prompts via pool for server: {}", 
                        prompts.prompts() != null ? prompts.prompts().size() : 0, serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to list prompts via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 获取提示内容
     */
    public Mono<McpSchema.GetPromptResult> getPrompt(McpServerInfo serverInfo, McpSchema.GetPromptRequest request) {
        log.debug("📝 Getting prompt '{}' from server via connection pool: {}", 
                request.name(), serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(client -> client.getPrompt(request))
                .doOnSuccess(result -> log.debug("✅ Got prompt successfully via pool"))
                .doOnError(error -> {
                    log.error("❌ Failed to get prompt via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 获取服务器的可用资源模板列表
     */
    public Mono<McpSchema.ListResourceTemplatesResult> listResourceTemplates(McpServerInfo serverInfo) {
        log.debug("📋 Listing resource templates for server via connection pool: {}", serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(McpAsyncClient::listResourceTemplates)
                .timeout(Duration.ofMillis(5000)) // 优化：放宽到5s
                .doOnSuccess(templates -> log.debug("✅ Listed {} resource templates via pool for server: {}", 
                        templates.resourceTemplates() != null ? templates.resourceTemplates().size() : 0, serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to list resource templates via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
    }

    /**
     * 清理空闲连接
     */
    public void cleanupIdleConnections() {
        log.debug("🧹 Starting idle connection cleanup...");
        
        LocalDateTime now = LocalDateTime.now();
        int removedCount = 0;
        
        for (Map.Entry<String, McpConnectionWrapper> entry : connectionPool.entrySet()) {
            McpConnectionWrapper wrapper = entry.getValue();
            
            if (wrapper.isExpired(now, IDLE_TIMEOUT, MAX_LIFETIME)) {
                connectionPool.remove(entry.getKey());
                closeConnectionSafely(wrapper.getClient(), entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("🧹 Cleaned up {} idle/expired connections (pool size: {}/{})", 
                    removedCount, connectionPool.size(), MAX_POOL_SIZE);
        }
    }

    /**
     * 使连接失效
     */
    public void invalidateConnection(McpServerInfo serverInfo) {
        String serverKey = buildServerKey(serverInfo);
        McpConnectionWrapper wrapper = connectionPool.remove(serverKey);
        if (wrapper != null) {
            closeConnectionSafely(wrapper.getClient(), serverKey);
            log.info("❌ Invalidated connection for server: {}", serverKey);
        }
    }

    /**
     * 获取连接池统计信息
     */
    public Map<String, Object> getPoolStats() {
        return Map.of(
                "active_connections", connectionPool.size(),
                "max_pool_size", MAX_POOL_SIZE,
                "total_created", totalConnectionsCreated.get(),
                "total_closed", totalConnectionsClosed.get(),
                "total_requests", totalConnectionRequests.get(),
                "cache_hits", cacheHits.get(),
                "cache_hit_rate", totalConnectionRequests.get() > 0 ? 
                        (double) cacheHits.get() / totalConnectionRequests.get() : 0.0,
                "idle_timeout_minutes", IDLE_TIMEOUT.toMinutes(),
                "max_lifetime_hours", MAX_LIFETIME.toHours()
        );
    }

    /**
     * 构建服务器 URL - 返回基础URL，MCP客户端会自动处理SSE端点路径
     */
    private String buildServerUrl(McpServerInfo serverInfo) {
        String baseUrl = String.format("http://%s:%d", serverInfo.getIp(), serverInfo.getPort());
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://" + baseUrl;
        }
        
        log.debug("🔗 Built server base URL: {} for server: {}", baseUrl, serverInfo.getName());
        
        // Log SSE endpoint info for debugging
        String sseEndpoint = serverInfo.getSseEndpoint();
        if (sseEndpoint != null && !sseEndpoint.isEmpty()) {
            log.debug("📡 Server {} has custom SSE endpoint: {}", serverInfo.getName(), sseEndpoint);
        } else {
            log.debug("📡 Server {} will use default SSE endpoint: /sse", serverInfo.getName());
        }
        
        return baseUrl;
    }

    /**
     * 构建服务器键
     */
    private String buildServerKey(McpServerInfo serverInfo) {
        return String.format("%s:%s:%d", 
                serverInfo.getName(), serverInfo.getIp(), serverInfo.getPort());
    }

    /**
     * 解析工具调用结果
     */
    private Object parseToolResult(java.util.List<McpSchema.Content> content) {
        if (content == null || content.isEmpty()) {
            return Map.of("result", "No content returned");
        }

        // 如果只有一个文本内容，尝试解析为JSON
        if (content.size() == 1) {
            McpSchema.Content firstContent = content.get(0);
            if (firstContent instanceof McpSchema.TextContent textContent) {
                String text = textContent.text();
                try {
                    // 尝试解析为JSON对象
                    return objectMapper.readValue(text, Object.class);
                } catch (Exception e) {
                    // 如果不是JSON，返回原始文本
                    return Map.of("text", text);
                }
            }
        }

        // 处理多个内容或其他类型
        return Map.of("content", content);
    }

    /**
     * 安全关闭连接
     */
    private void closeConnectionSafely(McpAsyncClient client, String serverKey) {
        try {
            client.close();
            totalConnectionsClosed.incrementAndGet();
            log.debug("✅ Safely closed connection for server: {}", serverKey);
        } catch (Exception e) {
            log.error("❌ Error closing connection for server: {}", serverKey, e);
        }
    }

    /**
     * 关闭指定服务器的客户端连接
     */
    public void closeClient(String serverName) {
        connectionPool.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(serverName + ":")) {
                closeConnectionSafely(entry.getValue().getClient(), entry.getKey());
                return true;
            }
            return false;
        });
        log.info("✅ Closed all connections for server: {}", serverName);
    }

    /**
     * 关闭所有客户端连接
     */
    @PreDestroy
    public void closeAllClients() {
        log.info("🛑 Shutting down MCP connection pool...");
        
        connectionPool.forEach((serverKey, wrapper) -> {
            closeConnectionSafely(wrapper.getClient(), serverKey);
        });
        connectionPool.clear();
        
        log.info("✅ MCP connection pool shutdown completed. Stats: created={}, closed={}, requests={}", 
                totalConnectionsCreated.get(), totalConnectionsClosed.get(), totalConnectionRequests.get());
    }

    /**
     * 连接包装器类
     */
    private static class McpConnectionWrapper {
        private final McpAsyncClient client;
        private final McpServerInfo serverInfo;
        private final LocalDateTime createdAt;
        private volatile LocalDateTime lastUsed;

        public McpConnectionWrapper(McpAsyncClient client, McpServerInfo serverInfo, LocalDateTime createdAt) {
            this.client = client;
            this.serverInfo = serverInfo;
            this.createdAt = createdAt;
            this.lastUsed = createdAt;
        }

        public McpAsyncClient getClient() {
            return client;
        }

        public void updateLastUsed() {
            this.lastUsed = LocalDateTime.now();
        }

        public boolean isValid() {
            // 简单的有效性检查，可以扩展为更复杂的健康检查
            return client != null;
        }

        public boolean isExpired(LocalDateTime now, Duration idleTimeout, Duration maxLifetime) {
            // 检查是否超过空闲时间
            if (lastUsed.plus(idleTimeout).isBefore(now)) {
                return true;
            }
            
            // 检查是否超过最大生命周期
            if (createdAt.plus(maxLifetime).isBefore(now)) {
                return true;
            }
            
            return false;
    }
}
} 