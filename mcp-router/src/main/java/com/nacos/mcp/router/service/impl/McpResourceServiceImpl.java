package com.nacos.mcp.router.service.impl;

import com.nacos.mcp.router.model.McpResource;
import com.nacos.mcp.router.service.McpResourceService;
import com.nacos.mcp.router.service.McpServerService;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * MCP Resource Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpResourceServiceImpl implements McpResourceService {

    private final McpServerService mcpServerService;

    @Override
    public Mono<List<McpResource>> listResources(String serverName) {
        log.info("Listing resources for MCP server: {}", serverName);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议获取资源列表
                    return mcpServerService.useTool(serverName, "list_resources", Map.of())
                            .map(result -> {
                                if (result instanceof List) {
                                    return ((List<?>) result).stream()
                                            .map(item -> {
                                                if (item instanceof Map) {
                                                    Map<?, ?> map = (Map<?, ?>) item;
                                                    return McpResource.builder()
                                                            .uri(String.valueOf(map.get("uri")))
                                                            .mimeType(String.valueOf(map.get("type")))
                                                            .description(String.valueOf(map.get("description")))
                                                            .build();
                                                }
                                                return null;
                                            })
                                            .filter(resource -> resource != null)
                                            .toList();
                                }
                                return Collections.<McpResource>emptyList();
                            });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to list resources for server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<McpResource> readResource(String serverName, String resourceUri) {
        log.info("Reading resource {} from MCP server: {}", resourceUri, serverName);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议读取资源
                    return mcpServerService.useTool(serverName, "read_resource", Map.of(
                            "uri", resourceUri
                    ))
                    .map(result -> {
                        if (result instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) result;
                            return McpResource.builder()
                                    .uri(String.valueOf(map.get("uri")))
                                    .mimeType(String.valueOf(map.get("type")))
                                    .description(String.valueOf(map.get("description")))
                                    .contents(String.valueOf(map.get("content")))
                                    .build();
                        }
                        return null;
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to read resource {} from server {}: {}", resourceUri, serverName, throwable.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<McpResource> subscribeToResource(String serverName, String resourceUri) {
        log.info("Subscribing to resource {} from MCP server: {}", resourceUri, serverName);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议订阅资源
                    return mcpServerService.useTool(serverName, "subscribe_resource", Map.of(
                            "uri", resourceUri
                    ))
                    .map(result -> {
                        if (result instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) result;
                            return McpResource.builder()
                                    .uri(String.valueOf(map.get("uri")))
                                    .mimeType(String.valueOf(map.get("type")))
                                    .description(String.valueOf(map.get("description")))
                                    .contents(String.valueOf(map.get("content")))
                                    .build();
                        }
                        return null;
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to subscribe to resource {} from server {}: {}", resourceUri, serverName, throwable.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<List<McpResource>> searchResources(String pattern, String serverName) {
        log.info("Searching resources with pattern '{}' in server '{}'", pattern, serverName);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议搜索资源
                    return mcpServerService.useTool(serverName, "search_resources", Map.of(
                            "pattern", pattern
                    ))
                    .map(result -> {
                        if (result instanceof List) {
                            return ((List<?>) result).stream()
                                    .map(item -> {
                                        if (item instanceof Map) {
                                            Map<?, ?> map = (Map<?, ?>) item;
                                            return McpResource.builder()
                                                    .uri(String.valueOf(map.get("uri")))
                                                    .mimeType(String.valueOf(map.get("type")))
                                                    .description(String.valueOf(map.get("description")))
                                                    .build();
                                        }
                                        return null;
                                    })
                                    .filter(resource -> resource != null)
                                    .toList();
                        }
                        return Collections.<McpResource>emptyList();
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to search resources: {}", throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<List<McpResource>> listAllResources() {
        log.info("Listing all resources from all MCP servers");
        
        return mcpServerService.listAllMcpServers()
                .flatMap(servers -> {
                    // 从所有服务器获取资源
                    return Mono.just(servers.stream()
                            .flatMap(server -> {
                                try {
                                    return listResources(server.getName())
                                            .block()
                                            .stream();
                                } catch (Exception e) {
                                    log.warn("Failed to list resources from server {}: {}", 
                                            server.getName(), e.getMessage());
                                    return Stream.empty();
                                }
                            })
                            .toList());
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to list all resources: {}", throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }
} 
 