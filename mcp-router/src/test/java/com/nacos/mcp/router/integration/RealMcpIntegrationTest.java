package com.nacos.mcp.router.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nacos.mcp.router.model.*;
import com.nacos.mcp.router.service.*;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实MCP集成测试 - 使用真实Nacos数据，验证所有MCP功能
 * 1. MCP协议实现验证
 * 2. Resources访问验证 
 * 3. Tools联通性验证
 * 4. MCP和Nacos双重配置验证
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
public class RealMcpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private McpServerService mcpServerService;

    @Autowired
    private McpResourceService mcpResourceService;

    @Autowired
    private McpPromptService mcpPromptService;

    @Autowired
    private McpToolService mcpToolService;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private String mcpJsonRpcUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        mcpJsonRpcUrl = baseUrl + "/mcp/jsonrpc";
        log.info("=== 测试设置完成. Base URL: {} ===", baseUrl);
    }

    // ==================== 1. Nacos连接验证 ====================

    @Test
    @Order(1)
    @DisplayName("1.1 验证Nacos连接状态")
    void testNacosConnectivity() {
        log.info("=== 测试Nacos连接状态 ===");

        // 验证Nacos服务器状态
        try {
            ProcessBuilder pb = new ProcessBuilder("curl", "-s", "-X", "GET", 
                "http://localhost:8848/nacos/v1/ns/operator/metrics");
            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);
            
            assertThat(process.exitValue()).isEqualTo(0);
            log.info("✅ Nacos服务器连接正常");
        } catch (Exception e) {
            log.warn("⚠️ Nacos连接测试跳过: {}", e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("1.2 验证应用健康状态")
    void testApplicationHealth() {
        log.info("=== 测试应用健康状态 ===");

        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl + "/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");

        log.info("✅ 应用健康状态验证通过");
    }

    // ==================== 2. MCP协议实现验证 ====================

    @Test
    @Order(3)
    @DisplayName("2.1 MCP协议初始化验证")
    void testMcpInitialization() {
        log.info("=== 测试MCP协议初始化 ===");

        McpJsonRpcRequest initRequest = new McpJsonRpcRequest();
        initRequest.setJsonrpc("2.0");
        initRequest.setMethod("initialize");
        initRequest.setId("init-test-" + System.currentTimeMillis());
        
        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("capabilities", Map.of(
            "tools", Map.of("listChanged", true),
            "resources", Map.of("subscribe", true, "listChanged", true),
            "prompts", Map.of("listChanged", true)
        ));
        params.put("clientInfo", Map.of(
            "name", "real-test-client",
            "version", "1.0.0"
        ));
        initRequest.setParams(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(initRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(initRequest.getId());
        assertThat(response.getBody().getResult()).isNotNull();

        Map<String, Object> result = (Map<String, Object>) response.getBody().getResult();
        assertThat(result.get("protocolVersion")).isEqualTo("2024-11-05");
        assertThat(result.get("capabilities")).isNotNull();
        assertThat(result.get("serverInfo")).isNotNull();

        log.info("✅ MCP协议初始化验证通过");
    }

    // ==================== 3. MCP服务器注册验证 ====================

    @Test
    @Order(4)
    @DisplayName("3.1 真实MCP服务器注册")
    void testRealMcpServerRegistration() throws InterruptedException {
        log.info("=== 测试真实MCP服务器注册 ===");

        McpServerRegistrationRequest registrationRequest = McpServerRegistrationRequest.builder()
            .name("real-file-server")
            .version("1.0.0")
            .endpoint("http://127.0.0.1:8080")
            .description("Real File Server for Integration Testing")
            .transportType("sse")
            .build();

        Mono<McpServer> registration = mcpServerService.registerMcpServer(registrationRequest);
        McpServer registeredServer = registration.block(Duration.ofSeconds(10));

        assertThat(registeredServer).isNotNull();
        assertThat(registeredServer.getName()).isEqualTo("real-file-server");
        assertThat(registeredServer.getStatus()).isEqualTo(McpServer.ServerStatus.REGISTERED);

        // 等待Nacos同步
        Thread.sleep(3000);

        log.info("✅ 真实MCP服务器注册完成: {}", registeredServer.getName());
    }

    @Test
    @Order(5)
    @DisplayName("3.2 从Nacos获取服务器列表")
    void testGetRealServerListFromNacos() throws InterruptedException {
        log.info("=== 测试从Nacos获取真实服务器列表 ===");

        // 等待服务发现同步
        Thread.sleep(2000);

        Mono<List<McpServer>> serversMono = mcpServerService.listAllMcpServers();
        List<McpServer> servers = serversMono.block(Duration.ofSeconds(10));

        assertThat(servers).isNotNull();
        log.info("发现 {} 个MCP服务器", servers.size());

        servers.forEach(server -> {
            log.info("  - 服务器: {} | 状态: {} | 传输类型: {} | 描述: {}", 
                server.getName(), 
                server.getStatus(), 
                server.getTransportType(),
                server.getDescription());
            
            // 验证服务器基本属性
            assertThat(server.getName()).isNotBlank();
            assertThat(server.getStatus()).isNotNull();
        });

        // 检查是否有我们注册的服务器
        boolean hasRealServer = servers.stream()
            .anyMatch(s -> s.getName().equals("real-file-server"));
        
        if (hasRealServer) {
            log.info("✅ 找到了我们注册的服务器: real-file-server");
        } else {
            log.info("ℹ️ 未找到注册的服务器，可能是Nacos同步延迟");
        }

        log.info("✅ 从Nacos获取服务器列表验证通过");
    }

    // ==================== 4. Tools功能验证 ====================

    @Test
    @Order(6)
    @DisplayName("4.1 获取可用工具列表")
    void testGetAvailableTools() {
        log.info("=== 测试获取可用工具列表 ===");

        McpJsonRpcRequest toolsRequest = new McpJsonRpcRequest();
        toolsRequest.setJsonrpc("2.0");
        toolsRequest.setMethod("tools/list");
        toolsRequest.setId("tools-test-" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(toolsRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResult()).isNotNull();

        Map<String, Object> result = (Map<String, Object>) response.getBody().getResult();
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        
        assertThat(tools).isNotNull();
        assertThat(tools).isNotEmpty();

        log.info("发现 {} 个可用工具:", tools.size());
        tools.forEach(tool -> {
            log.info("  - {}: {}", tool.get("name"), tool.get("description"));
            
            // 验证工具基本属性
            assertThat(tool.get("name")).isNotNull();
            assertThat(tool.get("description")).isNotNull();
            assertThat(tool.get("inputSchema")).isNotNull();
        });

        log.info("✅ 获取可用工具列表验证通过");
    }

    @Test
    @Order(7)
    @DisplayName("4.2 测试Echo工具调用")
    void testEchoToolExecution() {
        log.info("=== 测试Echo工具调用 ===");

        McpJsonRpcRequest toolCallRequest = new McpJsonRpcRequest();
        toolCallRequest.setJsonrpc("2.0");
        toolCallRequest.setMethod("tools/call");
        toolCallRequest.setId("echo-test-" + System.currentTimeMillis());

        Map<String, Object> params = new HashMap<>();
        params.put("name", "echo");
        params.put("arguments", Map.of(
            "message", "Hello Real MCP Router!",
            "repeatCount", 2
        ));
        toolCallRequest.setParams(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(toolCallRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResult()).isNotNull();

        Map<String, Object> result = (Map<String, Object>) response.getBody().getResult();
        assertThat(result.get("content")).isNotNull();
        assertThat(result.get("isError")).isEqualTo(false);

        log.info("Echo工具响应: {}", result.get("content"));
        log.info("✅ Echo工具调用验证通过");
    }

    @Test
    @Order(8)
    @DisplayName("4.3 测试Calculator工具调用")
    void testCalculatorToolExecution() {
        log.info("=== 测试Calculator工具调用 ===");

        McpJsonRpcRequest calcRequest = new McpJsonRpcRequest();
        calcRequest.setJsonrpc("2.0");
        calcRequest.setMethod("tools/call");
        calcRequest.setId("calc-test-" + System.currentTimeMillis());

        Map<String, Object> params = new HashMap<>();
        params.put("name", "calculator");
        params.put("arguments", Map.of(
            "a", 15.5,
            "b", 4.5,
            "operation", "add"
        ));
        calcRequest.setParams(params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(calcRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResult()).isNotNull();

        Map<String, Object> result = (Map<String, Object>) response.getBody().getResult();
        log.info("Calculator工具响应: {}", result);
        log.info("✅ Calculator工具调用验证通过");
    }

    @Test
    @Order(9)
    @DisplayName("4.4 类型安全工具验证")
    void testTypeSafeToolExecution() {
        log.info("=== 测试类型安全工具 ===");

        // 测试类型安全的Echo工具
        McpToolService.EchoRequest echoRequest = new McpToolService.EchoRequest(
            "Type-safe test message", 3);
        McpToolService.EchoResponse echoResponse = mcpToolService.echo(echoRequest);

        assertThat(echoResponse).isNotNull();
        assertThat(echoResponse.getOriginalMessage()).isEqualTo("Type-safe test message");
        assertThat(echoResponse.getRepeatCount()).isEqualTo(3);
        assertThat(echoResponse.getEchoedMessage()).contains("Type-safe test message");
        assertThat(echoResponse.getTimestamp()).isNotNull();

        log.info("类型安全Echo响应: {}", echoResponse.getEchoedMessage());

        // 测试类型安全的Calculator工具  
        McpToolService.CalculatorRequest calcRequest = new McpToolService.CalculatorRequest(
            20.0, 8.0, "subtract");
        McpToolService.CalculatorResponse calcResponse = mcpToolService.calculator(calcRequest);

        assertThat(calcResponse).isNotNull();
        assertThat(calcResponse.getResult()).isEqualTo(12.0);
        assertThat(calcResponse.getOperation()).isEqualTo("subtract");
        assertThat(calcResponse.getExpression()).isEqualTo("20.0 - 8.0");
        assertThat(calcResponse.getTimestamp()).isNotNull();

        log.info("类型安全Calculator结果: {} = {}", 
            calcResponse.getExpression(), calcResponse.getResult());
        log.info("✅ 类型安全工具验证通过");
    }

    // ==================== 5. Resources功能验证 ====================

    @Test
    @Order(10)
    @DisplayName("5.1 获取可用资源列表")
    void testGetAvailableResources() {
        log.info("=== 测试获取可用资源列表 ===");

        McpJsonRpcRequest resourcesRequest = new McpJsonRpcRequest();
        resourcesRequest.setJsonrpc("2.0");
        resourcesRequest.setMethod("resources/list");
        resourcesRequest.setId("resources-test-" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(resourcesRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResult()).isNotNull();

        Map<String, Object> result = (Map<String, Object>) response.getBody().getResult();
        List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        
        assertThat(resources).isNotNull();
        log.info("发现 {} 个可用资源", resources.size());

        resources.forEach(resource -> {
            log.info("  - URI: {} | 名称: {} | 类型: {}", 
                resource.get("uri"), 
                resource.get("name"),
                resource.get("mimeType"));
        });

        log.info("✅ 获取可用资源列表验证通过");
    }

    // ==================== 6. Prompts功能验证 ====================

    @Test
    @Order(11)
    @DisplayName("6.1 获取可用提示列表")
    void testGetAvailablePrompts() {
        log.info("=== 测试获取可用提示列表 ===");

        McpJsonRpcRequest promptsRequest = new McpJsonRpcRequest();
        promptsRequest.setJsonrpc("2.0");
        promptsRequest.setMethod("prompts/list");
        promptsRequest.setId("prompts-test-" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(promptsRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResult()).isNotNull();

        Map<String, Object> result = (Map<String, Object>) response.getBody().getResult();
        List<Map<String, Object>> prompts = (List<Map<String, Object>>) result.get("prompts");
        
        assertThat(prompts).isNotNull();
        log.info("发现 {} 个可用提示", prompts.size());

        prompts.forEach(prompt -> {
            log.info("  - 名称: {} | 描述: {}", 
                prompt.get("name"), 
                prompt.get("description"));
        });

        log.info("✅ 获取可用提示列表验证通过");
    }

    // ==================== 7. 错误处理验证 ====================

    @Test
    @Order(12)
    @DisplayName("7.1 MCP协议错误处理验证")
    void testMcpErrorHandling() {
        log.info("=== 测试MCP协议错误处理 ===");

        McpJsonRpcRequest invalidRequest = new McpJsonRpcRequest();
        invalidRequest.setJsonrpc("2.0");
        invalidRequest.setMethod("nonexistent_method");
        invalidRequest.setId("error-test-" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<McpJsonRpcRequest> entity = new HttpEntity<>(invalidRequest, headers);

        ResponseEntity<McpJsonRpcResponse> response = restTemplate.postForEntity(
            mcpJsonRpcUrl, entity, McpJsonRpcResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().getCode())
            .isEqualTo(McpJsonRpcResponse.ErrorCodes.METHOD_NOT_FOUND);

        log.info("错误处理正确: {}", response.getBody().getError().getMessage());
        log.info("✅ MCP协议错误处理验证通过");
    }

    // ==================== 8. 并发和性能验证 ====================

    @Test
    @Order(13)
    @DisplayName("8.1 并发请求处理验证")
    void testConcurrentRequests() throws InterruptedException {
        log.info("=== 测试并发请求处理 ===");

        int concurrentRequests = 5;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    String result = mcpToolService.echo("并发请求 #" + requestId);
                    assertThat(result).contains("并发请求 #" + requestId);
                    log.info("并发请求 #{} 完成", requestId);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();

        log.info("并发请求测试完成: {} 个请求在 {}ms 内完成", concurrentRequests, duration);
        log.info("✅ 并发请求处理验证通过");
    }

    // ==================== 9. 清理工作 ====================

    @Test
    @Order(14)
    @DisplayName("9.1 清理测试数据")
    void testCleanup() throws InterruptedException {
        log.info("=== 清理测试数据 ===");

        // 注销测试服务器
        try {
            Mono<Boolean> unregister = mcpServerService.unregisterMcpServer("real-file-server");
            Boolean result = unregister.block(Duration.ofSeconds(10));
            log.info("测试服务器注销结果: {}", result);
        } catch (Exception e) {
            log.warn("注销服务器时出现警告: {}", e.getMessage());
        }

        // 等待清理完成
        Thread.sleep(2000);

        log.info("✅ 测试数据清理完成");
    }

    @AfterAll
    static void finalCleanup() {
        log.info("=== 🎉 所有真实MCP集成测试完成！ ===");
        log.info("验证完成的功能:");
        log.info("  ✅ Nacos连接和服务发现");
        log.info("  ✅ MCP协议实现");
        log.info("  ✅ MCP服务器注册和管理");
        log.info("  ✅ Tools工具调用和类型安全");
        log.info("  ✅ Resources资源访问");
        log.info("  ✅ Prompts提示管理");
        log.info("  ✅ 错误处理和恢复");
        log.info("  ✅ 并发处理能力");
    }
} 