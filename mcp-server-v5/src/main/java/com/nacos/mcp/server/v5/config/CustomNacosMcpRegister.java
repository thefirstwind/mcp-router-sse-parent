package com.nacos.mcp.server.v5.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * 自定义 Nacos MCP 注册器
 * 确保 IP 地址正确设置
 */
@Slf4j
@Component
public class CustomNacosMcpRegister {

    @Value("${server.port:8065}")
    private int serverPort;

    /**
     * 应用启动完成后设置 IP 地址
     */
    @EventListener(ApplicationReadyEvent.class)
    public void setIpAddress() {
        try {
            // 获取本地IP地址
            String localIp = getLocalIpAddress();
            log.info("🔧 Custom NacosMcpRegister: Setting IP address to {}", localIp);

            // 设置系统属性
            System.setProperty("spring.cloud.client.ip-address", localIp);
            System.setProperty("spring.cloud.client.hostname", localIp);
            System.setProperty("server.address", localIp);
            System.setProperty("nacos.client.ip", localIp);
            System.setProperty("nacos.client.host", localIp);
            
            // 设置更多 Nacos 相关的系统属性
            System.setProperty("com.alibaba.nacos.client.naming.client.ip", localIp);
            System.setProperty("com.alibaba.nacos.client.naming.client.host", localIp);
            System.setProperty("com.alibaba.nacos.client.naming.client.port", String.valueOf(serverPort));

            // 设置 Spring AI Alibaba MCP Nacos 的 IP 配置
            System.setProperty("spring.ai.alibaba.mcp.nacos.ip", localIp);

            log.info("✅ Custom NacosMcpRegister: System properties set successfully");
            log.info("📡 Service URL will be: http://{}:{}/sse", localIp, serverPort);

        } catch (Exception e) {
            log.error("❌ Custom NacosMcpRegister: Failed to set IP address", e);
        }
    }

    /**
     * 获取本地IP地址，优先获取非回环地址
     */
    private String getLocalIpAddress() throws Exception {
        // 首先尝试获取非回环地址
        String nonLoopbackIp = getNonLoopbackIpAddress();
        if (nonLoopbackIp != null && !nonLoopbackIp.isEmpty()) {
            return nonLoopbackIp;
        }

        // 如果获取不到非回环地址，则使用localhost
        String localhostIp = InetAddress.getLocalHost().getHostAddress();
        if (localhostIp != null && !localhostIp.isEmpty()) {
            return localhostIp;
        }

        // 最后兜底使用127.0.0.1
        return "127.0.0.1";
    }

    /**
     * 获取非回环IP地址
     */
    private String getNonLoopbackIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    
                    // 只获取IPv4地址，跳过回环地址
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') == -1) {
                        String ip = inetAddress.getHostAddress();
                        log.info("Found non-loopback IP address: {} on interface: {}", ip, networkInterface.getDisplayName());
                        return ip;
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Failed to get network interfaces", e);
        }
        
        return null;
    }
} 