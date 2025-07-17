package com.nacos.mcp.router.v2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.v2.model.McpServerInfo;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端管理器
 * 负责管理到远程 MCP 服务器的连接
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientManager {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    // MCP 客户端缓存
    private final Map<String, McpAsyncClient> mcpClientCache = new ConcurrentHashMap<>();

    /**
     * 获取或创建 MCP 客户端
     */
    public Mono<McpAsyncClient> getOrCreateMcpClient(McpServerInfo serverInfo) {
        if (serverInfo == null) {
            return Mono.error(new IllegalArgumentException("ServerInfo cannot be null"));
        }
        
        String serverKey = serverInfo.getName();
        if (serverKey == null || serverKey.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Server name cannot be null or empty"));
        }
        
        // 检查缓存中是否已有客户端
        McpAsyncClient cachedClient = mcpClientCache.get(serverKey);
        if (cachedClient != null) {
            log.debug("Using cached MCP client for server: {}", serverKey);
            return Mono.just(cachedClient);
        }

        // 创建新的MCP客户端
        return Mono.fromCallable(() -> {
            log.info("🔗 Creating new MCP client for server: {}", serverKey);
            
            String serverBaseUrl = buildServerUrl(serverInfo);
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
                    "mcp-router-v2-client",
                    "2.0.0"
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
     * 调用远程 MCP 服务器的工具
     */
    public Mono<Object> callTool(McpServerInfo serverInfo, String toolName, Map<String, Object> arguments) {
        log.info("🔧 Calling tool '{}' on server '{}' with params: {}", toolName, serverInfo.getName(), arguments);

        return getOrCreateMcpClient(serverInfo)
                .flatMap(client -> {
                    // 构建工具调用请求
                    McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                            toolName, 
                            arguments != null ? arguments : Map.of()
                    );
                    
                    log.info("📤 Sending tool call request: {}", request);
                    
                    return client.callTool(request)
                            .map(result -> {
                                log.info("📥 Received tool call result: {}", result);
                                
                                if (result.isError() != null && result.isError()) {
                                    throw new RuntimeException("Tool execution error: " + result.content());
                                }
                                
                                return parseToolResult(result.content());
                            })
                            .doOnSuccess(result -> log.info("✅ Tool call successful for '{}'", toolName))
                            .doOnError(error -> log.error("❌ Tool call failed for '{}': {}", toolName, error.getMessage()));
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorMap(e -> new RuntimeException("MCP call failed for tool '" + toolName + "' on server '" +
                        serverInfo.getName() + "': " + e.getMessage()));
    }

    /**
     * 获取服务器的可用工具列表
     */
    public Mono<McpSchema.ListToolsResult> listTools(McpServerInfo serverInfo) {
        log.info("📋 Listing tools for server: {}", serverInfo.getName());

        return getOrCreateMcpClient(serverInfo)
                .flatMap(McpAsyncClient::listTools)
                .doOnSuccess(tools -> log.info("✅ Listed {} tools for server: {}", 
                        tools.tools().size(), serverInfo.getName()))
                .doOnError(error -> log.error("❌ Failed to list tools for server: {}", 
                        serverInfo.getName(), error));
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
     * 构建服务器 URL
     */
    private String buildServerUrl(McpServerInfo serverInfo) {
        String serverUrl = String.format("http://%s:%d", serverInfo.getIp(), serverInfo.getPort());
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://" + serverUrl;
        }
        return serverUrl;
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
     * 关闭指定服务器的客户端连接
     */
    public void closeClient(String serverName) {
        McpAsyncClient client = mcpClientCache.remove(serverName);
        if (client != null) {
            try {
                client.close();
                log.info("✅ Closed MCP client for server: {}", serverName);
            } catch (Exception e) {
                log.error("❌ Error closing MCP client for server: {}", serverName, e);
            }
        }
    }

    /**
     * 关闭所有客户端连接
     */
    public void closeAllClients() {
        mcpClientCache.forEach((serverName, client) -> {
            try {
                client.close();
                log.info("✅ Closed MCP client for server: {}", serverName);
            } catch (Exception e) {
                log.error("❌ Error closing MCP client for server: {}", serverName, e);
            }
        });
        mcpClientCache.clear();
    }
} 