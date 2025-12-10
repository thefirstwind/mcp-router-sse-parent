package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.config.McpSessionProperties;
import com.pajk.mcpbridge.core.session.RedisClient;
import com.pajk.mcpbridge.core.session.SessionMeta;
import com.pajk.mcpbridge.core.session.SessionRedisRepository;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 数据管理控制器
 * 提供 Redis 数据的查询和管理功能
 */
@RestController
@RequestMapping("/admin/api/redis")
public class RedisManagementController {

    private static final Logger log = LoggerFactory.getLogger(RedisManagementController.class);

    private final RedisClient redisClient;
    private final SessionRedisRepository sessionRedisRepository;
    private final McpSessionProperties sessionProperties;

    public RedisManagementController(RedisClient redisClient, SessionRedisRepository sessionRedisRepository,
                                    McpSessionProperties sessionProperties) {
        this.redisClient = redisClient;
        this.sessionRedisRepository = sessionRedisRepository;
        this.sessionProperties = sessionProperties;
    }

    /**
     * 获取 Redis 中所有键的统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<RedisStatsResponse> getRedisStats(@RequestParam(required = false) String pattern) {
        try {
            // 默认使用 Redis 前缀模式
            String defaultPattern = sessionProperties.getRedisPrefix() + ":*";
            String searchPattern = pattern != null && !pattern.isEmpty() ? pattern : defaultPattern;
            Set<String> allKeys = redisClient.keys(searchPattern);
            
            RedisStatsResponse stats = new RedisStatsResponse();
            stats.setTotalKeys(allKeys != null ? allKeys.size() : 0);
            stats.setPattern(searchPattern);
            
            if (allKeys != null && !allKeys.isEmpty()) {
                // 按前缀分类统计
                Map<String, Long> keyTypeCounts = allKeys.stream()
                        .collect(Collectors.groupingBy(
                                key -> {
                                    if (key.contains(":sessions:")) return "sessions";
                                    if (key.contains(":instance:")) return "instances";
                                    return "other";
                                },
                                Collectors.counting()
                        ));
                stats.setKeyTypeCounts(keyTypeCounts);
            }
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get Redis stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有 instance IDs（第一层）
     */
    @GetMapping("/instances")
    public ResponseEntity<RedisInstancesResponse> getAllInstances() {
        try {
            Set<String> instances = sessionRedisRepository.findAllInstances();
            RedisInstancesResponse response = new RedisInstancesResponse();
            response.setInstances(new ArrayList<>(instances));
            response.setCount(instances.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all instances", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据 instanceId 获取该 instance 下的所有 sessionIds（第二层）
     */
    @GetMapping("/instances/{instanceId}/sessions")
    public ResponseEntity<RedisInstanceSessionsResponse> getSessionsByInstance(@PathVariable String instanceId) {
        try {
            Set<String> sessionIds = sessionRedisRepository.findSessionIdsByInstance(instanceId);
            RedisInstanceSessionsResponse response = new RedisInstanceSessionsResponse();
            response.setInstanceId(instanceId);
            response.setSessionIds(new ArrayList<>(sessionIds));
            response.setCount(sessionIds.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get sessions by instance: {}", instanceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有会话数据（兼容旧接口，支持过滤）
     */
    @GetMapping("/sessions")
    public ResponseEntity<RedisSessionsResponse> getAllSessions(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String transportType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String sessionId) {
        try {
            // 点击刷新按钮时清理空的instance
            sessionRedisRepository.cleanupEmptyInstances();
            
            List<SessionMeta> allSessions;
            
            // 如果指定了 sessionId，只查询该 session
            if (sessionId != null && !sessionId.isEmpty()) {
                Optional<SessionMeta> sessionOpt = sessionRedisRepository.findSession(sessionId);
                allSessions = sessionOpt.map(Collections::singletonList).orElse(Collections.emptyList());
            } else if (instanceId != null && !instanceId.isEmpty()) {
                // 如果指定了 instanceId，只查询该 instance 下的 sessions
                Set<String> sessionIds = sessionRedisRepository.findSessionIdsByInstance(instanceId);
                allSessions = sessionIds.stream()
                        .map(sid -> sessionRedisRepository.findSession(sid))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
            } else {
                // 如果 instanceId 为空，查找所有 instance，然后查找这些 instance 的 sessionId
                Set<String> allInstances = sessionRedisRepository.findAllInstances();
                Set<String> allSessionIds = new HashSet<>();
                for (String instId : allInstances) {
                    Set<String> sessionIds = sessionRedisRepository.findSessionIdsByInstance(instId);
                    if (sessionIds != null && !sessionIds.isEmpty()) {
                        allSessionIds.addAll(sessionIds);
                    }
                }
                // 根据 sessionId 获取 session 数据
                allSessions = allSessionIds.stream()
                        .map(sid -> sessionRedisRepository.findSession(sid))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
            }
            
            // 过滤
            List<SessionMeta> filteredSessions = allSessions.stream()
                    .filter(session -> {
                        if (serviceName != null && !serviceName.isEmpty()) {
                            if (session.getServiceName() == null || 
                                !session.getServiceName().contains(serviceName)) {
                                return false;
                            }
                        }
                        if (transportType != null && !transportType.isEmpty()) {
                            if (session.getTransportType() == null || 
                                !session.getTransportType().equalsIgnoreCase(transportType)) {
                                return false;
                            }
                        }
                        if (active != null) {
                            if (session.isActive() != active) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted((a, b) -> {
                        LocalDateTime lastActiveA = a.getLastActive();
                        LocalDateTime lastActiveB = b.getLastActive();
                        if (lastActiveA == null && lastActiveB == null) return 0;
                        if (lastActiveA == null) return 1;
                        if (lastActiveB == null) return -1;
                        return lastActiveB.compareTo(lastActiveA);
                    })
                    .collect(Collectors.toList());
            
            RedisSessionsResponse response = new RedisSessionsResponse();
            response.setTotal(allSessions.size());
            response.setFiltered(filteredSessions.size());
            response.setSessions(filteredSessions);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取指定会话的详细信息
     */
    @GetMapping("/sessions/{sessionId}/detail")
    public ResponseEntity<SessionDetailResponse> getSessionDetail(@PathVariable String sessionId) {
        try {
            Optional<SessionMeta> sessionOpt = sessionRedisRepository.findSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            SessionMeta session = sessionOpt.get();
            SessionDetailResponse response = new SessionDetailResponse();
            response.setSession(session);
            
            // 获取 Redis 原始数据
            try {
                String sessionKey = sessionProperties.getRedisPrefix() + ":sessions:" + sessionId;
                Map<String, String> rawData = redisClient.hgetAll(sessionKey);
                response.setRawData(rawData);
            } catch (Exception e) {
                log.warn("Failed to get raw data for session: {}", sessionId, e);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get session detail: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除指定会话
     */
    @PostMapping("/sessions/{sessionId}/delete")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        try {
            Optional<SessionMeta> sessionOpt = sessionRedisRepository.findSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            SessionMeta session = sessionOpt.get();
            sessionRedisRepository.removeSession(sessionId, session.getInstanceId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Session deleted successfully");
            response.put("sessionId", sessionId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete session: {}", sessionId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete session: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 搜索 Redis 键的值（支持通配符匹配 values）
     * 生产环境限制：无法根据通配符查询 key，只能指定固定的 key 按照通配符查询 values
     * 
     * @param key 固定的 Redis 键名（必填）
     * @param valuePattern values 的通配符模式（可选，例如：*session*）
     * @param limit 返回结果数量限制
     */
    @GetMapping("/keys")
    public ResponseEntity<RedisKeysResponse> searchKeys(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String valuePattern,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        try {
            // 如果没有提供 key，返回空结果
            if (key == null || key.isEmpty()) {
                RedisKeysResponse response = new RedisKeysResponse();
                response.setKey(key);
                response.setValuePattern(valuePattern);
                response.setTotal(0);
                response.setReturned(0);
                response.setKeys(Collections.emptyList());
                response.setKeyType("none");
                return ResponseEntity.ok(response);
            }
            
            // 获取键的类型
            String keyType = redisClient.type(key);
            if (keyType == null || "none".equals(keyType)) {
                RedisKeysResponse response = new RedisKeysResponse();
                response.setKey(key);
                response.setValuePattern(valuePattern);
                response.setTotal(0);
                response.setReturned(0);
                response.setKeys(Collections.emptyList());
                response.setKeyType("none");
                return ResponseEntity.ok(response);
            }
            
            List<String> valueList = new ArrayList<>();
            
            // 根据类型获取 values
            if ("hash".equals(keyType)) {
                // Hash 类型：获取所有 field-value，根据 value 进行通配符匹配
                Map<String, String> hashData = redisClient.hgetAll(key);
                if (hashData != null) {
                    for (Map.Entry<String, String> entry : hashData.entrySet()) {
                        String value = entry.getValue();
                        if (valuePattern == null || valuePattern.isEmpty() || 
                            matchesPattern(value, valuePattern)) {
                            // 格式：field:value
                            valueList.add(entry.getKey() + ":" + value);
                        }
                    }
                }
            } else if ("set".equals(keyType)) {
                // Set 类型：获取所有 members，根据 member 进行通配符匹配
                Set<String> members = redisClient.smembers(key);
                if (members != null) {
                    for (String member : members) {
                        if (valuePattern == null || valuePattern.isEmpty() || 
                            matchesPattern(member, valuePattern)) {
                            valueList.add(member);
                        }
                    }
                }
            } else if ("string".equals(keyType)) {
                // String 类型：直接匹配 value
                String value = redisClient.get(key);
                if (value != null) {
                    if (valuePattern == null || valuePattern.isEmpty() || 
                        matchesPattern(value, valuePattern)) {
                        valueList.add(value);
                    }
                }
            }
            
            // 排序并限制数量
            List<String> resultList = valueList.stream()
                    .sorted()
                    .limit(limit)
                    .collect(Collectors.toList());
            
            RedisKeysResponse response = new RedisKeysResponse();
            response.setKey(key);
            response.setValuePattern(valuePattern);
            response.setTotal(valueList.size());
            response.setReturned(resultList.size());
            response.setKeys(resultList);
            response.setKeyType(keyType);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to search keys with key={}, valuePattern={}", key, valuePattern, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 简单的通配符匹配（支持 * 和 ?）
     * @param text 要匹配的文本
     * @param pattern 通配符模式（* 匹配任意字符，? 匹配单个字符）
     * @return 是否匹配
     */
    private boolean matchesPattern(String text, String pattern) {
        if (text == null || pattern == null) {
            return false;
        }
        // 将 Redis 通配符模式转换为正则表达式
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return text.matches(regex);
    }

    /**
     * 获取指定键的值
     */
    @GetMapping("/keys/{key:.*}")
    public ResponseEntity<RedisKeyDetailResponse> getKeyDetail(@PathVariable String key) {
        try {
            // URL 解码
            String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
            log.debug("Getting key detail for: {}", decodedKey);
            
            RedisKeyDetailResponse response = new RedisKeyDetailResponse();
            response.setKey(decodedKey);
            
            // 先获取键的类型
            String keyType;
            try {
                keyType = redisClient.type(decodedKey);
                if (keyType == null || "none".equals(keyType)) {
                    response.setType("none");
                    response.setHashData(Collections.emptyMap());
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                log.warn("Failed to get key type for: {}", decodedKey, e);
                keyType = "unknown";
            }
            
            response.setType(keyType);
            
            // 根据类型获取数据
            Map<String, String> data = new HashMap<>();
            try {
                if ("hash".equals(keyType)) {
                    // Hash 类型
                    Map<String, String> hashData = redisClient.hgetAll(decodedKey);
                    if (hashData != null) {
                        data.putAll(hashData);
                    }
                } else if ("set".equals(keyType)) {
                    // Set 类型
                    Set<String> members = redisClient.smembers(decodedKey);
                    if (members != null) {
                        int index = 0;
                        for (String member : members) {
                            data.put("member_" + index++, member);
                        }
                        data.put("_count", String.valueOf(members.size()));
                    }
                } else if ("string".equals(keyType)) {
                    // String 类型
                    String value = redisClient.get(decodedKey);
                    if (value != null) {
                        data.put("value", value);
                    }
                } else {
                    // 其他类型，尝试作为 Hash 获取（兼容处理）
                    try {
                        Map<String, String> hashData = redisClient.hgetAll(decodedKey);
                        if (hashData != null && !hashData.isEmpty()) {
                            data.putAll(hashData);
                            response.setType("hash"); // 更新类型
                        }
                    } catch (Exception e) {
                        log.debug("Key {} is not a hash, type: {}", decodedKey, keyType);
                        data.put("_note", "Key type: " + keyType + ", cannot read as hash");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read key data for: {}, type: {}", decodedKey, keyType, e);
                data.put("_error", e.getMessage());
            }
            
            response.setHashData(data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get key detail: {}", key, e);
            // 返回错误信息而不是空响应
            RedisKeyDetailResponse errorResponse = new RedisKeyDetailResponse();
            errorResponse.setKey(key);
            errorResponse.setType("error");
            errorResponse.setHashData(Collections.singletonMap("error", e.getMessage()));
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * 删除指定键
     */
    @DeleteMapping("/keys/{key:.*}")
    public ResponseEntity<Map<String, Object>> deleteKey(@PathVariable String key) {
        try {
            // URL 解码
            String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
            log.debug("Deleting key: {}", decodedKey);
            redisClient.del(decodedKey);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Key deleted successfully");
            response.put("key", decodedKey);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete key: {}", key, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to delete key: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Response DTOs
    @Data
    public static class RedisStatsResponse {
        private int totalKeys;
        private String pattern;
        private Map<String, Long> keyTypeCounts;
    }

    @Data
    public static class RedisInstancesResponse {
        private List<String> instances;
        private int count;
    }
    
    @Data
    public static class RedisInstanceSessionsResponse {
        private String instanceId;
        private List<String> sessionIds;
        private int count;
    }
    
    @Data
    public static class RedisSessionsResponse {
        private int total;
        private int filtered;
        private List<SessionMeta> sessions;
    }

    @Data
    public static class SessionDetailResponse {
        private SessionMeta session;
        private Map<String, String> rawData;
    }

    @Data
    public static class RedisKeysResponse {
        private String key;  // 固定的 Redis 键名
        private String valuePattern;  // values 的通配符模式
        private String keyType;  // 键的类型（hash, set, string 等）
        private int total;  // 匹配的 values 总数
        private int returned;  // 返回的 values 数量
        private List<String> keys;  // values 列表（对于 hash 类型，格式为 field:value）
    }

    @Data
    public static class RedisKeyDetailResponse {
        private String key;
        private String type;
        private Map<String, String> hashData;
    }
}

