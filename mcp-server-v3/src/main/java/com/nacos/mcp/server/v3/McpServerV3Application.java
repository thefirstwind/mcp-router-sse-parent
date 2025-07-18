package com.nacos.mcp.server.v3;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV3Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV3Application.class, args);
    }

}