package com.nacos.mcp.server.v2;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import com.nacos.mcp.server.v2.tools.PersonManagementTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
// import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

//@SpringBootApplication
// @EnableDiscoveryClient
@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV2Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV2Application.class, args);
    }

//    @Bean
//    public ToolCallbackProvider tools(PersonManagementTool personManagementTool) {
//        return MethodToolCallbackProvider.builder().toolObjects(personManagementTool).build();
//    }
} 