package com.nacos.mcp.client;

import com.nacos.mcp.client.service.CustomMcpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

/**
 * MCP Client基础测试
 * 验证mcp-client能够正确启动
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.ai.deepseek.api-key=test-key",
    "spring.ai.mcp.client.enabled=true",  // 启用MCP客户端进行测试
    "logging.level.org.springframework.ai=WARN"  // 减少日志输出
})
public class McpClientIntegrationTest {

    @Autowired(required = false)
    private CustomMcpClient customMcpClient;

    @Test
    public void testSpringContextLoads() {
        // 基本的上下文加载测试
        // 如果这个测试通过，说明Spring Boot应用能够正确启动
    }

    @Test
    public void testGetPersonById() {
        if (customMcpClient != null) {
            try {
                // 测试调用 getPersonById 工具
                Map<String, Object> result = customMcpClient.callTool("getPersonById", Map.of("id", 1L)).block();
                System.out.println("getPersonById result: " + result);
                
                // 验证结果不为空
                assert result != null && !result.isEmpty();
                
                // 验证结果包含预期的字段
                String resultStr = result.toString();
                assert resultStr.contains("id") || resultStr.contains("found");
                
            } catch (Exception e) {
                System.err.println("Error calling getPersonById: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } else {
            System.out.println("CustomMcpClient is not available, skipping test");
        }
    }
} 