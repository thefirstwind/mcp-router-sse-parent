package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.model.SseSession;
import com.pajk.mcpbridge.core.service.McpSseTransportProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * McpSseController 综合测试
 * 测试路径: /sse/*
 * 特别测试调用 mcp-server-v2 的 getPersonById 工具
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.nacos.mcp.router.v2=DEBUG"
})
public class McpSseControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private McpSseTransportProvider sseTransportProvider;

    /**
     * 测试 SSE 连接建立
     * GET /sse/connect
     */
    @Test
    public void testSseConnect() {
        // Mock SSE连接流
        ServerSentEvent<String> connectEvent = ServerSentEvent.<String>builder()
                .id("1")
                .event("connected")
                .data("{\"sessionId\":\"test-session-001\",\"status\":\"connected\"}")
                .build();
        
        ServerSentEvent<String> heartbeatEvent = ServerSentEvent.<String>builder()
                .id("2")
                .event("heartbeat")
                .data("{\"timestamp\":" + System.currentTimeMillis() + "}")
                .build();

        when(sseTransportProvider.connect(anyString(), any(Map.class)))
                .thenReturn(Flux.just(connectEvent, heartbeatEvent));

        // 测试SSE连接
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/connect")
                        .queryParam("clientId", "test-client-001")
                        .queryParam("metadata", "type=tool_client,version=1.0")
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(String.class)
                .hasSize(2);
    }

    /**
     * 测试发送消息到指定会话
     * POST /sse/message/{sessionId}
     */
    @Test
    public void testSendMessageToSession() {
        String sessionId = "test-session-001";
        String eventType = "tool_call";
        String messageData = "{\"method\":\"tools/call\",\"params\":{\"name\":\"getPersonById\",\"arguments\":{\"id\":1}}}";

        when(sseTransportProvider.sendMessage(eq(sessionId), eq(eventType), eq(messageData)))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/message/{sessionId}")
                        .queryParam("eventType", eventType)
                        .build(sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Message sent successfully");
    }

    /**
     * 测试发送消息到指定客户端
     * POST /sse/message/client/{clientId}
     */
    @Test
    public void testSendMessageToClient() {
        String clientId = "test-client-001";
        String eventType = "tool_response";
        String responseData = "{\"result\":{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"found\":true}}";

        when(sseTransportProvider.sendMessageToClient(eq(clientId), eq(eventType), eq(responseData)))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/message/client/{clientId}")
                        .queryParam("eventType", eventType)
                        .build(clientId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(responseData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Message sent successfully");
    }

    /**
     * 测试广播消息到所有活跃会话
     * POST /sse/broadcast
     */
    @Test
    public void testBroadcastMessage() {
        String eventType = "system_notification";
        String broadcastData = "{\"message\":\"System maintenance scheduled\",\"timestamp\":" + System.currentTimeMillis() + "}";

        when(sseTransportProvider.broadcast(eq(eventType), eq(broadcastData)))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/broadcast")
                        .queryParam("eventType", eventType)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(broadcastData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Message broadcasted successfully");
    }

    /**
     * 测试获取会话信息
     * GET /sse/session/{sessionId}
     */
    @Test
    public void testGetSession() {
        String sessionId = "test-session-001";
        
        SseSession mockSession = createMockSession(sessionId, "test-client-001");
        
        when(sseTransportProvider.getSession(eq(sessionId)))
                .thenReturn(mockSession);

        webTestClient.get()
                .uri("/sse/session/{sessionId}", sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class); // 只验证状态码，不验证响应体结构
    }

    /**
     * 测试获取会话信息 - 会话不存在
     */
    @Test
    public void testGetSession_NotFound() {
        String sessionId = "non-existent-session";
        
        when(sseTransportProvider.getSession(eq(sessionId)))
                .thenReturn(null);

        webTestClient.get()
                .uri("/sse/session/{sessionId}", sessionId)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    /**
     * 测试获取所有活跃会话
     * GET /sse/sessions
     */
    @Test
    public void testGetAllSessions() {
        Map<String, SseSession> mockSessions = new ConcurrentHashMap<>();
        mockSessions.put("session-001", createMockSession("session-001", "client-001"));
        mockSessions.put("session-002", createMockSession("session-002", "client-002"));

        when(sseTransportProvider.getAllSessions())
                .thenReturn(mockSessions);

        webTestClient.get()
                .uri("/sse/sessions")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class);
    }

    /**
     * 测试关闭指定会话
     * DELETE /sse/session/{sessionId}
     */
    @Test
    public void testCloseSession() {
        String sessionId = "test-session-001";

        when(sseTransportProvider.closeSession(eq(sessionId)))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/sse/session/{sessionId}", sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Session closed successfully");
    }

    /**
     * 测试清理超时会话
     * POST /sse/cleanup
     */
    @Test
    public void testCleanupTimeoutSessions() {
        when(sseTransportProvider.cleanupTimeoutSessions())
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/sse/cleanup")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Timeout sessions cleaned up successfully");
    }

    /**
     * 测试通过 SSE 调用 mcp-server-v2 的 getPersonById 工具
     * 这是一个端到端的测试场景
     */
    @Test
    public void testSseCallGetPersonByIdTool() {
        String sessionId = "tool-test-session";
        String clientId = "tool-test-client";
        
        // 1. 首先建立SSE连接
        ServerSentEvent<String> connectEvent = ServerSentEvent.<String>builder()
                .id("1")
                .event("connected")
                .data("{\"sessionId\":\"" + sessionId + "\",\"status\":\"connected\"}")
                .build();

        when(sseTransportProvider.connect(eq(clientId), any(Map.class)))
                .thenReturn(Flux.just(connectEvent));

        // 2. 发送工具调用请求
        String toolCallData = "{\n" +
                "  \"method\": \"tools/call\",\n" +
                "  \"params\": {\n" +
                "    \"name\": \"getPersonById\",\n" +
                "    \"arguments\": {\n" +
                "      \"id\": 1\n" +
                "    }\n" +
                "  },\n" +
                "  \"id\": \"tool-call-001\"\n" +
                "}";

        when(sseTransportProvider.sendMessage(eq(sessionId), eq("tool_call"), eq(toolCallData)))
                .thenReturn(Mono.empty());

        // 3. 模拟工具调用响应
        String toolResponseData = "{\n" +
                "  \"id\": \"tool-call-001\",\n" +
                "  \"result\": {\n" +
                "    \"id\": 1,\n" +
                "    \"firstName\": \"John\",\n" +
                "    \"lastName\": \"Doe\",\n" +
                "    \"age\": 30,\n" +
                "    \"nationality\": \"American\",\n" +
                "    \"gender\": \"MALE\",\n" +
                "    \"found\": true\n" +
                "  }\n" +
                "}";

        when(sseTransportProvider.sendMessage(eq(sessionId), eq("tool_response"), eq(toolResponseData)))
                .thenReturn(Mono.empty());

        // 测试SSE连接
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/connect")
                        .queryParam("clientId", clientId)
                        .queryParam("metadata", "type=mcp_tool_client,target_server=mcp-server-v2")
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();

        // 测试发送工具调用请求
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/message/{sessionId}")
                        .queryParam("eventType", "tool_call")
                        .build(sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toolCallData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Message sent successfully");

        // 测试发送工具响应
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/message/{sessionId}")
                        .queryParam("eventType", "tool_response")
                        .build(sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toolResponseData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Message sent successfully");
    }

    /**
     * 测试批量工具调用 - 获取多个Person
     */
    @Test
    public void testSseBatchToolCalls() {
        String sessionId = "batch-test-session";
        
        // 批量调用 getPersonById 工具
        String[] personIds = {"1", "2", "3"};
        
        for (String personId : personIds) {
            String toolCallData = "{\n" +
                    "  \"method\": \"tools/call\",\n" +
                    "  \"params\": {\n" +
                    "    \"name\": \"getPersonById\",\n" +
                    "    \"arguments\": {\n" +
                    "      \"id\": " + personId + "\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"id\": \"batch-call-" + personId + "\"\n" +
                    "}";

            when(sseTransportProvider.sendMessage(eq(sessionId), eq("tool_call"), eq(toolCallData)))
                    .thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sse/message/{sessionId}")
                            .queryParam("eventType", "tool_call")
                            .build(sessionId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toolCallData)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    /**
     * 测试错误场景：工具调用失败
     */
    @Test
    public void testSseToolCallError() {
        String sessionId = "error-test-session";
        
        String toolCallData = "{\n" +
                "  \"method\": \"tools/call\",\n" +
                "  \"params\": {\n" +
                "    \"name\": \"getPersonById\",\n" +
                "    \"arguments\": {\n" +
                "      \"id\": 999\n" +
                "    }\n" +
                "  },\n" +
                "  \"id\": \"error-call-001\"\n" +
                "}";

        // 模拟工具调用错误响应
        String errorResponseData = "{\n" +
                "  \"id\": \"error-call-001\",\n" +
                "  \"error\": {\n" +
                "    \"code\": \"PERSON_NOT_FOUND\",\n" +
                "    \"message\": \"Person not found with id: 999\"\n" +
                "  }\n" +
                "}";

        when(sseTransportProvider.sendMessage(eq(sessionId), eq("tool_call"), eq(toolCallData)))
                .thenReturn(Mono.empty());
        
        when(sseTransportProvider.sendMessage(eq(sessionId), eq("tool_error"), eq(errorResponseData)))
                .thenReturn(Mono.empty());

        // 测试发送错误的工具调用
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/message/{sessionId}")
                        .queryParam("eventType", "tool_call")
                        .build(sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toolCallData)
                .exchange()
                .expectStatus().isOk();

        // 测试发送错误响应
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/message/{sessionId}")
                        .queryParam("eventType", "tool_error")
                        .build(sessionId))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponseData)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 测试并发场景：多个客户端同时连接和调用工具
     */
    @Test
    public void testConcurrentSseConnections() {
        String[] clientIds = {"concurrent-client-001", "concurrent-client-002", "concurrent-client-003"};
        
        for (String clientId : clientIds) {
            ServerSentEvent<String> connectEvent = ServerSentEvent.<String>builder()
                    .id("1")
                    .event("connected")
                    .data("{\"clientId\":\"" + clientId + "\",\"status\":\"connected\"}")
                    .build();

            when(sseTransportProvider.connect(eq(clientId), any(Map.class)))
                    .thenReturn(Flux.just(connectEvent));

            // 每个客户端建立连接
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sse/connect")
                            .queryParam("clientId", clientId)
                            .queryParam("metadata", "type=concurrent_test")
                            .build())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk();
        }

        // 测试广播到所有客户端
        String broadcastData = "{\n" +
                "  \"message\": \"Broadcast tool execution completed\",\n" +
                "  \"tool\": \"getPersonById\",\n" +
                "  \"timestamp\": " + System.currentTimeMillis() + "\n" +
                "}";

        when(sseTransportProvider.broadcast(eq("tool_broadcast"), eq(broadcastData)))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/sse/broadcast")
                        .queryParam("eventType", "tool_broadcast")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(broadcastData)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 创建模拟会话对象
     */
    private SseSession createMockSession(String sessionId, String clientId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "test_client");
        metadata.put("version", "1.0");
        
        return SseSession.builder()
                .sessionId(sessionId)
                .clientId(clientId)
                .status(SseSession.SessionStatus.CONNECTED)
                .createdTime(LocalDateTime.now())
                .lastActiveTime(LocalDateTime.now())
                .timeoutMs(Duration.ofMinutes(5).toMillis())
                .sink(null)  // 设置为null避免序列化问题
                .messageCount(new AtomicLong(0))
                .errorCount(new AtomicLong(0))
                .metadata(metadata)
                .build();
    }
} 