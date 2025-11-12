package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.service.McpSseTransportProvider;
import com.pajk.mcpbridge.core.service.McpSessionBridgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.model.SseSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * MCP SSE控制器
 * 处理SSE连接和消息传输
 */
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class McpSseController {

    private final static Logger log = LoggerFactory.getLogger(McpSseController.class);
    private final McpSseTransportProvider sseTransportProvider;
    private final McpSessionBridgeService sessionBridgeService; // 注入 SessionBridgeService
    private final ObjectMapper objectMapper; // 注入 ObjectMapper

    /**
     * 建立SSE连接
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> connect(
            @RequestParam String clientId,
            @RequestParam(required = false) String metadata) {
        
        log.info("SSE connect request from client: {}", clientId);
        
        // 解析元数据
        Map<String, String> metadataMap = parseMetadata(metadata);
        
        return sseTransportProvider.connect(clientId, metadataMap)
                .doOnSubscribe(subscription -> 
                    log.info("SSE connection established for client: {}", clientId))
                .doOnComplete(() -> 
                    log.info("SSE connection completed for client: {}", clientId))
                .doOnError(error -> 
                    log.error("SSE connection error for client: {}", clientId, error))
                .doOnCancel(() ->
                    log.info("SSE connection cancelled for client: {}", clientId));
    }
    
    /**
     * 为指定服务建立SSE连接 (兼容 MCP Inspector)
     * /sse/{serviceName} → 建立客户端到路由器的SSE连接，并桥接到后端服务
     */
    @GetMapping(value = "/{serviceName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> connectForService(
            @PathVariable String serviceName,
            @RequestParam(value = "clientId", required = false) String clientId,
            @RequestParam(value = "metadata", required = false) String metadata) {
        
        String effectiveClientId = (clientId == null || clientId.isBlank())
                ? "inspector-" + java.util.UUID.randomUUID()
                : clientId;
        log.info("SSE connect request for service: {}, clientId: {}", serviceName, effectiveClientId);
        
        Map<String, String> metadataMap = parseMetadata(metadata);
        
        // 1. 建立客户端到路由器的 SSE 连接
        return sseTransportProvider.connect(effectiveClientId, metadataMap)
                .doOnNext(event -> {
                    // 2. 当客户端连接成功并收到第一个 "connected" 事件时，进行桥接
                    if ("connected".equals(event.event()) && event.data() != null) {
                        try {
                            Map<String, String> data = objectMapper.readValue(event.data(), new TypeReference<Map<String, String>>() {}); // 使用 TypeReference
                            String sessionId = data.get("sessionId");
                            if (sessionId != null) {
                                log.info("Client SSE connected with sessionId: {}, attempting to bridge to service: {}", sessionId, serviceName);
                                // 建立客户端到后端服务的桥接
                                sessionBridgeService.bridgeSseSession(sessionId, serviceName)
                                        .doOnSuccess(v -> log.info("SSE session {} successfully bridged to service {}", sessionId, serviceName))
                                        .doOnError(error -> log.error("Failed to bridge SSE session {} to service {}: {}", sessionId, serviceName, error.getMessage()))
                                        .subscribe(); // 订阅以触发执行
                            }
                        } catch (Exception e) {
                            log.error("Failed to parse connected event data for client: {}. Error: {}", effectiveClientId, e.getMessage());
                        }
                    }
                })
                .doOnComplete(() -> {
                    log.info("Client SSE connection completed for client: {}, service: {}", effectiveClientId, serviceName);
                    // 客户端连接关闭时，断开桥接
                    sseTransportProvider.findSessionByClientId(effectiveClientId).ifPresent(session -> {
                        sessionBridgeService.removeBridge(session.getSessionId())
                                .doOnSuccess(v -> log.info("Removed bridge for client session: {}", session.getSessionId()))
                                .doOnError(error -> log.error("Failed to remove bridge for client session {}: {}", session.getSessionId(), error.getMessage()))
                                .subscribe();
                    });
                })
                .doOnError(error -> {
                    log.error("Client SSE connection error for client: {}, service: {}. Error: {}", effectiveClientId, serviceName, error.getMessage());
                    // 客户端连接发生错误时，断开桥接
                    sseTransportProvider.findSessionByClientId(effectiveClientId).ifPresent(session -> {
                        sessionBridgeService.removeBridge(session.getSessionId())
                                .doOnSuccess(v -> log.info("Removed bridge for client session: {}", session.getSessionId()))
                                .doOnError(error_ -> log.error("Failed to remove bridge for client session {}: {}", session.getSessionId(), error_.getMessage()))
                                .subscribe();
                    });
                })
                .doOnCancel(() -> {
                    log.info("Client SSE connection cancelled for client: {}, service: {}", effectiveClientId, serviceName);
                    // 客户端连接取消时，断开桥接
                    sseTransportProvider.findSessionByClientId(effectiveClientId).ifPresent(session -> {
                        sessionBridgeService.removeBridge(session.getSessionId())
                                .doOnSuccess(v -> log.info("Removed bridge for client session: {}", session.getSessionId()))
                                .doOnError(error_ -> log.error("Failed to remove bridge for client session {}: {}", session.getSessionId(), error_.getMessage()))
                                .subscribe();
                    });
                });
    }

    /**
     * 发送消息到指定会话
     */
    @PostMapping("/message/{sessionId}")
    public Mono<String> sendMessage(
            @PathVariable String sessionId,
            @RequestParam String eventType,
            @RequestBody String data) {
        
        log.info("Sending message to session: {}, event: {}", sessionId, eventType);
        
        return sseTransportProvider.sendMessage(sessionId, eventType, data)
                .then(Mono.just("Message sent successfully"));
    }
    
    /**
     * 发送消息到指定客户端
     */
    @PostMapping("/message/client/{clientId}")
    public Mono<String> sendMessageToClient(
            @PathVariable String clientId,
            @RequestParam String eventType,
            @RequestBody String data) {
        
        log.info("Sending message to client: {}, event: {}", clientId, eventType);
        
        return sseTransportProvider.sendMessageToClient(clientId, eventType, data)
                .then(Mono.just("Message sent successfully"));
    }
    
    /**
     * 广播消息到所有活跃会话
     */
    @PostMapping("/broadcast")
    public Mono<String> broadcast(
            @RequestParam String eventType,
            @RequestBody String data) {
        
        log.info("Broadcasting message, event: {}", eventType);
        
        return sseTransportProvider.broadcast(eventType, data)
                .then(Mono.just("Message broadcasted successfully"));
    }
    
    /**
     * 获取会话信息
     */
    @GetMapping("/session/{sessionId}")
    public Mono<SseSession> getSession(@PathVariable String sessionId) {
        log.info("Getting session info: {}", sessionId);
        
        SseSession session = sseTransportProvider.getSession(sessionId);
        if (session == null) {
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        return Mono.just(session);
    }
    
    /**
     * 获取所有活跃会话
     */
    @GetMapping("/sessions")
    public Mono<Map<String, SseSession>> getAllSessions() {
        log.info("Getting all active sessions");
        
        return Mono.just(sseTransportProvider.getAllSessions());
    }
    
    /**
     * 关闭指定会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Mono<String> closeSession(@PathVariable String sessionId) {
        log.info("Closing session: {}", sessionId);
        
        return sseTransportProvider.closeSession(sessionId)
                .then(Mono.just("Session closed successfully"));
    }
    
    /**
     * 清理超时会话
     */
    @PostMapping("/cleanup")
    public Mono<String> cleanupTimeoutSessions() {
        log.info("Cleaning up timeout sessions");
        
        return sseTransportProvider.cleanupTimeoutSessions()
                .then(Mono.just("Timeout sessions cleaned up successfully"));
    }
    
    /**
     * 解析元数据字符串
     */
    private Map<String, String> parseMetadata(String metadata) {
        Map<String, String> result = new HashMap<>();
        
        if (metadata != null && !metadata.trim().isEmpty()) {
            try {
                // 简单的键值对解析，格式：key1=value1,key2=value2
                String[] pairs = metadata.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        result.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse metadata: {}", metadata, e);
            }
        }
        
        return result;
    }

}