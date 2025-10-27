package com.pajk.mcpbridge.core.integration;

import com.pajk.mcpbridge.core.model.McpMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.Duration;
import java.util.Map;

/**
 * Router to Server Integration Test
 * 测试 mcp-router-v3 调用 mcp-server-v6 的完整流程
 * 注意：mcp-server-v6 使用标准 MCP 协议 (SSE + JSON-RPC)
 * 这个测试需要 mcp-router-v3 正确实现 MCP 客户端功能
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.server-addr=127.0.0.1:8848",
        "spring.ai.alibaba.mcp.nacos.namespace=public",
        "spring.ai.alibaba.mcp.nacos.username=nacos",
        "spring.ai.alibaba.mcp.nacos.password=nacos",
        "mcp.router.registry.service-groups=mcp-server",
        "mcp.router.registry.health-check.enabled=true",
        "mcp.router.registry.health-check.interval=30",
        "mcp.router.registry.connection-pool.max-size=10",
        "mcp.router.registry.connection-pool.min-size=2",
        "mcp.router.registry.connection-pool.idle-timeout=300",
        "logging.level.com.nacos.mcp=DEBUG",
        "logging.level.io.modelcontextprotocol=DEBUG"
})
public class RouterToServerIntegrationTest {

    @LocalServerPort
    private int routerPort;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + routerPort)
                .responseTimeout(Duration.ofSeconds(60)) // Increased timeout for integration tests
                .build();
    }

    @Test
    void testRouteToMcpServerV6_GetAllPersons() {
        // 构建获取所有人员的请求
        McpMessage getAllPersonsMessage = McpMessage.builder()
                .id("router-to-server-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "getAllPersons",
                        "arguments", Map.of()
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(getAllPersonsMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("router-to-server-001")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.routerVersion").isEqualTo("v3")
                .jsonPath("$.metadata.routingStrategy").isEqualTo("intelligent")
                .jsonPath("$.metadata.toolName").isEqualTo("getAllPersons")
                .jsonPath("$.metadata.responseTime").exists();
    }

    @Test
    void testRouteToMcpServerV6_GetPersonById() {
        // 构建根据ID获取人员的请求
        McpMessage getPersonByIdMessage = McpMessage.builder()
                .id("router-to-server-002")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "getPersonById",
                        "arguments", Map.of("id", 1L)
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(getPersonByIdMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("router-to-server-002")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.toolName").isEqualTo("getPersonById");
    }

    @Test
    void testRouteToMcpServerV6_AddPerson() {
        // 构建添加人员的请求
        McpMessage addPersonMessage = McpMessage.builder()
                .id("router-to-server-003")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "addPerson",
                        "arguments", Map.of(
                                "firstName", "Router",
                                "lastName", "Integration",
                                "age", 28,
                                "nationality", "Integration Test",
                                "gender", "MALE"
                        )
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(addPersonMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("router-to-server-003")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.toolName").isEqualTo("addPerson");
    }

    @Test
    void testRouteToMcpServerV6_GetSystemInfo() {
        // 构建获取系统信息的请求
        McpMessage getSystemInfoMessage = McpMessage.builder()
                .id("router-to-server-004")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "get_system_info",
                        "arguments", Map.of()
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(getSystemInfoMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("router-to-server-004")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.toolName").isEqualTo("get_system_info");
    }

    @Test
    void testRouteToMcpServerV6_ListServers() {
        // 构建列出服务器的请求
        McpMessage listServersMessage = McpMessage.builder()
                .id("router-to-server-005")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "list_servers",
                        "arguments", Map.of()
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(listServersMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("router-to-server-005")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.toolName").isEqualTo("list_servers");
    }



    @Test
    void testRouteWithTimeout_GetPersonById() {
        // 测试带超时的路由
        McpMessage timeoutMessage = McpMessage.builder()
                .id("timeout-test-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "getPersonById",
                        "arguments", Map.of("id", 5L)
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6/timeout/30")
                .body(BodyInserters.fromValue(timeoutMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("timeout-test-001")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist();
    }

    @Test
    void testRouteToMcpServerV6_InvalidTool() {
        // 测试调用不存在的工具
        McpMessage invalidToolMessage = McpMessage.builder()
                .id("invalid-tool-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "nonExistentTool",
                        "arguments", Map.of()
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(invalidToolMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("invalid-tool-001")
                .jsonPath("$.error").exists()
                .jsonPath("$.error.code").exists()
                .jsonPath("$.metadata.errorType").isEqualTo("routing_error");
    }

    @Test
    void testRouteToMcpServerV6_InvalidPersonId() {
        // 测试使用无效ID获取人员
        McpMessage invalidIdMessage = McpMessage.builder()
                .id("invalid-id-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "getPersonById",
                        "arguments", Map.of("id", 999L)
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(invalidIdMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("invalid-id-001")
                .jsonPath("$.targetService").isEqualTo("mcp-server-v6")
                .jsonPath("$.result").exists() // Tool should execute but return "not found"
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.toolName").isEqualTo("getPersonById");
    }

    @Test
    void testListToolsForMcpServerV6() {
        // 测试列出 mcp-server-v6 的工具
        webTestClient.get()
                .uri("/mcp/router/tools/mcp-server-v6")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").exists();
    }

    @Test
    void testCheckToolExistence() {
        // 测试检查工具是否存在
        webTestClient.get()
                .uri("/mcp/router/tools/mcp-server-v6/getAllPersons")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .value(exists -> {
                    assert exists != null && exists;
                });

        // 测试检查不存在的工具
        webTestClient.get()
                .uri("/mcp/router/tools/mcp-server-v6/nonExistentTool")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .value(exists -> {
                    assert exists != null && !exists;
                });
    }

    @Test
    void testGetRoutingStats() {
        // 先执行一个路由请求以生成统计数据
        McpMessage statsTestMessage = McpMessage.builder()
                .id("stats-test-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "getAllPersons",
                        "arguments", Map.of()
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(statsTestMessage))
                .exchange()
                .expectStatus().isOk();

        // 然后获取路由统计信息
        webTestClient.get()
                .uri("/mcp/router/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.routing_strategy").isEqualTo("intelligent")
                .jsonPath("$.features").isArray()
                .jsonPath("$.features[0]").isEqualTo("smart_routing")
                .jsonPath("$.features[1]").isEqualTo("connection_pooling")
                .jsonPath("$.features[2]").isEqualTo("performance_monitoring");
    }

    @Test
    void testFullWorkflow_AddAndRetrievePerson() {
        // 完整工作流测试：添加一个人员，然后检索验证
        
        // 1. 添加新人员
        McpMessage addPersonMessage = McpMessage.builder()
                .id("workflow-add-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "addPerson",
                        "arguments", Map.of(
                                "firstName", "Workflow",
                                "lastName", "Test",
                                "age", 33,
                                "nationality", "Test Workflow",
                                "gender", "FEMALE"
                        )
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(addPersonMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("workflow-add-001")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist();

        // 2. 获取所有人员验证添加成功
        McpMessage getAllPersonsMessage = McpMessage.builder()
                .id("workflow-get-001")
                .method("tools/call")
                .jsonrpc("2.0")
                .params(Map.of(
                        "name", "getAllPersons",
                        "arguments", Map.of()
                ))
                .timestamp(System.currentTimeMillis())
                .build();

        webTestClient.post()
                .uri("/mcp/router/route/mcp-server-v6")
                .body(BodyInserters.fromValue(getAllPersonsMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("workflow-get-001")
                .jsonPath("$.result").exists()
                .jsonPath("$.error").doesNotExist()
                .jsonPath("$.metadata.toolName").isEqualTo("getAllPersons");
    }

    @Test
    void testConcurrentRequests() {
        // 测试并发请求处理
        for (int i = 1; i <= 5; i++) {
            final int requestId = i;
            McpMessage concurrentMessage = McpMessage.builder()
                    .id("concurrent-" + String.format("%03d", requestId))
                    .method("tools/call")
                    .jsonrpc("2.0")
                    .params(Map.of(
                            "name", "getPersonById",
                            "arguments", Map.of("id", (long) requestId)
                    ))
                    .timestamp(System.currentTimeMillis())
                    .build();

            webTestClient.post()
                    .uri("/mcp/router/route/mcp-server-v6")
                    .body(BodyInserters.fromValue(concurrentMessage))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo("concurrent-" + String.format("%03d", requestId))
                    .jsonPath("$.result").exists()
                    .jsonPath("$.error").doesNotExist();
        }
    }

    @Test
    void testLoadBalancing_MultipleRequests() {
        // 发送多个请求以测试负载均衡
        for (int i = 1; i <= 10; i++) {
            McpMessage loadBalanceMessage = McpMessage.builder()
                    .id("load-balance-" + String.format("%03d", i))
                    .method("tools/call")
                    .jsonrpc("2.0")
                    .params(Map.of(
                            "name", "get_system_info",
                            "arguments", Map.of()
                    ))
                    .timestamp(System.currentTimeMillis())
                    .build();

            webTestClient.post()
                    .uri("/mcp/router/route/mcp-server-v6")
                    .body(BodyInserters.fromValue(loadBalanceMessage))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.result").exists()
                    .jsonPath("$.metadata.routingStrategy").isEqualTo("intelligent");
        }
    }
} 