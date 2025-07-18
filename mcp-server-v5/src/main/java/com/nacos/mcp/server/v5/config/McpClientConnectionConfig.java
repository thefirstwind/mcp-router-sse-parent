package com.nacos.mcp.server.v5.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 客户端连接配置
 * MCP Server 启动后主动连接到 mcp-router-v3，并将连接状态注册到 Nacos
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientConnectionConfig {

    private final NamingService namingService;
    private final WebClient.Builder webClientBuilder;

    @Value("${server.port:8063}")
    private int serverPort;

    @Value("${spring.application.name:mcp-server-v3}")
    private String applicationName;

    @Value("${spring.ai.alibaba.mcp.nacos.registry.service-group:mcp-server}")
    private String serviceGroup;

    @Value("${mcp.router.service-name:mcp-router-v3}")
    private String routerServiceName;

    @Value("${mcp.router.service-group:mcp-server}")
    private String routerServiceGroup;

    private String connectionId;
    private boolean connected = false;

    /**
     * 应用启动完成后主动连接到 mcp-router-v3
     */
    @EventListener(ApplicationReadyEvent.class)
    public void connectToMcpRouter() {
        log.info("🚀 MCP Server starting connection to mcp-router-v3...");
        
        // 生成连接ID
        connectionId = applicationName + "-" + System.currentTimeMillis();
        
        // 先注册连接状态到 Nacos
        registerConnectionStatus(true)
                .then(findAndConnectToRouter())
                .subscribe(
                    success -> {
                        if (success) {
                            log.info("✅ Successfully connected to mcp-router-v3 and registered connection status");
                            setupHeartbeat();
                        } else {
                            log.error("❌ Failed to connect to mcp-router-v3");
                            registerConnectionStatus(false).subscribe();
                        }
                    },
                    error -> {
                        log.error("❌ Error during connection process", error);
                        registerConnectionStatus(false).subscribe();
                    }
                );
    }

    /**
     * 通过 Nacos 服务发现找到 mcp-router-v3 并连接
     */
    private Mono<Boolean> findAndConnectToRouter() {
        return Mono.fromCallable(() -> {
            try {
                // 通过 Nacos 服务发现找到 mcp-router-v3 实例
                var instances = namingService.selectInstances(routerServiceName, routerServiceGroup, true);
                if (instances.isEmpty()) {
                    log.warn("⚠️ No healthy mcp-router-v3 instances found in Nacos");
                    return false;
                }

                // 选择第一个健康的路由器实例
                Instance routerInstance = instances.get(0);
                String routerUrl = "http://" + routerInstance.getIp() + ":" + routerInstance.getPort();
                
                log.info("🔗 Discovered mcp-router-v3 via Nacos service discovery: {}", routerUrl);
                log.info("📍 Router instance metadata: {}", routerInstance.getMetadata());
                
                // 建立 SSE 连接到路由器
                return establishSseConnection(routerUrl);
                
            } catch (Exception e) {
                log.error("Failed to discover and connect to mcp-router-v3", e);
                return false;
            }
        });
    }

    /**
     * 建立 SSE 连接到 mcp-router-v3
     */
    private boolean establishSseConnection(String routerUrl) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(routerUrl).build();
            
            // 构建连接信息
            Map<String, Object> connectionInfo = new HashMap<>();
            connectionInfo.put("serverId", connectionId);
            connectionInfo.put("serverName", applicationName);
            connectionInfo.put("serverPort", serverPort);
            connectionInfo.put("timestamp", System.currentTimeMillis());
            connectionInfo.put("capabilities", "tools,resources");
            
            // 发送连接请求
            String response = webClient.post()
                    .uri("/api/mcp/servers/connect")
                    .bodyValue(connectionInfo)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
                    
            connected = response != null && response.contains("success");
            log.info("📡 SSE connection established: {}, response: {}", connected, response);
            
            return connected;
            
        } catch (Exception e) {
            log.error("Failed to establish SSE connection", e);
            return false;
        }
    }

    /**
     * 注册连接状态到 Nacos
     */
    private Mono<Void> registerConnectionStatus(boolean isConnected) {
        return Mono.fromRunnable(() -> {
            try {
                String localIp = InetAddress.getLocalHost().getHostAddress();
                
                // 创建连接状态实例
                Instance connectionInstance = new Instance();
                connectionInstance.setIp(localIp);
                connectionInstance.setPort(serverPort);
                connectionInstance.setHealthy(isConnected);
                connectionInstance.setEnabled(isConnected);
                
                // 设置连接元数据
                Map<String, String> metadata = new HashMap<>();
                metadata.put("connectionId", connectionId);
                metadata.put("serverName", applicationName);
                metadata.put("connected", String.valueOf(isConnected));
                metadata.put("lastUpdate", String.valueOf(System.currentTimeMillis()));
                metadata.put("connectionType", "sse");
                metadata.put("routerServiceName", routerServiceName);
                
                connectionInstance.setMetadata(metadata);
                
                // 注册到特殊的连接状态服务
                String connectionServiceName = applicationName + "-connection";
                namingService.registerInstance(connectionServiceName, serviceGroup, connectionInstance);
                
                log.info("📝 Registered connection status to Nacos: {} -> {}", connectionServiceName, isConnected);
                
            } catch (Exception e) {
                log.error("Failed to register connection status to Nacos", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 设置心跳机制，定期更新连接状态
     */
    private void setupHeartbeat() {
        // 每30秒发送一次心跳
        reactor.core.publisher.Flux.interval(Duration.ofSeconds(30))
                .doOnNext(tick -> {
                    if (connected) {
                        // 更新连接状态
                        registerConnectionStatus(true).subscribe();
                        log.debug("💓 Heartbeat sent, connection status updated");
                    }
                })
                .subscribe();
    }

    /**
     * 应用关闭时断开连接
     */
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void disconnectFromRouter() {
        log.info("🛑 MCP Server shutting down, disconnecting from mcp-router-v3...");
        connected = false;
        
        // 注销连接状态
        registerConnectionStatus(false).subscribe();
        
        try {
            String connectionServiceName = applicationName + "-connection";
            namingService.deregisterInstance(connectionServiceName, serviceGroup, 
                    InetAddress.getLocalHost().getHostAddress(), serverPort);
            log.info("✅ Connection status deregistered from Nacos");
        } catch (Exception e) {
            log.error("Failed to deregister connection status", e);
        }
    }
} 