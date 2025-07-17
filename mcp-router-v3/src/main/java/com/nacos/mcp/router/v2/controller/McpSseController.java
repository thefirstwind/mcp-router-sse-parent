package com.nacos.mcp.router.v2.controller;

import com.nacos.mcp.router.v2.model.SseSession;
import com.nacos.mcp.router.v2.service.McpSseTransportProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP SSE控制器
 * 处理SSE连接和消息传输
 */
@Slf4j
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class McpSseController {
    
    private final McpSseTransportProvider sseTransportProvider;
    
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
                    log.error("SSE connection error for client: {}", clientId, error));
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