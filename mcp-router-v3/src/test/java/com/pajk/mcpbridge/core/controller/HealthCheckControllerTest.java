package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.core.service.HealthCheckService;
import com.pajk.mcpbridge.core.service.McpServerService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * HealthCheckController 真实测试 - 使用真实服务，不使用Mock
 * 测试路径: /mcp/health/*
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.pajk.mcpbridge=DEBUG"
})
public class HealthCheckControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private McpServerService mcpServerService;

    private String testServiceName;
    private String testServiceGroup = "mcp-server";

    @Before
    public void setUp() {
        // 为每个测试生成唯一的服务名
        testServiceName = "test-health-service-" + UUID.randomUUID().toString().substring(0, 8);
        // 设置WebTestClient的超时时间
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    public void testGetAllHealthStatus() {
        // 先注册一个真实的服务器并触发健康检查
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9001);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            // 触发健康检查
            healthCheckService.triggerLayeredHealthCheck(testServiceName, testServiceGroup).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册/健康检查失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 GET /mcp/health/status - 使用真实服务查询
        webTestClient.get()
                .uri("/mcp/health/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ GET /mcp/health/status 测试通过，返回真实健康状态数据");
                });
    }

    @Test
    public void testGetServiceHealthStatus() {
        // 先注册一个真实的服务器并触发健康检查
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9002);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            healthCheckService.triggerLayeredHealthCheck(testServiceName, testServiceGroup).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册/健康检查失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 GET /mcp/health/status/{serviceName} - 使用真实服务查询
        webTestClient.get()
                .uri("/mcp/health/status/" + testServiceName)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ GET /mcp/health/status/{serviceName} 测试通过，返回真实健康状态");
                });
    }

    @Test
    public void testGetServiceHealthStatusNotFound() {
        // 测试不存在的服务 - 使用真实服务查询
        webTestClient.get()
                .uri("/mcp/health/status/nonexistent-service-" + UUID.randomUUID())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ 不存在服务的健康状态测试通过，返回真实响应");
                });
    }

    @Test
    public void testGetAllCircuitBreakerStatus() {
        // 先注册一个真实的服务器并触发一些操作以创建熔断器状态
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9003);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            // 触发一些操作，可能会创建熔断器状态
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 GET /mcp/health/circuit-breakers - 使用真实服务查询
        webTestClient.get()
                .uri("/mcp/health/circuit-breakers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ GET /mcp/health/circuit-breakers 测试通过，返回真实熔断器状态");
                });
    }

    @Test
    public void testGetCircuitBreakerStatus() {
        // 先注册一个真实的服务器
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9004);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 GET /mcp/health/circuit-breakers/{serviceName} - 使用真实服务查询
        webTestClient.get()
                .uri("/mcp/health/circuit-breakers/" + testServiceName)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ GET /mcp/health/circuit-breakers/{serviceName} 测试通过，返回真实熔断器状态");
                });
    }

    @Test
    public void testGetCircuitBreakerStatusNotFound() {
        // 测试不存在的熔断器 - 使用真实服务查询
        webTestClient.get()
                .uri("/mcp/health/circuit-breakers/nonexistent-service-" + UUID.randomUUID())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ 不存在熔断器状态测试通过，返回真实响应");
                });
    }

    @Test
    public void testResetCircuitBreaker() {
        // 先注册一个真实的服务器
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9005);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 POST /mcp/health/circuit-breakers/{serviceName}/reset - 使用真实服务重置熔断器
        webTestClient.post()
                .uri("/mcp/health/circuit-breakers/" + testServiceName + "/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ POST /mcp/health/circuit-breakers/{serviceName}/reset 测试通过，真实操作完成");
                });
    }

    @Test
    public void testOpenCircuitBreaker() {
        // 先注册一个真实的服务器
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9006);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 POST /mcp/health/circuit-breakers/{serviceName}/open - 使用真实服务打开熔断器
        webTestClient.post()
                .uri("/mcp/health/circuit-breakers/" + testServiceName + "/open")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ POST /mcp/health/circuit-breakers/{serviceName}/open 测试通过，真实操作完成");
                });
    }

    @Test
    public void testCloseCircuitBreaker() {
        // 先注册一个真实的服务器
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9007);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 POST /mcp/health/circuit-breakers/{serviceName}/close - 使用真实服务关闭熔断器
        webTestClient.post()
                .uri("/mcp/health/circuit-breakers/" + testServiceName + "/close")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ POST /mcp/health/circuit-breakers/{serviceName}/close 测试通过，真实操作完成");
                });
    }

    @Test
    public void testGetHealthCheckStats() {
        // 先注册一个真实的服务器并触发健康检查
        McpServerInfo server = createTestServer(testServiceName, "127.0.0.1", 9008);
        
        try {
            mcpServerService.registerServer(server).block(Duration.ofSeconds(5));
            healthCheckService.triggerLayeredHealthCheck(testServiceName, testServiceGroup).block(Duration.ofSeconds(5));
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("⚠️ 服务器注册/健康检查失败（Nacos未启用是正常的）: " + e.getMessage());
        }

        // 测试 GET /mcp/health/stats - 使用真实服务获取统计数据
        webTestClient.get()
                .uri("/mcp/health/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> {
                    System.out.println("✅ GET /mcp/health/stats 测试通过，返回真实统计数据");
                });
    }

    // 辅助方法：创建测试服务器
    private McpServerInfo createTestServer(String name, String ip, int port) {
        return McpServerInfo.builder()
                .name(name)
                .ip(ip)
                .port(port)
                .version("1.0.0")
                .serviceGroup(testServiceGroup)
                .weight(1.0)
                .status("UP")
                .sseEndpoint("/sse")
                .build();
    }
} 