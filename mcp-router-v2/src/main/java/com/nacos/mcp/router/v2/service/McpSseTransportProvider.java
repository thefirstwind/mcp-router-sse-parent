package com.nacos.mcp.router.v2.service;

import com.nacos.mcp.router.v2.model.SseSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP SSE传输提供者
 * 负责管理SSE连接和消息传输
 */
@Slf4j
@Component
public class McpSseTransportProvider {
    
    // 活跃会话存储
    private final Map<String, SseSession> activeSessions = new ConcurrentHashMap<>();
    
    // 会话超时时间（60秒）
    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    
    // 心跳间隔（30秒）
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    
    // 消息ID生成器
    private final AtomicLong messageIdGenerator = new AtomicLong(0);
    
    /**
     * 建立SSE连接
     */
    public Flux<ServerSentEvent<String>> connect(String clientId, Map<String, String> metadata) {
        String sessionId = UUID.randomUUID().toString();
        
        log.info("Creating SSE connection for client: {}, session: {}", clientId, sessionId);
        
        return Flux.create(emitter -> {
            // 创建Sinks.Many用于消息传输
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
            
            // 创建SSE会话
            SseSession session = createSession(sessionId, clientId, metadata, sink);
            activeSessions.put(sessionId, session);
            
            // 订阅sink的消息并发送给客户端
            sink.asFlux().subscribe(event -> emitter.next(event));
            
            // 发送连接成功事件
            sendConnectionEvent(session, "connected");
            
            // 启动心跳
            startHeartbeat(session);
            
            // 处理连接关闭
            emitter.onDispose(() -> {
                log.info("SSE connection disposed for client: {}, session: {}", clientId, sessionId);
                session.setStatus(SseSession.SessionStatus.DISCONNECTED);
                activeSessions.remove(sessionId);
                sink.tryEmitComplete();
            });
            
            // 处理错误 - 这里不需要设置错误处理器，因为Flux.create的错误处理会由emitter内部处理
            // 移除 emitter.onError 调用
        });
    }
    
    /**
     * 发送消息到指定会话
     */
    public Mono<Void> sendMessage(String sessionId, String eventType, String data) {
        SseSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        return Mono.fromRunnable(() -> {
            try {
                ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                        .id(String.valueOf(messageIdGenerator.incrementAndGet()))
                        .event(eventType)
                        .data(data)
                        .build();
                
                session.getSink().tryEmitNext(event);
                session.incrementMessageCount();
                session.updateLastActiveTime();
                
                log.debug("Sent message to session: {}, event: {}", sessionId, eventType);
            } catch (Exception e) {
                log.error("Failed to send message to session: {}", sessionId, e);
                session.incrementErrorCount();
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }
    
    /**
     * 发送消息到指定客户端
     */
    public Mono<Void> sendMessageToClient(String clientId, String eventType, String data) {
        SseSession session = findSessionByClientId(clientId);
        if (session == null) {
            return Mono.error(new IllegalArgumentException("No active session for client: " + clientId));
        }
        
        return sendMessage(session.getSessionId(), eventType, data);
    }
    
    /**
     * 广播消息到所有活跃会话
     */
    public Mono<Void> broadcast(String eventType, String data) {
        return Flux.fromIterable(activeSessions.values())
                .flatMap(session -> sendMessage(session.getSessionId(), eventType, data))
                .then();
    }
    
    /**
     * 获取活跃会话信息
     */
    public SseSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }
    
    /**
     * 获取所有活跃会话
     */
    public Map<String, SseSession> getAllSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }
    
    /**
     * 关闭指定会话
     */
    public Mono<Void> closeSession(String sessionId) {
        SseSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Mono.empty();
        }
        
        return Mono.fromRunnable(() -> {
            try {
                sendConnectionEvent(session, "disconnected");
                session.getSink().tryEmitComplete();
                session.setStatus(SseSession.SessionStatus.DISCONNECTED);
                activeSessions.remove(sessionId);
                log.info("Closed SSE session: {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to close session: {}", sessionId, e);
                throw new RuntimeException("Failed to close session", e);
            }
        });
    }
    
    /**
     * 清理超时会话
     */
    public Mono<Void> cleanupTimeoutSessions() {
        return Flux.fromIterable(activeSessions.values())
                .filter(SseSession::isTimeout)
                .flatMap(session -> {
                    log.info("Cleaning up timeout session: {}", session.getSessionId());
                    session.setStatus(SseSession.SessionStatus.TIMEOUT);
                    return closeSession(session.getSessionId());
                })
                .then();
    }
    
    /**
     * 创建SSE会话
     */
    private SseSession createSession(String sessionId, String clientId, Map<String, String> metadata, 
                                   Sinks.Many<ServerSentEvent<String>> sink) {
        return SseSession.builder()
                .sessionId(sessionId)
                .clientId(clientId)
                .status(SseSession.SessionStatus.CONNECTING)
                .createdTime(LocalDateTime.now())
                .lastActiveTime(LocalDateTime.now())
                .timeoutMs(DEFAULT_TIMEOUT_MS)
                .sink(sink)
                .messageCount(new AtomicLong(0))
                .errorCount(new AtomicLong(0))
                .metadata(metadata)
                .build();
    }
    
    /**
     * 发送连接事件
     */
    private void sendConnectionEvent(SseSession session, String eventType) {
        try {
            ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                    .id(String.valueOf(messageIdGenerator.incrementAndGet()))
                    .event(eventType)
                    .data("{\"sessionId\":\"" + session.getSessionId() + "\",\"clientId\":\"" + session.getClientId() + "\"}")
                    .build();
            
            session.getSink().tryEmitNext(event);
            session.setStatus(SseSession.SessionStatus.CONNECTED);
            session.updateLastActiveTime();
            
            log.debug("Sent connection event: {} for session: {}", eventType, session.getSessionId());
        } catch (Exception e) {
            log.error("Failed to send connection event for session: {}", session.getSessionId(), e);
            session.incrementErrorCount();
        }
    }
    
    /**
     * 启动心跳
     */
    private void startHeartbeat(SseSession session) {
        Flux.interval(Duration.ofMillis(HEARTBEAT_INTERVAL_MS))
                .takeWhile(tick -> session.getStatus() == SseSession.SessionStatus.CONNECTED)
                .subscribe(tick -> {
                    try {
                        sendMessage(session.getSessionId(), "heartbeat", 
                                "{\"timestamp\":\"" + LocalDateTime.now() + "\"}");
                    } catch (Exception e) {
                        log.error("Failed to send heartbeat for session: {}", session.getSessionId(), e);
                    }
                });
    }
    
    /**
     * 根据客户端ID查找会话
     */
    private SseSession findSessionByClientId(String clientId) {
        return activeSessions.values().stream()
                .filter(session -> clientId.equals(session.getClientId()))
                .findFirst()
                .orElse(null);
    }
} 