package com.nacos.mcp.server.v2;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import com.alibaba.nacos.api.config.annotation.NacosProperty;
import com.alibaba.nacos.spring.context.annotation.config.EnableNacosConfig;
import com.alibaba.nacos.spring.context.annotation.config.NacosPropertySource;
import com.nacos.mcp.server.v2.tools.PersonManagementTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
@SpringBootApplication(exclude = NacosMcpGatewayAutoConfiguration.class)
public class McpServerV2Application {

    public static void main(String[] args) {
        SpringApplication.run(McpServerV2Application.class, args);
    }

}