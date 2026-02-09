package com.nacos.mcp.server.v6.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Properties;

/**
 * MCP Server 配置清理器（启动前处理）
 * 
 * 使用 EnvironmentPostProcessor，在 Spring 容器初始化之前执行
 * 这样可以在 NacosMcpRegister 创建之前清理旧配置
 * 
 * 注意：需要在 META-INF/spring.factories 中注册此类
 * 
 * @author MCP Router Team
 */
@Slf4j
public class McpServerPreStartupCleaner implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        // 在配置文件加载之后执行，确保能读取到 application.yml 中的配置
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 检查是否启用清理
        String cleanOnStartup = environment.getProperty("mcp.server.config.clean-on-startup", "false");
        if (!"true".equalsIgnoreCase(cleanOnStartup)) {
            log.info("⏭️ MCP config cleanup disabled (mcp.server.config.clean-on-startup={})", cleanOnStartup);
            return;
        }

        System.out.println("🔥🔥🔥 McpServerPreStartupCleaner is running! Clean config enabled: " + cleanOnStartup);
        log.info("🧹 Starting MCP server config cleanup (pre-startup)...");

        // 获取配置
        String serverName = environment.getProperty("spring.ai.mcp.server.name", 
            environment.getProperty("spring.application.name", "mcp-server-v6"));
        String serverVersion = environment.getProperty("spring.ai.mcp.server.version", "1.0.1");
        String nacosServerAddr = environment.getProperty("spring.ai.alibaba.mcp.nacos.server-addr", "127.0.0.1:8848");
        String namespace = environment.getProperty("spring.ai.alibaba.mcp.nacos.namespace", "public");
        String username = environment.getProperty("spring.ai.alibaba.mcp.nacos.username", "nacos");
        String password = environment.getProperty("spring.ai.alibaba.mcp.nacos.password", "nacos");

        log.info("📋 Server: {}, Version: {}", serverName, serverVersion);
        log.info("📡 Nacos: {}, Namespace: {}", nacosServerAddr, namespace);

        try {
            // 使用 Nacos Config API 删除相关配置
            Properties props = new Properties();
            props.put("serverAddr", nacosServerAddr);
            props.put("namespace", namespace);
            props.put("username", username);
            props.put("password", password);

            ConfigService configService = NacosFactory.createConfigService(props);

            // 尝试删除可能存在的配置文件
            // 配置文件命名格式参考 nacos-register.md
            String[] dataIds = {
                serverName + "-" + serverVersion + "-mcp-tools.json",
                serverName + "-" + serverVersion + "-mcp-server.json",
                serverName + "-mcp-versions.json",
                serverName + "::" + serverVersion  // 另一种可能的格式
            };

            String[] groups = {"mcp-tools", "mcp-server", "mcp-server-versions"};

            boolean anyDeleted = false;
            for (String group : groups) {
                for (String dataId : dataIds) {
                    try {
                        // 先检查配置是否存在
                        String config = configService.getConfig(dataId, group, 3000);
                        if (config != null && !config.isEmpty()) {
                            // 删除配置
                            boolean removed = configService.removeConfig(dataId, group);
                            if (removed) {
                                log.info("✅ Removed config: dataId={}, group={}", dataId, group);
                                anyDeleted = true;
                            }
                        }
                    } catch (NacosException e) {
                        // 配置不存在，忽略
                        if (e.getErrCode() != 404) {
                            log.debug("Skip config: dataId={}, group={}, reason={}", dataId, group, e.getMessage());
                        }
                    }
                }
            }

            if (anyDeleted) {
                log.info("🎉 Config cleanup completed, service will create new config on startup");
                // 等待 Nacos 处理删除操作
                Thread.sleep(500);
            } else {
                log.info("ℹ️ No config found to clean up");
            }

        } catch (Exception e) {
            log.warn("⚠️ Failed to cleanup config: {}. Will try to start anyway.", e.getMessage());
            // 不抛出异常，让应用继续启动
        }
    }
}
