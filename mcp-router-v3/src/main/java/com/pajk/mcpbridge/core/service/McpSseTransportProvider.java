package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.model.SseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;

/**
 * MCP SSE传输提供者
 * 负责管理SSE连接和消息传输
 */
@Component
public class McpSseTransportProvider {

    private final static Logger log = LoggerFactory.getLogger(McpSseTransportProvider.class);

    // 活跃会话存储
    private final Map<String, SseSession> activeSessions = new ConcurrentHashMap<>();
    
    // 会话超时时间（60秒）
    private static final long DEFAULT_TIMEOUT_MS = 600_000;
    
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
            
            // 订阅sink的消息并发送给客户端，保存订阅以便清理
            Disposable sinkSubscription = sink.asFlux()
                    .doOnError(error -> {
                        log.error("Error in sink flux for session: {}", sessionId, error);
                        emitter.error(error);
                    })
                    .subscribe(
                            event -> {
                                try {
                                    emitter.next(event);
                                } catch (Exception e) {
                                    log.error("Error emitting event for session: {}", sessionId, e);
                                }
                            },
                            error -> {
                                log.error("Subscription error for session: {}", sessionId, error);
                                emitter.error(error);
                            },
                            () -> {
                                log.info("Sink flux completed for session: {}", sessionId);
                                emitter.complete();
                            }
                    );
            
            // 发送连接成功事件
            sendConnectionEvent(session, "connected");
            
            // 启动心跳，保存订阅
            Disposable heartbeatSubscription = startHeartbeat(session);
            
            // 处理连接关闭
            emitter.onDispose(() -> {
                log.info("SSE connection disposed for client: {}, session: {}", clientId, sessionId);
                // 取消心跳订阅
                if (heartbeatSubscription != null && !heartbeatSubscription.isDisposed()) {
                    heartbeatSubscription.dispose();
                }
                // 取消sink订阅
                if (sinkSubscription != null && !sinkSubscription.isDisposed()) {
                    sinkSubscription.dispose();
                }
                session.setStatus(SseSession.SessionStatus.DISCONNECTED);
                activeSessions.remove(sessionId);
                sink.tryEmitComplete();
            });
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
        return findSessionByClientId(clientId)
                .map(session -> sendMessage(session.getSessionId(), eventType, data))
                .orElse(Mono.error(new IllegalArgumentException("No active session for client: " + clientId)));
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
     * @return 心跳订阅，用于后续清理
     */
    private Disposable startHeartbeat(SseSession session) {
        return Flux.interval(Duration.ofMillis(HEARTBEAT_INTERVAL_MS))
                .takeWhile(tick -> {
                    SseSession.SessionStatus status = session.getStatus();
                    boolean shouldContinue = status == SseSession.SessionStatus.CONNECTED || 
                                           status == SseSession.SessionStatus.CONNECTING;
                    if (!shouldContinue) {
                        log.debug("Stopping heartbeat for session: {} due to status: {}", 
                                session.getSessionId(), status);
                    }
                    return shouldContinue;
                })
                .subscribe(
                        tick -> {
                            try {
                                // 检查会话是否仍然存在
                                if (activeSessions.containsKey(session.getSessionId())) {
                                    ServerSentEvent<String> heartbeatEvent = ServerSentEvent.<String>builder()
                                            .id(String.valueOf(messageIdGenerator.incrementAndGet()))
                                            .event("heartbeat")
                                            .data("{\"timestamp\":\"" + LocalDateTime.now() + "\"}")
                                            .build();
                                    
                                    Sinks.EmitResult result = session.getSink().tryEmitNext(heartbeatEvent);
                                    if (result.isFailure()) {
                                        log.warn("Failed to emit heartbeat for session: {}, result: {}", 
                                                session.getSessionId(), result);
                                    } else {
                                        session.updateLastActiveTime();
                                        log.debug("Sent heartbeat for session: {}", session.getSessionId());
                                    }
                                } else {
                                    log.debug("Session {} no longer exists, stopping heartbeat", session.getSessionId());
                                }
                            } catch (Exception e) {
                                log.error("Failed to send heartbeat for session: {}", session.getSessionId(), e);
                            }
                        },
                        error -> {
                            log.error("Heartbeat flux error for session: {}", session.getSessionId(), error);
                        },
                        () -> {
                            log.debug("Heartbeat flux completed for session: {}", session.getSessionId());
                        }
                );
    }
    
    /**
     * 根据客户端ID查找会话
     */
    public Optional<SseSession> findSessionByClientId(String clientId) {
        return activeSessions.values().stream()
                .filter(session -> clientId.equals(session.getClientId()))
                .findFirst();
    }
} 