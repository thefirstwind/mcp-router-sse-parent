package com.nacos.mcp.server;

import com.nacos.mcp.server.repository.PersonRepository;
import com.nacos.mcp.server.tools.PersonQueryTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * MCP Server Application using Spring AI Alibaba with Nacos integration
 * Provides person management tools via MCP protocol
 * 
 * Spring AI MCP Server Boot Starter 会自动扫描所有标注了 @Tool 的方法
 * PersonTools 类作为 @Service 会被自动发现，其中的 @Tool 方法会被自动注册为 MCP 工具
 * 同时通过 Nacos 进行服务注册和发现
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    /**
     * 创建 PersonTools 的 ToolCallbackProvider
     * 这确保所有 @Tool 注解的方法都能被正确注册到 MCP 服务器
     */
    @Bean
    public ToolCallbackProvider personToolsCallbackProvider(PersonQueryTools personQueryTools) {
        return MethodToolCallbackProvider.builder().toolObjects(personQueryTools).build();
    }

    @Bean
    public PersonQueryTools personTools(PersonRepository personRepository) {
        return new PersonQueryTools(personRepository);
    }
}