package com.nacos.mcp.router.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Router 配置类
 * 负责初始化MCP客户端连接和相关配置
 */
@Configuration
public class McpRouterConfig {

    private static final Logger logger = LoggerFactory.getLogger(McpRouterConfig.class);

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${mcp.router.request-timeout:PT30S}")
    private Duration requestTimeout;

    private final Map<String, McpAsyncClient> clients = new ConcurrentHashMap<>();
    private final NamingService namingService;
    private final ObjectMapper objectMapper;

    public McpRouterConfig(NamingService namingService, ObjectMapper objectMapper) {
        this.namingService = namingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建MCP客户端映射，用于连接到各个MCP服务器
     * 使用异步初始化，避免启动时阻塞
     */
    @Bean
    public Map<String, McpAsyncClient> mcpClients() {
        logger.info("Initializing MCP Router clients with service discovery...");
        
        // 异步初始化客户端连接
        CompletableFuture.runAsync(() -> {
            try {
                // 等待一段时间让服务器启动
                Thread.sleep(5000);
                
                // 发现所有MCP服务器
                List<String> serviceNames = List.of(
                    "mcp-server-v1-mcp-service",
                    "mcp-server-v2-mcp-service", 
                    "mcp-server-v3-mcp-service"
                );
                
                for (String serviceName : serviceNames) {
                    try {
                        // 订阅服务变化
                        subscribeToServiceChanges(serviceName);
                        
                        // 初始化客户端
                        List<Instance> instances = namingService.selectInstances(serviceName, "mcp-server", true);
                        
                        if (!instances.isEmpty()) {
                            Instance instance = instances.get(0); // 选择第一个健康实例
                            McpAsyncClient client = createMcpClient(instance, serviceName);
                            if (client != null) {
                                clients.put(serviceName, client);
                                logger.info("MCP Router connected to server: {}", serviceName);
                            }
                        } else {
                            logger.warn("No healthy instances found for MCP server: {}", serviceName);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to connect to MCP server: {}", serviceName, e);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Failed to initialize MCP server connections", e);
            }
        });
        
        return clients;
    }

    /**
     * 订阅 Nacos 服务变化
     */
    private void subscribeToServiceChanges(String serviceName) {
        try {
            namingService.subscribe(serviceName, "mcp-server", new EventListener() {
                @Override
                public void onEvent(Event event) {
                    if (event instanceof NamingEvent namingEvent) {
                        logger.info("Received service instance change event for service: {}", serviceName);
                        List<Instance> instances = namingEvent.getInstances();
                        logger.info("Updated instances count: {}", instances.size());
                        
                        // 打印每个实例的详细信息
                        instances.forEach(instance -> {
                            logger.info("Instance: {}:{} (Healthy: {}, Enabled: {}, Metadata: {})", 
                                    instance.getIp(), instance.getPort(), instance.isHealthy(), 
                                    instance.isEnabled(), instance.getMetadata());
                        });
                        
                        // 更新客户端连接
                        updateClientConnection(serviceName, instances);
                    }
                }
            });
            logger.info("Successfully subscribed to service changes for: {}", serviceName);
        } catch (Exception e) {
            logger.error("Failed to subscribe to service changes for: {}", serviceName, e);
        }
    }

    /**
     * 更新客户端连接
     */
    private void updateClientConnection(String serviceName, List<Instance> instances) {
        try {
            // 关闭旧的客户端连接
            McpAsyncClient oldClient = clients.get(serviceName);
            if (oldClient != null) {
                try {
                    oldClient.close();
                    logger.info("Closed old MCP client for service: {}", serviceName);
                } catch (Exception e) {
                    logger.warn("Failed to close old MCP client for service: {}", serviceName, e);
                }
            }
            
            // 创建新的客户端连接
            if (!instances.isEmpty()) {
                Instance instance = instances.get(0); // 选择第一个健康实例
                McpAsyncClient newClient = createMcpClient(instance, serviceName);
                if (newClient != null) {
                    clients.put(serviceName, newClient);
                    logger.info("Successfully updated MCP client for service: {}", serviceName);
                } else {
                    clients.remove(serviceName);
                    logger.warn("Failed to create new MCP client for service: {}", serviceName);
                }
            } else {
                clients.remove(serviceName);
                logger.warn("No healthy instances available for service: {}", serviceName);
            }
        } catch (Exception e) {
            logger.error("Failed to update client connection for service: {}", serviceName, e);
        }
    }

    /**
     * 创建MCP客户端连接
     */
    private McpAsyncClient createMcpClient(Instance instance, String serviceName) {
        String baseUrl = instance.getMetadata().getOrDefault("scheme", "http") + "://" + 
                        instance.getIp() + ":" + instance.getPort();
        
        logger.info("Creating MCP client connection to: {}", baseUrl);
        
        try {
            // 创建WebClient Builder
            WebClient.Builder webClientBuilder = WebClient.builder().baseUrl(baseUrl);
            
            // 创建SSE传输
            WebFluxSseClientTransport transport = new WebFluxSseClientTransport(webClientBuilder, objectMapper);
            
            // 创建客户端信息
            McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                    applicationName + "-" + serviceName, 
                    "1.0.0"
            );
            
            // 创建异步客户端
            McpClient.AsyncSpec asyncSpec = McpClient.async(transport)
                    .clientInfo(clientInfo)
                    .requestTimeout(requestTimeout);
            
            McpAsyncClient client = asyncSpec.build();
            
            // 初始化客户端（异步）
            try {
                if (requestTimeout != null) {
                    client.initialize().block(requestTimeout);
                } else {
                    client.initialize().block(Duration.ofSeconds(30));
                }
                logger.info("Successfully initialized MCP client for {}", serviceName);
                return client;
            } catch (Exception e) {
                logger.warn("Failed to initialize MCP client for {} (will retry later): {}", serviceName, e.getMessage());
                return client; // 返回未初始化的客户端，稍后可能会成功
            }
        } catch (Exception e) {
            logger.error("Failed to create MCP client for service: {}", serviceName, e);
            return null;
        }
    }
} 