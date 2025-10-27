package com.nacos.mcp.server.v5.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class WebFluxConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxConfig.class);

    @Value("${server.port:8065}")
    private int serverPort;

    @Value("${spring.ai.alibaba.mcp.nacos.ip:127.0.0.1}")
    private String serverIp;

    @Bean
    @Primary
    public RouterFunction<ServerResponse> mcpRoutes() {
        String baseUrl = String.format("http://%s:%d", serverIp, serverPort);
        logger.info("Creating MCP routes with baseUrl: {}", baseUrl);

        return route(GET("/sse"), this::handleSse)
                .andRoute(POST("/mcp/message"), this::handleMessage);
    }

    private Mono<ServerResponse> handleSse(ServerRequest request) {
        String baseUrl = String.format("http://%s:%d", serverIp, serverPort);
        String jsonData = String.format(
            "{\"type\":\"connection\",\"status\":\"connected\",\"baseUrl\":\"%s\",\"timestamp\":%d}",
             baseUrl, System.currentTimeMillis()
        );
        
        logger.info("Handling SSE request, sending: {}", jsonData);
        
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .bodyValue("data: " + jsonData + "\n\n");
    }

    private Mono<ServerResponse> handleMessage(ServerRequest request) {
        return request.bodyToMono(String.class)
                .flatMap(body -> {
                    logger.info("Received MCP message: {}", body);
                    // Echo back for now
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("{\"status\":\"received\",\"message\":\"" + body + "\"}");
                });
    }
} 