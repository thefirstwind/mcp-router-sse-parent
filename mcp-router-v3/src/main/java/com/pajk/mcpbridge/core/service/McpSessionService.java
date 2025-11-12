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

