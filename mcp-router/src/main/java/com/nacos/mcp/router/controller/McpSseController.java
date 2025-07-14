package com.nacos.mcp.router.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.model.*;
import com.nacos.mcp.router.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP SSE (Server-Sent Events) Controller
 * 提供基于SSE的MCP协议支持，兼容标准MCP客户端
 * 
 * 该控制器同时支持：
 * 1. SSE连接建立和维护
 * 2. MCP JSON-RPC消息处理
 * 3. 双向通信（通过SSE + POST）
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class McpSseController {

    private final McpServerService mcpServerService;
    private final McpResourceService mcpResourceService;
    private final McpPromptService mcpPromptService;
    private final ObjectMapper objectMapper;

    // 管理活跃的SSE连接
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> activeConnections = new ConcurrentHashMap<>();

    // Enhanced session management with lifecycle tracking
    private final Map<String, SseSession> activeSessions = new ConcurrentHashMap<>();
    
    // Session class for better management
    @Data
    @Builder
    public static class SseSession {
        private final String sessionId;
        private final String clientId;
        private final Sinks.Many<ServerSentEvent<String>> sink;
        private final LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private SessionStatus status;
        private Map<String, Object> metadata;
        
        public enum SessionStatus {
            ACTIVE, IDLE, EXPIRED, CLOSED
        }
    }

    /**
     * Enhanced SSE endpoint with session management
     */
    @GetMapping(value = "/jsonrpc/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> establishSseConnection(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String authToken) {
        
        String sessionId = "session-" + System.currentTimeMillis();
        String connectionId = clientId != null ? clientId : "client-" + System.currentTimeMillis();
        
        log.info("Establishing SSE connection: sessionId={}, clientId={}", sessionId, connectionId);

        // Validate authentication if provided
        if (authToken != null && !validateAuthToken(authToken)) {
            return Flux.error(new RuntimeException("Invalid authentication token"));
        }

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        SseSession session = SseSession.builder()
            .sessionId(sessionId)
            .clientId(connectionId)
            .sink(sink)
            .createdAt(LocalDateTime.now())
            .lastActivity(LocalDateTime.now())
            .status(SseSession.SessionStatus.ACTIVE)
            .metadata(new HashMap<>())
            .build();
            
        activeSessions.put(sessionId, session);
        activeConnections.put(connectionId, sink); // Keep backward compatibility

        // 发送连接确认消息
        try {
            Map<String, Object> connectMessage = Map.of(
                "type", "connection",
                "status", "established",
                "clientId", connectionId,
                "timestamp", System.currentTimeMillis()
            );
            
            ServerSentEvent<String> connectEvent = ServerSentEvent.<String>builder()
                .event("connection")
                .data(objectMapper.writeValueAsString(connectMessage))
                .build();
                
            sink.tryEmitNext(connectEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to send connection message", e);
        }

        // 定期发送心跳
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(30))
            .map(sequence -> {
                try {
                    Map<String, Object> heartbeatMessage = Map.of(
                        "type", "heartbeat",
                        "timestamp", System.currentTimeMillis(),
                        "sequence", sequence
                    );
                    return ServerSentEvent.<String>builder()
                        .event("heartbeat")
                        .data(objectMapper.writeValueAsString(heartbeatMessage))
                        .build();
                } catch (JsonProcessingException e) {
                    log.error("Failed to create heartbeat message", e);
                    return ServerSentEvent.<String>builder()
                        .event("heartbeat")
                        .data("{\"type\":\"heartbeat\",\"error\":\"serialization_failed\"}")
                        .build();
                }
            });

        // 合并消息流和心跳流
        return Flux.merge(sink.asFlux(), heartbeat)
            .doOnCancel(() -> {
                log.info("SSE connection cancelled for client: {}", connectionId);
                activeConnections.remove(connectionId);
            })
            .doOnError(error -> {
                log.error("SSE connection error for client {}: {}", connectionId, error.getMessage());
                activeConnections.remove(connectionId);
            });
    }

    /**
     * MCP消息处理端点 - 处理来自客户端的JSON-RPC请求
     * 客户端通过POST发送消息，响应通过SSE连接返回
     */
    @PostMapping("/jsonrpc/message")
    public Mono<Map<String, Object>> handleMcpMessage(
            @RequestBody McpJsonRpcRequest request,
            @RequestParam(required = false) String clientId) {
        
        String connectionId = clientId != null ? clientId : "unknown";
        log.info("Received MCP message from client {}: method={}, id={}", connectionId, request.getMethod(), request.getId());

        return processJsonRpcRequest(request)
            .flatMap(response -> {
                // 通过SSE发送响应
                sendResponseViaSSE(connectionId, response);
                
                // 返回简单的确认
                Map<String, Object> ack = new HashMap<>();
                ack.put("status", "received");
                ack.put("messageId", request.getId());
                ack.put("timestamp", System.currentTimeMillis());
                return Mono.just(ack);
            })
            .onErrorResume(error -> {
                log.error("Error processing MCP message from client {}: {}", connectionId, error.getMessage(), error);
                
                // 发送错误响应
                McpJsonRpcResponse errorResponse = McpJsonRpcResponse.error(
                    McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                    error.getMessage(),
                    request.getId()
                );
                sendResponseViaSSE(connectionId, errorResponse);
                
                Map<String, Object> errorAck = new HashMap<>();
                errorAck.put("status", "error");
                errorAck.put("messageId", request.getId());
                errorAck.put("error", error.getMessage());
                return Mono.just(errorAck);
            });
    }

    /**
     * 通过SSE连接发送响应
     */
    private void sendResponseViaSSE(String clientId, McpJsonRpcResponse response) {
        Sinks.Many<ServerSentEvent<String>> connection = activeConnections.get(clientId);
        if (connection != null) {
            try {
                ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                    .event("mcp-response")
                    .data(objectMapper.writeValueAsString(response))
                    .build();
                    
                connection.tryEmitNext(event);
                log.debug("Sent response via SSE to client {}: {}", clientId, response.getId());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize response for SSE: {}", e.getMessage());
            }
        } else {
            log.warn("No active SSE connection found for client: {}", clientId);
        }
    }

    /**
     * 处理JSON-RPC请求 - 复用现有的JSON-RPC处理逻辑
     */
    private Mono<McpJsonRpcResponse> processJsonRpcRequest(McpJsonRpcRequest request) {
        try {
            switch (request.getMethod()) {
                case "initialize":
                    return handleInitialize(request);
                case "notifications/initialized":
                    return handleInitialized(request);
                case "tools/list":
                    return handleToolsList(request);
                case "tools/call":
                    return handleToolsCall(request);
                case "resources/list":
                    return handleResourcesList(request);
                case "resources/read":
                    return handleResourcesRead(request);
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
            log.error("Error processing JSON-RPC request: {}", e.getMessage(), e);
            return Mono.just(McpJsonRpcResponse.error(
                McpJsonRpcResponse.ErrorCodes.INVALID_REQUEST,
                "Invalid request: " + e.getMessage(),
                request.getId()
            ));
        }
    }

    // ==================== MCP协议处理方法 ====================
    // 以下方法复用McpJsonRpcController中的逻辑

    private Mono<McpJsonRpcResponse> handleInitialize(McpJsonRpcRequest request) {
        log.info("Handling initialize request via SSE");
        
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", true));
        capabilities.put("resources", Map.of("subscribe", true, "listChanged", true));
        capabilities.put("prompts", Map.of("listChanged", true));
        capabilities.put("logging", Map.of());
        result.put("capabilities", capabilities);
        
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "nacos-mcp-router-sse");
        serverInfo.put("version", "1.0.0");
        serverInfo.put("description", "Nacos MCP Router with SSE Support");
        result.put("serverInfo", serverInfo);
        
        return Mono.just(McpJsonRpcResponse.success(result, request.getId()));
    }

    private Mono<McpJsonRpcResponse> handleInitialized(McpJsonRpcRequest request) {
        log.info("Handling initialized notification via SSE");
        if (request.isNotification()) {
            return Mono.empty();
        }
        return Mono.just(McpJsonRpcResponse.success(null, request.getId()));
    }

    private Mono<McpJsonRpcResponse> handleToolsList(McpJsonRpcRequest request) {
        log.info("Handling tools/list request via SSE");
        
        return mcpServerService.listAllMcpServers()
            .map(servers -> {
                List<McpTool> allTools = servers.stream()
                    .filter(server -> server.getStatus() == McpServer.ServerStatus.CONNECTED)
                    .flatMap(server -> server.getTools().stream())
                    .toList();
                
                Map<String, Object> result = new HashMap<>();
                result.put("tools", allTools);
                
                log.info("Returning {} tools via SSE", allTools.size());
                return McpJsonRpcResponse.success(result, request.getId());
            })
            .onErrorReturn(McpJsonRpcResponse.error(
                McpJsonRpcResponse.ErrorCodes.INTERNAL_ERROR,
                "Failed to list tools",
                request.getId()
            ));
    }

    @SuppressWarnings("unchecked")
    private Mono<McpJsonRpcResponse> handleToolsCall(McpJsonRpcRequest request) {
        log.info("Handling tools/call request via SSE");
        
        try {
            Map<String, Object> params = (Map<String, Object>) request.getParams();
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            
            return mcpServerService.listAllMcpServers()
                .flatMap(servers -> {
                    for (McpServer server : servers) {
                        if (server.getTools().stream().anyMatch(tool -> tool.getName().equals(toolName))) {
                            return mcpServerService.useTool(server.getName(), toolName, arguments)
                                .map(result -> {
                                    // 直接返回原始数据，不要包装
                                    return McpJsonRpcResponse.success(result, request.getId());
                                });
                        }
                    }
                    
                    return Mono.just(McpJsonRpcResponse.error(
                        McpJsonRpcResponse.ErrorCodes.TOOL_NOT_FOUND,
                        "Tool not found: " + toolName,
                        request.getId()
                    ));
                });
        } catch (Exception e) {
            return Mono.just(McpJsonRpcResponse.error(
                McpJsonRpcResponse.ErrorCodes.INVALID_PARAMS,
                "Invalid parameters: " + e.getMessage(),
                request.getId()
            ));
        }
    }

    private Mono<McpJsonRpcResponse> handleResourcesList(McpJsonRpcRequest request) {
        // 简化实现
        Map<String, Object> result = new HashMap<>();
        result.put("resources", List.of());
        return Mono.just(McpJsonRpcResponse.success(result, request.getId()));
    }

    private Mono<McpJsonRpcResponse> handleResourcesRead(McpJsonRpcRequest request) {
        // 简化实现
        Map<String, Object> result = new HashMap<>();
        result.put("contents", List.of());
        return Mono.just(McpJsonRpcResponse.success(result, request.getId()));
    }

    private Mono<McpJsonRpcResponse> handlePromptsList(McpJsonRpcRequest request) {
        // 简化实现
        Map<String, Object> result = new HashMap<>();
        result.put("prompts", List.of());
        return Mono.just(McpJsonRpcResponse.success(result, request.getId()));
    }

    private Mono<McpJsonRpcResponse> handlePromptsGet(McpJsonRpcRequest request) {
        // 简化实现
        Map<String, Object> result = new HashMap<>();
        result.put("description", "Sample prompt");
        result.put("messages", List.of());
        return Mono.just(McpJsonRpcResponse.success(result, request.getId()));
    }

    /**
     * 获取活跃连接状态的端点
     */
    @GetMapping("/connections/status")
    public Mono<Map<String, Object>> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeConnections", activeConnections.size());
        status.put("connectionIds", activeConnections.keySet());
        status.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(status);
    }

    /**
     * Validate authentication token (simple implementation)
     */
    private boolean validateAuthToken(String authToken) {
        // Simple token validation - in production use proper JWT/OAuth validation
        return authToken != null && !authToken.trim().isEmpty() && authToken.length() > 10;
    }

    /**
     * 向特定客户端发送通知的管理端点
     */
    @PostMapping("/connections/{clientId}/notify")
    public Mono<Map<String, Object>> sendNotification(
            @PathVariable String clientId,
            @RequestBody Map<String, Object> notification) {
        
        Sinks.Many<ServerSentEvent<String>> connection = activeConnections.get(clientId);
        if (connection != null) {
            try {
                ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                    .event("notification")
                    .data(objectMapper.writeValueAsString(notification))
                    .build();
                    
                connection.tryEmitNext(event);
                
                return Mono.just(Map.of(
                    "status", "sent",
                    "clientId", clientId,
                    "timestamp", System.currentTimeMillis()
                ));
            } catch (JsonProcessingException e) {
                return Mono.just(Map.of(
                    "status", "error",
                    "error", "Failed to serialize notification",
                    "clientId", clientId
                ));
            }
        } else {
            return Mono.just(Map.of(
                "status", "error",
                "error", "Client not connected",
                "clientId", clientId
            ));
        }
    }
} 