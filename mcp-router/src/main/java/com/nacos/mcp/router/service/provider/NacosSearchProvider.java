package com.nacos.mcp.router.service.provider;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.McpServerBasicInfo;
import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.service.McpServerDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NacosSearchProvider implements SearchProvider {

    private final NamingService namingService;
    private final ObjectMapper objectMapper;
    private final McpServerDiscoveryService mcpServerDiscoveryService;

    @Value("${nacos.config.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.config.namespace:public}")
    private String namespace;

    @Override
    public String getProviderName() {
        return "Nacos";
    }

    @Override
    public Mono<List<McpServer>> search(SearchRequest request) {
        return Mono.<List<McpServer>>fromCallable(() -> {
            List<McpServer> allServers = new ArrayList<>();

            try {
                // 获取所有MCP服务器的基本信息
                List<McpServerBasicInfo> mcpServers = mcpServerDiscoveryService.listMcpServers();
                log.info("Found {} MCP servers from Nacos", mcpServers.size());

                for (McpServerBasicInfo mcpServerInfo : mcpServers) {
                    try {
                        // 获取服务实例信息
                        String serviceName = mcpServerInfo.getEndpointReference().getServiceName();
                        String groupName = mcpServerInfo.getEndpointReference().getGroupName();
                        
                        List<Instance> instances = namingService.selectInstances(serviceName, groupName, true);
                        log.info("Found {} instances for MCP server: {}", instances.size(), serviceName);

                        for (Instance instance : instances) {
                            try {
                                McpServer server = buildMcpServerFromMcpInfo(instance, mcpServerInfo);
                                if (server != null) {
                                    allServers.add(server);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to build McpServer from instance: {}", e.getMessage());
                            }
                        }
                    } catch (NacosException e) {
                        log.warn("Failed to get instances for MCP server: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to list MCP servers: {}", e.getMessage());
            }

            return allServers;
        });
    }

    private McpServer buildMcpServerFromMcpInfo(Instance instance, McpServerBasicInfo mcpInfo) {
        try {
            String endpoint = String.format("%s://%s:%d%s",
                    instance.getMetadata().getOrDefault("scheme", "http"),
                    instance.getIp(),
                    instance.getPort(),
                    mcpInfo.getRemoteServerConfig().getSsePath());

            McpServer server = new McpServer();
            server.setName(mcpInfo.getName());
            server.setVersion(mcpInfo.getVersion());
            server.setEndpoint(endpoint);
            server.setTransportType("sse");
            server.setHealthy(instance.isHealthy());
            server.setMetadata(instance.getMetadata());
            
            // 设置工具信息
            if (mcpInfo.getTools() != null) {
                server.setTools(mcpInfo.getTools());
            }

            return server;
        } catch (Exception e) {
            log.error("Failed to build McpServer from MCP info: {}", e.getMessage());
            return null;
        }
    }
} 