//package com.nacos.mcp.server.v2.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nacos.mcp.server.v2.tools.PersonManagementTool;
//import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
//import io.modelcontextprotocol.spec.McpServerTransportProvider;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.tool.ToolCallbackProvider;
//import org.springframework.ai.tool.method.MethodToolCallbackProvider;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//import org.springframework.core.env.Environment;
//import org.springframework.retry.annotation.EnableRetry;
//import org.springframework.web.reactive.function.server.RouterFunction;
//
///**
// * MCP Server配置类
// * 按照MCP标准协议实现SSE传输和路由配置
// */
//@Slf4j
//@Configuration
//@EnableRetry
//public class McpServerConfig {
//
//    @Autowired
//    private Environment environment;
//
//    /**
//     * 获取服务器端口
//     */
//    private int getServerPort() {
//        String port = environment.getProperty("server.port", "8062");
//        return Integer.parseInt(port);
//    }
//
//    /**
//     * 获取服务器IP地址
//     */
//    private String getServerIp() {
//        // 从环境变量或配置中获取IP，默认使用本地IP
//        return environment.getProperty("server.address", "127.0.0.1");
//    }
//
//    /**
//     * 创建工具回调提供者
//     */
//    @Bean
//    public ToolCallbackProvider toolCallbackProvider(PersonManagementTool personManagementTool) {
//        log.info("Registering PersonManagementTool as MCP Tool Provider");
//        return MethodToolCallbackProvider.builder()
//                .toolObjects(personManagementTool)
//                .build();
//    }
//
//    /**
//     * 创建ObjectMapper Bean
//     */
//    @Bean
//    @Primary
//    public ObjectMapper objectMapper() {
//        return new ObjectMapper();
//    }
//
//    /**
//     * 创建MCP Server Transport Provider
//     * 按照MCP标准协议实现SSE传输
//     */
//    @Bean
//    @ConditionalOnMissingBean
//    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
//        // 构建基础URL
//        String baseUrl = "http://" + getServerIp() + ":" + getServerPort();
//        log.info("Creating MCP Server Transport with baseUrl: {}", baseUrl);
//
//        // 创建WebFlux SSE Server Transport Provider
//        WebFluxSseServerTransportProvider provider = new WebFluxSseServerTransportProvider(
//                objectMapper,
//                baseUrl,
//                "/mcp/message",  // 消息端点
//                "/sse"          // SSE端点
//        );
//
//        log.info("✅ MCP Server Transport Provider created successfully");
//        log.info("📡 SSE endpoint: {}/sse", baseUrl);
//        log.info("📨 Message endpoint: {}/mcp/message", baseUrl);
//
//        return provider;
//    }
//
//    /**
//     * 创建路由函数
//     * 暴露MCP协议要求的SSE和消息端点
//     */
//    @Bean
//    public RouterFunction<?> mcpRouterFunction(McpServerTransportProvider transportProvider) {
//        if (transportProvider instanceof WebFluxSseServerTransportProvider webFluxProvider) {
//            RouterFunction<?> routerFunction = webFluxProvider.getRouterFunction();
//            log.info("✅ MCP Router Function created successfully");
//            return routerFunction;
//        } else {
//            throw new IllegalStateException("Expected WebFluxSseServerTransportProvider but got: " +
//                    transportProvider.getClass().getSimpleName());
//        }
//    }
//}