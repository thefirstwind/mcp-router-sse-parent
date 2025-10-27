package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.config.NacosMcpRegistryConfig;
import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.core.registry.McpServerRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能MCP路由服务
 * 实现基于工具名称的自动服务发现、权重选择和前置校验
 */
@Service
@RequiredArgsConstructor
public class SmartMcpRouterService {

    private final static Logger log = LoggerFactory.getLogger(SmartMcpRouterService.class);

    private final McpServerRegistry serverRegistry;
    private final McpClientManager mcpClientManager;
    private final LoadBalancer loadBalancer;
    private final NacosMcpRegistryConfig.McpRegistryProperties registryProperties;

    /**
     * 智能工具调用 - 只需要工具名称和参数
     * 
     * @param toolName 工具名称
     * @param arguments 调用参数
     * @return 调用结果
     */
    public Mono<Object> callTool(String toolName, Map<String, Object> arguments) {
        log.info("🎯 Smart tool call: {} with arguments: {}", toolName, arguments);
        
        return findServersWithTool(toolName)
                .collectList()
                .flatMap(servers -> {
                    if (servers.isEmpty()) {
                        log.warn("❌ No servers found supporting tool: {}", toolName);
                        return Mono.error(new RuntimeException("Tool not found: " + toolName));
                    }
                    
                    // 选择最优服务器
                    McpServerInfo selectedServer = selectOptimalServer(servers, toolName);
                    
                    log.info("✅ Selected server '{}' for tool '{}' (weight: {})", 
                            selectedServer.getName(), toolName, selectedServer.getWeight());
                    
                    // 执行工具调用
                    return mcpClientManager.callTool(selectedServer, toolName, arguments);
                })
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(result -> log.info("✅ Tool '{}' executed successfully", toolName))
                .doOnError(error -> log.error("❌ Tool '{}' execution failed: {}", toolName, error.getMessage()));
    }

    /**
     * 高级工具调用 - 支持指定服务器
     * 
     * @param serverName 服务器名称（可选，为null时自动发现）
     * @param toolName 工具名称
     * @param arguments 调用参数
     * @return 调用结果
     */
    public Mono<Object> callTool(String serverName, String toolName, Map<String, Object> arguments) {
        if (serverName == null || serverName.trim().isEmpty()) {
            return callTool(toolName, arguments);
        }

        log.info("🎯 Directed tool call: {} on server {} with arguments: {}", toolName, serverName, arguments);
        
        return findServerByName(serverName)
                .flatMap(server -> {
                    // 验证服务器是否支持该工具
                    return validateToolSupport(server, toolName)
                            .flatMap(supported -> {
                                if (!supported) {
                                    return Mono.error(new RuntimeException(
                                            String.format("Server '%s' does not support tool '%s'", serverName, toolName)));
                                }
                                
                                return mcpClientManager.callTool(server, toolName, arguments);
                            });
                })
                .timeout(Duration.ofSeconds(30))
                .doOnSuccess(result -> log.info("✅ Directed tool '{}' executed successfully on '{}'", toolName, serverName))
                .doOnError(error -> log.error("❌ Directed tool '{}' execution failed on '{}': {}", toolName, serverName, error.getMessage()));
    }

    /**
     * 发现支持指定工具的所有服务器
     */
    private Flux<McpServerInfo> findServersWithTool(String toolName) {
        return serverRegistry.getAllHealthyServers("*", registryProperties.getServiceGroups())
                .filterWhen(server -> validateToolSupport(server, toolName))
                .doOnNext(server -> log.debug("🔍 Found server '{}' supporting tool '{}'", server.getName(), toolName));
    }

    /**
     * 根据名称查找服务器
     */
    private Mono<McpServerInfo> findServerByName(String serverName) {
        return serverRegistry.getAllHealthyServers(serverName, registryProperties.getServiceGroups())
                .next()
                .switchIfEmpty(Mono.error(new RuntimeException("Server not found: " + serverName)));
    }

    /**
     * 验证服务器是否支持指定工具
     */
    private Mono<Boolean> validateToolSupport(McpServerInfo server, String toolName) {
        return mcpClientManager.hasTool(server, toolName)
                .onErrorReturn(false)
                .doOnNext(supported -> {
                    if (supported) {
                        log.debug("✅ Server '{}' supports tool '{}'", server.getName(), toolName);
                    } else {
                        log.debug("❌ Server '{}' does not support tool '{}'", server.getName(), toolName);
                    }
                });
    }

    /**
     * 选择最优服务器 - 基于权重和健康状态
     */
    private McpServerInfo selectOptimalServer(List<McpServerInfo> servers, String toolName) {
        if (servers.size() == 1) {
            return servers.get(0);
        }

        // 转换为 Nacos Instance 格式进行负载均衡
        List<com.alibaba.nacos.api.naming.pojo.Instance> instances = servers.stream()
                .map(this::convertToNacosInstance)
                .collect(Collectors.toList());

        // 使用加权轮询算法
        com.alibaba.nacos.api.naming.pojo.Instance selected = loadBalancer.selectServer(
                instances, LoadBalancer.Strategy.WEIGHTED_ROUND_ROBIN);

        if (selected == null) {
            log.warn("⚠️ Load balancer returned null, falling back to first server");
            return servers.get(0);
        }

        // 根据选中的实例找回原始服务器信息
        String selectedKey = selected.getIp() + ":" + selected.getPort();
        return servers.stream()
                .filter(server -> (server.getIp() + ":" + server.getPort()).equals(selectedKey))
                .findFirst()
                .orElse(servers.get(0));
    }

    /**
     * 转换McpServerInfo为Nacos Instance
     */
    private com.alibaba.nacos.api.naming.pojo.Instance convertToNacosInstance(McpServerInfo serverInfo) {
        com.alibaba.nacos.api.naming.pojo.Instance instance = new com.alibaba.nacos.api.naming.pojo.Instance();
        instance.setIp(serverInfo.getIp());
        instance.setPort(serverInfo.getPort());
        instance.setWeight(serverInfo.getWeight() > 0 ? serverInfo.getWeight() : 1.0);
        instance.setHealthy(true);
        instance.setEnabled(true);
        return instance;
    }

    /**
     * 获取工具的可用服务器列表
     */
    public Mono<List<String>> getServersForTool(String toolName) {
        return findServersWithTool(toolName)
                .map(McpServerInfo::getName)
                .collectList()
                .doOnSuccess(servers -> log.info("🔍 Found {} servers supporting tool '{}': {}", 
                        servers.size(), toolName, servers));
    }

    /**
     * 检查工具是否可用
     */
    public Mono<Boolean> isToolAvailable(String toolName) {
        return findServersWithTool(toolName)
                .hasElements()
                .doOnSuccess(available -> log.debug("🔍 Tool '{}' availability: {}", toolName, available));
    }

    /**
     * 获取所有可用工具
     */
    public Mono<List<String>> listAvailableTools() {
        return serverRegistry.getAllHealthyServers("*", registryProperties.getServiceGroups())
                .flatMap(server -> mcpClientManager.listTools(server)
                        .map(toolsResult -> toolsResult.tools().stream()
                                .map(tool -> tool.name())
                                .collect(Collectors.toList()))
                        .onErrorReturn(List.of()))
                .flatMapIterable(tools -> tools)
                .distinct()
                .collectList()
                .doOnSuccess(tools -> log.info("🔍 Found {} unique available tools: {}", tools.size(), tools));
    }
} 