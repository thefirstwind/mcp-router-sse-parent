package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.model.McpServerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 会话桥接服务
 * 管理客户端会话和服务器会话的映射，实现桥接功能
 * 
 * 设计：
 * - 客户端会话：客户端与 router 的 SSE 连接，客户端不断开就保持连接
 * - 服务器会话：router 与后端服务器的 SSE 连接，可以闲时断开，用时再连接，有效时间10分钟
 * - 会话映射：clientSessionId -> serverSessionId 的映射
 */
@Slf4j
@Service
public class McpSessionBridgeService {

    /**
     * 客户端会话信息
     */
    public static class ClientSession {
        private String clientSessionId;
        private String serviceName;
        private Sinks.Many<ServerSentEvent<String>> clientSink;
        private LocalDateTime createdTime;
        private LocalDateTime lastActiveTime;
        private String serverSessionId; // 关联的服务器会话ID
        
        public ClientSession(String clientSessionId, String serviceName, 
                            Sinks.Many<ServerSentEvent<String>> clientSink) {
            this.clientSessionId = clientSessionId;
            this.serviceName = serviceName;
            this.clientSink = clientSink;
            this.createdTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
        }
        
        public void updateLastActiveTime() {
            this.lastActiveTime = LocalDateTime.now();
        }
        
        // Getters and setters
        public String getClientSessionId() { return clientSessionId; }
        public String getServiceName() { return serviceName; }
        public Sinks.Many<ServerSentEvent<String>> getClientSink() { return clientSink; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public LocalDateTime getLastActiveTime() { return lastActiveTime; }
        public String getServerSessionId() { return serverSessionId; }
        public void setServerSessionId(String serverSessionId) { this.serverSessionId = serverSessionId; }
    }
    
    /**
     * 服务器会话信息
     */
    public static class ServerSession {
        private String serverSessionId; // router 生成的会话ID
        private String backendSessionId; // 后端服务器的会话ID（从 SSE endpoint 事件中提取）
        private String serviceName;
        private McpServerInfo serverInfo;
        public Flux<ServerSentEvent<String>> serverEventFlux; // 服务器 SSE 流
        private LocalDateTime createdTime;
        private LocalDateTime lastActiveTime;
        private LocalDateTime expireTime; // 过期时间（10分钟后）
        private boolean isActive; // 是否活跃
        
        public ServerSession(String serverSessionId, String serviceName, 
                           McpServerInfo serverInfo, Flux<ServerSentEvent<String>> serverEventFlux) {
            this.serverSessionId = serverSessionId;
            this.serviceName = serviceName;
            this.serverInfo = serverInfo;
            this.serverEventFlux = serverEventFlux;
            this.createdTime = LocalDateTime.now();
            this.lastActiveTime = LocalDateTime.now();
            this.expireTime = LocalDateTime.now().plusMinutes(10); // 10分钟后过期
            this.isActive = true;
        }
        
        public String getBackendSessionId() { return backendSessionId; }
        public void setBackendSessionId(String backendSessionId) { this.backendSessionId = backendSessionId; }
        
        public void updateLastActiveTime() {
            this.lastActiveTime = LocalDateTime.now();
            this.expireTime = LocalDateTime.now().plusMinutes(10); // 重置过期时间
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireTime);
        }
        
        // Getters and setters
        public String getServerSessionId() { return serverSessionId; }
        public String getServiceName() { return serviceName; }
        public McpServerInfo getServerInfo() { return serverInfo; }
        public Flux<ServerSentEvent<String>> getServerEventFlux() { return serverEventFlux; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public LocalDateTime getLastActiveTime() { return lastActiveTime; }
        public LocalDateTime getExpireTime() { return expireTime; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
    }
    
    // 客户端会话存储：clientSessionId -> ClientSession
    private final Map<String, ClientSession> clientSessions = new ConcurrentHashMap<>();
    
    // 服务器会话存储：serverSessionId -> ServerSession
    private final Map<String, ServerSession> serverSessions = new ConcurrentHashMap<>();
    
    // 会话映射：clientSessionId -> serverSessionId
    private final Map<String, String> sessionMapping = new ConcurrentHashMap<>();
    
    // 反向映射：serverSessionId -> clientSessionId（一个服务器会话可能对应多个客户端会话）
    private final Map<String, String> reverseMapping = new ConcurrentHashMap<>();
    
    private final WebClient.Builder webClientBuilder;
    private final McpServerService serverService;
    private final ObjectMapper objectMapper; // 注入 ObjectMapper
    private final McpSessionService sessionService;

    // 服务器会话有效时间：10分钟
    private static final Duration SERVER_SESSION_TIMEOUT = Duration.ofMinutes(10);
    
    public McpSessionBridgeService(WebClient.Builder webClientBuilder,
                                  McpServerService serverService,
                                  ObjectMapper objectMapper,
                                  McpSessionService sessionService) {
        this.webClientBuilder = webClientBuilder;
        this.serverService = serverService;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        
        // 启动定期清理任务
        startCleanupTask();
    }
    
    /**
     * 注册客户端会话
     */
    public void registerClientSession(String clientSessionId, String serviceName, 
                                     Sinks.Many<ServerSentEvent<String>> clientSink) {
        ClientSession clientSession = new ClientSession(clientSessionId, serviceName, clientSink);
        clientSessions.put(clientSessionId, clientSession);
        log.info("✅ Registered client session: clientSessionId={}, serviceName={}", 
                clientSessionId, serviceName);
    }
    
    /**
     * 获取客户端会话
     */
    public ClientSession getClientSession(String clientSessionId) {
        return clientSessions.get(clientSessionId);
    }
    
    /**
     * 移除客户端会话
     */
    public void removeClientSession(String clientSessionId) {
        ClientSession clientSession = clientSessions.remove(clientSessionId);
        if (clientSession != null) {
            String serverSessionId = clientSession.getServerSessionId();
            if (serverSessionId != null) {
                sessionMapping.remove(clientSessionId);
                reverseMapping.remove(serverSessionId);
                log.info("🗑️ Removed client session mapping: clientSessionId={}, serverSessionId={}", 
                        clientSessionId, serverSessionId);
            }
            log.info("🗑️ Removed client session: clientSessionId={}", clientSessionId);
        }
    }
    
    /**
     * 获取或创建服务器会话
     * 如果服务器会话不存在或已过期，创建新的服务器会话
     */
    public Mono<ServerSession> getOrCreateServerSession(String clientSessionId, String serviceName) {
        ClientSession clientSession = clientSessions.get(clientSessionId);
        if (clientSession == null) {
            return Mono.error(new IllegalArgumentException("Client session not found: " + clientSessionId));
        }
        
        // 检查是否已有服务器会话
        String existingServerSessionId = clientSession.getServerSessionId();
        if (existingServerSessionId != null) {
            ServerSession existingSession = serverSessions.get(existingServerSessionId);
            if (existingSession != null && !existingSession.isExpired() && existingSession.isActive()) {
                existingSession.updateLastActiveTime();
                log.debug("🎯 Using existing server session: serverSessionId={}", existingServerSessionId);
                return Mono.just(existingSession);
            } else {
                // 会话已过期或已断开，移除
                log.info("🔄 Server session expired or inactive, creating new one: serverSessionId={}", 
                        existingServerSessionId);
                removeServerSession(existingServerSessionId);
            }
        }
        
        // 创建新的服务器会话
        return createServerSession(clientSessionId, serviceName)
                .doOnNext(serverSession -> {
                    // 建立映射关系
                    clientSession.setServerSessionId(serverSession.getServerSessionId());
                    sessionMapping.put(clientSessionId, serverSession.getServerSessionId());
                    reverseMapping.put(serverSession.getServerSessionId(), clientSessionId);
                    log.info("✅ Created and mapped server session: clientSessionId={}, serverSessionId={}", 
                            clientSessionId, serverSession.getServerSessionId());
                });
    }
    
    /**
     * 创建服务器会话
     * 建立与后端服务器的 SSE 连接
     */
    private Mono<ServerSession> createServerSession(String clientSessionId, String serviceName) {
        // 获取服务器信息
        return serverService.selectHealthyServer(serviceName, "mcp-server")
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Server not found: " + serviceName)))
                .flatMap(serverInfo -> {
                    String serverBaseUrl = buildServerUrl(serverInfo);
                    String serverSessionId = UUID.randomUUID().toString();

                    log.info("🔗 Creating server session: serverSessionId={}, serviceName={}, serverBaseUrl={}",
                            serverSessionId, serviceName, serverBaseUrl);

                    Sinks.One<String> backendSessionIdSink = Sinks.one(); // 用于异步通知 backendSessionId

                    // 创建服务器会话
                    ServerSession serverSession = new ServerSession(serverSessionId, serviceName,
                            serverInfo, null); // 暂时传入 null，稍后设置真正的 Flux
                    serverSessions.put(serverSessionId, serverSession);

                    // 建立与后端服务器的 SSE 连接
                    WebClient webClient = webClientBuilder.baseUrl(serverBaseUrl).build();
                    Flux<ServerSentEvent<String>> serverEventFlux = webClient.get()
                            .uri("/sse")
                            .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                            .retrieve()
                            .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                            .doOnNext(event -> {
                                log.debug("📥 Received event from server: serverSessionId={}, event={}, id={}, data={}",
                                        serverSessionId, event.event(), event.id(), event.data());

                                // 从 endpoint 事件中提取后端服务器的 sessionId
                                if ("endpoint".equals(event.event())) {
                                    Object data = event.data();
                                    String endpointData = data != null ? data.toString() : null;
                                    if (endpointData != null && endpointData.contains("sessionId=")) {
                                        String backendSessionId = extractSessionIdFromEndpoint(endpointData);
                                        if (backendSessionId != null) {
                                            serverSession.setBackendSessionId(backendSessionId); // 设置 backendSessionId
                                            sessionService.updateBackendSessionId(clientSessionId, backendSessionId);
                                            backendSessionIdSink.tryEmitValue(backendSessionId); // 发送信号
                                            log.info("✅ Extracted backend sessionId: serverSessionId={}, backendSessionId={}",
                                                    serverSessionId, backendSessionId);
                                        }
                                    }
                                }

                                // 将服务器事件转发到客户端
                                forwardServerEventToClient(serverSessionId, event);
                            })
                            .doOnError(error -> {
                                log.error("❌ Backend Server SSE stream error: serverSessionId={}, error={}", serverSessionId, error.getMessage(), error);
                                serverSession.setActive(false);
                                backendSessionIdSink.tryEmitError(error); // 错误时也发出信号
                            })
                            .doOnComplete(() -> {
                                log.info("✅ Backend Server SSE stream completed: serverSessionId={}", serverSessionId);
                                serverSession.setActive(false);
                                backendSessionIdSink.tryEmitEmpty(); // 完成时也发出信号
                            });

                    // 更新 serverSession 中的实际 Flux
                    serverSession.serverEventFlux = serverEventFlux; // 更新 ServerSession 中的 Flux

                    // 订阅服务器事件流（保持连接），并等待 backendSessionId 就绪
                    serverEventFlux.subscribe(); // 保持连接活跃

                    return backendSessionIdSink.asMono() // 等待 backendSessionId 就绪
                            .thenReturn(serverSession) // 一旦就绪，返回 serverSession
                            .doOnSuccess(s -> log.info("✅ Server session created and backend sessionId ready: serverSessionId={}", serverSessionId));
                });
    }
    
    /**
     * 将服务器事件转发到客户端
     */
    private void forwardServerEventToClient(String serverSessionId, ServerSentEvent<String> event) {
        String clientSessionId = reverseMapping.get(serverSessionId);
        if (clientSessionId != null) {
            ClientSession clientSession = clientSessions.get(clientSessionId);
            if (clientSession != null && clientSession.getClientSink() != null) {
                String eventData = event.data();
                String processedEventData;

                // Determine if the event type is one of the list methods expecting JSON array
                boolean isListMethod = "tools/list".equals(event.event()) ||
                                       "prompts/list".equals(event.event()) ||
                                       "resources/list".equals(event.event()) ||
                                       "resources/templates/list".equals(event.event());

                // Special handling for "endpoint" event, which expects a plain string URL
                if ("endpoint".equals(event.event())) {
                    processedEventData = (eventData != null) ? eventData.trim() : "";
                } else if (eventData == null || eventData.trim().isEmpty()) {
                    processedEventData = isListMethod ? "[]" : "{}"; // Default to empty array for lists, empty object otherwise
                } else {
                    eventData = eventData.trim();
                    try {
                        JsonNode jsonNode = objectMapper.readTree(eventData);
                        processedEventData = objectMapper.writeValueAsString(jsonNode);
                    } catch (JsonProcessingException e) {
                        log.warn("⚠️ Event data is not valid JSON for event: {}. Original data: \"{}\". Error: {}", event.event(), eventData, e.getMessage());
                        if (isListMethod) {
                            processedEventData = "[]"; // For list methods, fallback to an empty JSON array
                        } else {
                            // For other events, if it's not JSON, wrap it as a JSON string literal
                            try {
                                processedEventData = objectMapper.writeValueAsString(eventData);
                            } catch (JsonProcessingException e2) {
                                log.warn("⚠️ Failed to serialize plain string data as JSON literal for event: {}. Falling back to empty JSON object. Original data: \"{}\", Error: {}", event.event(), eventData, e2.getMessage());
                                processedEventData = "{}"; // Ultimate fallback to empty JSON object
                            }
                        }
                    }
                }

                ServerSentEvent<String> forwardedEvent = ServerSentEvent.<String>builder()
                        .id(event.id())
                        .event(event.event())
                        .data(processedEventData)
                        .build();

                // Synchronize emission to prevent concurrent modification
                synchronized (clientSession.getClientSink()) {
                    clientSession.getClientSink().emitNext(forwardedEvent, Sinks.EmitFailureHandler.FAIL_FAST);
                }
                log.info("✅ Forwarded server event to client: clientSessionId={}, serverSessionId={}, event={}, id={}, data={}",
                        clientSessionId, serverSessionId, event.event(), event.id(), processedEventData);
            } else {
                log.warn("⚠️ Client session not found or sink is null for clientSessionId: {}", clientSessionId);
            }
        }
    }
    
    /**
     * 移除服务器会话
     */
    public void removeServerSession(String serverSessionId) {
        ServerSession serverSession = serverSessions.remove(serverSessionId);
        if (serverSession != null) {
            String clientSessionId = reverseMapping.remove(serverSessionId);
            if (clientSessionId != null) {
                ClientSession clientSession = clientSessions.get(clientSessionId);
                if (clientSession != null) {
                    clientSession.setServerSessionId(null);
                }
                sessionMapping.remove(clientSessionId);
            }
            log.info("🗑️ Removed server session: serverSessionId={}", serverSessionId);
        }
    }
    
    /**
     * 获取服务器会话
     */
    public ServerSession getServerSession(String serverSessionId) {
        return serverSessions.get(serverSessionId);
    }
    
    /**
     * 构建服务器URL
     */
    private String buildServerUrl(McpServerInfo serverInfo) {
        String host = serverInfo.getHost();
        int port = serverInfo.getPort();
        String protocol = serverInfo.getProtocol() != null ? serverInfo.getProtocol() : "http";
        return String.format("%s://%s:%d", protocol, host, port);
    }
    
    /**
     * 从 endpoint URL 中提取 sessionId
     * 格式：http://localhost:8071/mcp/message?sessionId=xxx
     */
    private String extractSessionIdFromEndpoint(String endpointUrl) {
        try {
            if (endpointUrl != null && endpointUrl.contains("sessionId=")) {
                int index = endpointUrl.indexOf("sessionId=");
                String sessionIdPart = endpointUrl.substring(index + "sessionId=".length());
                // 移除可能的查询参数
                int endIndex = sessionIdPart.indexOf("&");
                if (endIndex > 0) {
                    return sessionIdPart.substring(0, endIndex);
                }
                return sessionIdPart;
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to extract sessionId from endpoint: {}", endpointUrl, e);
        }
        return null;
    }
    
    /**
     * 启动定期清理任务
     * 清理过期的服务器会话
     */
    private void startCleanupTask() {
        reactor.core.publisher.Flux.interval(Duration.ofMinutes(1))
                .doOnNext(tick -> cleanupExpiredSessions())
                .doOnError(error -> log.error("Cleanup task failed", error))
                .subscribe();
    }
    
    /**
     * 清理过期的服务器会话
     */
    private void cleanupExpiredSessions() {
        int cleanedCount = 0;
        for (Map.Entry<String, ServerSession> entry : serverSessions.entrySet()) {
            ServerSession session = entry.getValue();
            if (session.isExpired() || !session.isActive()) {
                removeServerSession(entry.getKey());
                cleanedCount++;
            }
        }
        if (cleanedCount > 0) {
            log.info("🧹 Cleaned up {} expired server sessions", cleanedCount);
        }
    }
    
    /**
     * 更新客户端会话的最后活跃时间
     */
    public void updateClientSessionLastActiveTime(String clientSessionId) {
        ClientSession clientSession = clientSessions.get(clientSessionId);
        if (clientSession != null) {
            clientSession.updateLastActiveTime();
        }
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("clientSessionCount", clientSessions.size());
        stats.put("serverSessionCount", serverSessions.size());
        stats.put("sessionMappingCount", sessionMapping.size());
        return stats;
    }
    
    /**
     * 通过服务器会话发送消息到后端服务器
     * 使用 HTTP POST 发送消息到后端服务器的 /mcp/message?sessionId=xxx 端点
     */
    public Mono<String> sendMessageToBackendServer(String clientSessionId, String messageJson) {
        ClientSession clientSession = clientSessions.get(clientSessionId);
        if (clientSession == null) {
            return Mono.error(new IllegalArgumentException("Client session not found: " + clientSessionId));
        }
        
        String serverSessionId = clientSession.getServerSessionId();
        if (serverSessionId == null) {
            return Mono.error(new IllegalArgumentException("No server session found for client session: " + clientSessionId));
        }
        
        ServerSession serverSession = serverSessions.get(serverSessionId);
        if (serverSession == null) {
            return Mono.error(new IllegalArgumentException("Server session not found: " + serverSessionId));
        }
        
        String backendSessionId = serverSession.getBackendSessionId();
        if (backendSessionId == null) {
            return Mono.error(new IllegalArgumentException("Backend sessionId not found for server session: " + serverSessionId));
        }
        
        // 更新服务器会话的最后活跃时间
        serverSession.updateLastActiveTime();
        
        // 构建后端服务器的消息端点URL
        String serverBaseUrl = buildServerUrl(serverSession.getServerInfo());
        String messageEndpoint = String.format("%s/mcp/message?sessionId=%s", serverBaseUrl, backendSessionId);
        
        log.info("📤 Sending message to backend server: serverSessionId={}, backendSessionId={}, endpoint={}", 
                serverSessionId, backendSessionId, messageEndpoint);
        
        // 使用 WebClient 发送 HTTP POST 请求
        // 注意：使用 DataBuffer 直接读取响应体，完全绕过自动解码，避免被解析为 SSE 事件流
        // WebClient 的超时配置已在 WebFluxConfig 中全局设置（60秒响应超时，30秒连接超时）
        WebClient webClient = webClientBuilder.build();
        
        return webClient.post()
                .uri(messageEndpoint)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON) // 明确指定接受 JSON 响应
                .bodyValue(messageJson)
                .exchangeToMono(response -> {
                    // 检查响应状态码
                    if (response.statusCode().isError()) {
                        log.error("❌ Backend server returned error status: serverSessionId={}, status={}", 
                                serverSessionId, response.statusCode());
                        // 读取错误响应体
                        return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class))
                                .map(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    DataBufferUtils.release(dataBuffer);
                                    return new String(bytes, StandardCharsets.UTF_8);
                                })
                                .flatMap(errorBody -> {
                                    log.error("❌ Error response body: {}", errorBody);
                                    return Mono.error(new RuntimeException(
                                            String.format("Backend server error: %s - %s", 
                                                    response.statusCode(), errorBody)));
                                });
                    }
                    // 使用 DataBuffer 直接读取响应体，完全绕过自动解码
                    // 这样可以避免 WebClient 根据 Content-Type 自动选择解码器（如 SSE 解码器）
                    return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class))
                            .map(dataBuffer -> {
                                try {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    return new String(bytes, StandardCharsets.UTF_8);
                                } finally {
                                    DataBufferUtils.release(dataBuffer);
                                }
                            })
                            .switchIfEmpty(Mono.just(""));
                })
                .doOnNext(response -> {
                    log.info("✅ Received response from backend server: serverSessionId={}, responseLength={}, response={}", 
                            serverSessionId, response != null ? response.length() : 0, 
                            response != null && response.length() > 200 ? response.substring(0, 200) + "..." : response);
                    // 验证响应是否为有效的 JSON
                    if (response != null && !response.trim().isEmpty()) {
                        try {
                            // 尝试解析 JSON 以验证格式
                            if (!response.trim().startsWith("{") && !response.trim().startsWith("[")) {
                                log.warn("⚠️ Response may not be valid JSON: serverSessionId={}, response={}", 
                                        serverSessionId, response.length() > 100 ? response.substring(0, 100) : response);
                            }
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to validate JSON response: serverSessionId={}, error={}", 
                                    serverSessionId, e.getMessage());
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("❌ Failed to send message to backend server: serverSessionId={}, endpoint={}, error={}", 
                            serverSessionId, messageEndpoint, error.getMessage(), error);
                });
    }

    public Mono<Void> bridgeSseSession(String clientSessionId, String serviceName) {
        ClientSession clientSession = clientSessions.get(clientSessionId);
        if (clientSession == null) {
            return Mono.error(new IllegalArgumentException("Client session not found: " + clientSessionId));
        }

        return getOrCreateServerSession(clientSessionId, serviceName)
                .flatMap(serverSession -> {
                    log.info("🔗 Successfully bridged client session {} to service {}. Server session: {}",
                            clientSessionId, serviceName, serverSession.getServerSessionId());
                    return Mono.empty(); // 直接返回 Mono.empty() 确保类型为 Mono<Void>
                })
                .then(); // 添加 .then() 确保最终返回 Mono<Void>
    }

    /**
     * 移除桥接
     */
    public Mono<Void> removeBridge(String clientSessionId) {
        ClientSession clientSession = clientSessions.get(clientSessionId);
        if (clientSession == null) {
            return Mono.empty();
        }

        String serverSessionId = clientSession.getServerSessionId();
        if (serverSessionId != null) {
            // 检查是否有其他客户端会话仍在使用此服务器会话
            boolean otherClientsUsingServerSession = clientSessions.values().stream()
                    .filter(s -> !s.getClientSessionId().equals(clientSessionId))
                    .anyMatch(s -> serverSessionId.equals(s.getServerSessionId()));

            if (!otherClientsUsingServerSession) {
                // 如果没有其他客户端使用，则移除服务器会话
                removeServerSession(serverSessionId);
            }
        }
        removeClientSession(clientSessionId);
        log.info("🗑️ Removed bridge for client session: {}", clientSessionId);
        return Mono.empty().then();
    }
}

















