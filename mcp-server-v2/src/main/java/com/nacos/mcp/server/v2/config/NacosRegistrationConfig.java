//package com.nacos.mcp.server.v2.config;
//
//import com.alibaba.nacos.api.NacosFactory;
//import com.alibaba.nacos.api.config.ConfigService;
//import com.alibaba.nacos.api.config.ConfigType;
//import com.alibaba.nacos.api.exception.NacosException;
//import com.alibaba.nacos.api.naming.NamingService;
//import com.alibaba.nacos.api.naming.pojo.Instance;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//import org.springframework.core.annotation.AnnotationUtils;
//import org.springframework.util.StringUtils;
//
//import java.lang.reflect.Method;
//import java.net.InetAddress;
//import java.security.MessageDigest;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Properties;
//import java.util.stream.Collectors;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Arrays;
//
///**
// * Nacos注册配置
// * 将MCP Server注册到Nacos服务发现和配置中心
// */
//@Component
//public class NacosRegistrationConfig {
//
//    private static final Logger logger = LoggerFactory.getLogger(NacosRegistrationConfig.class);
//
//    @Value("${server.port:8061}")
//    private int serverPort;
//
//    @Value("${spring.application.name:mcp-server-v2}")
//    private String applicationName;
//
//    @Value("${spring.ai.mcp.server.description:MCP Server V2 with Person Management Tools}")
//    private String serverDescription;
//
//    @Value("${spring.ai.mcp.server.version:1.0.0}")
//    private String serverVersion;
//
//    @Value("${spring.ai.mcp.server.sse-message-endpoint:/sse}")
//    private String sseEndpoint;
//
//    @Value("${spring.ai.mcp.server.nacos.namespace:public}")
//    private String namespace;
//
//    @Value("${spring.ai.mcp.server.nacos.group:mcp-server}")
//    private String group;
//
//    @Value("${spring.ai.mcp.server.nacos.server-addr:127.0.0.1:8848}")
//    private String serverAddr;
//
//    private final NamingService namingService;
//    private final ObjectMapper objectMapper;
//    private final ApplicationContext applicationContext;
//
//    public NacosRegistrationConfig(NamingService namingService, ObjectMapper objectMapper, ApplicationContext applicationContext) {
//        this.namingService = namingService;
//        this.objectMapper = objectMapper;
//        this.applicationContext = applicationContext;
//    }
//
//    /**
//     * 应用启动完成后注册到Nacos
//     */
//    @EventListener(ApplicationReadyEvent.class)
//    public void registerToNacos() {
//        try {
//            String localIp = InetAddress.getLocalHost().getHostAddress();
//            String serviceName = applicationName + "-mcp-service";
//
//            // 1. 创建和上传MCP Server配置文件
//            String serverConfigDataId = applicationName + "-mcp-server.json";
//            String serverConfigContent = createServerConfigContent(serviceName);
//            String serverMd5 = calculateMd5(serverConfigContent);
//
//            // 2. 创建和上传MCP Tools配置文件
//            String toolsConfigDataId = applicationName + "-mcp-tools.json";
//            String toolsConfigContent = createToolsConfigContent();
//
//            // 3. 上传配置到Nacos
//            uploadConfigToNacos(serverConfigDataId, "mcp-server", serverConfigContent);
//            uploadConfigToNacos(toolsConfigDataId, "mcp-tools", toolsConfigContent);
//
//            // 4. 注册服务实例
//            Instance instance = new Instance();
//            instance.setIp(localIp);
//            instance.setPort(serverPort);
//            instance.setHealthy(true);
//            instance.setEnabled(true);
//
//            // 设置元数据
//            Map<String, String> metadata = new HashMap<>();
//            metadata.put("scheme", "http");
//            metadata.put("mcp.version", serverVersion);
//            metadata.put("mcp.type", "server");
//            metadata.put("transport.type", "sse");  // 添加传输类型
//
//            // 动态获取所有@Tool注解的工具名称
//            String toolNames = findAllToolNames();
//            metadata.put("tools.names", toolNames);
//
//            metadata.put("server.md5", serverMd5);
//            instance.setMetadata(metadata);
//
//            // 注册实例
//            namingService.registerInstance(serviceName, group, instance);
//
//            logger.info("Successfully registered MCP Server to Nacos: {}:{} with service name: {}",
//                    localIp, serverPort, serviceName);
//            logger.info("Registered tools: {}", toolNames);
//
//        } catch (Exception e) {
//            logger.error("Failed to register MCP Server to Nacos", e);
//        }
//    }
//
//    /**
//     * 查找所有标注了@Tool注解的方法名称
//     */
//    private String findAllToolNames() {
//        logger.info("Scanning for @Tool annotated methods...");
//        List<String> toolNames = new ArrayList<>();
//
//        // 获取所有Spring管理的Bean
//        String[] beanNames = applicationContext.getBeanDefinitionNames();
//        for (String beanName : beanNames) {
//            Object bean = applicationContext.getBean(beanName);
//            Class<?> beanClass = bean.getClass();
//
//            // 检查该Bean的所有方法
//            for (Method method : beanClass.getDeclaredMethods()) {
//                Tool toolAnnotation = AnnotationUtils.findAnnotation(method, Tool.class);
//                if (toolAnnotation != null) {
//                    // 优先使用注解中指定的名称，如果没有则使用方法名
//                    String toolName = toolAnnotation.name();
//                    if (!StringUtils.hasText(toolName)) {
//                        toolName = method.getName();
//                    }
//                    toolNames.add(toolName);
//                    logger.info("Found @Tool method: {}", toolName);
//                }
//            }
//        }
//
//        // 将工具名称列表转换为逗号分隔的字符串
//        return String.join(",", toolNames);
//    }
//
//    /**
//     * 创建MCP Server配置内容
//     */
//    private String createServerConfigContent(String serviceName) throws Exception {
//        Map<String, Object> serverConfig = new HashMap<>();
//        serverConfig.put("protocol", "sse");
//        serverConfig.put("name", applicationName);
//        serverConfig.put("description", serverDescription);
//        serverConfig.put("version", serverVersion);
//        serverConfig.put("enabled", true);
//
//        Map<String, Object> remoteServerConfig = new HashMap<>();
//        Map<String, Object> serviceRef = new HashMap<>();
//        serviceRef.put("namespaceId", namespace);
//        serviceRef.put("groupName", group);
//        serviceRef.put("serviceName", serviceName);
//        remoteServerConfig.put("serviceRef", serviceRef);
//        remoteServerConfig.put("exportPath", sseEndpoint);
//
//        serverConfig.put("remoteServerConfig", remoteServerConfig);
//        serverConfig.put("toolsDescriptionRef", applicationName + "-mcp-tools.json");
//
//        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(serverConfig);
//    }
//
//    /**
//     * 创建MCP Tools配置内容
//     */
//    private String createToolsConfigContent() throws Exception {
//        Map<String, Object> toolsConfig = new HashMap<>();
//
//        // 工具定义
//        List<Map<String, Object>> toolsList = new ArrayList<>();
//        Map<String, Object> toolsMeta = new HashMap<>();
//
//        // 扫描所有@Tool注解的方法，动态创建工具定义
//        String[] beanNames = applicationContext.getBeanDefinitionNames();
//        for (String beanName : beanNames) {
//            Object bean = applicationContext.getBean(beanName);
//            Class<?> beanClass = bean.getClass();
//
//            for (Method method : beanClass.getDeclaredMethods()) {
//                Tool toolAnnotation = AnnotationUtils.findAnnotation(method, Tool.class);
//                if (toolAnnotation != null) {
//                    String toolName = toolAnnotation.name();
//                    if (!StringUtils.hasText(toolName)) {
//                        toolName = method.getName();
//                    }
//
//                    String description = toolAnnotation.description();
//
//                    // 创建工具定义
//                    Map<String, Object> toolDef = createToolDefinition(
//                            toolName,
//                            description,
//                            "object",
//                            new HashMap<>(),  // 简化处理，实际项目中可以解析@ToolParam注解
//                            new String[0]
//                    );
//
//                    toolsList.add(toolDef);
//
//                    // 添加工具元数据
//                    toolsMeta.put(toolName, Map.of("enabled", true));
//                }
//            }
//        }
//
//        toolsConfig.put("tools", toolsList);
//        toolsConfig.put("toolsMeta", toolsMeta);
//
//        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolsConfig);
//    }
//
//    /**
//     * 创建工具定义
//     */
//    private Map<String, Object> createToolDefinition(String name, String description, String type,
//                                                     Map<String, Object> properties, String[] required) {
//        Map<String, Object> tool = new HashMap<>();
//        tool.put("name", name);
//        tool.put("description", description);
//
//        Map<String, Object> inputSchema = new HashMap<>();
//        inputSchema.put("type", "object");
//        inputSchema.put("properties", properties);
//        inputSchema.put("required", required);
//        inputSchema.put("additionalProperties", false);
//
//        tool.put("inputSchema", inputSchema);
//        return tool;
//    }
//
//    /**
//     * 上传配置到Nacos
//     */
//    private void uploadConfigToNacos(String dataId, String group, String content) {
//        try {
//            Properties properties = new Properties();
//            properties.put("serverAddr", serverAddr);
//            properties.put("namespace", namespace);
//
//            ConfigService configService = NacosFactory.createConfigService(properties);
//            boolean result = configService.publishConfig(dataId, group, content, ConfigType.JSON.getType());
//
//            if (result) {
//                logger.info("Successfully published config to Nacos: {}, group: {}", dataId, group);
//            } else {
//                logger.warn("Failed to publish config to Nacos: {}, group: {}", dataId, group);
//            }
//        } catch (NacosException e) {
//            logger.error("Error publishing config to Nacos: {}", e.getMessage(), e);
//        }
//    }
//
//    /**
//     * 计算MD5
//     */
//    private String calculateMd5(String input) {
//        try {
//            MessageDigest md = MessageDigest.getInstance("MD5");
//            byte[] messageDigest = md.digest(input.getBytes());
//            StringBuilder hexString = new StringBuilder();
//
//            for (byte b : messageDigest) {
//                String hex = Integer.toHexString(0xff & b);
//                if (hex.length() == 1) {
//                    hexString.append('0');
//                }
//                hexString.append(hex);
//            }
//
//            return hexString.toString();
//        } catch (Exception e) {
//            logger.error("Failed to calculate MD5", e);
//            return "";
//        }
//    }
//}