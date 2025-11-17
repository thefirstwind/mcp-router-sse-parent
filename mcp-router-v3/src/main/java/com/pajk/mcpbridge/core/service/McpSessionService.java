package com.pajk.mcpbridge.core.service;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话服务：维护 sessionId 与 serviceName、SSE sink、活跃时间的映射
 */
@Service
public class McpSessionService {

    private static final Duration SESSION_HISTORY_TTL = Duration.ofMinutes(30);

    private final Map<String, String> sessionIdToServiceName = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sessionIdToSseSink = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> sessionIdToLastActive = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessionHistory = new ConcurrentHashMap<>();

    public void registerSessionService(String sessionId, String serviceName) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        if (serviceName != null && !serviceName.isEmpty()) {
            sessionIdToServiceName.put(sessionId, serviceName);
        }
        touch(sessionId);
        sessionHistory.compute(sessionId, (id, existing) -> {
            SessionInfo info = existing != null ? existing : new SessionInfo(id);
            info.setServiceName(serviceName);
            info.setActive(true);
            info.setLastActive(LocalDateTime.now());
            return info;
        });
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
        sessionHistory.compute(sessionId, (id, existing) -> {
            SessionInfo info = existing != null ? existing : new SessionInfo(id);
            info.setActive(true);
            info.setLastActive(LocalDateTime.now());
            return info;
        });
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

    /**
     * 获取当前会话概览信息（用于 UI 展示）
     */
    public List<SessionOverview> getSessionOverview() {
        cleanupStaleHistory();
        return sessionHistory.values().stream()
                .map(info -> new SessionOverview(
                        info.getSessionId(),
                        info.getServiceName(),
                        info.getLastActive(),
                        info.isActive()))
                .sorted((a, b) -> {
                    LocalDateTime lastActiveA = a.lastActive();
                    LocalDateTime lastActiveB = b.lastActive();
                    if (lastActiveA == null && lastActiveB == null) {
                        return 0;
                    }
                    if (lastActiveA == null) {
                        return 1;
                    }
                    if (lastActiveB == null) {
                        return -1;
                    }
                    return lastActiveB.compareTo(lastActiveA);
                })
                .collect(Collectors.toList());
    }

    public void touch(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessionIdToLastActive.put(sessionId, LocalDateTime.now());
        sessionHistory.computeIfPresent(sessionId, (id, info) -> {
            info.setLastActive(LocalDateTime.now());
            return info;
        });
    }

    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessionIdToServiceName.remove(sessionId);
        sessionIdToSseSink.remove(sessionId);
        sessionIdToLastActive.remove(sessionId);
        sessionHistory.computeIfPresent(sessionId, (id, info) -> {
            info.setActive(false);
            info.setLastActive(LocalDateTime.now());
            return info;
        });
    }

    /**
     * 会话概览记录
     */
    private void cleanupStaleHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minus(SESSION_HISTORY_TTL);
        sessionHistory.entrySet().removeIf(entry -> {
            SessionInfo info = entry.getValue();
            LocalDateTime lastActive = info.getLastActive();
            return lastActive != null && lastActive.isBefore(cutoff);
        });
    }

    public record SessionOverview(String sessionId, String serviceName, LocalDateTime lastActive, boolean active) { }

    private static class SessionInfo {
        private final String sessionId;
        private String serviceName;
        private LocalDateTime lastActive;
        private boolean active;

        SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.lastActive = LocalDateTime.now();
            this.active = true;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            if (serviceName != null && !serviceName.isEmpty()) {
                this.serviceName = serviceName;
            }
        }

        public LocalDateTime getLastActive() {
            return lastActive;
        }

        public void setLastActive(LocalDateTime lastActive) {
            this.lastActive = lastActive;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}

