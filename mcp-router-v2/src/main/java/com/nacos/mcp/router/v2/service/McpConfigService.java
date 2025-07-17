package com.nacos.mcp.router.v2.service;

import com.alibaba.nacos.api.config.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.v2.config.McpNacosConstants;
import com.nacos.mcp.router.v2.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * MCP配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConfigService {
    
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    
    /**
     * 发布服务器配置
     */
    public Mono<Boolean> publishServerConfig(McpServerInfo serverInfo) {
        return Mono.fromCallable(() -> {
            try {
                // 构建服务器配置
                McpServerConfig serverConfig = buildServerConfig(serverInfo);
                
                // 序列化为JSON
                String configContent = objectMapper.writeValueAsString(serverConfig);
                
                // 构建dataId，包含版本号
                String version = serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0";
                String dataId = serverInfo.getName() + "-" + version + McpNacosConstants.SERVER_CONFIG_SUFFIX;
                
                // 发布配置，使用带type的方法
                boolean success = configService.publishConfig(
                    dataId,
                    McpNacosConstants.SERVER_GROUP,
                    configContent,
                    "json"
                );
                
                if (success) {
                    log.info("Successfully published server config for: {}", serverInfo.getName());
                } else {
                    log.warn("Failed to publish server config for: {}", serverInfo.getName());
                }
                
                return success;
            } catch (Exception e) {
                log.error("Error publishing server config for: {}", serverInfo.getName(), e);
                throw new RuntimeException("Failed to publish server config", e);
            }
        });
    }
    
    /**
     * 发布工具配置
     */
    public Mono<Boolean> publishToolsConfig(McpServerInfo serverInfo) {
        return Mono.fromCallable(() -> {
            try {
                // 构建工具配置
                McpToolsConfig toolsConfig = buildToolsConfig(serverInfo);
                
                // 序列化为JSON
                String configContent = objectMapper.writeValueAsString(toolsConfig);
                
                // 构建dataId
                String dataId = serverInfo.getName() + "-" + serverInfo.getVersion() + McpNacosConstants.TOOLS_CONFIG_SUFFIX;
                
                // 发布配置，使用带type的方法
                boolean success = configService.publishConfig(
                    dataId,
                    McpNacosConstants.TOOLS_GROUP,
                    configContent,
                    "json"
                );
                
                if (success) {
                    log.info("Successfully published tools config for: {}", serverInfo.getName());
                } else {
                    log.warn("Failed to publish tools config for: {}", serverInfo.getName());
                }
                
                return success;
            } catch (Exception e) {
                log.error("Error publishing tools config for: {}", serverInfo.getName(), e);
                throw new RuntimeException("Failed to publish tools config", e);
            }
        });
    }
    
    /**
     * 发布版本配置
     */
    public Mono<Boolean> publishVersionConfig(McpServerInfo serverInfo) {
        return Mono.fromCallable(() -> {
            try {
                // 构建版本配置
                McpVersionConfig versionConfig = buildVersionConfig(serverInfo);
                
                // 序列化为JSON
                String configContent = objectMapper.writeValueAsString(versionConfig);
                
                // 构建dataId
                String dataId = serverInfo.getName() + McpNacosConstants.VERSIONS_CONFIG_SUFFIX;
                
                // 发布配置，使用带type的方法
                boolean success = configService.publishConfig(
                    dataId,
                    McpNacosConstants.VERSIONS_GROUP,
                    configContent,
                    "json"
                );
                
                if (success) {
                    log.info("Successfully published version config for: {}", serverInfo.getName());
                } else {
                    log.warn("Failed to publish version config for: {}", serverInfo.getName());
                }
                
                return success;
            } catch (Exception e) {
                log.error("Error publishing version config for: {}", serverInfo.getName(), e);
                throw new RuntimeException("Failed to publish version config", e);
            }
        });
    }
    
    /**
     * 获取服务器配置
     */
    public Mono<McpServerConfig> getServerConfig(String serverName) {
        return getServerConfig(serverName, "1.0.0");
    }
    
    /**
     * 获取服务器配置（指定版本）
     */
    public Mono<McpServerConfig> getServerConfig(String serverName, String version) {
        return Mono.fromCallable(() -> {
            try {
                String dataId = serverName + "-" + version + McpNacosConstants.SERVER_CONFIG_SUFFIX;
                String configContent = configService.getConfig(
                    dataId,
                    McpNacosConstants.SERVER_GROUP,
                    McpNacosConstants.DEFAULT_TIMEOUT
                );
                
                if (configContent != null) {
                    return objectMapper.readValue(configContent, McpServerConfig.class);
                }
                return null;
            } catch (Exception e) {
                log.error("Error getting server config for: {} version: {}", serverName, version, e);
                throw new RuntimeException("Failed to get server config", e);
            }
        });
    }
    
    /**
     * 构建服务器配置
     */
    private McpServerConfig buildServerConfig(McpServerInfo serverInfo) {
        return McpServerConfig.builder()
                .id(UUID.randomUUID().toString())
                .name(serverInfo.getName())
                .version(serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0")
                .protocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .frontProtocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .description(serverInfo.getMetadata() != null ? 
                    serverInfo.getMetadata().getOrDefault("description", serverInfo.getName()) : 
                    serverInfo.getName())
                .enabled(true)
                .capabilities(Arrays.asList(McpNacosConstants.DEFAULT_CAPABILITIES))
                .latestPublishedVersion(serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0")
                .ip(serverInfo.getIp())
                .port(serverInfo.getPort())
                .sseEndpoint(serverInfo.getSseEndpoint() != null ? 
                    serverInfo.getSseEndpoint() : McpNacosConstants.DEFAULT_SSE_ENDPOINT)
                .status(serverInfo.getStatus() != null ? serverInfo.getStatus() : "UP")
                .serviceGroup(serverInfo.getServiceGroup())
                .registrationTime(serverInfo.getRegistrationTime())
                .lastHeartbeat(serverInfo.getLastHeartbeat())
                .metadata(serverInfo.getMetadata())
                .ephemeral(serverInfo.isEphemeral())
                .healthy(serverInfo.isHealthy())
                .weight(serverInfo.getWeight())
                .build();
    }
    
    /**
     * 构建工具配置
     */
    private McpToolsConfig buildToolsConfig(McpServerInfo serverInfo) {
        // 构建示例工具配置
        List<McpToolsConfig.McpTool> tools = new ArrayList<>();
        
        // 添加一个示例工具
        McpToolsConfig.McpTool.InputSchema.Property idProperty = 
            McpToolsConfig.McpTool.InputSchema.Property.builder()
                .type("integer")
                .format("int64")
                .description("Person's ID")
                .build();
        
        McpToolsConfig.McpTool.InputSchema inputSchema = 
            McpToolsConfig.McpTool.InputSchema.builder()
                .type("object")
                .properties(java.util.Map.of("id", idProperty))
                .required(List.of("id"))
                .additionalProperties(false)
                .build();
        
        McpToolsConfig.McpTool exampleTool = McpToolsConfig.McpTool.builder()
                .name("exampleTool")
                .description("Example tool for " + serverInfo.getName())
                .inputSchema(inputSchema)
                .build();
        
        tools.add(exampleTool);
        
        return McpToolsConfig.builder()
                .tools(tools)
                .toolsMeta(java.util.Map.of(
                    "serverName", serverInfo.getName(),
                    "version", serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0",
                    "createdTime", LocalDateTime.now().toString()
                ))
                .build();
    }
    
    /**
     * 构建版本配置
     */
    private McpVersionConfig buildVersionConfig(McpServerInfo serverInfo) {
        // 构建版本详情
        McpVersionConfig.VersionDetail versionDetail = McpVersionConfig.VersionDetail.builder()
                .version(serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0")
                .publishTime(LocalDateTime.now())
                .description("Version " + (serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0"))
                .status("ACTIVE")
                .metadata(java.util.Map.of("serverName", serverInfo.getName()))
                .build();
        
        return McpVersionConfig.builder()
                .id(UUID.randomUUID().toString())
                .name(serverInfo.getName())
                .protocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .frontProtocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .description(serverInfo.getMetadata() != null ? 
                    serverInfo.getMetadata().getOrDefault("description", serverInfo.getName()) : 
                    serverInfo.getName())
                .enabled(true)
                .capabilities(Arrays.asList(McpNacosConstants.DEFAULT_CAPABILITIES))
                .latestPublishedVersion(serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0")
                .versionHistory(List.of(versionDetail))
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
    }
} 