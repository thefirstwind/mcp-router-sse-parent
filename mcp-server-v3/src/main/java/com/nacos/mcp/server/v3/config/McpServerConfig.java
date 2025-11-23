package com.nacos.mcp.server.v3.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.server.v3.tools.PersonManagementTool;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

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


    @Value("${spring.ai.mcp.server.sse-message-endpoint}")
    private String sseMessageEndpoint;

    @Value("${spring.ai.mcp.server.sse-endpoint}")
    private String sseEndpoint;

    /**
     * 获取服务器端口
     */
    private int getServerPort() {
        String port = environment.getProperty("server.port", serverPort);
        return Integer.parseInt(port);
    }
//    private int getServerPort() {
//        // 优先使用实际绑定端口（RANDOM_PORT场景）
//        String localPort = environment.getProperty("local.server.port");
//        String portToUse = (localPort != null && !localPort.isBlank())
//                ? localPort
//                : environment.getProperty("server.port", serverPort);
//        try {
//            return Integer.parseInt(portToUse);
//        } catch (NumberFormatException ex) {
//            // 回退到默认端口
//            return 8080;
//        }
//    }

    /**
     * 获取服务器IP地址
     */
    private String getServerIp() {
        String address = environment.getProperty("server.address", "127.0.0.1");
        // 如果配置的是 0.0.0.0（绑定所有接口），获取实际IP
        if ("0.0.0.0".equals(address)) {
            try {
                // 获取本机实际IP地址
                return java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                log.warn("Failed to get local IP, using 127.0.0.1", e);
                return "127.0.0.1";
            }
        }
        return address;

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
     * 创建MCP Server Transport Provider
     * 按照MCP标准协议实现SSE传输
     */
    @Bean
    @ConditionalOnMissingBean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        // 使用相对端点，避免 RANDOM_PORT 下的主机/端口不一致导致的客户端校验失败
        String baseUrl = "";
        log.info("Creating MCP Server Transport with relative baseUrl (empty), endpoints will be relative");

        // 创建WebFlux SSE Server Transport Provider
        WebFluxSseServerTransportProvider provider = new WebFluxSseServerTransportProvider(
                objectMapper,
                baseUrl,
                sseMessageEndpoint,  // 消息端点
                sseEndpoint  // SSE端点
        );

        log.info("✅ MCP Server Transport Provider created successfully");
        log.info("📡 SSE endpoint: {}", sseEndpoint);
        log.info("📨 Message endpoint: {}", sseMessageEndpoint);

        return provider;
    }

    /**
     * 创建路由函数
     * 暴露MCP协议要求的SSE和消息端点
     */
    @Bean
    public RouterFunction<?> mcpRouterFunction(McpServerTransportProvider transportProvider) {
        if (transportProvider instanceof WebFluxSseServerTransportProvider webFluxProvider) {
            RouterFunction<?> routerFunction = webFluxProvider.getRouterFunction();
            log.info("✅ MCP Router Function created successfully");
            return routerFunction;
        } else {
            throw new IllegalStateException("Expected WebFluxSseServerTransportProvider but got: " +
                    transportProvider.getClass().getSimpleName());
        }
    }
//    @Bean
//    public RouterFunction<?> mcpRouterFunction(McpServerTransportProvider transportProvider) {
//        if (transportProvider instanceof WebFluxSseServerTransportProvider webFluxProvider) {
//            RouterFunction<?> routerFunction = webFluxProvider.getRouterFunction();
//            // 显式支持预检请求，确保返回 200 而不是 404
//            RouterFunction<ServerResponse> corsOptions = RouterFunctions
//                    .route(RequestPredicates.OPTIONS(sseEndpoint), req -> ServerResponse.ok().build())
//                    .andRoute(RequestPredicates.OPTIONS(sseMessageEndpoint), req -> ServerResponse.ok().build());
//            routerFunction = routerFunction.andOther(corsOptions);
//            log.info("✅ MCP Router Function created successfully");
//            return routerFunction;
//        } else {
//            throw new IllegalStateException("Expected WebFluxSseServerTransportProvider but got: " +
//                    transportProvider.getClass().getSimpleName());
//        }
//    }
//
//    /**
//     * CORS 配置：允许 /sse 与 /mcp/message 的跨域与预检请求（测试需要）
//     */
//    @Bean
//    public CorsWebFilter corsWebFilter() {
//        CorsConfiguration cors = new CorsConfiguration();
//        cors.addAllowedOriginPattern("*");
//        cors.addAllowedHeader("*");
//        cors.addAllowedMethod("GET");
//        cors.addAllowedMethod("POST");
//        cors.addAllowedMethod("OPTIONS");
//        cors.setAllowCredentials(false);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", cors);
//        return new CorsWebFilter(source);
//    }
}