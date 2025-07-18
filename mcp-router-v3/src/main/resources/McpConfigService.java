package com.nacos.mcp.router.v3.service;

import com.alibaba.nacos.api.config.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.v3.config.McpNacosConstants;
import com.nacos.mcp.router.v3.model.McpServerConfig;
import com.nacos.mcp.router.v3.model.McpServerInfo;
import com.nacos.mcp.router.v3.model.McpToolsConfig;
import com.nacos.mcp.router.v3.model.McpVersionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * MCP配置服务
 */
@Slf4j
// @Service
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
                
                // 统一dataId生成规则
                String id = serverInfo.getId() != null ? serverInfo.getId() : UUID.randomUUID().toString();
                String version = serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0";
                String dataId = id + "-" + version + McpNacosConstants.SERVER_CONFIG_SUFFIX;
                
                // 发布配置，使用带type的方法
                log.info("Publishing server config with dataId: {}, group: {}, type: json", dataId, McpNacosConstants.SERVER_GROUP);
                boolean success = configService.publishConfig(
                    dataId,
                    McpNacosConstants.SERVER_GROUP,
                    configContent,
                    "json"
                );
                
                if (success) {
                    log.info("Successfully published server config for: {} with dataId: {}", serverInfo.getName(), dataId);
                } else {
                    log.warn("Failed to publish server config for: {} with dataId: {}", serverInfo.getName(), dataId);
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
                
                // 统一dataId生成规则
                String id = serverInfo.getId() != null ? serverInfo.getId() : UUID.randomUUID().toString();
                String version = serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0";
                String dataId = id + "-" + version + McpNacosConstants.TOOLS_CONFIG_SUFFIX;
                
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
                
                // 统一dataId生成规则
                String id = serverInfo.getId() != null ? serverInfo.getId() : UUID.randomUUID().toString();
                String dataId = id + McpNacosConstants.VERSIONS_CONFIG_SUFFIX;
                
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
    public Mono<McpServerConfig> getServerConfig(String id, String version) {
        String realId = id != null ? id : UUID.randomUUID().toString();
        String realVersion = version != null ? version : "1.0.0";
        String dataId = realId + "-" + realVersion + McpNacosConstants.SERVER_CONFIG_SUFFIX;
        return Mono.fromCallable(() -> {
            try {
                String config = configService.getConfig(dataId, McpNacosConstants.SERVER_GROUP, 3000);
                if (config == null) return null;
                return objectMapper.readValue(config, McpServerConfig.class);
            } catch (Exception e) {
                log.error("Error getting server config for: {}", dataId, e);
                throw new RuntimeException("Failed to get server config", e);
            }
        });
    }
    
    /**
     * 删除服务器配置（支持指定版本）
     */
    public Mono<Boolean> deleteServerConfig(String id, String version) {
        String realId = id != null ? id : UUID.randomUUID().toString();
        String realVersion = version != null ? version : "1.0.0";
        String dataId = realId + "-" + realVersion + McpNacosConstants.SERVER_CONFIG_SUFFIX;
        return Mono.fromCallable(() -> {
            try {
                return configService.removeConfig(dataId, McpNacosConstants.SERVER_GROUP);
            } catch (Exception e) {
                log.error("Error deleting server config for: {}", dataId, e);
                throw new RuntimeException("Failed to delete server config", e);
            }
        });
    }

    /**
     * 获取工具配置（指定版本）
     */
    public Mono<McpToolsConfig> getToolsConfig(String id, String version) {
        String realId = id != null ? id : UUID.randomUUID().toString();
        String realVersion = version != null ? version : "1.0.0";
        String dataId = realId + "-" + realVersion + McpNacosConstants.TOOLS_CONFIG_SUFFIX;
        return Mono.fromCallable(() -> {
            try {
                String config = configService.getConfig(dataId, McpNacosConstants.TOOLS_GROUP, 3000);
                if (config == null) return null;
                return objectMapper.readValue(config, McpToolsConfig.class);
            } catch (Exception e) {
                log.error("Error getting tools config for: {}", dataId, e);
                throw new RuntimeException("Failed to get tools config", e);
            }
        });
    }
    /**
     * 获取版本配置（所有版本）
     */
    public Mono<McpVersionConfig> getVersionConfig(String id) {
        String realId = id != null ? id : UUID.randomUUID().toString();
        String dataId = realId + McpNacosConstants.VERSIONS_CONFIG_SUFFIX;
        return Mono.fromCallable(() -> {
            try {
                String config = configService.getConfig(dataId, McpNacosConstants.VERSIONS_GROUP, 3000);
                if (config == null) return null;
                return objectMapper.readValue(config, McpVersionConfig.class);
            } catch (Exception e) {
                log.error("Error getting version config for: {}", dataId, e);
                throw new RuntimeException("Failed to get version config", e);
            }
        });
    }

    /**
     * 删除工具配置（指定版本）
     */
    public Mono<Boolean> deleteToolsConfig(String id, String version) {
        String realId = id != null ? id : UUID.randomUUID().toString();
        String realVersion = version != null ? version : "1.0.0";
        String dataId = realId + "-" + realVersion + McpNacosConstants.TOOLS_CONFIG_SUFFIX;
        return Mono.fromCallable(() -> {
            try {
                return configService.removeConfig(dataId, McpNacosConstants.TOOLS_GROUP);
            } catch (Exception e) {
                log.error("Error deleting tools config for: {}", dataId, e);
                throw new RuntimeException("Failed to delete tools config", e);
            }
        });
    }

    /**
     * 删除版本配置（所有版本索引）
     */
    public Mono<Boolean> deleteVersionConfig(String id) {
        String realId = id != null ? id : UUID.randomUUID().toString();
        String dataId = realId + McpNacosConstants.VERSIONS_CONFIG_SUFFIX;
        return Mono.fromCallable(() -> {
            try {
                return configService.removeConfig(dataId, McpNacosConstants.VERSIONS_GROUP);
            } catch (Exception e) {
                log.error("Error deleting version config for: {}", dataId, e);
                throw new RuntimeException("Failed to delete version config", e);
            }
        });
    }
    
    /**
     * 序列化服务器配置为JSON
     */
    public String serializeServerConfig(McpServerConfig config) throws Exception {
        return objectMapper.writeValueAsString(config);
    }

    /**
     * 计算字符串的MD5
     */
    public String md5(String content) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] array = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 构建服务器配置（完全对齐官方结构）
     */
    private McpServerConfig buildServerConfig(McpServerInfo serverInfo) {
        String id = serverInfo.getId() != null ? serverInfo.getId() : UUID.randomUUID().toString();
        String version = serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0";
        String name = serverInfo.getName();
        String groupName = serverInfo.getServiceGroup() != null ? serverInfo.getServiceGroup() : McpNacosConstants.SERVER_GROUP;
        String namespaceId = serverInfo.getNamespaceId() != null ? serverInfo.getNamespaceId() : "public";
        String toolsDataId = id + "-" + version + McpNacosConstants.TOOLS_CONFIG_SUFFIX;
        // versionDetail
        McpServerConfig.VersionDetail versionDetail = McpServerConfig.VersionDetail.builder()
                .version(version)
                .release_date(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString())
                .build();
        // remoteServerConfig
        McpServerConfig.ServiceRef serviceRef = McpServerConfig.ServiceRef.builder()
                .namespaceId(namespaceId)
                .groupName(groupName)
                .serviceName(name)
                .build();
        McpServerConfig.RemoteServerConfig remoteServerConfig = McpServerConfig.RemoteServerConfig.builder()
                .serviceRef(serviceRef)
                .exportPath(serverInfo.getSseEndpoint() != null ? serverInfo.getSseEndpoint() : McpNacosConstants.DEFAULT_SSE_ENDPOINT)
                .build();
        return McpServerConfig.builder()
                .id(id)
                .name(name)
                .protocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .frontProtocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .description(name)
                .versionDetail(versionDetail)
                .remoteServerConfig(remoteServerConfig)
                .enabled(true)
                .capabilities(List.of("TOOL"))
                .toolsDescriptionRef(toolsDataId)
                .build();
    }

    /**
     * 构建版本配置（完全对齐官方结构）
     */
    private McpVersionConfig buildVersionConfig(McpServerInfo serverInfo) {
        String id = serverInfo.getId() != null ? serverInfo.getId() : UUID.randomUUID().toString();
        String version = serverInfo.getVersion() != null ? serverInfo.getVersion() : "1.0.0";
        String name = serverInfo.getName();
        // versionDetails
        McpVersionConfig.VersionDetail versionDetail = McpVersionConfig.VersionDetail.builder()
                .version(version)
                .release_date(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString())
                .build();
        return McpVersionConfig.builder()
                .id(id)
                .name(name)
                .protocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .frontProtocol(McpNacosConstants.DEFAULT_PROTOCOL)
                .description(name)
                .enabled(true)
                .capabilities(List.of("TOOL"))
                .latestPublishedVersion(version)
                .versionDetails(List.of(versionDetail))
                .build();
    }

    /**
     * 构建工具配置（完全对齐官方结构）
     */
    private McpToolsConfig buildToolsConfig(McpServerInfo serverInfo) {
        // 这里建议实际业务中从 serverInfo 或其它来源动态构建 tools
        // 这里只给出官方样例结构
        List<McpToolsConfig.McpTool> tools = new ArrayList<>();
        tools.add(McpToolsConfig.McpTool.builder()
                .name("deletePerson")
                .description("Delete a person from the database")
                .inputSchema(McpToolsConfig.McpTool.InputSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "id", McpToolsConfig.McpTool.InputSchema.Property.builder()
                                        .type("integer").format("int64").description("Person's ID").build()
                        ))
                        .required(List.of("id"))
                        .additionalProperties(false)
                        .build())
                .build());
        tools.add(McpToolsConfig.McpTool.builder()
                .name("getPersonById")
                .description("Get a person by their ID")
                .inputSchema(McpToolsConfig.McpTool.InputSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "id", McpToolsConfig.McpTool.InputSchema.Property.builder()
                                        .type("integer").format("int64").description("Person's ID").build()
                        ))
                        .required(List.of("id"))
                        .additionalProperties(false)
                        .build())
                .build());
        tools.add(McpToolsConfig.McpTool.builder()
                .name("getAllPersons")
                .description("Get all persons from the database")
                .inputSchema(McpToolsConfig.McpTool.InputSchema.builder()
                        .type("object")
                        .properties(Map.of())
                        .required(List.of())
                        .additionalProperties(false)
                        .build())
                .build());
        tools.add(McpToolsConfig.McpTool.builder()
                .name("get_system_info")
                .description("Get system information")
                .inputSchema(McpToolsConfig.McpTool.InputSchema.builder()
                        .type("object")
                        .properties(Map.of())
                        .required(List.of())
                        .additionalProperties(false)
                        .build())
                .build());
        tools.add(McpToolsConfig.McpTool.builder()
                .name("list_servers")
                .description("List all registered servers")
                .inputSchema(McpToolsConfig.McpTool.InputSchema.builder()
                        .type("object")
                        .properties(Map.of())
                        .required(List.of())
                        .additionalProperties(false)
                        .build())
                .build());
        tools.add(McpToolsConfig.McpTool.builder()
                .name("addPerson")
                .description("Add a new person to the database")
                .inputSchema(McpToolsConfig.McpTool.InputSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "firstName", McpToolsConfig.McpTool.InputSchema.Property.builder().type("string").description("Person's first name").build(),
                                "lastName", McpToolsConfig.McpTool.InputSchema.Property.builder().type("string").description("Person's last name").build(),
                                "age", McpToolsConfig.McpTool.InputSchema.Property.builder().type("integer").format("int32").description("Person's age").build(),
                                "nationality", McpToolsConfig.McpTool.InputSchema.Property.builder().type("string").description("Person's nationality").build(),
                                "gender", McpToolsConfig.McpTool.InputSchema.Property.builder().type("string").description("Person's gender (MALE, FEMALE, OTHER)").build()
                        ))
                        .required(List.of("firstName","lastName","age","nationality","gender"))
                        .additionalProperties(false)
                        .build())
                .build());
        // toolsMeta 必须存在，允许为空对象
        Map<String, Object> toolsMeta = serverInfo.getToolsMeta() != null ? serverInfo.getToolsMeta() : new HashMap<>();
        return McpToolsConfig.builder()
                .tools(tools)
                .toolsMeta(toolsMeta)
                .build();
    }
} 