package com.nacos.mcp.router.service;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Router服务
 * 实现基于MCP协议的路由功能
 */
@Service
public class McpRouterService {

    private static final Logger logger = LoggerFactory.getLogger(McpRouterService.class);

    private final Map<String, McpAsyncClient> mcpServerClients;

    public McpRouterService(Map<String, McpAsyncClient> mcpServerClients) {
        this.mcpServerClients = mcpServerClients;
    }

    /**
     * 路由工具调用到相应的MCP服务器
     */
    public Mono<Object> routeToolCall(String toolName, Map<String, Object> arguments) {
        logger.info("Routing tool call: {} with arguments: {}", toolName, arguments);
        
        return findAndCallTool(toolName, arguments)
                .doOnSuccess(result -> logger.info("Tool call successful: {}", toolName))
                .doOnError(error -> logger.error("Tool call failed: {}", toolName, error));
    }

    /**
     * 获取所有可用工具
     */
    public Mono<Map<String, List<String>>> getAllAvailableTools() {
        logger.info("Getting all available tools from MCP servers");
        
        return Mono.fromCallable(() -> {
            return mcpServerClients.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                try {
                                    McpSchema.ListToolsResult result = entry.getValue()
                                            .listTools()
                                            .block();
                                    return result.tools().stream()
                                            .map(McpSchema.Tool::name)
                                            .collect(Collectors.toList());
                                } catch (Exception e) {
                                    logger.error("Failed to list tools from server: {}", entry.getKey(), e);
                                    return List.<String>of();
                                }
                            }
                    ));
        });
    }

    /**
     * 获取服务器状态
     */
    public Mono<Map<String, Object>> getServerStatus() {
        logger.info("Getting MCP server status");
        
        return Mono.fromCallable(() -> {
            Map<String, Object> serverStatus = mcpServerClients.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                try {
                                    // 尝试列出工具来检查连接状态
                                    entry.getValue().listTools().block();
                                    return Map.of(
                                            "status", "connected",
                                            "clientInfo", entry.getValue().getClientInfo()
                                    );
                                } catch (Exception e) {
                                    return Map.of(
                                            "status", "disconnected",
                                            "error", e.getMessage()
                                    );
                                }
                            }
                    ));
            
            return Map.of(
                    "servers", serverStatus,
                    "totalServers", mcpServerClients.size(),
                    "timestamp", System.currentTimeMillis()
            );
        });
    }

    /**
     * 查找并调用工具
     */
    private Mono<Object> findAndCallTool(String toolName, Map<String, Object> arguments) {
        // 遍历所有MCP服务器客户端查找工具
        for (Map.Entry<String, McpAsyncClient> entry : mcpServerClients.entrySet()) {
            String serverName = entry.getKey();
            McpAsyncClient client = entry.getValue();
            
            try {
                // 先检查工具是否存在
                McpSchema.ListToolsResult toolsResult = client.listTools().block();
                boolean toolExists = toolsResult.tools().stream()
                        .anyMatch(tool -> tool.name().equals(toolName));
                
                if (toolExists) {
                    logger.info("Found tool {} on server {}", toolName, serverName);
                    
                    // 调用工具
                    McpSchema.CallToolRequest callRequest = new McpSchema.CallToolRequest(toolName, arguments);
                    return client.callTool(callRequest)
                            .map(result -> {
                                if (result.isError() != null && result.isError()) {
                                    throw new RuntimeException("Tool execution error: " + result.content());
                                }
                                return (Object) result.content();
                            });
                }
            } catch (Exception e) {
                logger.error("Error checking/calling tool {} on server {}", toolName, serverName, e);
            }
        }
        
        return Mono.error(new RuntimeException("Tool not found: " + toolName));
    }

    /**
     * 根据服务器名称路由工具调用
     */
    public Mono<Object> routeToolCallToServer(String serverName, String toolName, Map<String, Object> arguments) {
        logger.info("Routing tool call to specific server: {} - tool: {}", serverName, toolName);
        
        McpAsyncClient client = mcpServerClients.get(serverName);
        if (client == null) {
            return Mono.error(new RuntimeException("Server not found: " + serverName));
        }
        
        McpSchema.CallToolRequest callRequest = new McpSchema.CallToolRequest(toolName, arguments);
        return client.callTool(callRequest)
                .map(result -> {
                    if (result.isError() != null && result.isError()) {
                        throw new RuntimeException("Tool execution error: " + result.content());
                    }
                    return (Object) result.content();
                })
                .doOnSuccess(result -> logger.info("Tool call to server {} successful", serverName))
                .doOnError(error -> logger.error("Tool call to server {} failed", serverName, error));
    }
} 