package com.nacos.mcp.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.client.service.CustomMcpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class McpClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(McpClientConfig.class);

    @Value("${mcp.router.url:http://localhost:8050}")
    private String mcpRouterUrl;

    @Value("${mcp.client.request-timeout:PT30S}")
    private Duration requestTimeout;

    /**
     * 创建自定义的 MCP 客户端，直接与 mcp-router 通信
     */
    @Bean
    public CustomMcpClient customMcpClient(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        logger.info("Creating custom MCP client for mcp-router: {}", mcpRouterUrl);
        
        WebClient webClient = webClientBuilder
                .baseUrl(mcpRouterUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        
        return new CustomMcpClient(webClient, objectMapper, requestTimeout);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
} 