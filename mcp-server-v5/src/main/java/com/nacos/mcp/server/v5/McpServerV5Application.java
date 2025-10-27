package com.nacos.mcp.server.v5;

import com.alibaba.cloud.ai.autoconfigure.mcp.server.NacosMcpGatewayAutoConfiguration;
import org.springframework.ai.mcp.server.autoconfigure.McpWebFluxServerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 只排除有 HttpStatusCode 兼容性问题的 WebFlux 自动配置和 Gateway 配置
// 保留 MCP Server 和 Nacos 注册功能
@SpringBootApplication(exclude = {
    NacosMcpGatewayAutoConfiguration.class,
    McpWebFluxServerAutoConfiguration.class
})
public class McpServerV5Application {
    public static void main(String[] args) {
        SpringApplication.run(McpServerV5Application.class, args);
    }
}