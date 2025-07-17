package com.nacos.mcp.router.v2.controller;

import com.nacos.mcp.router.v2.service.CircuitBreakerService;
import com.nacos.mcp.router.v2.service.HealthCheckService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * HealthCheckController 真实测试
 * 测试路径: /mcp/health/*
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.nacos.mcp.router.v2=DEBUG"
})
public class HealthCheckControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private HealthCheckService healthCheckService;

    @MockBean
    private CircuitBreakerService circuitBreakerService;

    @Test
    public void testGetAllHealthStatus() {
        // Mock健康状态数据
        HealthCheckService.HealthStatus healthyStatus = new HealthCheckService.HealthStatus("server1", "test-service");
        healthyStatus.recordSuccess();
        
        HealthCheckService.HealthStatus unhealthyStatus = new HealthCheckService.HealthStatus("server2", "test-service");
        unhealthyStatus.recordFailure();
        
        Map<String, HealthCheckService.HealthStatus> healthStatuses = Map.of(
                "server1", healthyStatus,
                "server2", unhealthyStatus
        );
        
        when(healthCheckService.getAllHealthStatus()).thenReturn(healthStatuses);

        // 测试 GET /mcp/health/status
        webTestClient.get()
                .uri("/mcp/health/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").isNumber()
                .jsonPath("$.healthStatuses").isMap()
                .jsonPath("$.healthStatuses.server1").exists()
                .jsonPath("$.healthStatuses.server2").exists();

        System.out.println("✅ GET /mcp/health/status 测试通过");
    }

    @Test
    public void testGetServiceHealthStatus() {
        HealthCheckService.HealthStatus healthyStatus = new HealthCheckService.HealthStatus("test-server", "test-service");
        healthyStatus.recordSuccess();
        
        when(healthCheckService.getServiceHealthStatus("test-service")).thenReturn(healthyStatus);

        // 测试 GET /mcp/health/status/{serviceName}
        webTestClient.get()
                .uri("/mcp/health/status/test-service")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("test-service")
                .jsonPath("$.found").isEqualTo(true)
                .jsonPath("$.healthy").isEqualTo(true)
                .jsonPath("$.serverId").isEqualTo("test-server");

        System.out.println("✅ GET /mcp/health/status/{serviceName} 测试通过");
    }

    @Test
    public void testGetServiceHealthStatusNotFound() {
        when(healthCheckService.getServiceHealthStatus("nonexistent-service")).thenReturn(null);

        // 测试不存在的服务
        webTestClient.get()
                .uri("/mcp/health/status/nonexistent-service")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("nonexistent-service")
                .jsonPath("$.found").isEqualTo(false);

        System.out.println("✅ 不存在服务的健康状态测试通过");
    }

    @Test
    public void testGetAllCircuitBreakerStatus() {
        // Mock熔断器状态数据
        CircuitBreakerService.CircuitBreakerState closedState = new CircuitBreakerService.CircuitBreakerState(
                "stable-service", 5, 3, Duration.ofSeconds(60));
        closedState.recordSuccess();
        
        CircuitBreakerService.CircuitBreakerState openState = new CircuitBreakerService.CircuitBreakerState(
                "unstable-service", 5, 3, Duration.ofSeconds(60));
        for (int i = 0; i < 6; i++) {
            openState.recordFailure();
        }
        
        Map<String, CircuitBreakerService.CircuitBreakerState> states = Map.of(
                "stable-service", closedState,
                "unstable-service", openState
        );
        
        when(circuitBreakerService.getAllCircuitBreakerStates()).thenReturn(states);

        // 测试 GET /mcp/health/circuit-breakers
        webTestClient.get()
                .uri("/mcp/health/circuit-breakers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").isNumber()
                .jsonPath("$.circuitBreakers").isMap()
                .jsonPath("$.circuitBreakers['stable-service']").exists()
                .jsonPath("$.circuitBreakers['unstable-service']").exists();

        System.out.println("✅ GET /mcp/health/circuit-breakers 测试通过");
    }

    @Test
    public void testGetCircuitBreakerStatus() {
        CircuitBreakerService.CircuitBreakerState state = new CircuitBreakerService.CircuitBreakerState(
                "test-service", 5, 3, Duration.ofSeconds(60));
        state.recordSuccess();
        
        when(circuitBreakerService.getCircuitBreakerState("test-service")).thenReturn(state);

        // 测试 GET /mcp/health/circuit-breakers/{serviceName}
        webTestClient.get()
                .uri("/mcp/health/circuit-breakers/test-service")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("test-service")
                .jsonPath("$.found").isEqualTo(true)
                .jsonPath("$.state").isEqualTo("CLOSED")
                .jsonPath("$.failureThreshold").isEqualTo(5)
                .jsonPath("$.successThreshold").isEqualTo(3);

        System.out.println("✅ GET /mcp/health/circuit-breakers/{serviceName} 测试通过");
    }

    @Test
    public void testGetCircuitBreakerStatusNotFound() {
        when(circuitBreakerService.getCircuitBreakerState("nonexistent-service")).thenReturn(null);

        // 测试不存在的熔断器
        webTestClient.get()
                .uri("/mcp/health/circuit-breakers/nonexistent-service")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("nonexistent-service")
                .jsonPath("$.found").isEqualTo(false);

        System.out.println("✅ 不存在熔断器状态测试通过");
    }

    @Test
    public void testResetCircuitBreaker() {
        doNothing().when(circuitBreakerService).resetCircuitBreaker("test-service");

        // 测试 POST /mcp/health/circuit-breakers/{serviceName}/reset
        webTestClient.post()
                .uri("/mcp/health/circuit-breakers/test-service/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("test-service")
                .jsonPath("$.action").isEqualTo("reset")
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.timestamp").isNumber();

        System.out.println("✅ POST /mcp/health/circuit-breakers/{serviceName}/reset 测试通过");
    }

    @Test
    public void testOpenCircuitBreaker() {
        doNothing().when(circuitBreakerService).openCircuit("test-service");

        // 测试 POST /mcp/health/circuit-breakers/{serviceName}/open
        webTestClient.post()
                .uri("/mcp/health/circuit-breakers/test-service/open")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("test-service")
                .jsonPath("$.action").isEqualTo("open")
                .jsonPath("$.success").isEqualTo(true);

        System.out.println("✅ POST /mcp/health/circuit-breakers/{serviceName}/open 测试通过");
    }

    @Test
    public void testCloseCircuitBreaker() {
        doNothing().when(circuitBreakerService).closeCircuit("test-service");

        // 测试 POST /mcp/health/circuit-breakers/{serviceName}/close
        webTestClient.post()
                .uri("/mcp/health/circuit-breakers/test-service/close")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("test-service")
                .jsonPath("$.action").isEqualTo("close")
                .jsonPath("$.success").isEqualTo(true);

        System.out.println("✅ POST /mcp/health/circuit-breakers/{serviceName}/close 测试通过");
    }

    @Test
    public void testGetHealthCheckStats() {
        // Mock统计数据
        HealthCheckService.HealthStatus healthyStatus = new HealthCheckService.HealthStatus("server1", "service1");
        healthyStatus.recordSuccess();
        
        HealthCheckService.HealthStatus unhealthyStatus = new HealthCheckService.HealthStatus("server2", "service2");
        unhealthyStatus.recordFailure();
        
        Map<String, HealthCheckService.HealthStatus> healthStatuses = Map.of(
                "service1", healthyStatus,
                "service2", unhealthyStatus
        );
        
        CircuitBreakerService.CircuitBreakerState state1 = new CircuitBreakerService.CircuitBreakerState(
                "service1", 5, 3, Duration.ofSeconds(60));
        CircuitBreakerService.CircuitBreakerState state2 = new CircuitBreakerService.CircuitBreakerState(
                "service2", 5, 3, Duration.ofSeconds(60));
        
        Map<String, CircuitBreakerService.CircuitBreakerState> circuitBreakers = Map.of(
                "service1", state1,
                "service2", state2
        );
        
        when(healthCheckService.getAllHealthStatus()).thenReturn(healthStatuses);
        when(circuitBreakerService.getAllCircuitBreakerStates()).thenReturn(circuitBreakers);

        // 测试 GET /mcp/health/stats
        webTestClient.get()
                .uri("/mcp/health/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").isNumber()
                .jsonPath("$.healthCheck").exists()
                .jsonPath("$.healthCheck.totalServices").isEqualTo(2)
                .jsonPath("$.circuitBreakers").exists()
                .jsonPath("$.circuitBreakers.totalCircuits").isEqualTo(2);

        System.out.println("✅ GET /mcp/health/stats 测试通过");
    }
} 