package com.nacos.mcp.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * MCP Server自动注册到Router的配置
 */
@Configuration
@EnableConfigurationProperties(McpRouterRegistrationConfig.McpServerProperties.class)
@EnableScheduling
@Slf4j
@EnableRetry
public class McpRouterRegistrationConfig {

    @Service
    @RequiredArgsConstructor
    public static class McpServerRegistrationService implements SmartLifecycle {
        
        private final McpServerProperties properties;
        private final ApplicationContext applicationContext;
        private final RestTemplate restTemplate = new RestTemplate();
        private final ObjectMapper objectMapper = new ObjectMapper();
        private volatile boolean running = false;
        private volatile boolean registered = false;
        
        @Override
        public void start() {
            log.info("Application context refreshed. Starting MCP Server registration process.");
            if (properties.getRouter().isAutoRegister()) {
                applicationContext.getBean(McpServerRegistrationInvoker.class).initiateRegistration();
            }
            running = true;
        }

        @Override
        public void stop() {
            log.info("Application context is closing. Unregistering MCP Server.");
            unregisterFromRouter();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
        
        public void initiateRegistration() {
            log.info("Attempting to register service and tools with MCP Router: {}", properties.getName());
            try {
                List<Map<String, Object>> tools = discoverTools();
                registerServiceAndTools(tools);
                log.info("Successfully registered service and tools with MCP Router: {}", properties.getName());
                this.registered = true;
            } catch (Exception e) {
                log.warn("Registration attempt failed. Retrying...", e);
                throw e;
            }
        }
        
        /**
         * Registers the service and its tools to the MCP Router in a single call.
         */
        private void registerServiceAndTools(List<Map<String, Object>> tools) {
            String url = properties.getRouter().getUrl() 
                       + properties.getRouter().getRegistrationEndpoint()
                       + "mcp-" + properties.getName() 
                       + "/register-with-tools"; // Using a new endpoint for atomic registration
            
            Map<String, Object> registrationRequest = new HashMap<>();
            registrationRequest.put("serverName", "mcp-" + properties.getName());
            try {
                registrationRequest.put("ip", InetAddress.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
                throw new RuntimeException("Could not get local host address", e);
            }
            // Assuming the server port is configured in application properties
            String port = applicationContext.getEnvironment().getProperty("server.port");
            registrationRequest.put("port", Integer.valueOf(port != null ? port : "8081"));
            registrationRequest.put("version", properties.getVersion());
            registrationRequest.put("description", properties.getDescription());
            registrationRequest.put("transportType", properties.getTransportType());
            registrationRequest.put("baseUrl", properties.getEndpoints().getBaseUrl());
            registrationRequest.put("mcpEndpoint", properties.getEndpoints().getMcpEndpoint());
            registrationRequest.put("healthEndpoint", properties.getEndpoints().getHealthEndpoint());
            registrationRequest.put("capabilities", properties.getCapabilities());
            registrationRequest.put("tools", tools); // Embed tools in the registration request

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(registrationRequest, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("服务和工具注册失败，状态码: " + response.getStatusCode());
            }
            log.info("成功注册 {} 个工具到MCP Router", tools.size());
            tools.forEach(tool -> log.info("已注册工具: {}", tool.get("name")));
        }
        
        @Scheduled(fixedDelayString = "${mcp.server.router.heartbeat-interval:30000}")
        public void sendHeartbeat() {
            if (properties.getRouter().isAutoRegister() && registered) {
                try {
                    sendHeartbeatToRouter();
                    log.debug("发送心跳到MCP Router成功: {}", properties.getName());
                } catch (Exception e) {
                    log.warn("发送心跳到MCP Router失败: {}", properties.getName(), e);
                }
            }
        }
        
        @PreDestroy
        public void unregisterFromRouter() {
            if (properties.getRouter().isAutoRegister()) {
                log.info("从MCP Router注销服务: {}", properties.getName());
                try {
                    unregisterService();
                    log.info("成功从MCP Router注销服务: {}", properties.getName());
                } catch (Exception e) {
                    log.error("从MCP Router注销服务失败: {}", properties.getName(), e);
                }
            }
        }
        
        /**
         * 自动发现项目中所有被@Tool注解标记的工具方法
         */
        private List<Map<String, Object>> discoverTools() {
            List<Map<String, Object>> tools = new ArrayList<>();
            log.info("开始扫描项目中的 @Tool 注解方法...");

            String[] beanNames = applicationContext.getBeanDefinitionNames();
            
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);
                Class<?> targetClass = AopUtils.getTargetClass(bean);

                // 过滤掉框架和配置类相关的bean
                if (isFrameworkOrConfigBean(targetClass)) {
                    continue;
                }

//                log.debug("正在扫描Bean: {}", targetClass.getName());
                for (Method method : targetClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        
                        if (!isValidToolMethod(method)) {
                            continue;
                        }

                        Tool toolAnnotation = method.getAnnotation(Tool.class);
                        
                        Map<String, Object> tool = new HashMap<>();
                        
                        String toolName = toolAnnotation.name();
                        if (toolName == null || toolName.trim().isEmpty()) {
                            toolName = method.getName();
                        }
                        
                        tool.put("name", toolName);
                        tool.put("description", toolAnnotation.description());
                        tool.put("serverName", properties.getName());
                        tool.put("category", "general-tools");
                        tool.put("version", "1.0.0");
                        tool.put("status", "ACTIVE");
                        
                        // 分析方法参数
                        List<Map<String, Object>> parameters = new ArrayList<>();
                        Class<?>[] paramTypes = method.getParameterTypes();
                        java.lang.reflect.Parameter[] methodParameters = method.getParameters();

                        for (int i = 0; i < paramTypes.length; i++) {
                            Map<String, Object> param = new HashMap<>();
                            param.put("name", methodParameters[i].getName());
                            param.put("type", paramTypes[i].getSimpleName());
                            param.put("required", true); 
                            param.put("description", "Parameter " + methodParameters[i].getName() + " of type " + paramTypes[i].getSimpleName());
                            parameters.add(param);
                        }
                        
                        tool.put("parameters", parameters);
                        tool.put("returnType", method.getReturnType().getSimpleName());
                        
                        tools.add(tool);
                        log.debug("发现并注册工具方法: {} 来自于Bean: {}", toolName, beanName);
                    }
                }
            }
            
            log.info("工具扫描完成，共发现 {} 个工具。", tools.size());
            return tools;
        }
        
        /**
         * 检查是否是框架或配置类相关的Bean
         */
        private boolean isFrameworkOrConfigBean(Class<?> beanClass) {
            String className = beanClass.getName();
            return className.startsWith("org.springframework.") ||
                   className.startsWith("com.nacos.mcp.server.config.") ||
                   className.contains("$$") || // 排除代理类
                   beanClass.isAnnotationPresent(Configuration.class);
        }
        
        /**
         * 检查方法是否是有效的工具方法
         */
        private boolean isValidToolMethod(Method method) {
            // 排除Object类的方法
            if (method.getDeclaringClass() == Object.class) {
                return false;
            }
            
            // 排除私有方法
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                return false;
            }
            
            // 排除静态方法
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                return false;
            }
            
            // 排除Lombok生成的方法
            String methodName = method.getName();
            if (methodName.startsWith("get") || methodName.startsWith("set") || 
                methodName.equals("equals") || methodName.equals("hashCode") || 
                methodName.equals("toString") || methodName.equals("canEqual")) {
                return false;
            }
            
            return true;
        }
        
        /**
         * 生成方法描述
         */
        private String generateDescription(Method method) {
            // This method is now a fallback and can be simplified or removed.
            // For now, let's keep a generic description.
            return "A tool for: " + method.getName();
        }
        
        /**
         * 获取方法参数名称（简化版本）
         */
        private String[] getParameterNames(Method method) {
            Class<?>[] paramTypes = method.getParameterTypes();
            String[] paramNames = new String[paramTypes.length];
            
            // 为常见的参数类型提供默认名称
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (type == Long.class || type == long.class) {
                    paramNames[i] = "id";
                } else if (type == String.class) {
                    if (method.getName().contains("Nationality")) {
                        paramNames[i] = "nationality";
                    } else if (method.getName().contains("Name")) {
                        paramNames[i] = "name";
                    } else {
                        paramNames[i] = "text";
                    }
                } else if (type == Integer.class || type == int.class) {
                    paramNames[i] = "age";
                } else {
                    paramNames[i] = "param" + i;
                }
            }
            
            return paramNames;
        }
        
        /**
         * 生成参数描述
         */
        private String generateParameterDescription(String paramName, Class<?> paramType) {
            Map<String, String> descriptions = Map.of(
                "id", "人员的唯一标识符",
                "nationality", "人员的国籍信息",
                "name", "人员姓名",
                "age", "人员年龄",
                "firstName", "名字",
                "lastName", "姓氏"
            );
            
            return descriptions.getOrDefault(paramName, paramType.getSimpleName() + "类型的参数");
        }
        
        /**
         * 向MCP Router发送心跳
         */
        private void sendHeartbeatToRouter() {
            String url = UriComponentsBuilder.fromHttpUrl(properties.getRouter().getUrl())
                    .path(properties.getRouter().getHeartbeatEndpoint())
                    .buildAndExpand(properties.getName())
                    .toUriString();

            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("发送心跳失败，状态码: " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("发送心跳到MCP Router失败: {}", properties.getName(), e);
                throw e; // Re-throw the exception
            }
        }
        
        /**
         * 从MCP Router注销服务
         */
        private void unregisterService() {
            String url = properties.getRouter().getUrl() + "/api/mcp/servers/" + properties.getName() + "/unregister";
            
            try {
                restTemplate.delete(url);
            } catch (Exception e) {
                log.error("注销服务失败", e);
            }
        }
    }

    @ConfigurationProperties(prefix = "spring.ai.mcp.server")
    @Data
    public static class McpServerProperties {
        private String id;
        private String name;
        private String version;
        private String description;
        private String transportType;
        
        private Router router = new Router();
        private Endpoints endpoints = new Endpoints();
        private Map<String, Object> capabilities = new HashMap<>();
        
        @Data
        public static class Router {
            private String url;
            private String registrationEndpoint;
            private String heartbeatEndpoint;
            private boolean autoRegister = false;
            private long heartbeatInterval = 30000;
        }
        
        @Data
        public static class Endpoints {
            private String baseUrl;
            private String mcpEndpoint;
            private String healthEndpoint;
        }
    }
} 