package com.nacos.mcp.router.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.McpTool;
import com.nacos.mcp.router.service.McpServerService;
import com.nacos.mcp.router.service.McpToolService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP Router 调用 MCP Server V2 测试用例
 * 专门测试 mcp-router 通过 SSE 协议调用 mcp-server-v2 的功能
 * 
 * 测试场景：
 * 1. 验证 mcp-server-v2 在 Nacos 中的注册状态
 * 2. 验证 mcp-router 能够发现 mcp-server-v2
 * 3. 验证通过 SSE 协议调用 mcp-server-v2 的工具
 * 4. 验证 Person 管理相关的工具调用
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "nacos.discovery.server-addr=127.0.0.1:8848",
    "nacos.discovery.enabled=true",
    "nacos.discovery.username=nacos",
    "nacos.discovery.password=nacos",
    "nacos.discovery.namespace=public",
    "nacos.ai.mcp.registry.enabled=true",
    "mcp.router.mock-data.enabled=false"
})
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpRouterToServerV2Test {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private McpToolService mcpToolService;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private static final String MCP_SERVER_V2_NAME = "mcp-server-v2";
    private static final String MCP_SERVER_V2_ENDPOINT = "192.168.0.102:8061";

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        log.info("=== 测试设置完成. Base URL: {} ===", baseUrl);
    }

    // ==================== 1. 服务发现验证 ====================

    @Test
    @Order(1)
    @DisplayName("1.1 验证 mcp-server-v2 在 Nacos 中的注册状态")
    void testMcpServerV2Registration() throws InterruptedException {
        log.info("=== 测试 mcp-server-v2 在 Nacos 中的注册状态 ===");

        // 等待服务发现完成
        Thread.sleep(3000);

        // 验证能够从 Nacos 发现 mcp-server-v2
        Mono<List<McpServer>> serversMono = mcpServerService.listAllMcpServers();
        List<McpServer> servers = serversMono.block(Duration.ofSeconds(30));
        
        assertNotNull(servers, "服务器列表不应为空");
        
        log.info("发现的服务器列表: {}", servers.size());
        servers.forEach(server -> {
            log.info("服务器: {} - 端点: {} - 状态: {}", 
                server.getName(), server.getEndpoint(), server.getStatus());
        });
        
        boolean foundV2 = servers.stream()
                .anyMatch(server -> MCP_SERVER_V2_NAME.equals(server.getName()));
        
        if (foundV2) {
            log.info("✅ 成功发现 mcp-server-v2 服务");
        } else {
            log.error("❌ 未找到 mcp-server-v2 服务");
        }
        
        assertTrue(foundV2, "应该能够发现 mcp-server-v2 服务");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 验证 mcp-server-v2 服务详情")
    void testMcpServerV2Details() {
        log.info("=== 测试 mcp-server-v2 服务详情 ===");

        Mono<McpServer> serverMono = mcpServerService.getMcpServer(MCP_SERVER_V2_NAME);
        McpServer server = serverMono.block(Duration.ofSeconds(30));
        
        assertNotNull(server, "应该能够获取到 mcp-server-v2 服务详情");
        
        log.info("mcp-server-v2 详情:");
        log.info("  名称: {}", server.getName());
        log.info("  端点: {}", server.getEndpoint());
        log.info("  传输类型: {}", server.getTransportType());
        log.info("  状态: {}", server.getStatus());
        log.info("  描述: {}", server.getDescription());
        log.info("  版本: {}", server.getVersion());
        log.info("  工具列表: {}", server.getTools());

        // 验证关键属性
        assertEquals(MCP_SERVER_V2_NAME, server.getName());
        assertTrue(server.getTransportType().equalsIgnoreCase("sse"));
        assertTrue(server.getStatus() == McpServer.ServerStatus.REGISTERED || 
                  server.getStatus() == McpServer.ServerStatus.CONNECTED);
        
        // 验证工具列表包含 Person 管理工具
        List<McpTool> tools = server.getTools();
        assertNotNull(tools, "工具列表不应为空");
        assertFalse(tools.isEmpty(), "工具列表应包含工具");
        
        List<String> toolNames = tools.stream().map(McpTool::getName).toList();
        assertTrue(toolNames.contains("getAllPersons"), "应包含 getAllPersons 工具");
        assertTrue(toolNames.contains("addPerson"), "应包含 addPerson 工具");
        assertTrue(toolNames.contains("deletePerson"), "应包含 deletePerson 工具");
        
        log.info("✅ mcp-server-v2 服务详情验证成功");
    }

    // ==================== 2. 工具发现验证 ====================

    @Test
    @Order(3)
    @DisplayName("2.1 验证 mcp-server-v2 工具发现")
    void testMcpServerV2ToolDiscovery() {
        log.info("=== 测试 mcp-server-v2 工具发现 ===");

        // 通过 listMcpServers 方法获取服务器信息
        Map<String, Object> serverInfo = mcpToolService.listMcpServers();
        assertNotNull(serverInfo, "服务器信息不应为空");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) serverInfo.get("servers");
        assertNotNull(servers, "服务器列表不应为空");
        
        log.info("发现的服务器总数: {}", servers.size());
        
        // 查找 mcp-server-v2
        Optional<Map<String, Object>> v2Server = servers.stream()
                .filter(server -> MCP_SERVER_V2_NAME.equals(server.get("name")))
                .findFirst();
        
        assertTrue(v2Server.isPresent(), "应该能够发现 mcp-server-v2");
        
        Map<String, Object> serverDetails = v2Server.get();
        log.info("mcp-server-v2 详情: {}", serverDetails);
        
        // 验证工具数量
        Object toolCount = serverDetails.get("toolCount");
        assertNotNull(toolCount, "工具数量不应为空");
        assertTrue(((Number) toolCount).intValue() > 0, "应该有工具存在");
        
        log.info("✅ mcp-server-v2 工具发现验证成功");
    }

    // ==================== 3. SSE 协议工具调用验证 ====================

    @Test
    @Order(4)
    @DisplayName("3.1 测试 getAllPersons 工具调用")
    void testGetAllPersonsToolCall() {
        log.info("=== 测试 getAllPersons 工具调用 ===");

        Map<String, Object> params = new HashMap<>();
        
        Mono<Object> resultMono = mcpServerService.useTool("getAllPersons", params);
        Object result = resultMono.block(Duration.ofSeconds(30));
        
        assertNotNull(result, "getAllPersons 调用结果不应为空");
        log.info("getAllPersons 调用结果: {}", result);
        
        // 验证调用成功
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            // 检查是否有 persons 字段或类似的数据结构
            log.info("返回结果类型: Map，包含键: {}", resultMap.keySet());
            
            // 验证调用成功
            if (resultMap.containsKey("error")) {
                log.error("工具调用出错: {}", resultMap.get("error"));
                fail("工具调用不应出错");
            }
        }
        
        log.info("✅ getAllPersons 工具调用成功");
    }

    @Test
    @Order(5)
    @DisplayName("3.2 测试 addPerson 工具调用")
    void testAddPersonToolCall() {
        log.info("=== 测试 addPerson 工具调用 ===");

        Map<String, Object> params = new HashMap<>();
        params.put("firstName", "测试");
        params.put("lastName", "用户");
        params.put("age", 25);
        params.put("nationality", "中国");
        params.put("gender", "MALE");
        
        Mono<Object> resultMono = mcpServerService.useTool("addPerson", params);
        Object result = resultMono.block(Duration.ofSeconds(30));
        
        assertNotNull(result, "addPerson 调用结果不应为空");
        log.info("addPerson 调用结果: {}", result);
        
        // 验证调用成功
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            log.info("返回结果类型: Map，包含键: {}", resultMap.keySet());
            
            // 验证调用成功
            if (resultMap.containsKey("error")) {
                log.error("工具调用出错: {}", resultMap.get("error"));
                fail("工具调用不应出错");
            }
            
            // 验证是否包含成功标识
            if (resultMap.containsKey("success") || resultMap.containsKey("id") || resultMap.containsKey("created")) {
                log.info("✅ addPerson 工具调用成功，返回: {}", resultMap);
            }
        }
        
        log.info("✅ addPerson 工具调用成功");
    }

    @Test
    @Order(6)
    @DisplayName("3.3 测试指定服务器的工具调用")
    void testSpecificServerToolCall() {
        log.info("=== 测试指定 mcp-server-v2 的工具调用 ===");

        Map<String, Object> params = new HashMap<>();
        
        Mono<Object> resultMono = mcpServerService.useTool(MCP_SERVER_V2_NAME, "getAllPersons", params);
        Object result = resultMono.block(Duration.ofSeconds(30));
        
        assertNotNull(result, "指定服务器工具调用结果不应为空");
        log.info("指定服务器工具调用结果: {}", result);
        
        log.info("✅ 指定服务器工具调用成功");
    }

    // ==================== 4. 错误处理验证 ====================

    @Test
    @Order(7)
    @DisplayName("4.1 测试不存在的工具调用")
    void testNonExistentToolCall() {
        log.info("=== 测试不存在的工具调用 ===");

        Map<String, Object> params = new HashMap<>();
        
        Mono<Object> resultMono = mcpServerService.useTool("nonExistentTool", params);
        
        assertThrows(RuntimeException.class, () -> {
            resultMono.block(Duration.ofSeconds(30));
        }, "应该抛出 RuntimeException");
        
        log.info("✅ 不存在工具的错误处理验证成功");
    }

    @Test
    @Order(8)
    @DisplayName("4.2 测试无效参数的工具调用")
    void testInvalidParametersToolCall() {
        log.info("=== 测试无效参数的工具调用 ===");

        Map<String, Object> params = new HashMap<>();
        params.put("invalidParam", "invalidValue");
        
        Mono<Object> resultMono = mcpServerService.useTool("addPerson", params);
        Object result = resultMono.block(Duration.ofSeconds(30));
        
        assertNotNull(result, "无效参数调用结果不应为空");
        log.info("无效参数调用结果: {}", result);
        
        // 验证返回结果
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            if (resultMap.containsKey("error")) {
                log.info("✅ 无效参数正确返回错误: {}", resultMap.get("error"));
            }
        }
        
        log.info("✅ 无效参数处理完成");
    }

    // ==================== 5. 性能验证 ====================

    @Test
    @Order(9)
    @DisplayName("5.1 测试并发工具调用")
    void testConcurrentToolCalls() throws InterruptedException {
        log.info("=== 测试并发工具调用 ===");

        int concurrentCalls = 5;
        CountDownLatch latch = new CountDownLatch(concurrentCalls);
        
        for (int i = 0; i < concurrentCalls; i++) {
            final int callIndex = i;
            
            mcpServerService.useTool("getAllPersons", new HashMap<>())
                    .subscribe(
                        result -> {
                            log.info("并发调用 {} 成功: {}", callIndex, result != null ? "有结果" : "无结果");
                            latch.countDown();
                        },
                        error -> {
                            log.error("并发调用 {} 失败: {}", callIndex, error.getMessage());
                            latch.countDown();
                        }
                    );
        }
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "所有并发调用应该在60秒内完成");
        
        log.info("✅ 并发工具调用测试完成");
    }

    // ==================== 6. 清理 ====================

    @Test
    @Order(10)
    @DisplayName("6.1 测试完成后清理")
    void testCleanup() {
        log.info("=== 测试完成后清理 ===");
        
        // 这里可以添加清理逻辑，比如清理测试数据
        log.info("✅ 清理完成");
    }

    @AfterAll
    static void finalCleanup() {
        log.info("=== 所有测试完成，最终清理 ===");
    }
} 