package com.nacos.mcp.server.v3.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Nacos注册配置
 * 将MCP Server注册到Nacos服务发现
 */
@Component
public class NacosRegistrationConfig {

    private static final Logger logger = LoggerFactory.getLogger(NacosRegistrationConfig.class);

    @Value("${server.port:8062}")
    private int serverPort;

    @Value("${spring.application.name:mcp-server-v3}")
    private String applicationName;

    private final NamingService namingService;

    public NacosRegistrationConfig(NamingService namingService) {
        this.namingService = namingService;
    }

    /**
     * 应用启动完成后注册到Nacos
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerToNacos() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String serviceName = applicationName + "-mcp-service";

            Instance instance = new Instance();
            instance.setIp(localIp);
            instance.setPort(serverPort);
            instance.setHealthy(true);
            instance.setEnabled(true);

            // 设置元数据
            Map<String, String> metadata = new HashMap<>();
            metadata.put("scheme", "http");
            metadata.put("mcp.version", "1.0.0");
            metadata.put("mcp.type", "server");
            metadata.put("tools.names", "getAllPersons,addPerson,deletePerson,get_system_info,list_servers");
            metadata.put("server.md5", generateServerMd5());
            instance.setMetadata(metadata);

            // 注册实例
            namingService.registerInstance(serviceName, "mcp-server", instance);

            logger.info("Successfully registered MCP Server to Nacos: {}:{} with service name: {}",
                       localIp, serverPort, serviceName);

        } catch (Exception e) {
            logger.error("Failed to register MCP Server to Nacos", e);
        }
    }

    /**
     * 生成服务器MD5标识
     */
    private String generateServerMd5() {
        String serverInfo = applicationName + ":" + serverPort + ":" + System.currentTimeMillis();
        return Integer.toHexString(serverInfo.hashCode());
    }
}