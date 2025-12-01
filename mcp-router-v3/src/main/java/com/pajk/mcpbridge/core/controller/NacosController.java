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
import jakarta.annotation.PostConstruct;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private WebClient webClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Value("${spring.ai.alibaba.mcp.nacos.server-addr:127.0.0.1:8848}")
    private String nacosServerAddr;
    
    @Value("${spring.ai.alibaba.mcp.nacos.namespace:public}")
    private String namespace;
    
    @Value("${spring.ai.alibaba.mcp.nacos.username:nacos}")
    private String nacosUsername;
    
    @Value("${spring.ai.alibaba.mcp.nacos.password:nacos}")
    private String nacosPassword;
    
    // Nacos API 超时配置（秒）
    @Value("${spring.ai.alibaba.mcp.nacos.api-timeout:10}")
    private int nacosApiTimeout;
    
    // Nacos API 连接超时配置（秒）
    @Value("${spring.ai.alibaba.mcp.nacos.api-connect-timeout:5}")
    private int nacosApiConnectTimeout;
    
    /**
     * 初始化带超时配置的 WebClient
     * 使用 @PostConstruct 确保 @Value 字段已经注入
     */
    @PostConstruct
    public void initWebClient() {
        // 配置 WebClient 超时设置
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(nacosApiTimeout))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nacosApiConnectTimeout * 1000);
        
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        
        log.info("Nacos WebClient initialized with timeout: {}s, connectTimeout: {}s", 
                nacosApiTimeout, nacosApiConnectTimeout);
    }

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
            
            // 按组分类统计（过滤掉 group 为 null 的配置）
            Map<String, Long> groupCounts = allConfigs.stream()
                    .filter(config -> config.getGroup() != null)
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
     * 登录 Nacos 并获取访问令牌
     * 
     * @return 访问令牌，如果登录失败则返回 null
     */
    private String loginNacos() {
        try {
            String loginUrl = String.format("http://%s/nacos/v1/auth/login", nacosServerAddr);
            log.info("Logging in to Nacos: {} (username={})", loginUrl, nacosUsername);
            
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return webClient.post()
                            .uri(loginUrl)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .body(BodyInserters.fromFormData("username", nacosUsername)
                                    .with("password", nacosPassword))
                            .retrieve()
                            .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                                log.error("Nacos login returned error status: {}", clientResponse.statusCode());
                                return Mono.error(new RuntimeException("Nacos login error: " + clientResponse.statusCode()));
                            })
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(nacosApiTimeout))
                            .block();
                } catch (Exception e) {
                    log.error("Error in Nacos login WebClient call: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, executorService);
            
            String loginResponse;
            try {
                loginResponse = future.get(nacosApiTimeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Nacos login timeout after {} seconds", nacosApiTimeout, e);
                return null;
            }
            log.info("Nacos login response received, length: {}", loginResponse != null ? loginResponse.length() : 0);
            
            if (loginResponse != null && !loginResponse.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(loginResponse, Map.class);
                    String accessToken = (String) responseMap.get("accessToken");
                    if (accessToken != null && !accessToken.isEmpty()) {
                        log.info("Nacos login successful, token length: {}", accessToken.length());
                        return accessToken;
                    } else {
                        log.warn("Nacos login response does not contain accessToken. Response: {}", loginResponse);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse Nacos login response: {} - {}", loginResponse, e.getMessage(), e);
                }
            } else {
                log.warn("Nacos login returned null or empty response");
            }
        } catch (Exception e) {
            log.error("Error logging in to Nacos: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 通过 Nacos Open API 根据 appName 查询配置列表
     */
    private List<ConfigInfo> getConfigsByAppName(String appName) {
        List<ConfigInfo> configs = new ArrayList<>();
        try {
            // 先登录获取 token
            String accessToken = loginNacos();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("Failed to get Nacos access token, cannot query configs");
                return configs;
            }
            
            // 使用 Nacos Open API 查询配置列表（包含 appName 信息）
            String url = String.format("http://%s/nacos/v3/admin/cs/config/list?pageNo=1&pageSize=100&namespaceId=%s&appName=%s",
                    nacosServerAddr, namespace, appName);
            
            log.info("Querying configs by appName from: {} (nacosServerAddr={}, namespace={})", url, nacosServerAddr, namespace);
            
            String response = null;
            try {
                // 使用 CompletableFuture 在独立线程中执行阻塞操作
                final String token = accessToken; // 需要在 lambda 中使用 final 变量
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return webClient.get()
                                .uri(url)
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                                    log.error("Nacos API returned error status: {}", clientResponse.statusCode());
                                    return Mono.error(new RuntimeException("Nacos API error: " + clientResponse.statusCode()));
                                })
                                .bodyToMono(String.class)
                                .timeout(Duration.ofSeconds(nacosApiTimeout))
                                .block();
                    } catch (Exception e) {
                        log.error("Error in WebClient call: {}", e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }, executorService);
                
                try {
                    response = future.get(nacosApiTimeout, TimeUnit.SECONDS); // 设置超时，避免长时间阻塞
                } catch (TimeoutException e) {
                    log.error("Nacos API call timeout after {} seconds: {}", nacosApiTimeout, url, e);
                    return configs;
                }
                
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
                        // 新接口返回的 data 是一个对象，包含 pageItems 数组
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dataObj = (Map<String, Object>) responseMap.get("data");
                        List<Map<String, Object>> dataList = null;
                        
                        if (dataObj != null && dataObj.containsKey("pageItems")) {
                            // 新接口格式：data.pageItems
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> pageItems = (List<Map<String, Object>>) dataObj.get("pageItems");
                            dataList = pageItems;
                            log.info("Using new API format: pageItems size={}, totalCount={}, pageNumber={}", 
                                    dataList != null ? dataList.size() : 0,
                                    dataObj.get("totalCount"),
                                    dataObj.get("pageNumber"));
                        } else if (dataObj instanceof List) {
                            // 兼容旧接口格式：data 直接是数组
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> legacyList = (List<Map<String, Object>>) dataObj;
                            dataList = legacyList;
                            log.info("Using legacy API format: data list size={}", dataList != null ? dataList.size() : 0);
                        }
                        
                        log.info("Data list size: {}", dataList != null ? dataList.size() : 0);
                        
                        if (dataList != null && !dataList.isEmpty()) {
                            // 用于存储所有符合条件的配置项（包含 modifyTime）
                            List<ConfigItemWithTime> allConfigItems = new ArrayList<>();
                            
                            // 第一步：收集所有符合条件的配置项
                            for (Map<String, Object> item : dataList) {
                                String dataId = (String) item.get("dataId");
                                // Nacos API 返回的字段是 groupName，不是 group
                                String configGroup = (String) item.get("groupName");
                                if (configGroup == null) {
                                    configGroup = (String) item.get("group"); // 兼容处理
                                }
                                String itemAppName = (String) item.get("appName");
                                
                                // 过滤：只处理 appName 匹配的配置
                                if (itemAppName == null || !itemAppName.equals(appName)) {
                                    log.debug("Skipping config with appName={}, expected={}", itemAppName, appName);
                                    continue;
                                }
                                
                                // 只处理 MCP 相关的配置
                                if (dataId != null && (dataId.contains("-mcp-server.json") || 
                                        dataId.contains("-mcp-tools.json") || 
                                        dataId.contains("-mcp-versions.json"))) {
                                    
                                    // 提取 modifyTime
                                    Long modifyTime = null;
                                    Object modifyTimeObj = item.get("modifyTime");
                                    if (modifyTimeObj != null) {
                                        if (modifyTimeObj instanceof Number) {
                                            modifyTime = ((Number) modifyTimeObj).longValue();
                                        } else if (modifyTimeObj instanceof String) {
                                            try {
                                                modifyTime = Long.parseLong((String) modifyTimeObj);
                                            } catch (NumberFormatException e) {
                                                log.debug("Failed to parse modifyTime: {}", modifyTimeObj);
                                            }
                                        }
                                    }
                                    
                                    // 从 dataId 中提取 UUID 和版本
                                    String uuid = null;
                                    String version = null;
                                    String configType = null;
                                    
                                    if (dataId.contains("-mcp-versions.json")) {
                                        uuid = dataId.replace("-mcp-versions.json", "");
                                        configType = "versions";
                                    } else if (dataId.contains("-mcp-server.json")) {
                                        String withoutSuffix = dataId.replace("-mcp-server.json", "");
                                        String[] parts = withoutSuffix.split("-");
                                        if (parts.length >= 5) {
                                            uuid = String.join("-", Arrays.copyOf(parts, 5));
                                            version = parts.length > 5 ? parts[5] : null;
                                            configType = "server";
                                        }
                                    } else if (dataId.contains("-mcp-tools.json")) {
                                        String withoutSuffix = dataId.replace("-mcp-tools.json", "");
                                        String[] parts = withoutSuffix.split("-");
                                        if (parts.length >= 5) {
                                            uuid = String.join("-", Arrays.copyOf(parts, 5));
                                            version = parts.length > 5 ? parts[5] : null;
                                            configType = "tools";
                                        }
                                    }
                                    
                                    if (uuid != null && configType != null) {
                                        ConfigItemWithTime configItem = new ConfigItemWithTime();
                                        configItem.dataId = dataId;
                                        configItem.group = configGroup;
                                        configItem.uuid = uuid;
                                        configItem.version = version;
                                        configItem.configType = configType;
                                        configItem.modifyTime = modifyTime != null ? modifyTime : 0L;
                                        configItem.serviceName = appName; // 直接使用 appName 作为 serviceName
                                        allConfigItems.add(configItem);
                                        
                                        log.debug("Collected config: dataId={}, type={}, uuid={}, version={}, modifyTime={}", 
                                                dataId, configType, uuid, version, modifyTime);
                                    }
                                }
                            }
                            
                            // 第二步：按类型分组，取 modifyTime 最大的
                            Map<String, ConfigItemWithTime> latestByType = new HashMap<>();
                            for (ConfigItemWithTime item : allConfigItems) {
                                String key = item.configType; // "versions", "server", "tools"
                                ConfigItemWithTime existing = latestByType.get(key);
                                if (existing == null || item.modifyTime > existing.modifyTime) {
                                    latestByType.put(key, item);
                                }
                            }
                            
                            // 第三步：验证 UUID 一致性
                            String commonUuid = null;
                            boolean uuidConsistent = true;
                            for (ConfigItemWithTime item : latestByType.values()) {
                                if (commonUuid == null) {
                                    commonUuid = item.uuid;
                                } else if (!commonUuid.equals(item.uuid)) {
                                    uuidConsistent = false;
                                    log.warn("UUID mismatch detected: {} vs {} for config type {}", 
                                            commonUuid, item.uuid, item.configType);
                                    break;
                                }
                            }
                            
                            // 第四步：如果 UUID 一致，添加到结果列表
                            if (uuidConsistent && commonUuid != null) {
                                for (ConfigItemWithTime item : latestByType.values()) {
                                    ConfigInfo configInfo = new ConfigInfo();
                                    configInfo.setDataId(item.dataId);
                                    configInfo.setGroup(item.group);
                                    configInfo.setUuid(item.uuid);
                                    configInfo.setVersion(item.version);
                                    configInfo.setServiceName(item.serviceName);
                                    configs.add(configInfo);
                                    log.info("Added config: dataId={}, group={}, uuid={}, version={}, serviceName={}, modifyTime={}", 
                                            item.dataId, item.group, item.uuid, item.version, item.serviceName, item.modifyTime);
                                }
                            } else {
                                log.warn("UUIDs are not consistent or no valid configs found for appName: {}", appName);
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
    
    /**
     * 带时间戳的配置项（内部使用）
     */
    private static class ConfigItemWithTime {
        String dataId;
        String group;
        String uuid;
        String version;
        String configType; // "versions", "server", "tools"
        long modifyTime;
        String serviceName;
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

