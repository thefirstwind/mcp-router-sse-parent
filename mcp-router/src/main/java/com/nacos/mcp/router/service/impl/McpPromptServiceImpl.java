package com.nacos.mcp.router.service.impl;

import com.nacos.mcp.router.model.McpPrompt;
import com.nacos.mcp.router.service.McpPromptService;
import com.nacos.mcp.router.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * MCP Prompt Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpPromptServiceImpl implements McpPromptService {

    private final McpServerService mcpServerService;

    @Override
    public Mono<List<McpPrompt>> listPrompts(String serverName) {
        log.info("Listing prompts for MCP server: {}", serverName);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议获取提示列表
                    return mcpServerService.useTool(serverName, "list_prompts", Map.of())
                            .map(result -> {
                                if (result instanceof List) {
                                    return ((List<?>) result).stream()
                                            .map(item -> {
                                                if (item instanceof Map) {
                                                    Map<?, ?> map = (Map<?, ?>) item;
                                                    return McpPrompt.builder()
                                                            .name(String.valueOf(map.get("name")))
                                                            .description(String.valueOf(map.get("description")))
                                                            .template(String.valueOf(map.get("template")))
                                                            .build();
                                                }
                                                return null;
                                            })
                                            .filter(prompt -> prompt != null)
                                            .toList();
                                }
                                return Collections.<McpPrompt>emptyList();
                            });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to list prompts for server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<List<McpPrompt>> listAllPrompts() {
        log.info("Listing all prompts from all MCP servers");
        
        return mcpServerService.listAllMcpServers()
                .flatMap(servers -> {
                    // 从所有服务器获取提示
                    return Mono.just(servers.stream()
                            .flatMap(server -> {
                                try {
                                    return listPrompts(server.getName())
                                            .block()
                                            .stream();
                                } catch (Exception e) {
                                    log.warn("Failed to list prompts from server {}: {}", 
                                            server.getName(), e.getMessage());
                                    return Stream.empty();
                                }
                            })
                            .toList());
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to list all prompts: {}", throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<McpPrompt> getPrompt(String serverName, String promptName, Map<String, Object> arguments) {
        log.info("Getting prompt {} from server {} with arguments: {}", promptName, serverName, arguments);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议获取提示
                    return mcpServerService.useTool(serverName, "get_prompt", Map.of(
                            "name", promptName,
                            "arguments", arguments
                    ))
                    .map(result -> {
                        if (result instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) result;
                            return McpPrompt.builder()
                                    .name(String.valueOf(map.get("name")))
                                    .description(String.valueOf(map.get("description")))
                                    .template(String.valueOf(map.get("template")))
                                    .build();
                        }
                        return null;
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to get prompt {} from server {}: {}", promptName, serverName, throwable.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<List<McpPrompt.PromptMessage>> executePrompt(String serverName, String promptName, Map<String, Object> arguments) {
        log.info("Executing prompt {} from server {} with arguments: {}", promptName, serverName, arguments);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议执行提示
                    return mcpServerService.useTool(serverName, "execute_prompt", Map.of(
                            "name", promptName,
                            "arguments", arguments
                    ))
                    .map(result -> {
                        if (result instanceof List) {
                            return ((List<?>) result).stream()
                                    .map(item -> {
                                        if (item instanceof Map) {
                                            Map<?, ?> map = (Map<?, ?>) item;
                                            return McpPrompt.PromptMessage.builder()
                                                    .role(String.valueOf(map.get("role")))
                                                    .content(String.valueOf(map.get("content")))
                                                    .build();
                                        }
                                        return null;
                                    })
                                    .filter(message -> message != null)
                                    .toList();
                        }
                        return Collections.<McpPrompt.PromptMessage>emptyList();
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to execute prompt {} from server {}: {}", promptName, serverName, throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<List<McpPrompt>> searchPrompts(String pattern, String serverName) {
        log.info("Searching prompts with pattern '{}' in server '{}'", pattern, serverName);
        
        return mcpServerService.getMcpServer(serverName)
                .flatMap(server -> {
                    // 使用MCP SSE协议搜索提示
                    return mcpServerService.useTool(serverName, "search_prompts", Map.of(
                            "pattern", pattern
                    ))
                    .map(result -> {
                        if (result instanceof List) {
                            return ((List<?>) result).stream()
                                    .map(item -> {
                                        if (item instanceof Map) {
                                            Map<?, ?> map = (Map<?, ?>) item;
                                            return McpPrompt.builder()
                                                    .name(String.valueOf(map.get("name")))
                                                    .description(String.valueOf(map.get("description")))
                                                    .template(String.valueOf(map.get("template")))
                                                    .build();
                                        }
                                        return null;
                                    })
                                    .filter(prompt -> prompt != null)
                                    .toList();
                        }
                        return Collections.<McpPrompt>emptyList();
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to search prompts: {}", throwable.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<Boolean> validatePromptArguments(String promptName, Map<String, Object> arguments) {
        log.info("Validating arguments for prompt {}: {}", promptName, arguments);
        
        // 使用MCP SSE协议验证参数
        return mcpServerService.getNextAvailableServer()
                .flatMap(server -> {
                    return mcpServerService.useTool(server.getName(), "validate_prompt_arguments", Map.of(
                            "name", promptName,
                            "arguments", arguments
                    ))
                    .map(result -> {
                        if (result instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) result;
                            return Boolean.TRUE.equals(map.get("valid"));
                        }
                        return false;
                    });
                })
                .onErrorResume(throwable -> {
                    log.error("Failed to validate prompt arguments: {}", throwable.getMessage());
                    return Mono.just(false);
                });
    }
}