package com.nacos.mcp.router.v2.registry;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
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
    
    /**
     * 注册MCP服务器
     */
    public Mono<Void> registerServer(McpServerInfo serverInfo) {
        return Mono.fromCallable(() -> {
            try {
                // 为必需字段提供默认值
                if (serverInfo.getServiceGroup() == null || serverInfo.getServiceGroup().isEmpty()) {
                    serverInfo.setServiceGroup("mcp-server");
                }
                
                // 为weight提供默认值
                if (serverInfo.getWeight() <= 0) {
                    serverInfo.setWeight(1.0);
                }
                
                // 为healthy提供默认值
                if (serverInfo.getStatus() == null) {
                    serverInfo.setStatus("UP");
                }
                
                // 为sseEndpoint提供默认值
                if (serverInfo.getSseEndpoint() == null) {
                    serverInfo.setSseEndpoint("/sse");
                }
                
                Instance instance = buildInstance(serverInfo);
                namingService.registerInstance(serverInfo.getName(), serverInfo.getServiceGroup(), instance);
                
                // 更新本地缓存
                serverInfo.setRegistrationTime(LocalDateTime.now());
                serverInfo.setLastHeartbeat(LocalDateTime.now());
                registeredServers.put(serverInfo.getName(), serverInfo);
                
                log.info("Successfully registered MCP server: {} at {}:{}", 
                        serverInfo.getName(), serverInfo.getIp(), serverInfo.getPort());
                return null;
            } catch (Exception e) {
                log.error("Failed to register MCP server: {}", serverInfo.getName(), e);
                throw new RuntimeException("Failed to register MCP server", e);
            }
        }).then(
            // 异步发布配置信息
            publishAllConfigs(serverInfo)
        );
    }
    
    /**
     * 注销MCP服务器
     */
    public Mono<Void> deregisterServer(String serviceName, String serviceGroup) {
        return Mono.fromCallable(() -> {
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
        });
    }
    
    /**
     * 获取所有健康的MCP服务器实例
     */
    public Flux<McpServerInfo> getAllHealthyServers(String serviceName, String serviceGroup) {
        return Mono.fromCallable(() -> {
            try {
                List<Instance> instances = namingService.selectInstances(serviceName, serviceGroup, true);
                return instances.stream()
                        .map(instance -> buildServerInfo(instance, serviceName))
                        .toList();
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