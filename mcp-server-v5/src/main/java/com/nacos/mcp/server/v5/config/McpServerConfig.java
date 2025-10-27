package com.nacos.mcp.server.v5.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.server.v5.tools.PersonManagementTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

// 导入正确的类用于创建 SSE Transport Provider
import io.modelcontextprotocol.spec.McpServerTransportProvider;

/**
 * MCP Server配置类
 * 按照MCP标准协议实现SSE传输和路由配置
 */
@Slf4j
@Configuration
@EnableRetry
public class McpServerConfig {

    @Autowired
    private Environment environment;

    @Value("${server.port}")
    private String serverPort;

    /**
     * 获取服务器端口
     */
    private int getServerPort() {
        String port = environment.getProperty("server.port", serverPort);
        return Integer.parseInt(port);
    }

    /**
     * 获取服务器IP地址
     */
    private String getServerIp() {
        // 从环境变量或配置中获取IP，默认使用本地IP
        return environment.getProperty("server.address", "127.0.0.1");
    }

    /**
     * 创建工具回调提供者
     */
    @Bean
    public ToolCallbackProvider toolCallbackProvider(PersonManagementTool personManagementTool) {
        log.info("Registering PersonManagementTool as MCP Tool Provider");
        return MethodToolCallbackProvider.builder()
                .toolObjects(personManagementTool)
                .build();
    }

    /**
     * 创建ObjectMapper Bean
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * 创建 Spring Boot 2.7.18 兼容的 SSE Transport Provider
     * 这个 bean 的目的是让 Spring AI 识别为非 StdioServerTransportProvider 类型
     * 从而注册为 SSE 类型到 Nacos，实际的 SSE 处理由 WebFluxConfig 负责
     */
    @Bean
    @ConditionalOnMissingBean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        // 构建基础URL
        String baseUrl = "http://" + getServerIp() + ":" + getServerPort();
        log.info("🔧 创建 SpringBoot27WebFluxSseServerTransportProvider with baseUrl: {}", baseUrl);
        
        return new SpringBoot27WebFluxSseServerTransportProvider(
            objectMapper,
            baseUrl,
            "/mcp/message",
            "/sse"
        );
    }

    /**
     * 创建路由函数
     * 使用现有的 WebFluxConfig 中的路由
     */
    @Bean
    public RouterFunction<?> mcpRouterFunction() {
        // 构建基础URL
        String baseUrl = "http://" + getServerIp() + ":" + getServerPort();
        log.info("✅ MCP Router Function will be created by WebFluxConfig");
        log.info("📡 SSE endpoint: {}/sse", baseUrl);
        log.info("📨 Message endpoint: {}/mcp/message", baseUrl);

        // 返回 null，让 WebFluxConfig 处理路由
        return null;
    }
}