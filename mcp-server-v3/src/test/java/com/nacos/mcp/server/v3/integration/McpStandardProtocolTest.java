package com.nacos.mcp.server.v3.integration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP Server V3 Standard Protocol Test
 * 使用标准 MCP 协议测试 PersonManagementTool 的 getAllPersons 和 getPersonById 方法
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.server-addr=127.0.0.1:8848",
        "spring.ai.alibaba.mcp.nacos.namespace=public", 
        "spring.ai.alibaba.mcp.nacos.username=nacos",
        "spring.ai.alibaba.mcp.nacos.password=nacos",
        "spring.ai.alibaba.mcp.nacos.registry.enabled=true",
        "spring.ai.alibaba.mcp.nacos.registry.service-group=mcp-server",
        "spring.ai.alibaba.mcp.nacos.registry.service-name=mcp-server-v3"
})
public class McpStandardProtocolTest {

    @LocalServerPort
    private int port;

    private McpSyncClient mcpClient;
    private HttpClientSseClientTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        // 构建 MCP 客户端传输 - 使用基础 URL，不是端点
        String baseUrl = "http://localhost:" + port;
        transport = HttpClientSseClientTransport.builder(baseUrl).build();
        
        // 创建 MCP 客户端
        mcpClient = McpClient.sync(transport).build();
        
        // 初始化连接
        mcpClient.initialize();
        
        // 等待一秒确保连接稳定
        Thread.sleep(1000);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void testMcpInitialize() throws Exception {
        // 测试 MCP 初始化
        assertNotNull(mcpClient);
        
        // 可以通过列出工具来验证连接
        McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
        assertNotNull(toolsResult);
        assertNotNull(toolsResult.tools());
        assertTrue(toolsResult.tools().size() > 0);
        
        // 验证包含我们期望的工具
        boolean hasGetAllPersons = toolsResult.tools().stream()
                .anyMatch(tool -> "getAllPersons".equals(tool.name()));
        boolean hasGetPersonById = toolsResult.tools().stream()
                .anyMatch(tool -> "getPersonById".equals(tool.name()));
        
        assertTrue(hasGetAllPersons, "应该包含 getAllPersons 工具");
        assertTrue(hasGetPersonById, "应该包含 getPersonById 工具");
    }

    @Test
    void testGetAllPersons_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "getAllPersons", 
                Map.of() // 无参数
        );

        // 调用工具
        McpSchema.CallToolResult result = mcpClient.callTool(request);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isError());
        assertNotNull(result.content());
        assertTrue(result.content().size() > 0);
        
        // 验证返回的是文本内容
        McpSchema.Content content = result.content().get(0);
        assertTrue(content instanceof McpSchema.TextContent);
        
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertNotNull(textContent.text());
        
        // 日志输出结果以便调试
        System.out.println("getAllPersons result: " + textContent.text());
        
        // 验证返回内容包含预期的人员信息
        String resultText = textContent.text();
        assertTrue(resultText.contains("John") || resultText.contains("Jane"), 
                "结果应该包含测试数据中的人员姓名");
    }

    @Test
    void testGetPersonById_ValidId_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求 - 获取 ID 为 1 的人员
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "getPersonById",
                Map.of("id", 1L)
        );

        // 调用工具
        McpSchema.CallToolResult result = mcpClient.callTool(request);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isError());
        assertNotNull(result.content());
        assertTrue(result.content().size() > 0);
        
        // 验证返回的是文本内容
        McpSchema.Content content = result.content().get(0);
        assertTrue(content instanceof McpSchema.TextContent);
        
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertNotNull(textContent.text());
        
        // 日志输出结果以便调试
        System.out.println("getPersonById(1) result: " + textContent.text());
        
        // 验证返回内容包含 John Doe 的信息（根据 PersonManagementTool 中的测试数据）
        String resultText = textContent.text();
        assertTrue(resultText.contains("John") && resultText.contains("Doe"), 
                "结果应该包含 John Doe 的信息");
        assertTrue(resultText.contains("found") || resultText.contains("id"), 
                "结果应该表明找到了人员信息");
    }

    @Test
    void testGetPersonById_InvalidId_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求 - 获取不存在的 ID
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "getPersonById",
                Map.of("id", 999L)
        );

        // 调用工具
        McpSchema.CallToolResult result = mcpClient.callTool(request);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isError()); // 工具调用本身成功，但是业务逻辑返回"未找到"
        assertNotNull(result.content());
        assertTrue(result.content().size() > 0);
        
        // 验证返回的是文本内容
        McpSchema.Content content = result.content().get(0);
        assertTrue(content instanceof McpSchema.TextContent);
        
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertNotNull(textContent.text());
        
        // 日志输出结果以便调试
        System.out.println("getPersonById(999) result: " + textContent.text());
        
        // 验证返回内容表明没有找到人员
        String resultText = textContent.text();
        assertTrue(resultText.contains("not found") || resultText.contains("false"), 
                "结果应该表明没有找到人员");
    }

    @Test
    void testGetPersonById_MultipleValidIds_StandardMcpProtocol() throws Exception {
        // 测试多个有效的 ID（根据 PersonManagementTool 的测试数据）
        Long[] testIds = {1L, 2L, 3L};
        String[] expectedNames = {"John", "Jane", "Hans"};
        
        for (int i = 0; i < testIds.length; i++) {
            Long id = testIds[i];
            String expectedName = expectedNames[i];
            
            // 构建 MCP tools/call 请求
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                    "getPersonById",
                    Map.of("id", id)
            );

            // 调用工具
            McpSchema.CallToolResult result = mcpClient.callTool(request);
            
            // 验证结果
            assertNotNull(result, "ID " + id + " 的结果不应为空");
            assertFalse(result.isError(), "ID " + id + " 的调用不应出错");
            assertNotNull(result.content(), "ID " + id + " 的内容不应为空");
            assertTrue(result.content().size() > 0, "ID " + id + " 应该有内容");
            
            // 验证返回的是文本内容
            McpSchema.Content content = result.content().get(0);
            assertTrue(content instanceof McpSchema.TextContent);
            
            McpSchema.TextContent textContent = (McpSchema.TextContent) content;
            assertNotNull(textContent.text());
            
            // 验证包含期望的姓名
            String resultText = textContent.text();
            assertTrue(resultText.contains(expectedName), 
                    "ID " + id + " 的结果应该包含姓名: " + expectedName);
            
            System.out.println("getPersonById(" + id + ") result: " + textContent.text());
        }
    }

    @Test 
    void testListTools_ContainsPersonManagementTools() throws Exception {
        // 列出所有可用工具
        McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
        
        assertNotNull(toolsResult);
        assertNotNull(toolsResult.tools());
        assertTrue(toolsResult.tools().size() > 0);
        
        // 检查是否包含 PersonManagementTool 的所有工具
        String[] expectedTools = {"getAllPersons", "getPersonById", "addPerson", "get_system_info", "list_servers"};
        
        for (String expectedTool : expectedTools) {
            boolean hasExpectedTool = toolsResult.tools().stream()
                    .anyMatch(tool -> expectedTool.equals(tool.name()));
            assertTrue(hasExpectedTool, "应该包含工具: " + expectedTool);
        }
        
        // 打印所有工具以便调试
        System.out.println("Available tools:");
        toolsResult.tools().forEach(tool -> 
                System.out.println("- " + tool.name() + ": " + tool.description()));
    }

    @Test
    void testAddPerson_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求 - 添加新人员
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "addPerson",
                Map.of(
                        "firstName", "Test",
                        "lastName", "User", 
                        "age", 25,
                        "nationality", "TestLand",
                        "gender", "MALE"
                )
        );

        // 调用工具
        McpSchema.CallToolResult result = mcpClient.callTool(request);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isError());
        assertNotNull(result.content());
        assertTrue(result.content().size() > 0);
        
        // 验证返回的是文本内容
        McpSchema.Content content = result.content().get(0);
        assertTrue(content instanceof McpSchema.TextContent);
        
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertNotNull(textContent.text());
        
        // 日志输出结果以便调试
        System.out.println("addPerson result: " + textContent.text());
        
        // 验证添加成功
        String resultText = textContent.text();
        assertTrue(resultText.contains("Test") && resultText.contains("User"), 
                "结果应该包含新添加的人员信息");
    }

    @Test
    void testGetSystemInfo_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求 - 获取系统信息
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_system_info",
                Map.of()
        );

        // 调用工具
        McpSchema.CallToolResult result = mcpClient.callTool(request);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isError());
        assertNotNull(result.content());
        assertTrue(result.content().size() > 0);
        
        // 验证返回的是文本内容
        McpSchema.Content content = result.content().get(0);
        assertTrue(content instanceof McpSchema.TextContent);
        
        McpSchema.TextContent textContent = (McpSchema.TextContent) content;
        assertNotNull(textContent.text());
        
        // 日志输出结果以便调试
        System.out.println("get_system_info result: " + textContent.text());
        
        // 验证包含系统信息
        String resultText = textContent.text();
        assertTrue(resultText.contains("server") || resultText.contains("system"), 
                "结果应该包含系统信息");
    }

    /**
     * 测试无效的工具调用
     */
    @Test
    void testInvalidToolCall_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求 - 调用不存在的工具
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "nonExistentTool",
                Map.of()
        );

        // 调用工具应该出错
        try {
            McpSchema.CallToolResult result = mcpClient.callTool(request);
            // 如果没有抛出异常，则检查结果是否包含错误
            assertTrue(result.isError(), "调用不存在的工具应该返回错误");
        } catch (Exception e) {
            // 预期的异常，表明工具不存在
            assertTrue(e.getMessage().contains("tool") || e.getMessage().contains("not found"), 
                    "异常消息应该指示工具不存在");
        }
    }

    /**
     * 测试边界情况 - 空参数调用 getPersonById
     */
    @Test
    void testGetPersonById_EmptyParameters_StandardMcpProtocol() throws Exception {
        // 构建 MCP tools/call 请求 - 空参数
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "getPersonById",
                Map.of() // 缺少必需的 id 参数
        );

        // 调用工具应该出错或返回错误信息
        try {
            McpSchema.CallToolResult result = mcpClient.callTool(request);
            // 检查是否有错误或异常信息
            if (!result.isError()) {
                // 如果没有错误标志，检查内容是否包含错误信息
                McpSchema.Content content = result.content().get(0);
                if (content instanceof McpSchema.TextContent textContent) {
                    String resultText = textContent.text();
                    assertTrue(resultText.contains("error") || resultText.contains("required"), 
                            "缺少必需参数应该返回错误信息");
                }
            }
        } catch (Exception e) {
            // 预期的异常，表明参数不正确
            assertTrue(e.getMessage().contains("parameter") || e.getMessage().contains("required"), 
                    "异常消息应该指示参数问题");
        }
    }
} 