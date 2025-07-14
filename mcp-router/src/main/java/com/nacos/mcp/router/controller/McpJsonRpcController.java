package com.nacos.mcp.router.controller;

import com.nacos.mcp.router.model.*;
import com.nacos.mcp.router.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

/**
 * MCP JSON-RPC 2.0 Protocol Controller
 * Implements standard MCP methods according to specification
 *
 * This controller provides a compliant MCP server implementation that can
 * communicate with standard MCP clients like Claude Desktop
 */
@Slf4j
@RestController
@RequestMapping("/mcp/jsonrpc")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class McpJsonRpcController {

    private final McpServerService mcpServerService;
    private final McpResourceService mcpResourceService;
    private final McpPromptService mcpPromptService;
    private final ObjectMapper objectMapper;

    /**
     * Main JSON-RPC 2.0 endpoint
     * This endpoint handles all MCP protocol communications
     */
    @PostMapping
    public Mono<ResponseEntity<McpJsonRpcResponse>> handleJsonRpc(@RequestBody McpJsonRpcRequest request) {
        log.info("Received JSON-RPC request: method={}, id={}", request.getMethod(), request.getId());

        return processRequest(request)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("JSON-RPC error: {}", throwable.getMessage(), throwable);
                    McpJsonRpcResponse errorResponse = McpJsonRpcResponse.error(
                            McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                            throwable.getMessage(),
                            request.getId()
                    );
                    return Mono.just(ResponseEntity.ok(errorResponse));
                });
    }

    private Mono<McpJsonRpcResponse> processRequest(McpJsonRpcRequest request) {
        try {
            switch (request.getMethod()) {
                // Core MCP methods - required for MCP compliance
                case "initialize":
                    return handleInitialize(request);
                case "notifications/initialized":
                    return handleInitialized(request);

                // Tools methods - for function calling
                case "tools/list":
                    return handleToolsList(request);
                case "tools/call":
                    return handleToolsCall(request);

                // Resources methods - for data access
                case "resources/list":
                    return handleResourcesList(request);
                case "resources/read":
                    return handleResourcesRead(request);

                // Prompts methods - for template management
                case "prompts/list":
                    return handlePromptsList(request);
                case "prompts/get":
                    return handlePromptsGet(request);

                default:
                    return Mono.just(McpJsonRpcResponse.error(
                            McpJsonRpcResponse.ErrorCodes.METHOD_NOT_FOUND,
                            "Method not found: " + request.getMethod(),
                            request.getId()
                    ));
            }
        } catch (Exception e) {
            log.error("Error processing request: {}", e.getMessage(), e);
            return Mono.just(McpJsonRpcResponse.error(
                    McpJsonRpcResponse.ErrorCodes.INVALID_REQUEST,
                    "Invalid request: " + e.getMessage(),
                    request.getId()
            ));
        }
    }

    // ==================== CORE METHODS ====================

    private Mono<McpJsonRpcResponse> handleInitialize(McpJsonRpcRequest request) {
        log.info("Handling initialize request - establishing MCP session");

        Map<String, Object> result = new HashMap<>();

        // MCP protocol version
        result.put("protocolVersion", "2024-11-05");

        // Server capabilities - what this server can do
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", true));
        capabilities.put("resources", Map.of("subscribe", true, "listChanged", true));
        capabilities.put("prompts", Map.of("listChanged", true));
        capabilities.put("logging", Map.of());
        result.put("capabilities", capabilities);

        // Server information
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "nacos-mcp-router");
        serverInfo.put("version", "1.0.0");
        serverInfo.put("description", "Nacos MCP Router - Bridge between MCP clients and microservices");
        result.put("serverInfo", serverInfo);

        log.info("MCP session initialized successfully");
        return Mono.just(McpJsonRpcResponse.success(result, request.getId()));
    }

    private Mono<McpJsonRpcResponse> handleInitialized(McpJsonRpcRequest request) {
        log.info("Handling initialized notification - MCP session ready");
        // This is a notification, no response needed
        if (request.isNotification()) {
            return Mono.empty();
        }
        return Mono.just(McpJsonRpcResponse.success(null, request.getId()));
    }

    // ==================== TOOLS METHODS ====================

    private Mono<McpJsonRpcResponse> handleToolsList(McpJsonRpcRequest request) {
        log.info("Handling tools/list request - returning available tools");

        return mcpServerService.listAllMcpServers()
                .map(servers -> {
                    List<McpTool> allTools = servers.stream()
                            .filter(server -> server.getStatus() == McpServer.ServerStatus.CONNECTED)
                            .flatMap(server -> server.getTools().stream())
                            .toList();

                    // Add built-in demonstration tools
                    List<McpTool> builtInTools = createBuiltInTools();
                    allTools = new java.util.ArrayList<>(allTools);
                    allTools.addAll(builtInTools);

                    Map<String, Object> result = new HashMap<>();
                    result.put("tools", allTools);

                    log.info("Returning {} tools to client", allTools.size());
                    return McpJsonRpcResponse.success(result, request.getId());
                })
                .onErrorReturn(McpJsonRpcResponse.error(
                        McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                        "Failed to list tools",
                        request.getId()
                ));
    }

    private Mono<McpJsonRpcResponse> handleToolsCall(McpJsonRpcRequest request) {
        log.info("Handling tools/call request - executing tool via intelligent routing");

        try {
            Map<String, Object> params = (Map<String, Object>) request.getParams();
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            if (toolName == null || toolName.isEmpty()) {
                return Mono.just(McpJsonRpcResponse.error(
                        McpJsonRpcResponse.ErrorCodes.INVALID_PARAMS,
                        "Tool name ('name') is missing in params.",
                        request.getId()));
            }

            log.info("Intelligently routing tool call: {} with arguments: {}", toolName, arguments);

            // Handle built-in demonstration tools first
            if (isBuiltInTool(toolName)) {
                return handleBuiltInToolCall(toolName, arguments, request.getId());
            }

            // Delegate to the service layer to find the server and execute the tool
            return mcpServerService.useTool(toolName, arguments)
                    .map(result -> {
                        // 直接返回原始数据，不要包装
                        log.info("Tool execution for '{}' completed successfully via intelligent routing.", toolName);
                        return McpJsonRpcResponse.success(result, request.getId());
                    })
                    .onErrorResume(e -> {
                        log.error("Failed to execute tool '{}' via intelligent routing: {}", toolName, e.getMessage());
                        // Check if the error is because the tool was not found
                        if (e.getMessage() != null && e.getMessage().contains("No server found providing tool")) {
                            return Mono.just(McpJsonRpcResponse.error(
                                    McpJsonRpcResponse.ErrorCodes.TOOL_NOT_FOUND,
                                    "Tool not found: " + toolName,
                                    request.getId()));
                        }
                        // Handle other potential errors (e.g., tool execution failed)
                        return Mono.just(McpJsonRpcResponse.error(
                                McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                                "Error executing tool '" + toolName + "': " + e.getMessage(),
                                request.getId()));
                    });

        } catch (Exception e) {
            log.error("Error parsing tool call request: {}", e.getMessage(), e);
            return Mono.just(McpJsonRpcResponse.error(
                    McpJsonRpcResponse.ErrorCodes.INVALID_PARAMS,
                    "Invalid parameters for tools/call: " + e.getMessage(),
                    request.getId()
            ));
        }
    }

    // ==================== BUILT-IN TOOLS ====================

    private List<McpTool> createBuiltInTools() {
        return Arrays.asList(
                McpTool.builder()
                        .name("get_system_info")
                        .description("Get information about the MCP router system")
                        .inputSchema(McpTool.InputSchema.builder()
                                .type("object")
                                .properties(Map.of())
                                .required(List.of())
                                .build())
                        .build(),
                McpTool.builder()
                        .name("list_servers")
                        .description("List all registered MCP servers")
                        .inputSchema(McpTool.InputSchema.builder()
                                .type("object")
                                .properties(Map.of())
                                .required(List.of())
                                .build())
                        .build(),
                McpTool.builder()
                        .name("ping_server")
                        .description("Ping a specific MCP server to check its status")
                        .inputSchema(McpTool.InputSchema.builder()
                                .type("object")
                                .properties(Map.of(
                                        "serverName", McpTool.Property.builder()
                                                .type("string")
                                                .description("Name of the server to ping")
                                                .build()
                                ))
                                .required(List.of("serverName"))
                                .build())
                        .build()
        );
    }

    private boolean isBuiltInTool(String toolName) {
        return Arrays.asList("get_system_info", "list_servers", "ping_server").contains(toolName);
    }

    private Mono<McpJsonRpcResponse> handleBuiltInToolCall(String toolName, Map<String, Object> arguments, Object requestId) {
        return switch (toolName) {
            case "get_system_info" -> {
                Map<String, Object> systemInfo = Map.of(
                        "name", "Nacos MCP Router",
                        "version", "1.0.0",
                        "description", "Bridge between MCP clients and microservices using Nacos service discovery",
                        "uptime", System.currentTimeMillis(),
                        "features", Arrays.asList("Service Discovery", "Load Balancing", "Tool Routing", "Resource Management")
                );

                Map<String, Object> response = Map.of(
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", "System Information:\n" + formatMapAsText(systemInfo)
                        )),
                        "isError", false
                );
                yield Mono.just(McpJsonRpcResponse.success(response, requestId));
            }

            case "list_servers" -> mcpServerService.listAllMcpServers()
                    .map(servers -> {
                        String serverList = servers.stream()
                                .map(server -> String.format("- %s (%s) - %d tools available",
                                        server.getName(), server.getStatus(), server.getTools().size()))
                                .reduce("Available MCP Servers:\n", (acc, server) -> acc + server + "\n");

                        Map<String, Object> response = Map.of(
                                "content", List.of(Map.of(
                                        "type", "text",
                                        "text", serverList
                                )),
                                "isError", false
                        );
                        return McpJsonRpcResponse.success(response, requestId);
                    });

            case "ping_server" -> {
                String serverName = (String) arguments.get("serverName");
                yield mcpServerService.pingServer(serverName)
                        .map(isOnline -> {
                            String status = isOnline ? "ONLINE" : "OFFLINE";
                            Map<String, Object> response = Map.of(
                                    "content", List.of(Map.of(
                                            "type", "text",
                                            "text", String.format("Server '%s' is %s", serverName, status)
                                    )),
                                    "isError", false
                            );
                            return McpJsonRpcResponse.success(response, requestId);
                        })
                        .onErrorReturn(McpJsonRpcResponse.error(
                                McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                                "Failed to ping server: " + serverName,
                                requestId
                        ));
            }

            default -> Mono.just(McpJsonRpcResponse.error(
                    McpJsonRpcResponse.ErrorCodes.TOOL_NOT_FOUND,
                    "Unknown built-in tool: " + toolName,
                    requestId
            ));
        };
    }

    private String formatMapAsText(Map<String, Object> map) {
        return map.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .reduce("", (acc, line) -> acc + line + "\n");
    }

    // ==================== RESOURCES METHODS ====================

    private Mono<McpJsonRpcResponse> handleResourcesList(McpJsonRpcRequest request) {
        log.info("Handling resources/list request");

        return mcpResourceService.listAllResources()
                .map(resources -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("resources", resources);

                    return McpJsonRpcResponse.success(result, request.getId());
                })
                .onErrorReturn(McpJsonRpcResponse.error(
                        McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                        "Failed to list resources",
                        request.getId()
                ));
    }

    @SuppressWarnings("unchecked")
    private Mono<McpJsonRpcResponse> handleResourcesRead(McpJsonRpcRequest request) {
        log.info("Handling resources/read request");

        try {
            Map<String, Object> params = (Map<String, Object>) request.getParams();
            String uri = (String) params.get("uri");

            // Extract server name from URI (simplified)
            String serverName = extractServerNameFromUri(uri);

            return mcpResourceService.readResource(serverName, uri)
                    .map(resource -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("contents", List.of(Map.of(
                                "uri", resource.getUri(),
                                "mimeType", resource.getMimeType(),
                                "text", resource.getContents()
                        )));

                        return McpJsonRpcResponse.success(result, request.getId());
                    })
                    .onErrorReturn(McpJsonRpcResponse.error(
                            McpJsonRpcResponse.ErrorCodes.RESOURCE_NOT_FOUND,
                            "Resource not found: " + uri,
                            request.getId()
                    ));
        } catch (Exception e) {
            log.error("Error reading resource: {}", e.getMessage(), e);
            return Mono.just(McpJsonRpcResponse.error(
                    McpJsonRpcResponse.ErrorCodes.INVALID_PARAMS,
                    "Invalid parameters: " + e.getMessage(),
                    request.getId()
            ));
        }
    }

    // ==================== PROMPTS METHODS ====================

    private Mono<McpJsonRpcResponse> handlePromptsList(McpJsonRpcRequest request) {
        log.info("Handling prompts/list request");

        return mcpPromptService.listAllPrompts()
                .map(prompts -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("prompts", prompts);

                    return McpJsonRpcResponse.success(result, request.getId());
                })
                .onErrorReturn(McpJsonRpcResponse.error(
                        McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                        "Failed to list prompts",
                        request.getId()
                ));
    }

    @SuppressWarnings("unchecked")
    private Mono<McpJsonRpcResponse> handlePromptsGet(McpJsonRpcRequest request) {
        log.info("Handling prompts/get request");

        try {
            Map<String, Object> params = (Map<String, Object>) request.getParams();
            String name = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            // For simplicity, use first server name
            return mcpServerService.listAllMcpServers()
                    .flatMap(servers -> {
                        if (!servers.isEmpty()) {
                            String serverName = servers.get(0).getName();
                            return mcpPromptService.getPrompt(serverName, name, arguments)
                                    .map(prompt -> {
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("description", prompt.getDescription());
                                        result.put("messages", prompt.getMessages());

                                        return McpJsonRpcResponse.success(result, request.getId());
                                    });
                        } else {
                            return Mono.just(McpJsonRpcResponse.error(
                                    McpJsonRpcResponse.ErrorCodes.PROMPT_NOT_FOUND,
                                    "No servers available",
                                    request.getId()
                            ));
                        }
                    })
                    .onErrorReturn(McpJsonRpcResponse.error(
                            McpJsonRpcResponse.ErrorCodes.PROMPT_NOT_FOUND,
                            "Prompt not found: " + name,
                            request.getId()
                    ));
        } catch (Exception e) {
            log.error("Error getting prompt: {}", e.getMessage(), e);
            return Mono.just(McpJsonRpcResponse.error(
                    McpJsonRpcResponse.ErrorCodes.INVALID_PARAMS,
                    "Invalid parameters: " + e.getMessage(),
                    request.getId()
            ));
        }
    }

    // ==================== HELPER METHODS ====================

    private String extractServerNameFromUri(String uri) {
        // Simple URI parsing - in a real implementation this would be more sophisticated
        if (uri.startsWith("file://")) {
            return "mcp-filesystem-server";
        } else if (uri.startsWith("db://")) {
            return "mcp-database-server";
        } else if (uri.startsWith("git://")) {
            return "mcp-git-server";
        } else {
            return "mcp-default-server";
        }
    }
} 