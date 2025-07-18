package com.nacos.mcp.router.v3.registry;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.nacos.mcp.router.v3.model.McpServerInfo;
import com.nacos.mcp.router.v3.service.McpConfigService;
import com.nacos.mcp.router.v3.config.NacosMcpRegistryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
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
    private final NacosMcpRegistryConfig.McpRegistryProperties registryProperties;
    
    // 本地缓存已注册的服务
    private final Map<String, McpServerInfo> registeredServers = new ConcurrentHashMap<>();
    
    // 本地缓存健康实例列表，key: serviceName@groupName
    public final Map<String, List<McpServerInfo>> healthyInstanceCache = new ConcurrentHashMap<>();
    public final Map<String, Long> healthyCacheTimestamp = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000; // 30秒缓存

    // 添加订阅管理
    private final Map<String, Boolean> serviceSubscriptions = new ConcurrentHashMap<>();
    // 添加重试机制配置
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    /**
     * 注册MCP服务器（原子操作，带重试机制）
     */
    public Mono<Void> registerServer(McpServerInfo serverInfo) {
        return registerServerWithRetry(serverInfo, 1)
                .doOnSuccess(unused -> log.info("✅ Successfully registered MCP server: {} ({}:{})", 
                        serverInfo.getName(), serverInfo.getIp(), serverInfo.getPort()))
                .doOnError(error -> log.error("❌ Failed to register MCP server: {} after {} attempts", 
                        serverInfo.getName(), MAX_RETRY_ATTEMPTS, error));
    }

    /**
     * 带重试机制的注册实现
     */
    private Mono<Void> registerServerWithRetry(McpServerInfo serverInfo, int attempt) {
        return performAtomicRegistration(serverInfo)
                .onErrorResume(error -> {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        log.warn("⚠️ Registration attempt {} failed for server: {}, retrying in {}s...", 
                                attempt, serverInfo.getName(), RETRY_DELAY.getSeconds());
                        return Mono.delay(RETRY_DELAY)
                                .then(registerServerWithRetry(serverInfo, attempt + 1));
                    }
                    return Mono.error(error);
                });
    }

    /**
     * 原子化注册实现
     */
    private Mono<Void> performAtomicRegistration(McpServerInfo serverInfo) {
        // 1. 先准备所有配置内容
                 return Mono.fromCallable(() -> {
             // 预先生成配置内容和MD5，确保一致性
             try {
                 // 使用 McpConfigService 的公共方法
                 String configJson = "{}"; // 临时简化
                 String md5 = mcpConfigService.md5(configJson);
                 
                 return new RegistrationData(null, configJson, md5);
             } catch (Exception e) {
                 throw new RuntimeException("Failed to prepare registration data", e);
             }
         })
        // 2. 原子化发布所有配置（使用事务思想）
        .flatMap(data -> publishAllConfigsAtomically(serverInfo, data))
        // 3. 注册实例（带配置MD5）
        .flatMap(data -> registerInstanceWithConfig(serverInfo, data))
        // 4. 更新本地状态
        .doOnSuccess(data -> updateLocalState(serverInfo))
        // 5. 自动订阅服务变更
        .doOnSuccess(data -> subscribeServiceChangeIfNeeded(serverInfo.getName(), serverInfo.getServiceGroup()))
        .then();
    }

    /**
     * 原子化发布所有配置
     */
    private Mono<RegistrationData> publishAllConfigsAtomically(McpServerInfo serverInfo, RegistrationData data) {
        return Mono.zip(
                mcpConfigService.publishServerConfig(serverInfo),
                mcpConfigService.publishToolsConfig(serverInfo),
                mcpConfigService.publishVersionConfig(serverInfo)
        )
        .flatMap(tuple -> {
            Boolean serverConfigSuccess = tuple.getT1();
            Boolean toolsConfigSuccess = tuple.getT2();
            Boolean versionConfigSuccess = tuple.getT3();
            
            if (!serverConfigSuccess || !toolsConfigSuccess || !versionConfigSuccess) {
                // 如果任何配置发布失败，尝试清理已发布的配置
                return cleanupPartialConfigs(serverInfo)
                        .then(Mono.error(new RuntimeException("Failed to publish all configs atomically")));
            }
            
            log.info("✅ All configs published successfully for server: {}", serverInfo.getName());
            return Mono.just(data);
        });
    }

    /**
     * 注册实例（带配置信息）
     */
         private Mono<RegistrationData> registerInstanceWithConfig(McpServerInfo serverInfo, RegistrationData data) {
         return Mono.<RegistrationData>fromCallable(() -> {
             try {
                 Instance instance = buildInstance(serverInfo);
                 // 添加配置MD5到元数据
                 instance.getMetadata().put("server.md5", data.md5);
                 // 添加工具名称到元数据
                 if (serverInfo.getMetadata() != null && serverInfo.getMetadata().get("tools.names") != null) {
                     instance.getMetadata().put("tools.names", serverInfo.getMetadata().get("tools.names"));
                 }
                 
                 namingService.registerInstance(serverInfo.getName(), serverInfo.getServiceGroup(), instance);
                 log.info("✅ Instance registered with MD5: {} for server: {}", data.md5, serverInfo.getName());
                 return data;
             } catch (Exception e) {
                 throw new RuntimeException("Failed to register instance", e);
             }
         });
    }

    /**
     * 清理部分发布的配置
     */
    private Mono<Void> cleanupPartialConfigs(McpServerInfo serverInfo) {
        return Mono.fromRunnable(() -> {
            try {
                // 尝试删除可能已发布的配置，忽略错误
                mcpConfigService.deleteServerConfig(serverInfo.getName(), serverInfo.getVersion())
                        .subscribe(null, error -> log.debug("Cleanup server config failed (ignored): {}", error.getMessage()));
                mcpConfigService.deleteToolsConfig(serverInfo.getName(), serverInfo.getVersion())
                        .subscribe(null, error -> log.debug("Cleanup tools config failed (ignored): {}", error.getMessage()));
                mcpConfigService.deleteVersionConfig(serverInfo.getName())
                        .subscribe(null, error -> log.debug("Cleanup version config failed (ignored): {}", error.getMessage()));
                
                log.info("🧹 Cleanup partial configs for server: {}", serverInfo.getName());
            } catch (Exception e) {
                log.warn("Failed to cleanup partial configs for server: {}", serverInfo.getName(), e);
            }
        });
    }

    /**
     * 更新本地状态
     */
    private void updateLocalState(McpServerInfo serverInfo) {
        serverInfo.setRegistrationTime(LocalDateTime.now());
        serverInfo.setLastHeartbeat(LocalDateTime.now());
        registeredServers.put(serverInfo.getName(), serverInfo);
    }

    /**
     * 智能订阅管理 - 避免重复订阅
     */
    private void subscribeServiceChangeIfNeeded(String serviceName, String serviceGroup) {
        String subscriptionKey = serviceName + "@" + serviceGroup;
        if (serviceSubscriptions.putIfAbsent(subscriptionKey, true) == null) {
            // 第一次订阅
            subscribeServiceChange(serviceName, serviceGroup);
            log.info("🔔 New subscription created for: {}", subscriptionKey);
        } else {
            log.debug("📋 Subscription already exists for: {}", subscriptionKey);
        }
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
        // 支持通配符查询，获取所有MCP服务
        if ("*".equals(serviceName)) {
            return getAllMcpServices(serviceGroup);
        }
        
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
                subscribeServiceChangeIfNeeded(serviceName, serviceGroup);
                return healthyList;
            } catch (Exception e) {
                log.error("Failed to get healthy servers for service: {}", serviceName, e);
                throw new RuntimeException("Failed to get healthy servers", e);
            }
        }).flatMapMany(Flux::fromIterable);
    }
    
    /**
     * 获取所有MCP服务（支持查询多个服务组）
     */
    private Flux<McpServerInfo> getAllMcpServices(String serviceGroup) {
        // 如果指定了特定的服务组，只查询该组
        if (!"*".equals(serviceGroup)) {
            return getAllMcpServicesFromGroup(serviceGroup);
        }
        
        // 通配符查询：遍历配置的所有服务组
        List<String> serviceGroups = registryProperties.getServiceGroups();
        if (serviceGroups == null || serviceGroups.isEmpty()) {
            log.warn("⚠️ No service groups configured, falling back to default group");
            return getAllMcpServicesFromGroup("mcp-server");
        }
        
        log.debug("🔍 Searching MCP services across {} groups: {}", serviceGroups.size(), serviceGroups);
        
        return Flux.fromIterable(serviceGroups)
                .flatMap(this::getAllMcpServicesFromGroup)
                .distinct(server -> server.getIp() + ":" + server.getPort()) // 去重，避免同一实例在多个组中重复
                .doOnComplete(() -> log.debug("✅ Completed searching across all configured service groups"));
    }
    
    /**
     * 从指定服务组获取所有MCP服务
     */
    private Flux<McpServerInfo> getAllMcpServicesFromGroup(String serviceGroup) {
        return Mono.fromCallable(() -> {
            try {
                log.debug("🔍 Searching for MCP services in group: {}", serviceGroup);
                
                // 获取指定group下的所有服务
                com.alibaba.nacos.api.naming.pojo.ListView<String> servicesList = 
                    namingService.getServicesOfServer(1, Integer.MAX_VALUE, serviceGroup);
                List<McpServerInfo> allServers = new ArrayList<>();
                
                if (servicesList == null || servicesList.getData() == null || servicesList.getData().isEmpty()) {
                    log.debug("📭 No services found in group: {}", serviceGroup);
                    return allServers;
                }
                
                log.debug("📋 Found {} services in group {}: {}", 
                    servicesList.getData().size(), serviceGroup, servicesList.getData());
                
                for (String service : servicesList.getData()) {
                    try {
                        List<Instance> instances = namingService.selectInstances(service, serviceGroup, true);
                        List<McpServerInfo> serviceServers = instances.stream()
                                .map(instance -> buildServerInfo(instance, service))
                                .toList();
                        allServers.addAll(serviceServers);
                        
                        if (!serviceServers.isEmpty()) {
                            log.debug("✅ Found {} healthy instances for service {} in group {}", 
                                serviceServers.size(), service, serviceGroup);
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to get instances for service: {} in group: {}", service, serviceGroup, e);
                    }
                }
                
                log.debug("📊 Total {} MCP servers found in group: {}", allServers.size(), serviceGroup);
                return allServers;
            } catch (Exception e) {
                log.error("❌ Failed to get all MCP services in group: {}", serviceGroup, e);
                throw new RuntimeException("Failed to get all MCP services", e);
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

    /**
     * 注册数据内部类
     */
    private static class RegistrationData {
        final Object serverConfig;
        final String configJson;
        final String md5;
        
        RegistrationData(Object serverConfig, String configJson, String md5) {
            this.serverConfig = serverConfig;
            this.configJson = configJson;
            this.md5 = md5;
        }
    }
} 