package com.nacos.mcp.router.v2.registry;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.nacos.mcp.router.v2.model.McpServerInfo;
import com.nacos.mcp.router.v2.service.McpConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP服务注册实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerRegistry {
    
    private final NamingService namingService;
    private final McpConfigService mcpConfigService;
    
    // 本地缓存已注册的服务
    private final Map<String, McpServerInfo> registeredServers = new ConcurrentHashMap<>();
    
    // 本地缓存健康实例列表，key: serviceName@groupName
    public final Map<String, List<McpServerInfo>> healthyInstanceCache = new ConcurrentHashMap<>();
    public final Map<String, Long> healthyCacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000; // 30秒缓存

    /**
     * 注册MCP服务器（严格顺序，原子操作）
     */
    public Mono<Void> registerServer(McpServerInfo serverInfo) {
        // 1. 先顺序发布 server config、tools config、version config
        return mcpConfigService.publishServerConfig(serverInfo)
            .flatMap(success -> {
                if (!success) {
                    log.error("Failed to publish server config for: {}", serverInfo.getName());
                    return Mono.error(new RuntimeException("Failed to publish server config"));
                }
                return mcpConfigService.publishToolsConfig(serverInfo);
            })
            .flatMap(success -> {
                if (!success) {
                    log.error("Failed to publish tools config for: {}", serverInfo.getName());
                    return Mono.error(new RuntimeException("Failed to publish tools config"));
                }
                return mcpConfigService.publishVersionConfig(serverInfo);
            })
            .flatMap(success -> {
                if (!success) {
                    log.error("Failed to publish version config for: {}", serverInfo.getName());
                    return Mono.error(new RuntimeException("Failed to publish version config"));
                }
                // 2. 全部成功后，获取 server config 内容，计算MD5，注册实例
                return mcpConfigService.getServerConfig(serverInfo.getName(), serverInfo.getVersion())
                    .flatMap(config -> {
                        String configJson = null;
                        String md5 = null;
                        try {
                            configJson = mcpConfigService.serializeServerConfig(config);
                            md5 = mcpConfigService.md5(configJson);
                        } catch (Exception e) {
                            log.error("Failed to serialize or md5 server config", e);
                        }
                        Instance instance = buildInstance(serverInfo);
                        if (md5 != null) {
                            instance.getMetadata().put("server.md5", md5);
                        }
                        // tools.names 由元数据决定
                        if (serverInfo.getMetadata() != null && serverInfo.getMetadata().get("tools.names") != null) {
                            instance.getMetadata().put("tools.names", serverInfo.getMetadata().get("tools.names"));
                        }
                        try {
                            namingService.registerInstance(serverInfo.getName(), serverInfo.getServiceGroup(), instance);
                        } catch (Exception e) {
                            log.error("Failed to register MCP server instance: {}", serverInfo.getName(), e);
                            return Mono.error(new RuntimeException("Failed to register MCP server instance", e));
                        }
                        // 更新本地缓存
                        serverInfo.setRegistrationTime(LocalDateTime.now());
                        serverInfo.setLastHeartbeat(LocalDateTime.now());
                        registeredServers.put(serverInfo.getName(), serverInfo);
                        log.info("Successfully registered MCP server: {} at {}:{}", 
                                serverInfo.getName(), serverInfo.getIp(), serverInfo.getPort());
                        return Mono.empty();
                    });
            });
    }
    
    /**
     * 注销MCP服务器
     */
    public Mono<Void> deregisterServer(String serviceName, String serviceGroup) {
        // 默认 version 取 1.0.0，可根据实际场景传参
        String defaultVersion = "1.0.0";
        return mcpConfigService.deleteServerConfig(serviceName, defaultVersion)
            .onErrorResume(e -> {
                log.warn("Failed to delete server config for: {} (ignore)", serviceName, e);
                return Mono.empty();
            })
            .then(mcpConfigService.deleteToolsConfig(serviceName, defaultVersion)
                .onErrorResume(e -> {
                    log.warn("Failed to delete tools config for: {} (ignore)", serviceName, e);
                    return Mono.empty();
                })
            )
            .then(mcpConfigService.deleteVersionConfig(serviceName)
                .onErrorResume(e -> {
                    log.warn("Failed to delete version config for: {} (ignore)", serviceName, e);
                    return Mono.empty();
                })
            )
            .then(Mono.fromCallable(() -> {
                try {
                    McpServerInfo serverInfo = registeredServers.get(serviceName);
                    if (serverInfo != null) {
                        namingService.deregisterInstance(serviceName, serviceGroup,
                                serverInfo.getIp(), serverInfo.getPort());
                        registeredServers.remove(serviceName);
                        log.info("Successfully deregistered MCP server: {}", serviceName);
                    }
                    return null;
                } catch (Exception e) {
                    log.error("Failed to deregister MCP server: {}", serviceName, e);
                    throw new RuntimeException("Failed to deregister MCP server", e);
                }
            }));
    }
    
    /**
     * 订阅服务变更，自动刷新本地健康实例缓存
     */
    public void subscribeServiceChange(String serviceName, String serviceGroup) {
        String cacheKey = serviceName + "@" + serviceGroup;
        try {
            namingService.subscribe(serviceName, serviceGroup, new EventListener() {
                @Override
                public void onEvent(com.alibaba.nacos.api.naming.listener.Event event) {
                    if (event instanceof NamingEvent namingEvent) {
                        List<Instance> instances = namingEvent.getInstances();
                        List<McpServerInfo> healthyList = instances.stream()
                                .filter(Instance::isHealthy)
                                .filter(Instance::isEnabled)
                                .map(instance -> buildServerInfo(instance, serviceName))
                                .toList();
                        // 实时刷新本地健康实例缓存
                        healthyInstanceCache.put(cacheKey, healthyList);
                        healthyCacheTimestamp.put(cacheKey, System.currentTimeMillis());
                        log.info("[订阅] 服务{}@{}变更，健康实例数：{}，本地缓存已刷新", serviceName, serviceGroup, healthyList.size());
                    }
                }
            });
            log.info("已订阅Nacos服务变更: {}@{}", serviceName, serviceGroup);
        } catch (Exception e) {
            log.error("订阅Nacos服务变更失败: {}@{}", serviceName, serviceGroup, e);
        }
    }

    /**
     * 获取所有健康的MCP服务器实例（优先查本地缓存）
     */
    public Flux<McpServerInfo> getAllHealthyServers(String serviceName, String serviceGroup) {
        String cacheKey = serviceName + "@" + serviceGroup;
        List<McpServerInfo> cached = healthyInstanceCache.get(cacheKey);
        Long ts = healthyCacheTimestamp.get(cacheKey);
        if (cached != null && ts != null && (System.currentTimeMillis() - ts < CACHE_TTL_MS)) {
            return Flux.fromIterable(cached);
        }
        // 首次或缓存过期，主动查Nacos并刷新缓存
        return Mono.fromCallable(() -> {
            try {
                List<Instance> instances = namingService.selectInstances(serviceName, serviceGroup, true);
                List<McpServerInfo> healthyList = instances.stream()
                        .map(instance -> buildServerInfo(instance, serviceName))
                        .toList();
                healthyInstanceCache.put(cacheKey, healthyList);
                healthyCacheTimestamp.put(cacheKey, System.currentTimeMillis());
                // 自动订阅
                subscribeServiceChange(serviceName, serviceGroup);
                return healthyList;
            } catch (Exception e) {
                log.error("Failed to get healthy servers for service: {}", serviceName, e);
                throw new RuntimeException("Failed to get healthy servers", e);
            }
        }).flatMapMany(Flux::fromIterable);
    }
    
    /**
     * 获取指定服务的所有实例
     */
    public Flux<McpServerInfo> getAllInstances(String serviceName, String serviceGroup) {
        return Mono.fromCallable(() -> {
            try {
                List<Instance> instances = namingService.getAllInstances(serviceName, serviceGroup);
                return instances.stream()
                        .map(instance -> buildServerInfo(instance, serviceName))
                        .toList();
            } catch (Exception e) {
                log.error("Failed to get all instances for service: {}", serviceName, e);
                throw new RuntimeException("Failed to get all instances", e);
            }
        }).flatMapMany(Flux::fromIterable);
    }
    
    /**
     * 选择一个健康的服务实例（负载均衡）
     */
    public Mono<McpServerInfo> selectHealthyServer(String serviceName, String serviceGroup) {
        return getAllHealthyServers(serviceName, serviceGroup)
                .next(); // 简单选择第一个健康实例，后续可以实现更复杂的负载均衡算法
    }
    
    /**
     * 同步本地健康/熔断状态到Nacos实例（仅日志提示，Nacos SDK无updateInstance方法）
     */
    public void updateInstanceHealth(String serviceName, String serviceGroup, String ip, int port, boolean healthy, boolean enabled) {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName, serviceGroup);
            for (Instance instance : instances) {
                if (instance.getIp().equals(ip) && instance.getPort() == port) {
                    log.info("[健康同步] 仅日志提示：如需变更Nacos实例健康状态，需注销+注册实例。目标：{}:{} healthy={} enabled={}", ip, port, healthy, enabled);
                    // 实际生产建议：namingService.deregisterInstance(...) + namingService.registerInstance(...)
                    return;
                }
            }
            log.warn("[健康同步] 未找到待同步的实例({}:{})，service={},group={}", ip, port, serviceName, serviceGroup);
        } catch (Exception e) {
            log.error("[健康同步] 同步实例健康状态到Nacos失败: {}@{} {}:{}", serviceName, serviceGroup, ip, port, e);
        }
    }
    
    /**
     * 构建Nacos实例
     */
    private Instance buildInstance(McpServerInfo serverInfo) {
        Instance instance = new Instance();
        instance.setIp(serverInfo.getIp());
        instance.setPort(serverInfo.getPort());
        instance.setWeight(serverInfo.getWeight() > 0 ? serverInfo.getWeight() : 1.0);
        instance.setEnabled(true);
        instance.setHealthy(serverInfo.isHealthy());
        instance.setEphemeral(serverInfo.isEphemeral());
        
        // 设置元数据
        Map<String, String> metadata = new HashMap<>();
        if (serverInfo.getMetadata() != null) {
            metadata.putAll(serverInfo.getMetadata());
        }
        metadata.put("version", serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0");
        metadata.put("sseEndpoint", serverInfo.getSseEndpoint() != null ? serverInfo.getSseEndpoint() : "/sse");
        
        // 只有当registrationTime不为null时才添加
        if (serverInfo.getRegistrationTime() != null) {
            metadata.put("registrationTime", serverInfo.getRegistrationTime().toString());
        }
        instance.setMetadata(metadata);
        
        return instance;
    }
    
    /**
     * 从Nacos实例构建服务器信息
     */
    private McpServerInfo buildServerInfo(Instance instance, String serviceName) {
        Map<String, String> metadata = instance.getMetadata();
        
        return McpServerInfo.builder()
                .name(serviceName)  // 设置服务名称
                .ip(instance.getIp())
                .port(instance.getPort())
                .weight(instance.getWeight())
                .healthy(instance.isHealthy())
                .ephemeral(instance.isEphemeral())
                .version(metadata.get("version"))
                .sseEndpoint(metadata.get("sseEndpoint"))
                .metadata(metadata)
                .build();
    }
    
    /**
     * 发布所有配置信息
     */
    private Mono<Void> publishAllConfigs(McpServerInfo serverInfo) {
        return Mono.when(
            mcpConfigService.publishServerConfig(serverInfo)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Successfully published server config for: {}", serverInfo.getName());
                    } else {
                        log.warn("Failed to publish server config for: {}", serverInfo.getName());
                    }
                })
                .doOnError(error -> log.error("Error publishing server config for: {}", serverInfo.getName(), error))
                .onErrorReturn(false),
            
            mcpConfigService.publishToolsConfig(serverInfo)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Successfully published tools config for: {}", serverInfo.getName());
                    } else {
                        log.warn("Failed to publish tools config for: {}", serverInfo.getName());
                    }
                })
                .doOnError(error -> log.error("Error publishing tools config for: {}", serverInfo.getName(), error))
                .onErrorReturn(false),
            
            mcpConfigService.publishVersionConfig(serverInfo)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Successfully published version config for: {}", serverInfo.getName());
                    } else {
                        log.warn("Failed to publish version config for: {}", serverInfo.getName());
                    }
                })
                .doOnError(error -> log.error("Error publishing version config for: {}", serverInfo.getName(), error))
                .onErrorReturn(false)
        ).then();
    }
} 