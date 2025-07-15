package com.nacos.mcp.router.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.nacos.mcp.router.model.McpServerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP Server 注册管理服务
 */
@Slf4j
@Service
public class McpServerRegistry {

    private final NamingService namingService;
    private final ConcurrentHashMap<String, McpServerInfo> serverInfoMap = new ConcurrentHashMap<>();

    public McpServerRegistry(NamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * 注册服务实例
     */
    public void registerInstance(Instance instance) {
        try {
            McpServerInfo serverInfo = convertToServerInfo(instance);
            serverInfoMap.put(serverInfo.getInstanceId(), serverInfo);
            log.info("Registered MCP Server instance: {}", serverInfo.getInstanceId());
        } catch (Exception e) {
            log.error("Failed to register MCP Server instance", e);
        }
    }

    /**
     * 处理实例变更
     */
    public void handleInstanceChange(List<Instance> instances) {
        try {
            // 获取当前所有实例ID
            List<String> currentInstanceIds = instances.stream()
                .map(this::getInstanceId)
                .collect(Collectors.toList());

            // 移除不存在的实例
            serverInfoMap.keySet().removeIf(instanceId -> !currentInstanceIds.contains(instanceId));

            // 更新或添加新实例
            instances.forEach(this::registerInstance);
        } catch (Exception e) {
            log.error("Failed to handle instance changes", e);
        }
    }

    /**
     * 获取所有健康的服务信息
     */
    public List<McpServerInfo> getHealthyServers() {
        return serverInfoMap.values().stream()
            .filter(McpServerInfo::isHealthy)
            .collect(Collectors.toList());
    }

    /**
     * 获取指定服务信息
     */
    public McpServerInfo getServerInfo(String instanceId) {
        return serverInfoMap.get(instanceId);
    }

    /**
     * 移除服务实例
     */
    public void removeInstance(String instanceId) {
        McpServerInfo removed = serverInfoMap.remove(instanceId);
        if (removed != null) {
            log.info("Removed MCP Server instance: {}", instanceId);
        }
    }

    private String getInstanceId(Instance instance) {
        return instance.getIp() + ":" + instance.getPort();
    }

    private McpServerInfo convertToServerInfo(Instance instance) {
        McpServerInfo serverInfo = new McpServerInfo();
        serverInfo.setInstanceId(getInstanceId(instance));
        serverInfo.setIp(instance.getIp());
        serverInfo.setPort(instance.getPort());
        serverInfo.setHealthy(instance.isHealthy());
        serverInfo.setMetadata(instance.getMetadata());
        serverInfo.setLastUpdateTime(System.currentTimeMillis());

        // 从元数据中提取服务信息
        Map<String, String> metadata = instance.getMetadata();
        serverInfo.setName(metadata.getOrDefault("name", "unknown"));
        serverInfo.setVersion(metadata.getOrDefault("version", "unknown"));
        serverInfo.setDescription(metadata.getOrDefault("description", ""));
        serverInfo.setEnabled(Boolean.parseBoolean(metadata.getOrDefault("enabled", "true")));

        return serverInfo;
    }
} 