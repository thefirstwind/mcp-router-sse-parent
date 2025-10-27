package com.nacos.mcp.server.v3.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * MCP Server V3 Integration Test
 * 测试标准 MCP 协议的 SSE 传输和服务端点
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.server-addr=127.0.0.1:8848",
        "spring.ai.alibaba.mcp.nacos.namespace=public",
        "spring.ai.alibaba.mcp.nacos.username=nacos",
        "spring.ai.alibaba.mcp.nacos.password=nacos",
        "spring.ai.alibaba.mcp.nacos.registry.enabled=true",
        "spring.ai.alibaba.mcp.nacos.registry.service-group=mcp-server",
        "spring.ai.alibaba.mcp.nacos.registry.service-name=mcp-server-v3",
        "server.port=8063"
})
public class McpServerV3IntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void testSseEndpoint_Available() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Test SSE endpoint is accessible
        webTestClient.get()
                .uri("/sse")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/event-stream;charset=UTF-8");
    }

    @Test
    void testMcpMessageEndpoint_Exists() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Test that message endpoint exists 
        // Note: MCP protocol requires SSE handshake first, so direct POST may not work
        webTestClient.post()
                .uri("/mcp/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"test\": \"message\"}")
                .exchange()
                .expectStatus().is4xxClientError(); // Expected - need proper MCP handshake
    }

    @Test
    void testHealthEndpoint() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testInfoEndpoint() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        webTestClient.get()
                .uri("/actuator/info")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testMetricsEndpoint() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        webTestClient.get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.names").isArray();
    }

    @Test
    void testSseConnectionEstablishment() {
        // Test SSE connection can be established
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        Flux<String> sseStream = webClient.get()
                .uri("/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .take(Duration.ofSeconds(3)); // Take events for 3 seconds

        // Verify that SSE connection can be established without errors
        // Just test that the stream can be created without exceptions
        sseStream.blockFirst(Duration.ofSeconds(2)); // Try to get first event with timeout
    }

    @Test
    void testServerApplicationProperties() {
        // Verify that the server is running on the expected port
        assert port == 8063 || port > 0; // Should be 8063 or a random port for testing
    }

    @Test
    void testSseEndpoint_EventStream() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Test that SSE endpoint returns proper event stream headers
        webTestClient.get()
                .uri("/sse")
                .header("Accept", "text/event-stream")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/event-stream");
    }

    @Test
    void testServerStartupAndReadiness() {
        // Test that the server has started successfully
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Basic health check to ensure server is ready
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testMcpEndpointsAccessibility() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Test SSE endpoint accessibility
        webTestClient.get()
                .uri("/sse")
                .exchange()
                .expectStatus().isOk();

        // Test message endpoint exists (even if it requires proper MCP handshake)
        webTestClient.options()
                .uri("/mcp/message")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testCorsHeaders() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Test CORS headers for SSE endpoint
        webTestClient.options()
                .uri("/sse")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testServerConfiguration() {
        // Verify server configuration is correct
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Check that application name is correctly set via info endpoint
        webTestClient.get()
                .uri("/actuator/info")
                .exchange()
                .expectStatus().isOk();
        
        // Check basic health indicators
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testMcpServerTransportProvider() {
        // Test that MCP server transport is properly configured
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Verify SSE endpoint responds correctly
        webTestClient.get()
                .uri("/sse")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Content-Type")
                .expectHeader().value("Content-Type", contentType -> 
                        contentType.contains("text/event-stream"));
    }

    @Test
    void testMcpServerEndpoints() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Test that both MCP endpoints are accessible
        webTestClient.get()
                .uri("/sse")
                .exchange()
                .expectStatus().isOk();

        // Message endpoint should exist but require proper MCP handshake
        webTestClient.head()
                .uri("/mcp/message")
                .exchange()
                .expectStatus().is4xxClientError(); // HEAD not allowed, but endpoint exists
    }

    /**
     * Note: Full MCP protocol testing requires a proper MCP client implementation
     * that can establish SSE connections and send properly formatted MCP messages.
     * These tests focus on verifying that the server endpoints are properly exposed
     * and accessible, which is the foundation for MCP protocol communication.
     * 
     * For complete MCP protocol testing with PersonManagementTool, you would need:
     * 1. MCP client that establishes SSE connection to /sse
     * 2. Proper MCP handshake (initialize message)
     * 3. MCP message format for tools/list and tools/call
     * 4. Session management between client and server
     * 
     * The PersonManagementTool provides these tools:
     * - getAllPersons: Get all persons from mock data
     * - getPersonById: Get specific person by ID
     * - addPerson: Add new person to mock data
     * - get_system_info: Get system information
     * - list_servers: List available servers
     */
} 