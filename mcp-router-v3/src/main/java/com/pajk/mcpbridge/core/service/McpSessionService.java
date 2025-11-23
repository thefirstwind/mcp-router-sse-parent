package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.session.SessionInstanceIdProvider;
import com.pajk.mcpbridge.core.session.SessionMeta;
import com.pajk.mcpbridge.core.session.SessionRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话服务：维护 sessionId 与 serviceName、SSE sink、活跃时间的映射（支持多实例 Redis 共享）
 */
@Service
public class McpSessionService {

    private static final Logger log = LoggerFactory.getLogger(McpSessionService.class);

    private final SessionRedisRepository sessionRepository;
    private final String instanceId;
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sessionIdToSseSink = new ConcurrentHashMap<>();

    public McpSessionService(SessionRedisRepository sessionRepository,
                             SessionInstanceIdProvider instanceIdProvider) {
        this.sessionRepository = sessionRepository;
        this.instanceId = instanceIdProvider.getInstanceId();
    }

    public void registerSessionService(String sessionId, String serviceName) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        SessionMeta meta = new SessionMeta(sessionId, instanceId, serviceName, null, LocalDateTime.now(), true);
        sessionRepository.saveSessionMeta(meta);
    }

    public String getServiceName(String sessionId) {
        return sessionRepository.findSession(sessionId)
                .map(SessionMeta::getServiceName)
                .orElse(null);
    }

    public void registerSseSink(String sessionId, Sinks.Many<ServerSentEvent<String>> sink) {
        if (!StringUtils.hasText(sessionId) || sink == null) {
            return;
        }
        sessionIdToSseSink.put(sessionId, sink);
        sessionRepository.updateLastActive(sessionId);
    }

    public Sinks.Many<ServerSentEvent<String>> getSseSink(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return sessionIdToSseSink.get(sessionId);
    }

    public Mono<Sinks.Many<ServerSentEvent<String>>> waitForSseSink(String sessionId, int maxWaitSeconds) {
        if (!StringUtils.hasText(sessionId)) {
            return Mono.empty();
        }
        Sinks.Many<ServerSentEvent<String>> sink = sessionIdToSseSink.get(sessionId);
        if (sink != null) {
            return Mono.just(sink);
        }
        return Mono.delay(java.time.Duration.ofMillis(100))
                .flatMap(delay -> {
                    Sinks.Many<ServerSentEvent<String>> retrySink = sessionIdToSseSink.get(sessionId);
                    if (retrySink != null) {
                        return Mono.just(retrySink);
                    }
                    return waitForSseSinkWithRetry(sessionId, maxWaitSeconds, 1);
                });
    }

    private Mono<Sinks.Many<ServerSentEvent<String>>> waitForSseSinkWithRetry(
            String sessionId, int maxWaitSeconds, int currentAttempt) {
        if (currentAttempt >= maxWaitSeconds * 10) {
            sessionRepository.findSession(sessionId).ifPresent(meta -> {
                if (!instanceId.equals(meta.getInstanceId())) {
                    log.warn("Session {} 属于实例 {}，当前实例 {} 未找到 SSE sink，可能是请求被路由到不同实例。",
                            sessionId, meta.getInstanceId(), instanceId);
                }
            });
            return Mono.empty();
        }

        Sinks.Many<ServerSentEvent<String>> sink = sessionIdToSseSink.get(sessionId);
        if (sink != null) {
            return Mono.just(sink);
        }

        return Mono.delay(java.time.Duration.ofMillis(100))
                .flatMap(delay -> waitForSseSinkWithRetry(sessionId, maxWaitSeconds, currentAttempt + 1));
    }

    public java.util.Set<String> getAllSessionIds() {
        return new java.util.HashSet<>(sessionIdToSseSink.keySet());
    }

    public List<SessionOverview> getSessionOverview() {
        return sessionRepository.findAllSessions().stream()
                .map(meta -> new SessionOverview(
                        meta.getSessionId(),
                        meta.getServiceName(),
                        meta.getLastActive(),
                        meta.isActive()))
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
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionRepository.updateLastActive(sessionId);
    }

    public void removeSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionIdToSseSink.remove(sessionId);
        sessionRepository.removeSession(sessionId, instanceId);
    }

    public void updateBackendSessionId(String sessionId, String backendSessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessionRepository.updateBackendSessionId(sessionId, backendSessionId);
    }

    public record SessionOverview(String sessionId, String serviceName, LocalDateTime lastActive, boolean active) { }
}

