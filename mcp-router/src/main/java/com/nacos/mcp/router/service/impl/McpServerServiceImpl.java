package com.nacos.mcp.router.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.McpServerRegistrationRequest;
import com.nacos.mcp.router.service.McpServerService;
import com.nacos.mcp.router.service.provider.SearchProvider;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Server Service Implementation
 * 使用标准的MCP协议和McpAsyncClient实现服务器通信
 */
@Service
@Slf4j
public class McpServerServiceImpl implements McpServerService {

    private final List<SearchProvider> searchProviders;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // 缓存MCP客户端连接，避免重复创建
    private final Map<String, McpAsyncClient> mcpClientCache = new ConcurrentHashMap<>();
    private final Map<String, McpServer> serverCache = new ConcurrentHashMap<>();

    @Autowired
    public McpServerServiceImpl(List<SearchProvider> searchProviders, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.searchProviders = searchProviders;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 发现MCP服务器
     */
    private Flux<McpServer> discoverMcpServers() {
        log.info("🔍 Discovering MCP servers via {} SearchProviders...", searchProviders.size());
        
        return Flux.fromIterable(searchProviders)
                .flatMap(provider -> provider.search(null))
                .flatMapIterable(servers -> servers)
                .doOnNext(server -> {
                    serverCache.put(server.getName(), server);
                    log.debug("Cached server: {}", server.getName());
                })
                .onErrorContinue((error, item) -> {
                    log.error("Error discovering servers from provider: {}", error.getMessage());
                });
    }

    /**
     * 使用工具 - 通过工具名称自动发现服务器
     */
    private Mono<Object> callTool(String toolName, Map<String, Object> params) {
        log.info("🔧 Calling tool '{}' with params: {}", toolName, params);

        return discoverMcpServers()
                .filter(server -> hasRequiredTool(server, toolName))
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("No MCP server found with tool: " + toolName)))
                .flatMap(server -> {
                    // 确保使用SSE传输协议
                    if (!server.getTransportType().equalsIgnoreCase("sse")) {
                        String errorMessage = "Protocol violation: Server must use SSE transport type, found: " + server.getTransportType();
                        log.error("❌ {}", errorMessage);
                        return Mono.error(new RuntimeException(errorMessage));
                    }

                    log.info("✅ Found server '{}' at endpoint: {}, using MCP SSE protocol", server.getName(), server.getEndpoint());

                    return callToolWithMcpClient(server, toolName, params);
                })
                .timeout(Duration.ofSeconds(30))
                .doOnError(e -> log.error("❌ Tool call failed for '{}': {}", toolName, e.getMessage()));
    }

    /**
     * 使用标准MCP客户端调用工具
     */
    private Mono<Object> callToolWithMcpClient(McpServer server, String toolName, Map<String, Object> params) {
        log.info("🚀 MCP client call for tool '{}' on server '{}'", toolName, server.getName());

        return getOrCreateMcpClient(server)
                .flatMap((McpAsyncClient client) -> {
                    // 构建工具调用请求
                    McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, params != null ? params : Map.of());
                    
                    log.info("📤 Sending tool call request: {}", request);
                    
                    return client.callTool(request)
                            .map((McpSchema.CallToolResult result) -> {
                                log.info("📥 Received tool call result: {}", result);
                                
                                if (result.isError() != null && result.isError()) {
                                    throw new RuntimeException("Tool execution error: " + result.content());
                                }
                                
                                return (Object) parseToolResult(result.content());
                            })
                            .doOnSuccess(result -> log.info("✅ Tool call successful for '{}'", toolName))
                            .doOnError(error -> log.error("❌ Tool call failed for '{}': {}", toolName, error.getMessage()));
                })
                .onErrorMap(e -> new RuntimeException("MCP call failed for tool '" + toolName + "' on server '" +
                        server.getName() + "': " + e.getMessage()));
    }

    /**
     * 获取或创建MCP客户端
     */
    private Mono<McpAsyncClient> getOrCreateMcpClient(McpServer server) {
        String serverKey = server.getName();
        
        // 检查缓存中是否已有客户端
        McpAsyncClient cachedClient = mcpClientCache.get(serverKey);
        if (cachedClient != null) {
            log.debug("Using cached MCP client for server: {}", serverKey);
            return Mono.just(cachedClient);
        }

        // 创建新的MCP客户端
        return Mono.fromCallable(() -> {
            log.info("🔗 Creating new MCP client for server: {}", serverKey);
            
            String serverBaseUrl = server.getEndpoint();
            if (!serverBaseUrl.startsWith("http://") && !serverBaseUrl.startsWith("https://")) {
                serverBaseUrl = "http://" + serverBaseUrl;
            }

            log.info("Using server URL: {}", serverBaseUrl);

            // 创建WebClient Builder
            WebClient.Builder clientBuilder = webClientBuilder
                    .clone()
                    .baseUrl(serverBaseUrl)
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024));

            // 创建SSE传输
            WebFluxSseClientTransport transport = new WebFluxSseClientTransport(clientBuilder, objectMapper);

            // 创建客户端信息
            McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                    "mcp-router-client",
                    "1.0.0"
            );

            // 构建异步MCP客户端
            McpAsyncClient client = McpClient.async(transport)
                    .clientInfo(clientInfo)
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();

            // 初始化客户端
            client.initialize().block(Duration.ofSeconds(30));
            
            // 缓存客户端
            mcpClientCache.put(serverKey, client);
            
            log.info("✅ MCP client created and initialized for server: {}", serverKey);
            return client;
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 解析工具调用结果
     */
    private Object parseToolResult(List<McpSchema.Content> content) {
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
            } else {
                return Map.of("type", firstContent.getClass().getSimpleName(), "content", firstContent.toString());
            }
        }

        // 多个内容，返回列表
        return content.stream()
                .map(contentItem -> {
                    if (contentItem instanceof McpSchema.TextContent textContent) {
                        return Map.of("type", "text", "text", textContent.text());
                    } else {
                        return Map.of("type", contentItem.getClass().getSimpleName(), "content", contentItem.toString());
                    }
                })
                .toList();
    }

    /**
     * 检查服务器是否有指定工具
     */
    private boolean hasRequiredTool(McpServer server, String toolName) {
        if (server.getTools() == null || server.getTools().isEmpty()) {
            log.debug("🔍 Server '{}' has no tools metadata", server.getName());
            return false;
        }

        boolean hasTool = server.getTools().stream()
                .anyMatch(tool -> tool.getName().equals(toolName));
        
        log.debug("🔍 Server '{}' has tool '{}': {}", server.getName(), toolName, hasTool);
        return hasTool;
    }

    @Override
    public Mono<Object> useTool(String toolName, Map<String, Object> params) {
        return callTool(toolName, params);
    }

    @Override
    public Mono<Object> useTool(String serverName, String toolName, Map<String, Object> params) {
        log.info("🔧 Calling tool '{}' on specific server '{}' with params: {}", toolName, serverName, params);

        return discoverMcpServers()
                .filter(server -> server.getName().equals(serverName))
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("Server not found: " + serverName)))
                .flatMap(server -> {
                    // 确保使用SSE传输协议
                    if (!server.getTransportType().equalsIgnoreCase("sse")) {
                        String errorMessage = "Protocol violation: Server must use SSE transport type, found: " + server.getTransportType();
                        log.error("❌ {}", errorMessage);
                        return Mono.error(new RuntimeException(errorMessage));
                    }

                    log.info("✅ Found server '{}' at endpoint: {}, using MCP SSE protocol", server.getName(), server.getEndpoint());

                    return callToolWithMcpClient(server, toolName, params);
                })
                .timeout(Duration.ofSeconds(30))
                .doOnError(e -> log.error("❌ Tool call failed for '{}' on server '{}': {}", toolName, serverName, e.getMessage()));
    }

    @Override
    public Mono<McpServer> registerMcpServer(McpServerRegistrationRequest request) {
        // TODO: 实现服务器注册逻辑
        return Mono.error(new UnsupportedOperationException("Server registration not implemented"));
    }

    @Override
    public Mono<McpServer> registerMcpServerWithTools(McpServerRegistrationRequest request) {
        // TODO: 实现带工具的服务器注册逻辑
        return Mono.error(new UnsupportedOperationException("Server registration with tools not implemented"));
    }

    @Override
    public Mono<McpServer> addMcpServer(String serverName) {
        // TODO: 实现添加服务器逻辑
        return Mono.error(new UnsupportedOperationException("Add server not implemented"));
    }

    @Override
    public Mono<McpServer> getMcpServer(String serverName) {
        log.info("🔍 Getting MCP server: {}", serverName);
        
        // 先检查缓存
        McpServer cachedServer = serverCache.get(serverName);
        if (cachedServer != null) {
            return Mono.just(cachedServer);
        }

        // 从服务发现中查找
        return discoverMcpServers()
                .filter(server -> server.getName().equals(serverName))
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("Server not found: " + serverName)));
    }

    @Override
    public Mono<McpServer> getServerByName(String serverName) {
        return getMcpServer(serverName);
    }

    @Override
    public Mono<Boolean> removeMcpServer(String serverName) {
        // 移除缓存的客户端
        McpAsyncClient client = mcpClientCache.remove(serverName);
        if (client != null) {
            try {
                client.close();
                log.info("✅ Closed MCP client for server: {}", serverName);
            } catch (Exception e) {
                log.error("❌ Error closing MCP client for server {}: {}", serverName, e.getMessage());
            }
        }
        
        serverCache.remove(serverName);
        return Mono.just(true);
    }

    @Override
    public Mono<Boolean> unregisterMcpServer(String serverName) {
        return removeMcpServer(serverName);
    }

    @Override
    public Mono<List<McpServer>> listAllMcpServers() {
        log.info("🔍 Listing all MCP servers");
        
        return discoverMcpServers()
                .collectList()
                .doOnSuccess(servers -> log.info("Found {} MCP servers", servers.size()));
    }

    @Override
    public Mono<Boolean> pingServer(String serverName) {
        return getMcpServer(serverName)
                .flatMap(server -> getOrCreateMcpClient(server))
                .map(client -> true)
                .onErrorReturn(false);
    }

    @Override
    public Mono<Boolean> updateServerHeartbeat(String serverName, Long timestamp, String status) {
        // TODO: 实现心跳更新逻辑
        return Mono.just(true);
    }

    @Override
    public Mono<List<McpServer>> searchMcpServers(String query) {
        return listAllMcpServers()
                .map(servers -> servers.stream()
                        .filter(server -> server.getName().contains(query) || 
                                         server.getDescription().contains(query))
                        .toList());
    }

    @Override
    public void registerServer(McpServerRegistrationRequest registrationRequest) {
        // TODO: 实现注册逻辑
    }

    @Override
    public Flux<McpServer> getRegisteredServers() {
        return discoverMcpServers();
    }

    @Override
    public Mono<Void> deregisterMcpServer(String serverName) {
        return removeMcpServer(serverName).then();
    }

    @Override
    public Mono<McpServer> getNextAvailableServer() {
        return listAllMcpServers()
                .map(servers -> servers.stream()
                        .filter(server -> server.getStatus() == McpServer.ServerStatus.CONNECTED)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No available servers")));
    }

    @Override
    public Mono<Void> recordHeartbeat(String serverName) {
        // TODO: 实现心跳记录逻辑
        return Mono.empty();
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        log.info("🧹 Cleaning up MCP clients...");
        
        mcpClientCache.values().forEach(client -> {
            try {
                client.close();
            } catch (Exception e) {
                log.error("Error closing MCP client: {}", e.getMessage());
            }
        });
        
        mcpClientCache.clear();
        serverCache.clear();
        
        log.info("✅ Cleanup completed");
    }
} 