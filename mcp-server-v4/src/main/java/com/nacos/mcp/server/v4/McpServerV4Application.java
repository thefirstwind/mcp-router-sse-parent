package com.nacos.mcp.server.v4;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV4Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV4Application.class, args);
    }

}