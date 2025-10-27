package com.nacos.mcp.server.v5.config;

import com.alibaba.cloud.ai.mcp.nacos.NacosMcpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * 自定义 Nacos MCP Properties 配置
 * 确保IP地址正确设置
 */
@Slf4j
@Component
@Primary
@ConfigurationProperties(prefix = "spring.ai.alibaba.mcp.nacos")
public class CustomNacosMcpProperties extends NacosMcpProperties {

    @PostConstruct
    @Override
    public void init() throws Exception {
        // 如果IP地址为空，则获取本地IP地址
        if (getIp() == null || getIp().isEmpty()) {
            String localIp = getLocalIpAddress();
            setIp(localIp);
            log.info("🔧 CustomNacosMcpProperties: Set IP address to {}", localIp);
        } else {
            log.info("🔧 CustomNacosMcpProperties: Using configured IP address: {}", getIp());
        }
        
        // 调用父类的初始化方法
        super.init();
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