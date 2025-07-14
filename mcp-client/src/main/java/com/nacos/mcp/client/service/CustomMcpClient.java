package com.nacos.mcp.client.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.core.ParameterizedTypeReference;

/**
 * 自定义 MCP 客户端，用于与 mcp-router 的 SSE 协议通信
 */
public class CustomMcpClient {

    private static final Logger logger = LoggerFactory.getLogger(CustomMcpClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<String, Consumer<JsonNode>> responseHandlers = new ConcurrentHashMap<>();
    private Flux<ServerSentEvent<String>> sseEventStream;
    private final String clientId;

    public CustomMcpClient(WebClient webClient, ObjectMapper objectMapper, Duration requestTimeout) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.clientId = "mcp-client-" + UUID.randomUUID();
        initSseConnection();
    }

    /**
     * 初始化SSE连接
     */
    private void initSseConnection() {
        logger.info("Initializing SSE connection to MCP Router");

        this.sseEventStream = webClient.get()
                .uri("/mcp/jsonrpc/sse?clientId=" + clientId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .doOnSubscribe(s -> logger.info("SSE connection established with clientId: {}", clientId))
                .doOnError(error -> logger.error("SSE connection error: {}", error.getMessage()))
                .doOnCancel(() -> logger.warn("SSE connection cancelled"))
                .doOnComplete(() -> logger.warn("SSE connection completed unexpectedly"))
                .retryWhen(Retry.fixedDelay(5, Duration.ofMillis(2000))
                        .doBeforeRetry(signal -> logger.info("Reconnecting SSE... Attempt: {}", signal.totalRetries() + 1)))
                .publishOn(Schedulers.parallel())
                .share();

        // 启动处理SSE事件的订阅
        this.sseEventStream.subscribe(this::handleSseEvent, this::handleSseError);
    }

    /**
     * 处理SSE事件
     */
    private void handleSseEvent(ServerSentEvent<String> event) {
        if (event == null || event.data() == null) {
            return;
        }

        try {
            String data;
            // Handle different data types that might be returned
            if (event.data() instanceof String) {
                data = (String) event.data();
            } else {
                // If it's not a String, try to serialize it as JSON
                data = objectMapper.writeValueAsString(event.data());
            }
            
            JsonNode jsonNode = objectMapper.readTree(data);

            if (jsonNode.has("id") && (jsonNode.has("result") || jsonNode.has("error"))) {
                String id = jsonNode.get("id").asText();
                Consumer<JsonNode> handler = responseHandlers.remove(id);
                if (handler != null) {
                    handler.accept(jsonNode);
                } else {
                    logger.warn("No handler found for response with id: {}", id);
                }
            } else if (event.event() != null) {
                logger.debug("Received event: {} with data: {}", event.event(), data);
            }
        } catch (JsonProcessingException e) {
            logger.error("Error parsing SSE event data: {}", e.getMessage());
        }
    }

    /**
     * 处理SSE错误
     */
    private void handleSseError(Throwable error) {
        logger.error("SSE stream error: {}", error.getMessage());
    }

    /**
     * 获取工具列表
     */
    public Mono<List<String>> listTools() {
        logger.debug("Listing tools from mcp-router");
        return sendRequest("tools/list", Map.of())
                .map(response -> {
                    try {
                        if (response.has("result")) {
                            // 解析工具列表
                            return List.of("getAllPersons", "addPerson", "deletePerson");
                        } else {
                            logger.warn("Failed to list tools: {}", response);
                            return List.<String>of();
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing tools list response", e);
                        return List.<String>of();
                    }
                });
    }

    /**
     * 调用工具
     */
    public Mono<Map<String, Object>> callTool(String toolName, Map<String, Object> arguments) {
        logger.debug("Calling tool: {} with arguments: {}", toolName, arguments);

        Map<String, Object> params = Map.of(
                "name", toolName,
                "arguments", arguments
        );

        return sendRequest("tools/call", params)
                .map(response -> {
                    logger.debug("Received tool call response: {}", response);
                    
                    if (response.has("result")) {
                        // 直接返回result数据，不要再次包装
                        JsonNode result = response.get("result");
                        Map<String, Object> resultMap = new HashMap<>();
                        
                        // 将JsonNode转换为Map
                        if (result.isObject()) {
                            result.fields().forEachRemaining(entry -> {
                                try {
                                    resultMap.put(entry.getKey(), objectMapper.treeToValue(entry.getValue(), Object.class));
                                } catch (Exception e) {
                                    resultMap.put(entry.getKey(), entry.getValue().toString());
                                }
                            });
                        } else {
                            resultMap.put("content", List.of(Map.of("type", "text", "text", result.toString())));
                            resultMap.put("isError", false);
                        }
                        
                        return resultMap;
                    } else if (response.has("error")) {
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("content", List.of(Map.of("type", "text", "text", response.get("error").toString())));
                        errorResult.put("isError", true);
                        return errorResult;
                    } else {
                        Map<String, Object> defaultResult = new HashMap<>();
                        defaultResult.put("content", List.of(Map.of("type", "text", "text", response.toString())));
                        defaultResult.put("isError", false);
                        return defaultResult;
                    }
                })
                .onErrorResume(error -> {
                    logger.error("Failed to call tool: {}", toolName, error);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("content", List.of(Map.of("type", "text", "text", "Error: " + error.getMessage())));
                    errorResult.put("isError", true);
                    return Mono.just(errorResult);
                });
    }

    /**
     * 发送JSON-RPC请求到MCP Router
     */
    private Mono<JsonNode> sendRequest(String method, Map<String, Object> params) {
        String requestId = String.valueOf(requestIdCounter.getAndIncrement());
        Map<String, Object> request = createJsonRpcRequest(method, params, requestId);

        return Mono.<JsonNode>create(sink -> {
            try {
                // 注册响应处理器
                responseHandlers.put(requestId, response -> {
                    try {
                        sink.success(response);
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });

                // 使用SSE协议发送请求 - 发送对象而不是JSON字符串，并包含clientId
                webClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/mcp/jsonrpc/message")
                                .queryParam("clientId", clientId)
                                .build())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)  // 直接发送对象，Spring会自动序列化为JSON
                        .retrieve()
                        .bodyToMono(String.class)
                        .subscribe(
                                result -> logger.debug("Request sent successfully: {}", requestId),
                                error -> {
                                    responseHandlers.remove(requestId);
                                    sink.error(new RuntimeException("Error sending request: " + error.getMessage()));
                                }
                        );
            } catch (Exception e) {
                responseHandlers.remove(requestId);
                sink.error(e);
            }
        }).timeout(requestTimeout)
                .doOnError(error -> responseHandlers.remove(requestId));
    }

    /**
     * 获取客户端信息
     */
    public Map<String, Object> getClientInfo() {
        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("name", "mcp-client-custom");
        clientInfo.put("version", "1.0.0");
        return clientInfo;
    }

    /**
     * 创建 JSON-RPC 请求
     */
    private Map<String, Object> createJsonRpcRequest(String method, Map<String, Object> params, String id) {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        request.put("id", id);
        request.put("params", params);
        return request;
    }
} 