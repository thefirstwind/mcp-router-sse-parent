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
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
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
        return createNewConnection(serverInfo)
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
        return Mono.fromCallable(() -> {
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

            // 初始化客户端
            client.initialize().block(CONNECTION_TIMEOUT);
            
            log.debug("✅ MCP connection created and initialized for server: {}", serverInfo.getName());
            return client;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }



    /**
     * 调用远程 MCP 服务器的工具
     */
    public Mono<Object> callTool(McpServerInfo serverInfo, String toolName, Map<String, Object> arguments) {
        log.debug("🔧 Calling tool '{}' on server '{}' via connection pool", toolName, serverInfo.getName());

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
                .timeout(Duration.ofSeconds(30))
                .onErrorMap(e -> new RuntimeException("MCP call failed for tool '" + toolName + "' on server '" +
                        serverInfo.getName() + "': " + e.getMessage()));
    }

    /**
     * 获取服务器的可用工具列表
     */
    public Mono<McpSchema.ListToolsResult> listTools(McpServerInfo serverInfo) {
        log.debug("📋 Listing tools for server via connection pool: {}", serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(McpAsyncClient::listTools)
                .doOnSuccess(tools -> log.debug("✅ Listed {} tools via pool for server: {}", 
                        tools.tools().size(), serverInfo.getName()))
                .doOnError(error -> {
                    log.error("❌ Failed to list tools via pool for server: {}", 
                            serverInfo.getName(), error);
                    invalidateConnection(serverInfo);
                });
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