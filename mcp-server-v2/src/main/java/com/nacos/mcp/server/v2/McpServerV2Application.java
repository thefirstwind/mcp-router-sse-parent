package com.nacos.mcp.server.v2;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

//@SpringBootApplication
// @EnableDiscoveryClient
@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV2Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV2Application.class, args);
    }

} 