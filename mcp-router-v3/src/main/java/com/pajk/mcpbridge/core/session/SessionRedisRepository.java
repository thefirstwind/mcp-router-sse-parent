package com.pajk.mcpbridge.core.session;

import com.pajk.mcpbridge.core.config.McpSessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SessionRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionRedisRepository.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RedisClient redisClient;
    private final McpSessionProperties properties;

    public SessionRedisRepository(RedisClient redisClient, McpSessionProperties properties) {
        this.redisClient = redisClient;
        this.properties = properties;
        log.info("✅ SessionRedisRepository initialized with RedisClient: {}", redisClient.getClass().getSimpleName());
    }

    public void saveSessionMeta(SessionMeta meta) {
        if (meta == null || meta.getSessionId() == null) {
            return;
        }
        String sessionKey = sessionKey(meta.getSessionId());
        Map<String, String> map = new HashMap<>();
        map.put("sessionId", meta.getSessionId());
        map.put("instanceId", meta.getInstanceId());
        map.put("serviceName", Optional.ofNullable(meta.getServiceName()).orElse(""));
        map.put("backendSessionId", Optional.ofNullable(meta.getBackendSessionId()).orElse(""));
        map.put("transportType", Optional.ofNullable(meta.getTransportType()).orElse(""));
        map.put("lastActive", FORMATTER.format(Optional.ofNullable(meta.getLastActive()).orElse(LocalDateTime.now())));
        map.put("active", Boolean.toString(meta.isActive()));
        
        long ttlSeconds = properties.getTtl().getSeconds();
        try {
            redisClient.hsetAll(sessionKey, map);
            redisClient.expire(sessionKey, ttlSeconds);
            redisClient.sadd(instanceKey(meta.getInstanceId()), meta.getSessionId());
            redisClient.expire(instanceKey(meta.getInstanceId()), ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to save session meta for sessionId: {}", meta.getSessionId(), e);
        }
    }

    public Optional<SessionMeta> findSession(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        String key = sessionKey(sessionId);
        try {
            Map<String, String> map = redisClient.hgetAll(key);
            if (map == null || map.isEmpty()) {
                return Optional.empty();
            }
            // Convert Map<String, String> to Map<Object, Object> for compatibility with SessionMeta.fromMap
            Map<Object, Object> objectMap = new HashMap<>(map);
            return Optional.ofNullable(SessionMeta.fromMap(objectMap));
        } catch (Exception e) {
            log.error("Failed to find session for sessionId: {}", sessionId, e);
            return Optional.empty();
        }
    }

    public void updateLastActive(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String key = sessionKey(sessionId);
        long ttlSeconds = properties.getTtl().getSeconds();
        try {
            redisClient.hset(key, "lastActive", FORMATTER.format(LocalDateTime.now()));
            redisClient.hset(key, "active", Boolean.TRUE.toString());
            redisClient.expire(key, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to update lastActive for sessionId: {}", sessionId, e);
        }
    }

    public void updateBackendSessionId(String sessionId, String backendSessionId) {
        if (sessionId == null) {
            return;
        }
        String key = sessionKey(sessionId);
        long ttlSeconds = properties.getTtl().getSeconds();
        try {
            redisClient.hset(key, "backendSessionId", Optional.ofNullable(backendSessionId).orElse(""));
            redisClient.expire(key, ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to update backendSessionId for sessionId: {}", sessionId, e);
        }
    }

    public void removeSession(String sessionId, String instanceId) {
        if (sessionId == null) {
            return;
        }
        String targetInstance = instanceId;
        if (targetInstance == null) {
            targetInstance = findSession(sessionId).map(SessionMeta::getInstanceId).orElse(null);
        }
        String key = sessionKey(sessionId);
        try {
            redisClient.del(key);
            if (targetInstance != null) {
                redisClient.srem(instanceKey(targetInstance), sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to remove session for sessionId: {}", sessionId, e);
        }
    }

    public List<SessionMeta> findAllSessions() {
        String pattern = sessionKey("*");
        try {
            Set<String> keys = redisClient.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }
            return keys.stream()
                    .map(k -> {
                        try {
                            Map<String, String> map = redisClient.hgetAll(k);
                            if (map == null || map.isEmpty()) {
                                return null;
                            }
                            // Convert Map<String, String> to Map<Object, Object> for compatibility
                            Map<Object, Object> objectMap = new HashMap<>(map);
                            return SessionMeta.fromMap(objectMap);
                        } catch (Exception e) {
                            log.warn("Failed to read session data for key: {}", k, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to findAllSessions", e);
            return Collections.emptyList();
        }
    }

    private String sessionKey(String sessionId) {
        return properties.getRedisPrefix() + ":sessions:" + sessionId;
    }

    private String instanceKey(String instanceId) {
        return properties.getRedisPrefix() + ":instance:" + instanceId;
    }
}

