package com.pajk.mcpbridge.core.controller;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.config.McpNacosConstants;
import com.pajk.mcpbridge.core.model.McpServerConfig;
import com.pajk.mcpbridge.core.model.McpToolsConfig;
import com.pajk.mcpbridge.core.model.McpVersionConfig;
import com.pajk.mcpbridge.core.service.McpConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nacos 服务与配置管理控制器
 * 用于获取 Nacos 中的服务列表和 MCP 配置列表
 */
@RestController
@RequestMapping({"/api/nacos", "/api/mcp/config"})
@RequiredArgsConstructor
public class NacosController {

    private static final Logger log = LoggerFactory.getLogger(NacosController.class);
    
    private final NamingService namingService;
    private final ConfigService configService;
    private final McpConfigService mcpConfigService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder().build();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Value("${spring.ai.alibaba.mcp.nacos.server-addr:127.0.0.1:8848}")
    private String nacosServerAddr;
    
    @Value("${spring.ai.alibaba.mcp.nacos.namespace:public}")
    private String namespace;

    /**
     * 获取 Nacos 中的服务列表
     * 
     * @param group 服务组（可选，默认: mcp-server）。如果为 "*" 或 "all"，则查询所有已知的 MCP 服务组
     * @param pageNo 页码（可选，默认: 1）
     * @param pageSize 每页大小（可选，默认: 100）
     * @return 服务列表信息
     * 
     * 示例请求:
     * GET /api/nacos/services?group=mcp-server&pageNo=1&pageSize=100
     * GET /api/nacos/services?group=*  (查询所有组)
     */
    @GetMapping("/services")
    public ResponseEntity<ServiceListResponse> getServices(
            @RequestParam(defaultValue = "mcp-server") String group,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "100") int pageSize) {
        log.info("Getting services from Nacos, group: {}, pageNo: {}, pageSize: {}", group, pageNo, pageSize);
        
        try {
            // 处理 "*" 或 "all" 的情况，查询所有已知的 MCP 服务组
            List<String> groupsToQuery;
            if ("*".equals(group) || "all".equalsIgnoreCase(group)) {
                // 查询所有已知的 MCP 服务组
                groupsToQuery = Arrays.asList("mcp-server", "mcp-endpoints", "DEFAULT_GROUP");
                log.info("Querying all groups: {}", groupsToQuery);
            } else {
                groupsToQuery = Collections.singletonList(group);
            }
            
            // 合并所有组的结果
            List<ServiceInfo> allServiceInfos = new ArrayList<>();
            Set<String> allServiceNames = new HashSet<>();
            
            for (String queryGroup : groupsToQuery) {
                try {
                    ListView<String> servicesList = namingService.getServicesOfServer(pageNo, pageSize, queryGroup);
                    
                    if (servicesList != null && servicesList.getData() != null) {
                        for (String serviceName : servicesList.getData()) {
                            // 避免重复（同一服务可能在多个组中）
                            String serviceKey = serviceName + "@" + queryGroup;
                            if (allServiceNames.contains(serviceKey)) {
                                continue;
                            }
                            allServiceNames.add(serviceKey);
                            
                            try {
                                List<Instance> instances = namingService.getAllInstances(serviceName, queryGroup);
                                ServiceInfo serviceInfo = new ServiceInfo();
                                serviceInfo.setServiceName(serviceName);
                                serviceInfo.setGroup(queryGroup);
                                serviceInfo.setInstanceCount(instances != null ? instances.size() : 0);
                                serviceInfo.setHealthyInstanceCount(instances != null ? 
                                    (int) instances.stream().filter(Instance::isHealthy).count() : 0);
                                allServiceInfos.add(serviceInfo);
                            } catch (Exception e) {
                                log.warn("Failed to get instances for service: {} in group: {}", serviceName, queryGroup, e);
                                ServiceInfo serviceInfo = new ServiceInfo();
                                serviceInfo.setServiceName(serviceName);
                                serviceInfo.setGroup(queryGroup);
                                serviceInfo.setInstanceCount(0);
                                serviceInfo.setHealthyInstanceCount(0);
                                allServiceInfos.add(serviceInfo);
                            }
                        }
                    }
                } catch (NacosException e) {
                    log.warn("Failed to get services from group: {}", queryGroup, e);
                    // 继续查询其他组
                }
            }
            
            ServiceListResponse response = new ServiceListResponse();
            response.setGroup("*".equals(group) || "all".equalsIgnoreCase(group) ? "all" : group);
            response.setPageNo(pageNo);
            response.setPageSize(pageSize);
            response.setCount(allServiceInfos.size());
            response.setServices(allServiceInfos.stream()
                    .map(ServiceInfo::getServiceName)
                    .distinct()
                    .collect(Collectors.toList()));
            response.setServiceInfos(allServiceInfos);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting services from Nacos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ServiceListResponse());
        }
    }

    /**
     * 获取 Nacos 中的 MCP 配置列表
     * 
     * @param group 配置组（可选，支持: mcp-server, mcp-tools, mcp-server-versions）
     * @param appName 应用名称（可选），如果指定，将通过 Nacos Open API 查询该应用的所有配置
     * @return MCP 配置列表
     * 
     * 示例请求:
     * GET /api/nacos/configs?group=mcp-server
     * GET /api/nacos/configs?group=mcp-tools
     * GET /api/nacos/configs?group=mcp-server-versions
     * GET /api/nacos/configs?appName=mcp_server_v6
     */
    @GetMapping("/configs")
    public ResponseEntity<ConfigListResponse> getMcpConfigs(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String appName) {
        log.info("Getting MCP configs from Nacos, group: {}, appName: {}", group, appName);
        
        ConfigListResponse response = new ConfigListResponse();
        List<ConfigInfo> allConfigs = new ArrayList<>();
        
        try {
            // 如果指定了 appName，通过 Nacos Open API 查询
            if (appName != null && !appName.isEmpty()) {
                List<ConfigInfo> configsByApp = getConfigsByAppName(appName);
                allConfigs.addAll(configsByApp);
                log.info("Found {} configs for appName: {}", configsByApp.size(), appName);
            } else {
                // 如果指定了组，只查询该组
                if (group != null && !group.isEmpty()) {
                    List<ConfigInfo> configs = getConfigsByGroup(group);
                    allConfigs.addAll(configs);
                } else {
                    // 查询所有 MCP 相关的配置组
                    List<ConfigInfo> serverConfigs = getConfigsByGroup(McpNacosConstants.SERVER_GROUP);
                    List<ConfigInfo> toolsConfigs = getConfigsByGroup(McpNacosConstants.TOOLS_GROUP);
                    List<ConfigInfo> versionsConfigs = getConfigsByGroup(McpNacosConstants.VERSIONS_GROUP);
                    
                    allConfigs.addAll(serverConfigs);
                    allConfigs.addAll(toolsConfigs);
                    allConfigs.addAll(versionsConfigs);
                }
            }
            
            response.setConfigs(allConfigs);
            response.setCount(allConfigs.size());
            
            // 按组分类统计
            Map<String, Long> groupCounts = allConfigs.stream()
                    .collect(Collectors.groupingBy(ConfigInfo::getGroup, Collectors.counting()));
            response.setGroupCounts(groupCounts);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting MCP configs from Nacos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ConfigListResponse());
        }
    }
    
    /**
     * 通过 Nacos Open API 根据 appName 查询配置列表
     */
    private List<ConfigInfo> getConfigsByAppName(String appName) {
        List<ConfigInfo> configs = new ArrayList<>();
        try {
            // 使用 Nacos Open API 查询配置历史（包含 appName 信息）
            String url = String.format("http://%s/nacos/v2/cs/history/configs?pageNo=1&pageSize=100&namespaceId=%s&appName=%s",
                    nacosServerAddr, namespace, appName);
            
            log.info("Querying configs by appName from: {} (nacosServerAddr={}, namespace={})", url, nacosServerAddr, namespace);
            
            String response = null;
            try {
                // 使用 CompletableFuture 在独立线程中执行阻塞操作
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return webClient.get()
                                .uri(url)
                                .retrieve()
                                .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                                    log.error("Nacos API returned error status: {}", clientResponse.statusCode());
                                    return Mono.error(new RuntimeException("Nacos API error: " + clientResponse.statusCode()));
                                })
                                .bodyToMono(String.class)
                                .block();
                    } catch (Exception e) {
                        log.error("Error in WebClient call: {}", e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }, executorService);
                
                response = future.get(); // 在独立线程中等待，不会阻塞响应式线程
                
                log.info("Nacos API response received, length: {}", response != null ? response.length() : 0);
                if (response != null && response.length() < 1000) {
                    log.info("Nacos API response content: {}", response);
                } else if (response != null) {
                    log.info("Nacos API response (first 500 chars): {}", response.substring(0, Math.min(500, response.length())));
                }
            } catch (Exception e) {
                log.error("Failed to call Nacos API: {} - {}", url, e.getMessage(), e);
                return configs;
            }
            
            if (response != null && !response.isEmpty()) {
                // 解析响应 JSON
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
                    log.info("Parsed response map, code: {}, has data: {}", 
                            responseMap != null ? responseMap.get("code") : "null",
                            responseMap != null && responseMap.containsKey("data"));
                    
                    if (responseMap != null && responseMap.containsKey("data")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseMap.get("data");
                        log.info("Data list size: {}", dataList != null ? dataList.size() : 0);
                        
                        if (dataList != null && !dataList.isEmpty()) {
                            for (Map<String, Object> item : dataList) {
                                String dataId = (String) item.get("dataId");
                                String configGroup = (String) item.get("group");
                                String itemAppName = (String) item.get("appName");
                                
                                log.info("Processing config item: dataId={}, group={}, appName={}, queryAppName={}", dataId, configGroup, itemAppName, appName);
                                
                                // 过滤：只处理 appName 匹配的配置
                                if (itemAppName == null || !itemAppName.equals(appName)) {
                                    log.debug("Skipping config with appName={}, expected={}", itemAppName, appName);
                                    continue;
                                }
                                
                                // 只处理 MCP 相关的配置
                                if (dataId != null && (dataId.contains("-mcp-server.json") || 
                                        dataId.contains("-mcp-tools.json") || 
                                        dataId.contains("-mcp-versions.json"))) {
                                    ConfigInfo configInfo = new ConfigInfo();
                                    configInfo.setDataId(dataId);
                                    configInfo.setGroup(configGroup);
                                    
                                    // 从 dataId 中提取 UUID 和版本
                                    if (dataId.contains("-mcp-versions.json")) {
                                        String uuid = dataId.replace("-mcp-versions.json", "");
                                        configInfo.setUuid(uuid);
                                    } else if (dataId.contains("-mcp-server.json")) {
                                        String withoutSuffix = dataId.replace("-mcp-server.json", "");
                                        String[] parts = withoutSuffix.split("-");
                                        if (parts.length >= 5) {
                                            String uuid = String.join("-", Arrays.copyOf(parts, 5));
                                            String version = parts.length > 5 ? parts[5] : null;
                                            configInfo.setUuid(uuid);
                                            configInfo.setVersion(version);
                                        }
                                    } else if (dataId.contains("-mcp-tools.json")) {
                                        String withoutSuffix = dataId.replace("-mcp-tools.json", "");
                                        String[] parts = withoutSuffix.split("-");
                                        if (parts.length >= 5) {
                                            String uuid = String.join("-", Arrays.copyOf(parts, 5));
                                            String version = parts.length > 5 ? parts[5] : null;
                                            configInfo.setUuid(uuid);
                                            configInfo.setVersion(version);
                                        }
                                    }
                                    
                                    // 通过 UUID 匹配服务名（优先使用 UUID 匹配，更准确）
                                    // 获取所有服务，然后通过服务名获取 UUID 进行匹配
                                    boolean matched = false;
                                    try {
                                        // 查询所有已知的服务组
                                        List<String> serviceGroups = Arrays.asList("mcp-server", "mcp-endpoints", "DEFAULT_GROUP");
                                        for (String serviceGroup : serviceGroups) {
                                            if (matched) break;
                                            try {
                                                ListView<String> services = namingService.getServicesOfServer(1, 100, serviceGroup);
                                                if (services != null && services.getData() != null) {
                                                    for (String serviceName : services.getData()) {
                                                        try {
                                                            String serviceUuid = mcpConfigService.getUuidFromServiceName(serviceName);
                                                            log.debug("Checking service: {} -> UUID: {} against config UUID: {}", serviceName, serviceUuid, configInfo.getUuid());
                                                            if (configInfo.getUuid() != null && configInfo.getUuid().equals(serviceUuid)) {
                                                                configInfo.setServiceName(serviceName);
                                                                log.info("Matched service name {} for UUID {} in group {}", serviceName, configInfo.getUuid(), serviceGroup);
                                                                matched = true;
                                                                break;
                                                            }
                                                        } catch (Exception e) {
                                                            log.debug("Failed to get UUID for service: {}", serviceName, e);
                                                        }
                                                    }
                                                }
                                            } catch (Exception e) {
                                                log.debug("Failed to get services from group: {}", serviceGroup, e);
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.debug("Failed to match service name by UUID: {}", e.getMessage());
                                    }
                                    
                                    // 如果 UUID 匹配失败，使用 appName 作为备选
                                    if (!matched && itemAppName != null && !itemAppName.isEmpty()) {
                                        configInfo.setServiceName(itemAppName);
                                        log.debug("Using appName as serviceName: {}", itemAppName);
                                    }
                                    
                                    configs.add(configInfo);
                                    log.info("Added config: dataId={}, group={}, uuid={}, version={}, serviceName={}", 
                                            dataId, configGroup, configInfo.getUuid(), configInfo.getVersion(), configInfo.getServiceName());
                                } else {
                                    log.debug("Skipping non-MCP config: dataId={}", dataId);
                                }
                            }
                        } else {
                            log.warn("Data list is null or empty");
                        }
                    } else {
                        log.warn("Response does not contain 'data' field. Response: {}", response);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Nacos API response: {} - {}", response, e.getMessage(), e);
                }
            } else {
                log.warn("Nacos API returned null or empty response");
            }
        } catch (Exception e) {
            log.error("Error querying configs by appName: {}", appName, e);
        }
        log.info("Returning {} configs for appName: {}", configs.size(), appName);
        return configs;
    }

    /**
     * 根据配置组获取配置列表
     * 通过服务列表获取版本配置，然后根据实际版本号查找配置
     */
    private List<ConfigInfo> getConfigsByGroup(String group) {
        List<ConfigInfo> configs = new ArrayList<>();
        
        // 查询所有已知的 MCP 服务组
        List<String> serviceGroups = Arrays.asList("mcp-server", "mcp-endpoints", "DEFAULT_GROUP");
        
        for (String serviceGroup : serviceGroups) {
            try {
                // 获取该组下的所有服务
                ListView<String> services = namingService.getServicesOfServer(1, 100, serviceGroup);
                if (services != null && services.getData() != null) {
                    log.debug("Found {} services in group: {}", services.getData().size(), serviceGroup);
                    for (String serviceName : services.getData()) {
                        try {
                            // 从服务名称生成 UUID
                            String uuid = mcpConfigService.getUuidFromServiceName(serviceName);
                            log.debug("Processing service: {}, UUID: {}", serviceName, uuid);
                            
                            // 先获取版本配置，获取实际版本号
                            String versionsDataId = uuid + McpNacosConstants.VERSIONS_CONFIG_SUFFIX;
                            McpVersionConfig versionConfig = null;
                            try {
                                String versionConfigJson = configService.getConfig(
                                    versionsDataId, 
                                    McpNacosConstants.VERSIONS_GROUP, 
                                    1000
                                );
                                if (versionConfigJson != null && !versionConfigJson.isEmpty()) {
                                    versionConfig = objectMapper.readValue(versionConfigJson, McpVersionConfig.class);
                                    log.debug("Found version config for service: {}, versions: {}", serviceName, versionConfig.getVersions());
                                }
                            } catch (Exception e) {
                                log.debug("Version config not found for service: {} (dataId: {}), trying alternatives", serviceName, versionsDataId);
                            }
                            
                            // 获取版本列表
                            List<String> versions = new ArrayList<>();
                            if (versionConfig != null && versionConfig.getVersions() != null && !versionConfig.getVersions().isEmpty()) {
                                versions = versionConfig.getVersions();
                            } else {
                                // 如果没有版本配置，尝试从实例中获取版本
                                try {
                                    List<Instance> instances = namingService.getAllInstances(serviceName, serviceGroup);
                                    if (instances != null) {
                                        for (Instance instance : instances) {
                                            String version = instance.getMetadata() != null 
                                                ? instance.getMetadata().get("version") : null;
                                            if (version != null && !versions.contains(version)) {
                                                versions.add(version);
                                            }
                                        }
                                        if (!versions.isEmpty()) {
                                            log.debug("Found versions from instances for service: {}, versions: {}", serviceName, versions);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("Failed to get instances for version detection: {}", serviceName, e);
                                }
                                // 如果还是没有版本，尝试更多常见版本
                                if (versions.isEmpty()) {
                                    versions = Arrays.asList("1.0.0", "1.0.1", "1.1.0", "1.0.2", "1.1.1", "2.0.0");
                                    log.debug("Using default versions for service: {}, versions: {}", serviceName, versions);
                                }
                            }
                            
                            // 根据组类型构建 dataId
                            if (McpNacosConstants.SERVER_GROUP.equals(group)) {
                                for (String version : versions) {
                                    String dataId = uuid + "-" + version + McpNacosConstants.SERVER_CONFIG_SUFFIX;
                                    if (configExists(dataId, group)) {
                                        log.debug("Found server config: {} in group: {}", dataId, group);
                                        ConfigInfo configInfo = new ConfigInfo();
                                        configInfo.setDataId(dataId);
                                        configInfo.setGroup(group);
                                        configInfo.setServiceName(serviceName);
                                        configInfo.setUuid(uuid);
                                        configInfo.setVersion(version);
                                        configs.add(configInfo);
                                    }
                                }
                            } else if (McpNacosConstants.TOOLS_GROUP.equals(group)) {
                                for (String version : versions) {
                                    String dataId = uuid + "-" + version + McpNacosConstants.TOOLS_CONFIG_SUFFIX;
                                    if (configExists(dataId, group)) {
                                        log.debug("Found tools config: {} in group: {}", dataId, group);
                                        ConfigInfo configInfo = new ConfigInfo();
                                        configInfo.setDataId(dataId);
                                        configInfo.setGroup(group);
                                        configInfo.setServiceName(serviceName);
                                        configInfo.setUuid(uuid);
                                        configInfo.setVersion(version);
                                        configs.add(configInfo);
                                    }
                                }
                            } else if (McpNacosConstants.VERSIONS_GROUP.equals(group)) {
                                if (configExists(versionsDataId, group)) {
                                    log.debug("Found versions config: {} in group: {}", versionsDataId, group);
                                    ConfigInfo configInfo = new ConfigInfo();
                                    configInfo.setDataId(versionsDataId);
                                    configInfo.setGroup(group);
                                    configInfo.setServiceName(serviceName);
                                    configInfo.setUuid(uuid);
                                    configs.add(configInfo);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Error processing service: {} in group: {}", serviceName, serviceGroup, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error getting services from group: {}", serviceGroup, e);
            }
        }
        
        log.info("Found {} configs for group: {}", configs.size(), group);
        return configs;
    }

    /**
     * 检查配置是否存在
     */
    private boolean configExists(String dataId, String group) {
        try {
            String config = configService.getConfig(dataId, group, 1000);
            boolean exists = config != null && !config.isEmpty();
            if (!exists) {
                log.trace("Config not found: dataId={}, group={}", dataId, group);
            }
            return exists;
        } catch (Exception e) {
            log.trace("Error checking config existence: dataId={}, group={}, error={}", dataId, group, e.getMessage());
            return false;
        }
    }

    /**
     * 服务列表响应
     */
    @Data
    public static class ServiceListResponse {
        private String group;
        private int pageNo;
        private int pageSize;
        private int count;
        private List<String> services;
        private List<ServiceInfo> serviceInfos;
    }

    /**
     * 服务信息
     */
    @Data
    public static class ServiceInfo {
        private String serviceName;
        private String group;
        private int instanceCount;
        private int healthyInstanceCount;
    }

    /**
     * 配置列表响应
     */
    @Data
    public static class ConfigListResponse {
        private int count;
        private List<ConfigInfo> configs;
        private Map<String, Long> groupCounts;
    }

    /**
     * 配置信息
     */
    @Data
    public static class ConfigInfo {
        private String dataId;
        private String group;
        private String serviceName;
        private String uuid;
        private String version;
    }

    // ==================== MCP 配置管理接口 ====================
    // 这些接口保持原有的路径 /api/mcp/config，以保持向后兼容

    /**
     * 根据服务名称获取UUID
     * 
     * @param serverName 服务名称，例如: mcp-server-v6
     * @return UUID字符串
     * 
     * 示例请求:
     * GET /api/mcp/config/uuid?serverName=mcp-server-v6
     */
    @GetMapping("/uuid")
    public ResponseEntity<UuidResponse> getUuidFromServerName(
            @RequestParam String serverName) {
        log.info("Getting UUID for server name: {}", serverName);
        try {
            String uuid = mcpConfigService.getUuidFromServiceName(serverName);
            UuidResponse response = new UuidResponse();
            response.setServerName(serverName);
            response.setUuid(uuid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting UUID for server name: {}", serverName, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new UuidResponse());
        }
    }

    /**
     * 获取服务器配置 (mcp-server.json)
     * 支持通过 uuid 或 serverName 获取配置
     * 
     * @param uuid 服务器UUID（可选），例如: f457f161-5735-46b6-b901-d2fa8e03cb21
     * @param serverName 服务名称（可选），例如: mcp-server-v6
     * @param version 版本号，例如: 1.0.1
     * @return 服务器配置对象
     * 
     * 示例请求:
     * GET /api/mcp/config/server?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21&version=1.0.1
     * GET /api/mcp/config/server?serverName=mcp-server-v6&version=1.0.1
     */
    @GetMapping("/server")
    public Mono<ResponseEntity<McpServerConfig>> getServerConfig(
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String serverName,
            @RequestParam(defaultValue = "1.0.0") String version) {
        String finalUuid = resolveUuid(uuid, serverName);
        if (finalUuid == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body((McpServerConfig) null));
        }
        log.info("Getting server config for uuid: {}, serverName: {}, version: {}", finalUuid, serverName, version);
        return mcpConfigService.getServerConfig(finalUuid, version)
                .map(config -> {
                    if (config == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((McpServerConfig) null);
                    }
                    return ResponseEntity.ok(config);
                })
                .onErrorResume(error -> {
                    log.error("Error getting server config for uuid: {}, serverName: {}, version: {}", finalUuid, serverName, version, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((McpServerConfig) null));
                });
    }

    /**
     * 获取工具配置 (mcp-tools.json)
     * 支持通过 uuid 或 serverName 获取配置
     * 
     * @param uuid 服务器UUID（可选），例如: f457f161-5735-46b6-b901-d2fa8e03cb21
     * @param serverName 服务名称（可选），例如: mcp-server-v6
     * @param version 版本号，例如: 1.0.1
     * @return 工具配置对象
     * 
     * 示例请求:
     * GET /api/mcp/config/tools?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21&version=1.0.1
     * GET /api/mcp/config/tools?serverName=mcp-server-v6&version=1.0.1
     */
    @GetMapping("/tools")
    public Mono<ResponseEntity<McpToolsConfig>> getToolsConfig(
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String serverName,
            @RequestParam(defaultValue = "1.0.0") String version) {
        String finalUuid = resolveUuid(uuid, serverName);
        if (finalUuid == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body((McpToolsConfig) null));
        }
        log.info("Getting tools config for uuid: {}, serverName: {}, version: {}", finalUuid, serverName, version);
        return mcpConfigService.getToolsConfig(finalUuid, version)
                .map(config -> {
                    if (config == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((McpToolsConfig) null);
                    }
                    return ResponseEntity.ok(config);
                })
                .onErrorResume(error -> {
                    log.error("Error getting tools config for uuid: {}, serverName: {}, version: {}", finalUuid, serverName, version, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((McpToolsConfig) null));
                });
    }

    /**
     * 获取版本配置 (mcp-versions.json)
     * 支持通过 uuid 或 serverName 获取配置
     * 
     * @param uuid 服务器UUID（可选），例如: f457f161-5735-46b6-b901-d2fa8e03cb21
     * @param serverName 服务名称（可选），例如: mcp-server-v6
     * @return 版本配置对象，包含所有版本信息
     * 
     * 示例请求:
     * GET /api/mcp/config/versions?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21
     * GET /api/mcp/config/versions?serverName=mcp-server-v6
     */
    @GetMapping("/versions")
    public Mono<ResponseEntity<McpVersionConfig>> getVersionConfig(
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String serverName) {
        String finalUuid = resolveUuid(uuid, serverName);
        if (finalUuid == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body((McpVersionConfig) null));
        }
        log.info("Getting version config for uuid: {}, serverName: {}", finalUuid, serverName);
        return mcpConfigService.getVersionConfig(finalUuid)
                .map(config -> {
                    if (config == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((McpVersionConfig) null);
                    }
                    return ResponseEntity.ok(config);
                })
                .onErrorResume(error -> {
                    log.error("Error getting version config for uuid: {}, serverName: {}", finalUuid, serverName, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((McpVersionConfig) null));
                });
    }

    /**
     * 获取所有配置（服务器配置、工具配置、版本配置）
     * 支持通过 uuid 或 serverName 获取配置
     * 
     * @param uuid 服务器UUID（可选）
     * @param serverName 服务名称（可选）
     * @param version 版本号
     * @return 包含所有配置的对象
     * 
     * 示例请求:
     * GET /api/mcp/config/all?uuid=f457f161-5735-46b6-b901-d2fa8e03cb21&version=1.0.1
     * GET /api/mcp/config/all?serverName=mcp-server-v6&version=1.0.1
     */
    @GetMapping("/all")
    public Mono<ResponseEntity<AllConfigResponse>> getAllConfigs(
            @RequestParam(required = false) String uuid,
            @RequestParam(required = false) String serverName,
            @RequestParam(defaultValue = "1.0.0") String version) {
        String finalUuid = resolveUuid(uuid, serverName);
        if (finalUuid == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AllConfigResponse()));
        }
        log.info("Getting all configs for uuid: {}, serverName: {}, version: {}", finalUuid, serverName, version);
        
        return Mono.zip(
                mcpConfigService.getServerConfig(finalUuid, version),
                mcpConfigService.getToolsConfig(finalUuid, version),
                mcpConfigService.getVersionConfig(finalUuid)
        ).map(tuple -> {
            AllConfigResponse response = new AllConfigResponse();
            response.setServerConfig(tuple.getT1());
            response.setToolsConfig(tuple.getT2());
            response.setVersionConfig(tuple.getT3());
            return ResponseEntity.ok(response);
        }).onErrorResume(error -> {
            log.error("Error getting all configs for uuid: {}, serverName: {}, version: {}", finalUuid, serverName, version, error);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AllConfigResponse()));
        });
    }

    /**
     * 解析UUID：优先使用传入的uuid，如果为空则从serverName推算
     * 
     * @param uuid 直接传入的UUID
     * @param serverName 服务名称
     * @return 解析后的UUID，如果两者都为空则返回null
     */
    private String resolveUuid(String uuid, String serverName) {
        // 如果直接提供了UUID，直接使用
        if (StringUtils.hasText(uuid)) {
            // 验证UUID格式
            try {
                java.util.UUID.fromString(uuid);
                return uuid;
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format: {}, will try to use serverName", uuid);
            }
        }
        
        // 如果提供了serverName，从serverName推算UUID
        if (StringUtils.hasText(serverName)) {
            try {
                return mcpConfigService.getUuidFromServiceName(serverName);
            } catch (Exception e) {
                log.error("Error generating UUID from serverName: {}", serverName, e);
                return null;
            }
        }
        
        // 两者都为空
        log.warn("Both uuid and serverName are empty");
        return null;
    }

    /**
     * UUID响应对象
     */
    @Data
    public static class UuidResponse {
        private String serverName;
        private String uuid;
    }

    /**
     * 所有配置的响应对象
     */
    @Data
    public static class AllConfigResponse {
        private McpServerConfig serverConfig;
        private McpToolsConfig toolsConfig;
        private McpVersionConfig versionConfig;
    }
}

