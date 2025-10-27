package com.nacos.mcp.server.v6;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV6Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV6Application.class, args);
    }

}