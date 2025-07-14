package com.nacos.mcp.router.service.impl;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.model.McpServerBasicInfo;
import com.nacos.mcp.router.service.McpServerDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NacosMcpServerDiscoveryServiceImpl implements McpServerDiscoveryService {

    private final NamingService namingService;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    @Value("${nacos.config.namespace:public}")
    private String namespace;

    private static final String MCP_SERVER_GROUP = "mcp-server";
    private static final String MCP_VERSIONS_GROUP = "mcp-server-versions";
    private static final String CONFIG_SUFFIX = "-mcp-versions.json";

    @Override
    public List<McpServerBasicInfo> listMcpServers() {
        List<McpServerBasicInfo> mcpServers = new ArrayList<>();
        try {
            // 获取所有服务列表
            List<String> serviceNames = namingService.getServicesOfServer(1, Integer.MAX_VALUE, MCP_SERVER_GROUP).getData();
            log.info("Found {} services in {} group", serviceNames.size(), MCP_SERVER_GROUP);

            // 定义要查找的MCP服务器名称
            List<String> mcpServiceNames = List.of(
                "mcp-server-v1",
                "mcp-server-v2",
                "mcp-server-v3"
            );

            for (String serviceName : serviceNames) {
                if (mcpServiceNames.stream().anyMatch(serviceName::contains)) {
                    try {
                        // 生成配置ID
                        String configId = generateConfigId(serviceName);
                        log.debug("Looking up config with ID: {}", configId);
                        
                        // 从配置中心获取服务器配置
                        String content = configService.getConfig(configId, MCP_VERSIONS_GROUP, 5000);
                        
                        if (content != null && !content.isEmpty()) {
                            try {
                                McpServerBasicInfo serverInfo = objectMapper.readValue(content, McpServerBasicInfo.class);
                                serverInfo.setName(serviceName); // 确保名称正确设置
                                mcpServers.add(serverInfo);
                                log.info("Found MCP server: {} with config ID: {}", serverInfo.getName(), configId);
                            } catch (Exception e) {
                                log.warn("Failed to parse MCP server config for {}: {}", serviceName, e.getMessage());
                            }
                        } else {
                            log.warn("No config found for MCP server: {} with config ID: {}", serviceName, configId);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get MCP server info for service: {}", serviceName, e);
                    }
                }
            }
        } catch (NacosException e) {
            log.error("Failed to list services from Nacos", e);
        }
        return mcpServers;
    }

    @Override
    public McpServerBasicInfo getMcpServer(String serviceName) {
        try {
            // 生成配置ID
            String configId = generateConfigId(serviceName);
            log.debug("Looking up config for {} with ID: {}", serviceName, configId);
            
            // 从Nacos配置中心获取服务器配置
            String content = configService.getConfig(configId, MCP_VERSIONS_GROUP, 5000);
            if (content != null && !content.isEmpty()) {
                McpServerBasicInfo serverInfo = objectMapper.readValue(content, McpServerBasicInfo.class);
                serverInfo.setName(serviceName); // 确保名称正确设置
                return serverInfo;
            } else {
                log.warn("No config found for MCP server: {} with config ID: {}", serviceName, configId);
            }
        } catch (Exception e) {
            log.error("Failed to get MCP server info from Nacos config: {}", serviceName, e);
        }
        return null;
    }

    /**
     * 生成配置ID
     * 格式: {uuid}-mcp-versions.json
     */
    private String generateConfigId(String serviceName) {
        // 使用UUID作为配置ID的一部分
        return UUID.randomUUID().toString() + CONFIG_SUFFIX;
    }
} 