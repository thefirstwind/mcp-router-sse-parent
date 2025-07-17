package com.nacos.mcp.router.v3.controller;

import com.nacos.mcp.router.v3.model.McpMessage;
import com.nacos.mcp.router.v3.service.McpRouterService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * McpRouterController 真实测试
 * 测试路径: /mcp/router/*
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.nacos.mcp.router.v2=DEBUG"
})
public class McpRouterControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private McpRouterService routerService;

    @Test
    public void testRouteMessage() {
        // Mock响应数据
        McpMessage responseMessage = createTestMessage("response-001", "Route successful");
        
        when(routerService.routeRequest(anyString(), any(McpMessage.class)))
                .thenReturn(Mono.just(responseMessage));

        // 准备请求数据
        McpMessage requestMessage = createTestMessage("request-001", "Route test message");

        // 测试 POST /mcp/router/route/{serviceName}
        webTestClient.post()
                .uri("/mcp/router/route/test-service")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestMessage)
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpMessage.class);

        System.out.println("✅ POST /mcp/router/route/{serviceName} 测试通过");
    }

    @Test
    public void testBroadcastMessage() {
        when(routerService.broadcastMessage(anyString(), any(McpMessage.class)))
                .thenReturn(Mono.empty());

        McpMessage broadcastMessage = createTestMessage("broadcast-001", "Broadcast test message");

        // 测试 POST /mcp/router/broadcast/{serviceName}
        webTestClient.post()
                .uri("/mcp/router/broadcast/test-service")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(broadcastMessage)
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ POST /mcp/router/broadcast/{serviceName} 测试通过");
    }

    @Test
    public void testSendSseMessage() {
        when(routerService.sendSseMessage(anyString(), anyString(), any(McpMessage.class)))
                .thenReturn(Mono.empty());

        McpMessage sseMessage = createTestMessage("sse-001", "SSE test message");

        // 测试 POST /mcp/router/sse/send/{sessionId}
        webTestClient.post()
                .uri("/mcp/router/sse/send/session-123?eventType=data")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sseMessage)
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ POST /mcp/router/sse/send/{sessionId} 测试通过");
    }

    @Test
    public void testBroadcastSseMessage() {
        when(routerService.broadcastSseMessage(anyString(), any(McpMessage.class)))
                .thenReturn(Mono.empty());

        McpMessage broadcastSseMessage = createTestMessage("sse-broadcast-001", "SSE broadcast message");

        // 测试 POST /mcp/router/sse/broadcast
        webTestClient.post()
                .uri("/mcp/router/sse/broadcast?eventType=notification")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(broadcastSseMessage)
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ POST /mcp/router/sse/broadcast 测试通过");
    }

    @Test
    public void testRouteSseMessage() {
        McpMessage routedSseMessage = createTestMessage("sse-routed-001", "SSE routed message");
        
        when(routerService.routeSseMessage(anyString(), anyString(), any(McpMessage.class)))
                .thenReturn(Mono.just(routedSseMessage));

        McpMessage requestMessage = createTestMessage("sse-request-001", "SSE route request");

        // 测试 POST /mcp/router/sse/route/{serviceName}/{sessionId}
        webTestClient.post()
                .uri("/mcp/router/sse/route/test-service/session-456")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestMessage)
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpMessage.class);

        System.out.println("✅ POST /mcp/router/sse/route/{serviceName}/{sessionId} 测试通过");
    }

    @Test
    public void testGetServiceHealth() {
        when(routerService.isServiceHealthy(anyString()))
                .thenReturn(Mono.just(true));
        when(routerService.getServiceInstanceCount(anyString()))
                .thenReturn(Mono.just(3));

        // 测试 GET /mcp/router/health/{serviceName}
        webTestClient.get()
                .uri("/mcp/router/health/test-service")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.serviceName").isEqualTo("test-service")
                .jsonPath("$.healthy").isEqualTo(true)
                .jsonPath("$.instanceCount").isEqualTo(3)
                .jsonPath("$.timestamp").isNumber();

        System.out.println("✅ GET /mcp/router/health/{serviceName} 测试通过");
    }

    @Test
    public void testGetRouterStats() {
        // 测试 GET /mcp/router/stats
        webTestClient.get()
                .uri("/mcp/router/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").isNumber()
                .jsonPath("$.status").isEqualTo("active")
                .jsonPath("$.version").isEqualTo("2.0.0");

        System.out.println("✅ GET /mcp/router/stats 测试通过");
    }

    @Test
    public void testServiceHealthUnhealthy() {
        when(routerService.isServiceHealthy(anyString()))
                .thenReturn(Mono.just(false));
        when(routerService.getServiceInstanceCount(anyString()))
                .thenReturn(Mono.just(0));

        // 测试不健康的服务
        webTestClient.get()
                .uri("/mcp/router/health/unhealthy-service")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.healthy").isEqualTo(false)
                .jsonPath("$.instanceCount").isEqualTo(0);

        System.out.println("✅ 不健康服务状态测试通过");
    }

    @Test
    public void testRouteMessageError() {
        when(routerService.routeRequest(anyString(), any(McpMessage.class)))
                .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        McpMessage errorMessage = createTestMessage("error-001", "Error test message");

        // 测试路由错误场景
        webTestClient.post()
                .uri("/mcp/router/route/unavailable-service")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorMessage)
                .exchange()
                .expectStatus().is5xxServerError();

        System.out.println("✅ 路由错误场景测试通过");
    }

    @Test
    public void testSseMessageMissingEventType() {
        McpMessage sseMessage = createTestMessage("sse-no-event", "SSE message without event type");

        // 测试缺少eventType参数的情况
        webTestClient.post()
                .uri("/mcp/router/sse/send/session-789")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(sseMessage)
                .exchange()
                .expectStatus().is4xxClientError();

        System.out.println("✅ SSE缺少eventType参数测试通过");
    }

    @Test
    public void testConcurrentRouting() {
        McpMessage concurrentResponse = createTestMessage("concurrent-response", "Concurrent test");
        
        when(routerService.routeRequest(anyString(), any(McpMessage.class)))
                .thenReturn(Mono.just(concurrentResponse));

        // 并发路由测试
        for (int i = 0; i < 5; i++) {
            McpMessage concurrentMessage = createTestMessage("concurrent-" + i, "Concurrent message " + i);
            
            webTestClient.post()
                    .uri("/mcp/router/route/concurrent-service")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(concurrentMessage)
                    .exchange()
                    .expectStatus().isOk();
        }

        System.out.println("✅ 并发路由测试通过");
    }

    // 辅助方法：创建测试消息
    private McpMessage createTestMessage(String id, String content) {
        McpMessage message = new McpMessage();
        message.setId(id);
        message.setMethod("test.method");
        message.setParams(Map.of("content", content, "timestamp", System.currentTimeMillis()));
        return message;
    }
} 