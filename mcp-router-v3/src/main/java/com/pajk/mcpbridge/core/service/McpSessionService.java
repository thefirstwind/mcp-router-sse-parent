package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.session.SessionInstanceIdProvider;
import com.pajk.mcpbridge.core.session.SessionMeta;
import com.pajk.mcpbridge.core.session.SessionRedisRepository;
import com.pajk.mcpbridge.core.transport.TransportType;
import com.pajk.mcpbridge.persistence.entity.RoutingLog;
import com.pajk.mcpbridge.persistence.mapper.RoutingLogMapper;
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
    private final RoutingLogMapper routingLogMapper;

    public McpSessionService(SessionRedisRepository sessionRepository,
                             SessionInstanceIdProvider instanceIdProvider,
                             RoutingLogMapper routingLogMapper) {
        this.sessionRepository = sessionRepository;
        this.instanceId = instanceIdProvider.getInstanceId();
        this.routingLogMapper = routingLogMapper;
    }

    public void registerSessionService(String sessionId, String serviceName, TransportType transportType) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        String transport = transportType != null ? transportType.name() : null;
        SessionMeta meta = new SessionMeta(sessionId, instanceId, serviceName, null, transport, LocalDateTime.now(), true);
        sessionRepository.saveSessionMeta(meta);
    }

    public String getServiceName(String sessionId) {
        return sessionRepository.findSession(sessionId)
                .map(SessionMeta::getServiceName)
                .orElse(null);
    }

    public TransportType getTransportType(String sessionId) {
        return sessionRepository.findSession(sessionId)
                .map(SessionMeta::getTransportType)
                .map(value -> {
                    try {
                        return TransportType.valueOf(value);
                    } catch (Exception e) {
                        return TransportType.SSE;
                    }
                })
                .orElse(TransportType.SSE);
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
        // 激进优化：立即检查，不延迟
        Sinks.Many<ServerSentEvent<String>> sink = sessionIdToSseSink.get(sessionId);
        if (sink != null) {
            return Mono.just(sink);
        }
        // 如果 maxWaitSeconds 为 0，立即返回空（不等待）
        if (maxWaitSeconds <= 0) {
            return Mono.empty();
        }
        // 使用更短的延迟（10毫秒）进行重试
        return Mono.delay(java.time.Duration.ofMillis(10))
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
        // 激进优化：减少重试次数，使用更短的延迟
        if (currentAttempt >= maxWaitSeconds * 20) { // 如果 maxWaitSeconds=0，这个条件不会触发
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

        // 激进优化：使用更短的延迟（10毫秒）
        return Mono.delay(java.time.Duration.ofMillis(10))
                .flatMap(delay -> waitForSseSinkWithRetry(sessionId, maxWaitSeconds, currentAttempt + 1));
    }

    public java.util.Set<String> getAllSessionIds() {
        return new java.util.HashSet<>(sessionIdToSseSink.keySet());
    }

    public List<SessionOverview> getSessionOverview() {
        // SSE 会话超时时间：10分钟（600秒）
        final long SSE_TIMEOUT_MS = 600_000;
        // Redis 会话 TTL：30分钟（从配置中获取，这里使用默认值）
        final long REDIS_SESSION_TTL_MS = 30 * 60 * 1000;
        
        return sessionRepository.findAllSessions().stream()
                .map(meta -> {
                    // 从 RoutingLog 中获取客户端信息（取最新的日志记录）
                    String clientId = null;
                    String clientIp = null;
                    String userAgent = null;
                    try {
                        List<RoutingLog> logs = routingLogMapper.selectBySessionId(meta.getSessionId(), 1);
                        if (logs != null && !logs.isEmpty()) {
                            RoutingLog latestLog = logs.get(0);
                            clientId = latestLog.getClientId();
                            clientIp = latestLog.getClientIp();
                            userAgent = latestLog.getUserAgent();
                        }
                    } catch (Exception e) {
                        log.debug("Failed to get client info for session: {}", meta.getSessionId(), e);
                    }
                    
                    // 计算过期时间
                    LocalDateTime expireTime = null;
                    long timeoutMs = 0;
                    if (meta.getLastActive() != null) {
                        // 根据传输类型选择超时时间
                        String transportType = meta.getTransportType();
                        if (transportType != null && "STREAMABLE".equalsIgnoreCase(transportType)) {
                            // Streamable 会话使用 Redis TTL（30分钟）
                            timeoutMs = REDIS_SESSION_TTL_MS;
                            expireTime = meta.getLastActive().plusNanos(timeoutMs * 1_000_000);
                        } else {
                            // SSE 会话使用 10 分钟超时
                            timeoutMs = SSE_TIMEOUT_MS;
                            expireTime = meta.getLastActive().plusNanos(timeoutMs * 1_000_000);
                        }
                    }
                    
                    return new SessionOverview(
                            meta.getSessionId(),
                            meta.getServiceName(),
                            meta.getTransportType(),
                            meta.getLastActive(),
                            meta.isActive(),
                            clientId,
                            clientIp,
                            userAgent,
                            expireTime,
                            timeoutMs);
                })
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

    public record SessionOverview(
            String sessionId, 
            String serviceName, 
            String transportType, 
            LocalDateTime lastActive, 
            boolean active,
            String clientId,
            String clientIp,
            String userAgent,
            LocalDateTime expireTime,
            long timeoutMs) { }
}

