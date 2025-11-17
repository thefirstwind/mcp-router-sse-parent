package com.pajk.mcpbridge.core.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话服务：维护 sessionId 与 serviceName、SSE sink、活跃时间的映射
 */
@Service
public class McpSessionService {

    private final Map<String, String> sessionIdToServiceName = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sessionIdToSseSink = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> sessionIdToLastActive = new ConcurrentHashMap<>();

    public void registerSessionService(String sessionId, String serviceName) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        if (serviceName != null && !serviceName.isEmpty()) {
            sessionIdToServiceName.put(sessionId, serviceName);
        }
        touch(sessionId);
    }

    public String getServiceName(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return sessionIdToServiceName.get(sessionId);
    }

    public void registerSseSink(String sessionId, Sinks.Many<ServerSentEvent<String>> sink) {
        if (sessionId == null || sessionId.isEmpty() || sink == null) {
            return;
        }
        sessionIdToSseSink.put(sessionId, sink);
        touch(sessionId);
    }

    public Sinks.Many<ServerSentEvent<String>> getSseSink(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return sessionIdToSseSink.get(sessionId);
    }
    
    /**
     * 等待 SSE sink 就绪（用于处理时序问题）
     * 如果 sink 不存在，等待一段时间后重试
     */
    public reactor.core.publisher.Mono<Sinks.Many<ServerSentEvent<String>>> waitForSseSink(String sessionId, int maxWaitSeconds) {
        if (sessionId == null || sessionId.isEmpty()) {
            return reactor.core.publisher.Mono.empty();
        }
        
        Sinks.Many<ServerSentEvent<String>> sink = sessionIdToSseSink.get(sessionId);
        if (sink != null) {
            return reactor.core.publisher.Mono.just(sink);
        }
        
        // 如果 sink 不存在，等待一段时间后重试
        return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(100))
                .flatMap(delay -> {
                    Sinks.Many<ServerSentEvent<String>> retrySink = sessionIdToSseSink.get(sessionId);
                    if (retrySink != null) {
                        return reactor.core.publisher.Mono.just(retrySink);
                    }
                    // 继续等待，最多等待 maxWaitSeconds 秒
                    return waitForSseSinkWithRetry(sessionId, maxWaitSeconds, 1);
                });
    }
    
    /**
     * 递归重试等待 SSE sink
     */
    private reactor.core.publisher.Mono<Sinks.Many<ServerSentEvent<String>>> waitForSseSinkWithRetry(
            String sessionId, int maxWaitSeconds, int currentAttempt) {
        if (currentAttempt >= maxWaitSeconds * 10) { // 每 100ms 重试一次
            return reactor.core.publisher.Mono.empty();
        }
        
        Sinks.Many<ServerSentEvent<String>> sink = sessionIdToSseSink.get(sessionId);
        if (sink != null) {
            return reactor.core.publisher.Mono.just(sink);
        }
        
        return reactor.core.publisher.Mono.delay(java.time.Duration.ofMillis(100))
                .flatMap(delay -> waitForSseSinkWithRetry(sessionId, maxWaitSeconds, currentAttempt + 1));
    }
    
    /**
     * 获取所有已注册的 sessionId（用于调试）
     */
    public java.util.Set<String> getAllSessionIds() {
        return new java.util.HashSet<>(sessionIdToSseSink.keySet());
    }

    public void touch(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessionIdToLastActive.put(sessionId, LocalDateTime.now());
    }

    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessionIdToServiceName.remove(sessionId);
        sessionIdToSseSink.remove(sessionId);
        sessionIdToLastActive.remove(sessionId);
    }
}

