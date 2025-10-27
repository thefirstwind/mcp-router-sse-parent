package com.pajk.mcpbridge.core.service;

import com.pajk.mcpbridge.core.model.McpServerInfo;
import com.pajk.mcpbridge.core.registry.McpServerRegistry;
import io.modelcontextprotocol.client.McpAsyncClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

/**
 * MCP协议健康检查服务测试
 */
@RunWith(MockitoJUnitRunner.class)
public class HealthCheckServiceTest {

    @Mock
    private McpServerRegistry serverRegistry;
    
    @Mock
    private McpClientManager mcpClientManager;
    
    @Mock
    private CircuitBreakerService circuitBreakerService;
    
    @Mock
    private McpAsyncClient mcpAsyncClient;
    
    @InjectMocks
    private HealthCheckService healthCheckService;

    /**
     * 测试MCP协议健康检查 - 成功案例
     */
    @Test
    public void testMcpHealthCheckSuccess() {
        // 准备测试数据
        McpServerInfo serverInfo = McpServerInfo.builder()
                .name("test-mcp-server")
                .ip("127.0.0.1")
                .port(8061)
                .sseEndpoint("/sse")
                .build();

        // Mock依赖调用 - 客户端创建成功
        when(mcpClientManager.getOrCreateMcpClient(any(McpServerInfo.class)))
                .thenReturn(Mono.just(mcpAsyncClient));

        // 执行测试
        Mono<HealthCheckService.HealthStatus> result = 
                healthCheckService.checkServerHealthWithMcp(serverInfo);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(status -> {
                    assertNotNull(status);
                    assertTrue("Health check should pass when MCP client connects successfully", 
                            status.isHealthy());
                    assertEquals("test-mcp-server", status.getServiceName());
                    assertTrue("Success count should be positive", status.getSuccessCount() > 0);
                })
                .verifyComplete();

        System.out.println("✅ MCP协议健康检查成功测试通过");
    }

    /**
     * 测试MCP协议健康检查 - 失败案例
     */
    @Test
    public void testMcpHealthCheckFailure() {
        // 准备测试数据
        McpServerInfo serverInfo = McpServerInfo.builder()
                .name("failing-mcp-server")
                .ip("127.0.0.1")
                .port(8062)
                .sseEndpoint("/sse")
                .build();

        // Mock客户端创建失败
        when(mcpClientManager.getOrCreateMcpClient(any(McpServerInfo.class)))
                .thenReturn(Mono.error(new RuntimeException("MCP connection failed")));

        // 执行多次健康检查以达到失败阈值（FAILURE_THRESHOLD = 3）
        Mono<HealthCheckService.HealthStatus> result1 = 
                healthCheckService.checkServerHealthWithMcp(serverInfo);
        Mono<HealthCheckService.HealthStatus> result2 = 
                healthCheckService.checkServerHealthWithMcp(serverInfo);
        Mono<HealthCheckService.HealthStatus> result3 = 
                healthCheckService.checkServerHealthWithMcp(serverInfo);

        // 验证第三次检查后状态变为不健康
        StepVerifier.create(
                result1.then(result2).then(result3)
        )
                .assertNext(status -> {
                    assertNotNull(status);
                    assertFalse("Health check should fail when MCP connection fails after 3 consecutive failures", 
                            status.isHealthy());
                    assertEquals("failing-mcp-server", status.getServiceName());
                    assertTrue("Failure count should be 3", status.getFailureCount() >= 3);
                    assertTrue("Consecutive failures should be 3", status.getConsecutiveFailures() >= 3);
                })
                .verifyComplete();

        System.out.println("✅ MCP协议健康检查失败测试通过");
    }

    /**
     * 测试手动触发健康检查
     */
    @Test
    public void testTriggerHealthCheck() {
        // 准备测试数据
        McpServerInfo serverInfo = McpServerInfo.builder()
                .name("manual-check-server")
                .ip("127.0.0.1")
                .port(8063)
                .sseEndpoint("/sse")
                .build();

        // Mock依赖调用
        when(serverRegistry.getAllInstances(anyString(), anyString()))
                .thenReturn(Flux.just(serverInfo));
        when(mcpClientManager.getOrCreateMcpClient(any(McpServerInfo.class)))
                .thenReturn(Mono.just(mcpAsyncClient));

        // 执行测试
        Mono<Void> result = healthCheckService.triggerHealthCheck("manual-check-server", "mcp-server");

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete();

        // 验证健康状态已更新
        HealthCheckService.HealthStatus status = 
                healthCheckService.getServiceHealthStatus("manual-check-server");
        assertNotNull("Health status should be updated after manual trigger", status);

        System.out.println("✅ 手动触发MCP健康检查测试通过");
    }

    /**
     * 测试熔断器状态更新
     */
    @Test
    public void testCircuitBreakerIntegration() {
        // 准备失败的服务器信息
        McpServerInfo serverInfo = McpServerInfo.builder()
                .name("circuit-test-server")
                .ip("127.0.0.1")
                .port(8064)
                .sseEndpoint("/sse")
                .build();

        // Mock连续失败
        when(mcpClientManager.getOrCreateMcpClient(any(McpServerInfo.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection failed")));

        // 执行多次健康检查以触发熔断器
        for (int i = 0; i < 4; i++) {
            StepVerifier.create(healthCheckService.checkServerHealthWithMcp(serverInfo))
                    .assertNext(status -> {
                        assertTrue("Failure count should increase with each failed check", 
                                status.getFailureCount() > 0);
                    })
                    .verifyComplete();
        }

        System.out.println("✅ 熔断器集成测试通过");
    }

    /**
     * 测试健康状态缓存
     */
    @Test
    public void testHealthStatusCache() {
        // 准备测试数据
        McpServerInfo serverInfo = McpServerInfo.builder()
                .name("cache-test-server")
                .ip("127.0.0.1")
                .port(8065)
                .sseEndpoint("/sse")
                .build();

        // Mock成功的MCP调用
        when(mcpClientManager.getOrCreateMcpClient(any(McpServerInfo.class)))
                .thenReturn(Mono.just(mcpAsyncClient));

        // 执行健康检查
        StepVerifier.create(healthCheckService.checkServerHealthWithMcp(serverInfo))
                .assertNext(status -> {
                    assertTrue("Initial MCP health check should pass", status.isHealthy());
                })
                .verifyComplete();

        // 验证缓存
        HealthCheckService.HealthStatus cachedStatus = 
                healthCheckService.getServiceHealthStatus("cache-test-server");
        assertNotNull("Health status should be cached", cachedStatus);
        assertTrue("Cached status should be healthy", cachedStatus.isHealthy());

        // 验证所有状态
        var allStatuses = healthCheckService.getAllHealthStatus();
        assertTrue("Should have at least one cached status", allStatuses.size() > 0);

        System.out.println("✅ 健康状态缓存测试通过");
    }

    /**
     * 测试健康状态阈值逻辑
     */
    @Test
    public void testHealthStatusThresholds() {
        // 创建健康状态实例
        HealthCheckService.HealthStatus status = new HealthCheckService.HealthStatus(
                "threshold-test:127.0.0.1:8066", "threshold-test-server");

        // 测试初始状态
        assertTrue("Initial status should be healthy", status.isHealthy());
        assertFalse("Should not trigger circuit breaker initially", status.shouldOpenCircuit());

        // 模拟连续失败
        status.recordFailure(); // 1st failure
        assertTrue("Should still be healthy after 1 failure", status.isHealthy());
        
        status.recordFailure(); // 2nd failure
        assertTrue("Should still be healthy after 2 failures", status.isHealthy());
        
        status.recordFailure(); // 3rd failure - should trigger circuit breaker
        assertFalse("Should be unhealthy after 3 consecutive failures", status.isHealthy());
        assertTrue("Should trigger circuit breaker after 3 failures", status.shouldOpenCircuit());

        // 测试恢复
        status.recordSuccess();
        status.recordSuccess(); // 2nd success
        assertTrue("Should close circuit breaker after 2 successes", status.shouldCloseCircuit());

        System.out.println("✅ 健康状态阈值逻辑测试通过");
    }
} 