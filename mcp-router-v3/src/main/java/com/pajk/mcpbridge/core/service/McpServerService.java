package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.core.model.McpServerConfig;
import com.pajk.mcpbridge.core.model.McpToolsConfig;
import com.pajk.mcpbridge.core.registry.McpServerRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.pajk.mcpbridge.core.model.McpMessage;

/**
 * MCP服务业务Service，封装注册、注销、发现、配置等业务逻辑
 */
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final static Logger log = LoggerFactory.getLogger(McpServerService.class);

    private final McpServerRegistry mcpServerRegistry;
    private final McpConfigService mcpConfigService;

    /** 注册服务 */
    public Mono<String> registerServer(McpServerInfo serverInfo) {
        return mcpServerRegistry.registerServer(serverInfo)
                .then(Mono.just("Server registered successfully"));
    }

    /** 注销服务 */
    public Mono<String> deregisterServer(String serviceName, String serviceGroup) {
        return mcpServerRegistry.deregisterServer(serviceName, serviceGroup)
                .then(Mono.just("Server deregistered successfully"));
    }

    /** 获取健康实例 */
    public Flux<McpServerInfo> getHealthyServers(String serviceName, String serviceGroup) {
        return mcpServerRegistry.getAllHealthyServers(serviceName, serviceGroup);
    }

    /** 获取所有实例 */
    public Flux<McpServerInfo> getAllInstances(String serviceName, String serviceGroup) {
        return mcpServerRegistry.getAllInstances(serviceName, serviceGroup);
    }

    /** 选择一个健康实例 */
    public Mono<McpServerInfo> selectHealthyServer(String serviceName, String serviceGroup) {
        return mcpServerRegistry.selectHealthyServer(serviceName, serviceGroup);
    }

    /** 获取服务器配置 */
    public Mono<Object> getServerConfig(String id, String version) {
        return mcpConfigService.getServerConfig(id, version)
                .flatMap(config -> {
                    if (config == null) {
                        return Mono.just(
                            McpMessage.builder()
                                .error(McpMessage.McpError.builder()
                                    .code(10002)
                                    .message("未找到目标服务配置，请检查服务是否注册")
                                    .build())
                                .timestamp(System.currentTimeMillis())
                                .build()
                        );
                    }
                    return Mono.just(config);
                });
    }

    /** 发布服务器配置 */
    public Mono<String> publishServerConfig(McpServerInfo serverInfo) {
        return mcpConfigService.publishServerConfig(serverInfo)
                .map(success -> success ? "Server config published successfully" : "Failed to publish server config");
    }

    /** 发布工具配置 */
    public Mono<String> publishToolsConfig(McpServerInfo serverInfo) {
        return mcpConfigService.publishToolsConfig(serverInfo)
                .map(success -> success ? "Tools config published successfully" : "Failed to publish tools config");
    }

    /** 发布版本配置 */
    public Mono<String> publishVersionConfig(McpServerInfo serverInfo) {
        return mcpConfigService.publishVersionConfig(serverInfo)
                .map(success -> success ? "Version config published successfully" : "Failed to publish version config");
    }

    /**
     * 获取完整服务配置（含工具能力）
     */
    public Mono<Object> getFullServerConfig(String id, String version) {
        return mcpConfigService.getServerConfig(id, version)
            .flatMap(serverConfig -> {
                if (serverConfig == null) {
                    return Mono.just(
                        McpMessage.builder()
                            .error(McpMessage.McpError.builder()
                                .code(10002)
                                .message("未找到目标服务配置，请检查服务是否注册")
                                .build())
                            .timestamp(System.currentTimeMillis())
                            .build()
                    );
                }
                String toolsRef = serverConfig.getToolsDescriptionRef();
                if (toolsRef == null || toolsRef.isBlank()) {
                    // 若无 toolsDescriptionRef，直接返回
                    return Mono.just(new FullServerConfig(serverConfig, null));
                }
                // 解析 toolsRef 获取 version
                String[] parts = toolsRef.replace("-mcp-tools.json", "").split("-");
                String refVersion = parts.length > 1 ? parts[parts.length - 1] : version;
                return mcpConfigService.getToolsConfig(id, refVersion)
                    .map(toolsConfig -> new FullServerConfig(serverConfig, toolsConfig));
            });
    }
    /**
     * 获取服务所有版本号
     */
    public Mono<Object> getServerVersions(String id) {
        return mcpConfigService.getVersionConfig(id)
            .map(versionConfig -> versionConfig != null ? versionConfig.getVersions() : null)
            .flatMap(versions -> {
                if (versions == null) {
                    return Mono.just(
                        McpMessage.builder()
                            .error(McpMessage.McpError.builder()
                                .code(10002)
                                .message("未找到目标服务版本信息，请检查服务是否注册")
                                .build())
                            .timestamp(System.currentTimeMillis())
                            .build()
                    );
                }
                return Mono.just(versions);
            });
    }
    /**
     * 完整服务能力结构体
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class FullServerConfig {
        private McpServerConfig serverConfig;
        private McpToolsConfig toolsConfig;
    }
} 