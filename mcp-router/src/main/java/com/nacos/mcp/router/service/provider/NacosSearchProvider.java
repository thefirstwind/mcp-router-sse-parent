package com.nacos.mcp.router.service.provider;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.McpTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Nacos Search Provider - 支持MCP标准格式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosSearchProvider implements SearchProvider {

    private final NamingService namingService;
    private final ObjectMapper objectMapper;

    @Value("${nacos.config.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.config.namespace:public}")
    private String namespace;

    @Override
    public Mono<List<McpServer>> search(SearchRequest request) {
        return Mono.<List<McpServer>>fromCallable(() -> {
            try {
                // 动态发现多个组中的所有MCP服务
                List<McpServer> allServers = new ArrayList<>();

                // 搜索的组列表
                List<String> groupsToSearch = List.of("mcp-server", "DEFAULT_GROUP");

                for (String groupName : groupsToSearch) {
                    try {
                        // 获取指定组中的所有服务列表
                        List<String> serviceNames = namingService.getServicesOfServer(1, Integer.MAX_VALUE, groupName).getData();
                        log.info("Found {} services in {} group", serviceNames.size(), groupName);

                        for (String serviceName : serviceNames) {
                            // 只处理以mcp-server开头或包含mcp-service的服务
                            if (serviceName.contains("mcp-service") || serviceName.startsWith("mcp-server")) {
                                try {
                                    List<Instance> instances = namingService.selectInstances(serviceName, groupName, true);
                                    log.info("Querying Nacos for service: '{}' in group '{}', found {} instances.", serviceName, groupName, instances.size());

                                    for (Instance instance : instances) {
                                        McpServer server = buildMcpServerFromInstance(instance, groupName);
                                        if (server != null) {
                                            allServers.add(server);
                                        }
                                    }
                                } catch (NacosException e) {
                                    log.warn("Failed to query Nacos for service '{}' in group '{}': {}", serviceName, groupName, e.getMessage());
                                    // 继续处理其他服务，不因为一个服务失败而全部失败
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to query services in group '{}': {}", groupName, e.getMessage());
                        // 继续处理其他组
                    }
                }

                log.info("Total discovered MCP servers: {}", allServers.size());
                return allServers;

            } catch (Exception e) {
                log.error("Unexpected error in NacosSearchProvider search execution: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        }).doOnError(e -> log.error("Error in NacosSearchProvider search execution", e));
    }

    @Override
    public String getProviderName() {
        return "Nacos";
    }

    /**
     * 从Nacos实例构建MCP服务器信息
     */
    private McpServer buildMcpServerFromInstance(Instance instance, String groupName) {
        try {
            Map<String, String> metadata = instance.getMetadata();

            // 处理元数据为空的情况
            if (metadata == null) {
                metadata = Collections.emptyMap();
            }

            String serviceName = instance.getServiceName();
            if (serviceName.contains("@@")) {
                serviceName = serviceName.substring(serviceName.indexOf("@@") + 2);
            }

            // 获取服务器配置文件名
            String serverName = getServerNameFromServiceName(serviceName);
            String serverConfigDataId = serverName + "-mcp-server.json";
            String toolsConfigDataId = serverName + "-mcp-tools.json";

            // 获取服务器配置
            JsonNode serverConfig = getConfigFromNacos(serverConfigDataId, "mcp-server");
            JsonNode toolsConfig = getConfigFromNacos(toolsConfigDataId, "mcp-tools");

            // 构建MCP服务器信息
            McpServer.McpServerBuilder builder = McpServer.builder()
                    .name(serverName)
                    .ip(instance.getIp())
                    .port(instance.getPort())
                    .status(instance.isEnabled() ? McpServer.ServerStatus.CONNECTED : McpServer.ServerStatus.DISCONNECTED)
                    .lastHeartbeat(LocalDateTime.now())
                    .registrationTime(LocalDateTime.now())
                    .lastUpdateTime(LocalDateTime.now());

            // 从配置文件中获取服务器信息
            if (serverConfig != null) {
                builder.description(serverConfig.path("description").asText("Unknown"))
                        .version(serverConfig.path("version").asText("1.0.0"))
                        .transportType(serverConfig.path("protocol").asText("sse"));

                // 获取端点信息 - 根据MCP标准，WebFluxSseClientTransport会自动添加/sse后缀
                JsonNode remoteServerConfig = serverConfig.path("remoteServerConfig");
                if (!remoteServerConfig.isMissingNode()) {
                    String exportPath = remoteServerConfig.path("exportPath").asText("/sse");
                    String scheme = metadata.getOrDefault("scheme", "http");
                    // WebFluxSseClientTransport会自动添加/sse后缀，所以这里只设置基础URL
                    String endpoint = scheme + "://" + instance.getIp() + ":" + instance.getPort();
                    builder.endpoint(endpoint);
                }
            } else {
                // 如果没有配置文件，则使用元数据
                builder.description(metadata.getOrDefault("description", "Unknown"))
                        .version(metadata.getOrDefault("mcp.version", "1.0.0"))
                        .transportType(metadata.getOrDefault("transportType", "sse"))
                        .endpoint(String.format("http://%s:%d/sse", instance.getIp(), instance.getPort()));
            }

            // 从工具配置文件中获取工具信息
            List<McpTool> tools = new ArrayList<>();
            if (toolsConfig != null) {
                JsonNode toolsNode = toolsConfig.path("tools");
                if (toolsNode.isArray()) {
                    for (JsonNode toolNode : toolsNode) {
                        String toolName = toolNode.path("name").asText();
                        String toolDescription = toolNode.path("description").asText();

                        McpTool tool = new McpTool();
                        tool.setName(toolName);
                        tool.setDescription(toolDescription);
//                        tool.setEnabled(true);
                        tools.add(tool);
                    }
                }
            } else {
                // 如果没有工具配置文件，则使用元数据中的工具列表
                String toolsStr = metadata.getOrDefault("tools.names", "");
                if (!toolsStr.isEmpty()) {
                    String[] toolNames = toolsStr.split(",");
                    for (String toolName : toolNames) {
                        McpTool tool = new McpTool();
                        tool.setName(toolName.trim());
                        tool.setDescription("Tool: " + toolName.trim());
//                        tool.setEnabled(true);
                        tools.add(tool);
                    }
                }
            }

            builder.tools(tools);

            // 设置元数据
            builder.metadata(metadata.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> (Object) entry.getValue())));

            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to build MCP server from instance: {}", instance, e);
            return null;
        }
    }

    /**
     * 从服务名称中提取服务器名称
     */
    private String getServerNameFromServiceName(String serviceName) {
        if (serviceName.endsWith("-mcp-service")) {
            return serviceName.substring(0, serviceName.length() - "-mcp-service".length());
        }
        return serviceName;
    }

    /**
     * 从Nacos获取配置
     */
    private JsonNode getConfigFromNacos(String dataId, String group) {
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            properties.put("namespace", namespace);

            ConfigService configService = NacosFactory.createConfigService(properties);
            String content = configService.getConfig(dataId, group, 5000);

            if (content != null && !content.isEmpty()) {
                return objectMapper.readTree(content);
            }
        } catch (Exception e) {
            log.warn("Failed to get config from Nacos: {}, group: {}, error: {}", dataId, group, e.getMessage());
        }
        return null;
    }

    private List<McpTool> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 使用ObjectMapper解析JSON字符串为McpTool对象列表
            return objectMapper.readValue(toolsJson, new TypeReference<List<McpTool>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tools JSON: {}. JSON content: {}", e.getMessage(), toolsJson);
            return Collections.emptyList();
        }
    }
} 