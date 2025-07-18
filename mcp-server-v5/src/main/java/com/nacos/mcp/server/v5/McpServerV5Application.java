package com.nacos.mcp.server.v5;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV5Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV5Application.class, args);
    }

}