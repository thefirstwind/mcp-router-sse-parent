package com.pajk.mcpbridge.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.model.McpMessage;
import com.pajk.mcpbridge.core.service.McpRequestValidator;
import com.pajk.mcpbridge.core.service.McpRouterService;
import com.pajk.mcpbridge.core.service.McpSessionService;
import com.pajk.mcpbridge.core.service.McpSessionBridgeService;
import com.pajk.mcpbridge.core.service.McpSseTransportProvider;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * MCP Router Server 配置类
 * 按照 mcp-server-v6 的方式，使用标准的 MCP SSE 协议
 */
@Slf4j
@Configuration
public class McpRouterServerConfig {

    @Autowired
    private Environment environment;

    @Value("${server.port}")
    private String serverPort;

    private final McpRouterService routerService;
    private final ObjectMapper objectMapper;
    private final McpSessionService sessionService;
    private final McpRequestValidator requestValidator;
    private final McpSessionBridgeService sessionBridgeService;
    private final McpSseTransportProvider sseTransportProvider;

    public McpRouterServerConfig(McpRouterService routerService, ObjectMapper objectMapper, 
                                 McpSessionService sessionService, McpRequestValidator requestValidator,
                                 McpSessionBridgeService sessionBridgeService,
                                 McpSseTransportProvider sseTransportProvider) {
        this.routerService = routerService;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.requestValidator = requestValidator;
        this.sessionBridgeService = sessionBridgeService;
        this.sseTransportProvider = sseTransportProvider;
    }

    /**
     * 解析简单的元数据字符串（key=value,key2=value2）
     */
    private Map<String, String> parseSimpleMetadata(String metadata) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (metadata != null && !metadata.trim().isEmpty()) {
            String[] pairs = metadata.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(kv[0].trim(), kv[1].trim());
                }
            }
        }
        return result;
    }
    /**
     * 获取服务器端口
     */
    private int getServerPort() {
        String port = environment.getProperty("server.port", serverPort);
        return Integer.parseInt(port);
    }

    /**
     * 获取服务器IP地址
     */
    private String getServerIp() {
        String address = environment.getProperty("server.address", "127.0.0.1");
        // 如果配置的是 0.0.0.0（绑定所有接口），获取实际IP
        if ("0.0.0.0".equals(address)) {
            try {
                return java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                log.warn("Failed to get local IP, using 127.0.0.1", e);
                return "127.0.0.1";
            }
        }
        return address;
    }


    /**
     * 创建MCP Server Transport Provider
     * 按照MCP标准协议实现SSE传输（与 mcp-server-v6 相同）
     */
    @Bean
    @ConditionalOnMissingBean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        // 构建基础URL
        String baseUrl = "http://" + getServerIp() + ":" + getServerPort();
        log.info("Creating MCP Router Server Transport with baseUrl: {}", baseUrl);

        // 创建WebFlux SSE Server Transport Provider
        WebFluxSseServerTransportProvider provider = new WebFluxSseServerTransportProvider(
                objectMapper,
                baseUrl,
                "/mcp/message",  // 消息端点（与 mcp-server-v6 相同）
                "/sse"          // SSE端点（与 mcp-server-v6 相同）
        );

        log.info("✅ MCP Router Server Transport Provider created successfully");
        log.info("📡 SSE endpoint: {}/sse (Spring AI standard)", baseUrl);
        log.info("📡 SSE endpoint with service: {}/sse/{{serviceName}}", baseUrl);
        log.info("📨 Message endpoint: {}/mcp/message?sessionId=xxx (compatible with mcp-server-v6)", baseUrl);

        return provider;
    }

    /**
     * 创建路由函数
     * 拦截 SSE 路由以提取 serviceName，但使用 Spring AI 的标准实现处理实际连接
     * 只拦截消息路由以实现路由功能
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "mcp.router.functionalSse.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    @SuppressWarnings("unchecked")
    public RouterFunction<?> mcpRouterFunction(McpServerTransportProvider transportProvider) {
        if (transportProvider instanceof WebFluxSseServerTransportProvider) {
            WebFluxSseServerTransportProvider webFluxProvider = (WebFluxSseServerTransportProvider) transportProvider;
            // 获取标准的路由函数（处理 SSE 连接和消息）
            RouterFunction<?> standardRouter = webFluxProvider.getRouterFunction();
            
            // 拦截 SSE 路由，提取 serviceName 并记录，但使用 Spring AI 的标准实现
            // 支持路径参数方式：GET /sse/{serviceName}
            // 支持查询参数方式：GET /sse?serviceName=xxx（用于 MCP Inspector 等工具）
            // 为避免与基于注解的 /sse 管理类接口冲突（如 /sse/sessions、/sse/session/{id} 等），
            // 显式排除这些保留路径，仅对真实的 serviceName 进行匹配
            // 负向前瞻排除：sessions, session, connect, message, broadcast, cleanup, admin
            RouterFunction<ServerResponse> sseRouter = route()
                    // Mirror controller endpoints to avoid conflicts with /sse/{serviceName}
                    .GET("/sse/connect", req -> {
                        String clientId = req.queryParam("clientId").orElse("");
                        String metadata = req.queryParam("metadata").orElse(null);
                        Map<String, String> metadataMap = parseSimpleMetadata(metadata);
                        Flux<String> body = sseTransportProvider.connect(clientId, metadataMap)
                                .map(event -> event.data() == null ? "" : event.data());
                        return ServerResponse.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(BodyInserters.fromPublisher(body, String.class));
                    })
                    .POST("/sse/message/{sessionId}", req -> {
                        String sessionId = req.pathVariable("sessionId");
                        String eventType = req.queryParam("eventType").orElse("");
                        Mono<String> dataMono = req.bodyToMono(String.class);
                        return dataMono.flatMap(data ->
                                sseTransportProvider.sendMessage(sessionId, eventType, data)
                                        .then(ServerResponse.ok().bodyValue("Message sent successfully"))
                        );
                    })
                    .POST("/sse/message/client/{clientId}", req -> {
                        String clientId = req.pathVariable("clientId");
                        String eventType = req.queryParam("eventType").orElse("");
                        Mono<String> dataMono = req.bodyToMono(String.class);
                        return dataMono.flatMap(data ->
                                sseTransportProvider.sendMessageToClient(clientId, eventType, data)
                                        .then(ServerResponse.ok().bodyValue("Message sent successfully"))
                        );
                    })
                    .POST("/sse/broadcast", req -> {
                        String eventType = req.queryParam("eventType").orElse("");
                        Mono<String> dataMono = req.bodyToMono(String.class);
                        return dataMono.flatMap(data ->
                                sseTransportProvider.broadcast(eventType, data)
                                        .then(ServerResponse.ok().bodyValue("Message broadcasted successfully"))
                        );
                    })
                    .GET("/sse/session/{sessionId}", req -> {
                        String sessionId = req.pathVariable("sessionId");
                        com.pajk.mcpbridge.core.model.SseSession session = sseTransportProvider.getSession(sessionId);
                        if (session == null) {
                            return ServerResponse.status(500).build();
                        }
                        return ServerResponse.ok().bodyValue(session);
                    })
                    .GET("/sse/sessions", req ->
                            ServerResponse.ok().bodyValue(sseTransportProvider.getAllSessions())
                    )
                    .DELETE("/sse/session/{sessionId}", req -> {
                        String sessionId = req.pathVariable("sessionId");
                        return sseTransportProvider.closeSession(sessionId)
                                .then(ServerResponse.ok().bodyValue("Session closed successfully"));
                    })
                    .POST("/sse/cleanup", req ->
                            sseTransportProvider.cleanupTimeoutSessions()
                                    .then(ServerResponse.ok().bodyValue("Timeout sessions cleaned up successfully"))
                    )
                    .GET("/sse/{serviceName}", this::handleSseWithServiceName)
                    .GET("/sse", this::handleSseWithQueryParam)
                    .build();
            
            // 创建自定义的消息处理路由（支持路径参数方式：/mcp/{serviceName}/message?sessionId=xxx）
            RouterFunction<ServerResponse> messagePathRouter = route()
                    .POST("/mcp/{serviceName}/message", this::handleMcpMessageWithPath)
                    .build();
            
            // 创建自定义的消息处理路由（支持查询参数方式：/mcp/message?sessionId=xxx）
            // 这个路由会拦截标准路由，通过 sessionId 查找 serviceName 并路由
            RouterFunction<ServerResponse> messageQueryRouter = route()
                    .POST("/mcp/message", this::handleMcpMessage)
                    .build();
            
            log.info("✅ MCP Router Function created successfully");
            log.info("📡 SSE endpoint: GET /sse (with optional ?serviceName=xxx query param for MCP Inspector)");
            log.info("📡 SSE endpoint with service: GET /sse/{serviceName}");
            log.info("📨 Message endpoint: POST /mcp/message?sessionId=xxx (routed by sessionId)");
            log.info("📨 Message endpoint: POST /mcp/{serviceName}/message?sessionId=xxx (routed by path)");
            
            // 预检请求（CORS）显式支持，避免 MCP Inspector 断开
            RouterFunction<ServerResponse> corsOptions = route()
                    .OPTIONS("/sse", req -> ServerResponse.ok().build())
                    .OPTIONS("/sse/{serviceName}", req -> ServerResponse.ok().build())
                    .OPTIONS("/mcp/message", req -> ServerResponse.ok().build())
                    .OPTIONS("/mcp/{serviceName}/message", req -> ServerResponse.ok().build())
                    .build();

            // 合并路由：自定义路由（优先级最高）+ 标准路由 + 预检处理
            // 注意：自定义路由优先级最高，会先匹配
            return (RouterFunction<?>) sseRouter
                    .and(messagePathRouter)
                    .and(messageQueryRouter)
                    .and(corsOptions)
                    .and((RouterFunction<ServerResponse>) standardRouter);
        } else {
            throw new IllegalStateException("Expected WebFluxSseServerTransportProvider but got: " +
                    transportProvider.getClass().getSimpleName());
        }
    }
    
    /**
     * 处理 SSE 连接请求，提取 serviceName 并调用 Spring AI 的标准实现
     * 然后从响应中提取 sessionId 并记录关联关系
     * 路径参数方式：GET /sse/{serviceName}
     */
    private Mono<ServerResponse> handleSseWithServiceName(ServerRequest request) {
        // 从路径变量中提取 serviceName
        String serviceName = request.pathVariable("serviceName");
        
        log.info("📡 SSE connection request: serviceName={}, path={}, queryParams={}", 
                serviceName, request.path(), request.queryParams());
        
        // 调用 Spring AI 的标准实现
        // 但是我们不能直接调用，因为 RouterFunction 是函数式的
        // 所以我们需要重新实现，但使用 Spring AI 的格式
        
        // 方案：使用 Spring AI 的标准格式，但记录 serviceName 和 SSE sink
        // Spring AI 的标准格式是：event:endpoint\ndata:http://.../mcp/message?sessionId=xxx
        String baseUrl = buildBaseUrlFromRequest(request);
        String sessionId = UUID.randomUUID().toString();
        String messageEndpoint = (serviceName != null && !serviceName.isEmpty())
                ? String.format("%s/mcp/%s/message?sessionId=%s", baseUrl, serviceName, sessionId)
                : String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);
        
        // 创建 SSE sink 用于后续通过 SSE 发送响应
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        // 记录关联关系（路径参数方式必须提供服务名称）
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("⚠️ No serviceName found in path, path={}", request.path());
        } else {
            sessionService.registerSessionService(sessionId, serviceName);
            log.info("✅ Registered service for SSE connection: sessionId={}, serviceName={}", sessionId, serviceName);
        }
        
        // 注册 SSE sink
        sessionService.registerSseSink(sessionId, sink);
        log.info("✅ Registered SSE sink for session: sessionId={}", sessionId);
        // 触发会话活跃
        sessionService.touch(sessionId);
        
        // 注册客户端会话到会话桥接服务
        if (serviceName != null && !serviceName.isEmpty()) {
            sessionBridgeService.registerClientSession(sessionId, serviceName, sink);
            log.info("✅ Registered client session in bridge service: sessionId={}, serviceName={}", 
                    sessionId, serviceName);
        }
        
        // 使用 Spring AI 的标准格式返回
        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data(messageEndpoint)
                .build();
        
        // 创建心跳流保持连接
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")
                        .build())
                .doOnNext(tick -> {
                    sessionService.touch(sessionId);
                    log.debug("💓 SSE heartbeat: sessionId={}", sessionId);
                });
        
        // 合并 endpoint 消息、sink 的消息流和心跳流
        // 使用 merge 来同时处理多个流：先发送 endpoint，然后合并 sink 消息和心跳
        Flux<ServerSentEvent<String>> eventFlux = Flux.concat(
                Flux.just(endpointEvent),
                Flux.merge(
                        sink.asFlux(),  // 通过 sink 发送的响应消息
                        heartbeatFlux   // 心跳流
                )
        )
        .doOnCancel(() -> {
            log.info("❌ SSE connection cancelled: sessionId={}", sessionId);
            sessionService.removeSession(sessionId);
            sessionBridgeService.removeClientSession(sessionId);
            sink.tryEmitComplete();
        })
        .doOnError(error -> {
            log.error("❌ SSE connection error: sessionId={}", sessionId, error);
            sessionService.removeSession(sessionId);
            sessionBridgeService.removeClientSession(sessionId);
            sink.tryEmitError(error);
        })
        .doOnComplete(() -> {
            log.info("✅ SSE connection completed: sessionId={}", sessionId);
        });
        
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(BodyInserters.fromServerSentEvents(eventFlux));
    }

    /**
     * 处理 SSE 连接请求，从查询参数中提取 serviceName
     * 查询参数方式：GET /sse?serviceName=xxx（用于 MCP Inspector 等工具）
     */
    private Mono<ServerResponse> handleSseWithQueryParam(ServerRequest request) {
        // 从查询参数中提取 serviceName（可选）
        String serviceName = request.queryParam("serviceName").orElse(null);
        
        log.info("📡 SSE connection request (query param): serviceName={}, path={}, queryParams={}", 
                serviceName, request.path(), request.queryParams());
        
        // 如果没有提供 serviceName，仍然处理但记录警告（向后兼容）
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("⚠️ No serviceName in query params for /sse endpoint, connection will work but routing may fail");
        }
        
        // 使用自定义处理逻辑（与路径参数方式相同）
        String baseUrl = buildBaseUrlFromRequest(request);
        String sessionId = UUID.randomUUID().toString();
        String messageEndpoint = (serviceName != null && !serviceName.isEmpty())
                ? String.format("%s/mcp/%s/message?sessionId=%s", baseUrl, serviceName, sessionId)
                : String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);
        
        // 创建 SSE sink 用于后续通过 SSE 发送响应
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        
        // 记录关联关系（如果有 serviceName）
        if (serviceName != null && !serviceName.isEmpty()) {
            sessionService.registerSessionService(sessionId, serviceName);
            log.info("✅ Registered service for SSE connection: sessionId={}, serviceName={}", sessionId, serviceName);
        } else {
            log.info("ℹ️ SSE connection without serviceName: sessionId={}", sessionId);
        }
        
        // 注册 SSE sink
        sessionService.registerSseSink(sessionId, sink);
        log.info("✅ Registered SSE sink for session: sessionId={}", sessionId);
        // 触发会话活跃
        sessionService.touch(sessionId);
        
        // 注册客户端会话到会话桥接服务（如果有 serviceName）
        if (serviceName != null && !serviceName.isEmpty()) {
            sessionBridgeService.registerClientSession(sessionId, serviceName, sink);
            log.info("✅ Registered client session in bridge service: sessionId={}, serviceName={}", 
                    sessionId, serviceName);
        }
        
        // 使用 Spring AI 的标准格式返回
        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data(messageEndpoint)
                .build();
        
        // 创建心跳流保持连接
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")
                        .build())
                .doOnNext(tick -> {
                    sessionService.touch(sessionId);
                    log.debug("💓 SSE heartbeat: sessionId={}", sessionId);
                });
        
        // 合并 endpoint 消息、sink 的消息流和心跳流
        Flux<ServerSentEvent<String>> eventFlux = Flux.concat(
                Flux.just(endpointEvent),
                Flux.merge(
                        sink.asFlux(),  // 通过 sink 发送的响应消息
                        heartbeatFlux   // 心跳流
                )
        )
        .doOnCancel(() -> {
            log.info("❌ SSE connection cancelled: sessionId={}", sessionId);
            sessionService.removeSession(sessionId);
            sessionBridgeService.removeClientSession(sessionId);
            sink.tryEmitComplete();
        })
        .doOnError(error -> {
            log.error("❌ SSE connection error: sessionId={}", sessionId, error);
            sessionService.removeSession(sessionId);
            sessionBridgeService.removeClientSession(sessionId);
            sink.tryEmitError(error);
        })
        .doOnComplete(() -> {
            log.info("✅ SSE connection completed: sessionId={}", sessionId);
        });
        
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(BodyInserters.fromServerSentEvents(eventFlux));
    }

    /**
     * 从请求推断 Base URL，优先使用代理头。形式如：http(s)://host[:port]
     */
    private String buildBaseUrlFromRequest(ServerRequest request) {
        try {
            // 优先读取代理相关头
            String forwardedProto = request.headers().firstHeader("X-Forwarded-Proto");
            String forwardedHost = request.headers().firstHeader("X-Forwarded-Host");
            String forwardedPort = request.headers().firstHeader("X-Forwarded-Port");
            String scheme;
            String hostPort;
            if (forwardedHost != null && !forwardedHost.isEmpty()) {
                scheme = (forwardedProto != null && !forwardedProto.isEmpty()) ? forwardedProto : "http";
                // X-Forwarded-Host 可能已包含端口
                if (forwardedPort != null && !forwardedPort.isEmpty() && !forwardedHost.contains(":")) {
                    hostPort = forwardedHost + ":" + forwardedPort;
                } else {
                    hostPort = forwardedHost;
                }
                return scheme + "://" + hostPort;
            }
            // 其次使用 Host 头与请求 scheme
            String host = request.headers().firstHeader("Host");
            if (host != null && !host.isEmpty()) {
                String reqScheme = request.uri().getScheme();
                if (reqScheme == null || reqScheme.isEmpty()) {
                    reqScheme = "http";
                }
                return reqScheme + "://" + host;
            }
            // 回退到本地配置
            return "http://" + getServerIp() + ":" + getServerPort();
        } catch (Exception e) {
            log.warn("Failed to build base URL from request, fallback to local config", e);
            return "http://" + getServerIp() + ":" + getServerPort();
        }
    }


    /**
     * 处理 MCP 消息请求（使用路径参数方式：/mcp/{serviceName}/message?sessionId=xxx）
     * 从路径变量中提取服务名称，从查询参数中提取 sessionId
     */
    private Mono<ServerResponse> handleMcpMessageWithPath(ServerRequest request) {
        // 从路径变量中提取服务名称
        String serviceName = request.pathVariable("serviceName");
        // 从查询参数中提取 sessionId（可选，如果不提供则自动生成）
        String sessionId = request.queryParam("sessionId").orElse(null);
        
        log.info("📥 Received MCP message request (path): path={}, serviceName={}, sessionId={}, queryParams={}", 
                request.path(), serviceName, sessionId, request.queryParams());
        
        // 如果查询参数中没有 sessionId，尝试自动生成
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("📥 Generated auto sessionId: sessionId={}", sessionId);
        }
        
        // 路径参数方式必须提供服务名称
        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("⚠️ No serviceName found in path, path={}", request.path());
        }
        
        return processMcpMessage(request, serviceName, sessionId);
    }

    /**
     * 处理 MCP 消息请求（使用查询参数方式：/mcp/message?sessionId=xxx，与 mcp-server-v6 一致）
     * 从查询参数中提取 sessionId 和 serviceName
     * 
     * 注意：这个路由会拦截 Spring AI 的标准消息路由，实现路由功能
     */
    private Mono<ServerResponse> handleMcpMessage(ServerRequest request) {
        // 从查询参数中提取 sessionId（必需）
        String sessionId = request.queryParam("sessionId").orElse(null);
        
        // 从查询参数中提取 serviceName（可选，如果提供则优先使用）
        // 使用 queryParams().get() 作为备用方案，确保能正确提取
        String serviceName = request.queryParam("serviceName").orElse(null);
        if (serviceName == null || serviceName.isEmpty()) {
            // 如果 queryParam 返回空，尝试从 queryParams 中获取第一个值
            List<String> values = request.queryParams().get("serviceName");
            if (values != null && !values.isEmpty()) {
                serviceName = values.get(0);
            }
        }
        
        log.info("📥 Received MCP message request: path={}, sessionId={}, serviceName={}, queryParams={}", 
                request.path(), sessionId, serviceName, request.queryParams());
        
        // 如果查询参数中没有 sessionId，尝试自动生成
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("📥 Generated auto sessionId: sessionId={}", sessionId);
        }
        
        // 如果查询参数中没有 serviceName，尝试从会话中获取
        if (serviceName == null || serviceName.isEmpty()) {
            if (sessionId != null && !sessionId.isEmpty()) {
                serviceName = sessionService.getServiceName(sessionId);
                if (serviceName != null && !serviceName.isEmpty()) {
                    log.info("📋 Service name from session: sessionId={}, serviceName={}", sessionId, serviceName);
                } else {
                    log.info("ℹ️ No service name found in session: sessionId={}, will use smart routing", sessionId);
                }
            }
        } else {
            log.info("📋 Service name from query parameter: serviceName={}", serviceName);
        }
        
        return processMcpMessage(request, serviceName, sessionId);
    }

    /**
     * 处理 MCP 消息的核心逻辑
     */
    private Mono<ServerResponse> processMcpMessage(ServerRequest request, String serviceName, String sessionId) {
        // 使用 final 变量存储初始服务名称
        final String initialServiceName = serviceName;
        log.info("📥 Processing MCP message: serviceName={}, sessionId={}", serviceName, sessionId);
        
        return request.bodyToMono(String.class)
                .doOnNext(body -> log.info("📨 Received MCP message body (length={}): {}", body != null ? body.length() : 0, body))
                .flatMap(body -> {
                    try {
                        // 消息到达，刷新会话活跃时间
                        if (sessionId != null && !sessionId.isEmpty()) {
                            sessionService.touch(sessionId);
                        }
                        if (body == null || body.isEmpty()) {
                            log.error("❌ Empty message body");
                            return ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error: Empty request\"}}");
                        }
                        
                        log.debug("📨 MCP message body: {}", body);
                        
                        // 解析 MCP 消息
                        McpMessage mcpMessage = objectMapper.readValue(body, McpMessage.class);
                        log.info("✅ Parsed MCP message: id={}, method={}, jsonrpc={}", 
                                mcpMessage.getId(), mcpMessage.getMethod(), mcpMessage.getJsonrpc());
                        
                        // 验证 JSON-RPC 版本
                        if (mcpMessage.getJsonrpc() == null || !"2.0".equals(mcpMessage.getJsonrpc())) {
                            log.error("❌ Invalid JSON-RPC version: {}", mcpMessage.getJsonrpc());
                            String errorResponse = String.format(
                                "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"error\":{\"code\":-32600,\"message\":\"Invalid JSON-RPC version, must be 2.0\"}}",
                                mcpMessage.getId() != null ? mcpMessage.getId() : "unknown"
                            );
                            return ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(errorResponse);
                        }
                        
                        // 验证请求参数格式（符合 MCP 标准）
                        String validationError = requestValidator.validateRequest(mcpMessage);
                        if (validationError != null) {
                            log.error("❌ Request validation failed: {}", validationError);
                            String errorResponse = String.format(
                                "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"error\":{\"code\":-32602,\"message\":\"Invalid params: %s\"}}",
                                mcpMessage.getId() != null ? mcpMessage.getId() : "unknown",
                                validationError
                            );
                            return ServerResponse.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(errorResponse);
                        }
                        
                        // JSON-RPC 通知（无 id）应不产生响应，直接忽略
                        if (mcpMessage.getId() == null) {
                            String method = mcpMessage.getMethod();
                            if (method != null && method.startsWith("notifications/")) {
                                log.info("ℹ️ Received JSON-RPC notification '{}', ignoring as per spec", method);
                                // 不通过 SSE 发送任何数据，直接返回 202
                                return ServerResponse.accepted()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue("{\"status\":\"accepted\",\"message\":\"Notification ignored\"}");
                            }
                        }
                        
                        // 确定最终的服务名称
                        String finalServiceName = initialServiceName;
                        
                        // 如果 path 中没有服务名称，尝试从会话中获取
                        if (finalServiceName == null || finalServiceName.isEmpty()) {
                            finalServiceName = sessionService.getServiceName(sessionId);
                            log.info("📋 Service name from session: {}", finalServiceName);
                        }
                        
                        // 如果会话中也没有服务名称，尝试从消息中提取
                        if (finalServiceName == null || finalServiceName.isEmpty()) {
                            finalServiceName = extractServiceName(mcpMessage);
                            log.info("📋 Service name from message: {}", finalServiceName);
                        }
                        
                        // initialize 方法由 router 本地处理，不路由到后端服务器
                        if ("initialize".equals(mcpMessage.getMethod())) {
                            log.info("🖐 Handling 'initialize' locally in router (no backend routing)");
                            Mono<McpMessage> initializeResponse = routerService.routeRequest(null, mcpMessage);
                            
                            // 获取 SSE sink（如果存在）
                            Sinks.Many<ServerSentEvent<String>> sseSink = sessionService.getSseSink(sessionId);
                            
                            return initializeResponse
                                    .flatMap(response -> {
                                        try {
                                            // 将 McpMessage 转换为标准 JSON-RPC 2.0 格式
                                            String responseJson = convertToJsonRpcResponse(response);
                                            log.info("📤 Sending initialize response via SSE (length={}): {}", responseJson.length(), responseJson);
                                            
                                            // 如果存在 SSE sink，通过 SSE 发送响应
                                            if (sseSink != null) {
                                                ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                                                        .data(responseJson)
                                                        .build();
                                                 Sinks.EmitResult emitResult = sseSink.tryEmitNext(sseEvent);
                                                 if (emitResult.isSuccess()) {
                                                    log.info("✅ Successfully sent initialize response via SSE: sessionId={}", sessionId);
                                                } else {
                                                     if (emitResult == Sinks.EmitResult.FAIL_TERMINATED || emitResult == Sinks.EmitResult.FAIL_CANCELLED) {
                                                         log.debug("ℹ️ SSE sink closed, drop initialize response: sessionId={}, result={}", sessionId, emitResult);
                                                     } else {
                                                         log.warn("⚠️ Failed to emit SSE event: sessionId={}, result={}", sessionId, emitResult);
                                                     }
                                                }
                                                
                                                // POST 请求立即返回 202 Accepted（符合 MCP 协议）
                                                return ServerResponse.accepted()
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}");
                                            } else {
                                                // 如果没有 SSE sink，回退到 HTTP 响应（向后兼容）
                                                log.warn("⚠️ No SSE sink found for sessionId={}, falling back to HTTP response", sessionId);
                                                return ServerResponse.ok()
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(responseJson);
                                            }
                                        } catch (Exception e) {
                                            log.error("❌ Failed to convert initialize response to JSON", e);
                                            String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
                                            
                                            // 尝试通过 SSE 发送错误响应
                                            if (sseSink != null) {
                                                ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                        .data(errorJson)
                                                        .build();
                                                 Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                                 if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                     log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                                 }
                                                return ServerResponse.accepted()
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue("{\"status\":\"accepted\",\"error\":\"Internal error, error response sent via SSE\"}");
                                            } else {
                                                return ServerResponse.status(500)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(errorJson);
                                            }
                                        }
                                    })
                                    .onErrorResume(error -> {
                                        log.error("❌ Initialize error: {}", error.getMessage(), error);
                                        try {
                                            String errorResponse = createErrorResponse(mcpMessage, error);
                                            log.info("📤 Sending initialize error response via SSE: {}", errorResponse);
                                            
                                            // 使用外部作用域的 sseSink
                                            // 尝试通过 SSE 发送错误响应
                                            if (sseSink != null) {
                                                ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                        .data(errorResponse)
                                                        .build();
                                                 Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                                 if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                     log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                                 }
                                                return ServerResponse.accepted()
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue("{\"status\":\"accepted\",\"error\":\"Error response sent via SSE\"}");
                                            } else {
                                                return ServerResponse.status(500)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(errorResponse);
                                            }
                                        } catch (Exception e) {
                                            log.error("❌ Failed to create initialize error response", e);
                                            String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + (mcpMessage != null ? mcpMessage.getId() : "unknown") + "\",\"error\":{\"code\":-32603,\"message\":\"Internal server error\"}}";
                                            
                                            // 使用外部作用域的 sseSink
                                            if (sseSink != null) {
                                                ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                        .data(errorJson)
                                                        .build();
                                                 Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                                 if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                     log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                                 }
                                                return ServerResponse.accepted()
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue("{\"status\":\"accepted\",\"error\":\"Error response sent via SSE\"}");
                                            } else {
                                                return ServerResponse.status(500)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(errorJson);
                                            }
                                        }
                                    });
                        }
                        
                        // 路由消息
                        final String targetServiceName = finalServiceName; // 用于 lambda 中的 final 变量
                        
                        // 更新客户端会话的最后活跃时间
                        sessionBridgeService.updateClientSessionLastActiveTime(sessionId);
                        
                        // 获取或创建服务器会话（如果需要）
                        // 注意：只有在客户端会话存在时才尝试获取或创建服务器会话
                        // 如果客户端会话不存在，回退到路由逻辑
                        Mono<McpSessionBridgeService.ServerSession> serverSessionMono;
                        boolean isListMethod = "tools/list".equals(mcpMessage.getMethod()) ||
                                "resources/list".equals(mcpMessage.getMethod()) ||
                                "prompts/list".equals(mcpMessage.getMethod()) ||
                                "resources/templates/list".equals(mcpMessage.getMethod());
                        boolean forceRouteInsteadOfBridge = isListMethod || "tools/call".equals(mcpMessage.getMethod());
                        if (forceRouteInsteadOfBridge) {
                            log.info("📝 Handling method via direct routing (skip backend bridge): method={}, sessionId={}", mcpMessage.getMethod(), sessionId);
                            serverSessionMono = Mono.empty();
                        } else if (targetServiceName != null && !targetServiceName.isEmpty()) {
                            // 先检查客户端会话是否存在
                            McpSessionBridgeService.ClientSession clientSession = 
                                    sessionBridgeService.getClientSession(sessionId);
                            if (clientSession != null) {
                                // 客户端会话存在，尝试获取或创建服务器会话
                                serverSessionMono = sessionBridgeService.getOrCreateServerSession(sessionId, targetServiceName)
                                        .doOnNext(serverSession -> log.info("✅ Got or created server session: serverSessionId={}", 
                                                serverSession.getServerSessionId()))
                                        .onErrorResume(error -> {
                                            // 如果获取服务器会话失败，记录错误但回退到路由逻辑
                                            log.warn("⚠️ Failed to get or create server session, falling back to routing: {}", error.getMessage());
                                            return Mono.empty();
                                        });
                            } else {
                                // 客户端会话不存在，直接使用路由逻辑
                                log.info("ℹ️ Client session not found for sessionId={}, using routing logic", sessionId);
                                serverSessionMono = Mono.empty();
                            }
                        } else {
                            serverSessionMono = Mono.empty();
                        }
                        
                        // 获取 SSE sink（如果存在）
                        Sinks.Many<ServerSentEvent<String>> sseSink = sessionService.getSseSink(sessionId);
                        
                        // 检查是否存在服务器会话，如果存在则使用 HTTP POST 发送消息
                        return serverSessionMono
                                .flatMap(serverSession -> {
                                    // 检查 backendSessionId 是否已准备好
                                    String backendSessionId = serverSession.getBackendSessionId();
                                    if (backendSessionId == null || backendSessionId.isEmpty()) {
                                        // backendSessionId 尚未从 SSE 事件中提取，回退到路由逻辑
                                        log.warn("⚠️ Backend sessionId not ready yet, falling back to routing: serverSessionId={}", 
                                                serverSession.getServerSessionId());
                                        return Mono.empty(); // 返回 empty 以触发 switchIfEmpty
                                    }
                                    
                                    // 存在服务器会话且 backendSessionId 已准备好，使用 HTTP POST 发送消息到后端服务器
                                    try {
                                        // 将 McpMessage 转换为 JSON 字符串
                                        String requestJson = objectMapper.writeValueAsString(mcpMessage);
                                        log.info("📤 Sending message to backend server via HTTP POST: serverSessionId={}, backendSessionId={}, method={}", 
                                                serverSession.getServerSessionId(), backendSessionId, mcpMessage.getMethod());
                                        
                                        // 使用 sendMessageToBackendServer 方法发送消息
                                        return sessionBridgeService.sendMessageToBackendServer(sessionId, requestJson)
                                                .timeout(Duration.ofSeconds(10))
                                                .flatMap(responseJson -> {
                                                    log.info("✅ Received response from backend server: {}", responseJson);
                                                    
                                                    // 如果存在 SSE sink，通过 SSE 发送响应
                                                    if (sseSink != null) {
                                                        ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                                                                .data(responseJson)
                                                                .build();
                                                         Sinks.EmitResult emitResult = sseSink.tryEmitNext(sseEvent);
                                                         if (emitResult.isSuccess()) {
                                                            log.info("✅ Successfully sent response via SSE: sessionId={}", sessionId);
                                                        } else {
                                                             if (emitResult == Sinks.EmitResult.FAIL_TERMINATED || emitResult == Sinks.EmitResult.FAIL_CANCELLED) {
                                                                 log.debug("ℹ️ SSE sink closed, drop response: sessionId={}, result={}", sessionId, emitResult);
                                                             } else {
                                                                 log.warn("⚠️ Failed to emit SSE event: sessionId={}, result={}", sessionId, emitResult);
                                                             }
                                                        }
                                                        
                                                        // POST 请求立即返回 202 Accepted（符合 MCP 协议）
                                                        return ServerResponse.accepted()
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}");
                                                    } else {
                                                        // 如果没有 SSE sink，回退到 HTTP 响应（向后兼容）
                                                        log.warn("⚠️ No SSE sink found for sessionId={}, falling back to HTTP response", sessionId);
                                                        return ServerResponse.ok()
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue(responseJson);
                                                    }
                                                })
                                                .onErrorResume(error -> {
                                                    if (error instanceof java.util.concurrent.TimeoutException ||
                                                            error instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
                                                        log.warn("⚠️ Backend server did not respond in time or connection failed, falling back to routing logic: {}", error.getMessage());
                                                        return Mono.empty();
                                                    }

                                                    log.error("❌ Failed to send message to backend server: {}", error.getMessage(), error);
                                                    try {
                                                        String errorResponse = createErrorResponse(mcpMessage, error);
                                                        log.info("📤 Sending error response via SSE: {}", errorResponse);
                                                        
                                                        // 尝试通过 SSE 发送错误响应
                                                        if (sseSink != null) {
                                                            ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                                    .data(errorResponse)
                                                                    .build();
                                                             Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                                             if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                                 log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                                             }
                                                            return ServerResponse.accepted()
                                                                    .contentType(MediaType.APPLICATION_JSON)
                                                                    .bodyValue("{\"status\":\"accepted\",\"error\":\"Error response sent via SSE\"}");
                                                        } else {
                                                            return ServerResponse.status(500)
                                                                    .contentType(MediaType.APPLICATION_JSON)
                                                                    .bodyValue(errorResponse);
                                                        }
                                                    } catch (Exception e) {
                                                        log.error("❌ Failed to create error response", e);
                                                        return ServerResponse.status(500)
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}");
                                                    }
                                                });
                                    } catch (Exception e) {
                                        log.error("❌ Failed to convert message to JSON", e);
                                        return Mono.error(e);
                                    }
                                })
                                .switchIfEmpty(Mono.defer(() -> {
                                    // 不存在服务器会话，使用原来的路由逻辑
                                    Mono<McpMessage> routeResult;
                                    if (targetServiceName != null && !targetServiceName.isEmpty()) {
                                        // 路由到指定服务
                                        log.info("🔄 Routing to specified service: {}, method: {}", targetServiceName, mcpMessage.getMethod());
                                        routeResult = routerService.routeRequest(targetServiceName, mcpMessage);
                                    } else {
                                        // 智能路由（自动发现服务）
                                        log.info("🧠 Smart routing (auto-discover service), method: {}", mcpMessage.getMethod());
                                        routeResult = routerService.smartRoute(mcpMessage, 
                                                Duration.ofSeconds(60), Map.of()); // 使用60秒超时，与默认超时一致
                                    }
                                    
                                    // 将路由结果转换为标准 MCP 响应格式，并通过 SSE 发送
                                    return routeResult
                                .doOnNext(response -> log.info("✅ Received routing response: id={}, hasResult={}, hasError={}", 
                                        response.getId(), response.getResult() != null, response.getError() != null))
                                .flatMap(response -> {
                                    try {
                                        // 将 McpMessage 转换为标准 JSON-RPC 2.0 格式
                                        String responseJson = convertToJsonRpcResponse(response);
                                        log.info("📤 Sending MCP response via SSE (length={}): {}", responseJson.length(), responseJson);
                                        
                                        // 如果存在 SSE sink，通过 SSE 发送响应
                                        if (sseSink != null) {
                                            ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                                                    .data(responseJson)
                                                    .build();
                                             Sinks.EmitResult emitResult = sseSink.tryEmitNext(sseEvent);
                                             if (emitResult.isSuccess()) {
                                                log.info("✅ Successfully sent response via SSE: sessionId={}", sessionId);
                                            } else {
                                                 if (emitResult == Sinks.EmitResult.FAIL_TERMINATED || emitResult == Sinks.EmitResult.FAIL_CANCELLED) {
                                                     log.debug("ℹ️ SSE sink closed, drop response: sessionId={}, result={}", sessionId, emitResult);
                                                 } else {
                                                     log.warn("⚠️ Failed to emit SSE event: sessionId={}, result={}", sessionId, emitResult);
                                                 }
                                            }
                                            
                                            // POST 请求立即返回 202 Accepted（符合 MCP 协议）
                                            return ServerResponse.accepted()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}");
                                        } else {
                                            // 如果没有 SSE sink，回退到 HTTP 响应（向后兼容）
                                            log.warn("⚠️ No SSE sink found for sessionId={}, falling back to HTTP response", sessionId);
                                            return ServerResponse.ok()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(responseJson);
                                        }
                                    } catch (Exception e) {
                                        log.error("❌ Failed to convert response to JSON", e);
                                        String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
                                        
                                        // 尝试通过 SSE 发送错误响应
                                        if (sseSink != null) {
                                            ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                    .data(errorJson)
                                                    .build();
                                             Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                             if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                 log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                             }
                                            return ServerResponse.accepted()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue("{\"status\":\"accepted\",\"error\":\"Internal error, error response sent via SSE\"}");
                                        } else {
                                            return ServerResponse.status(500)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorJson);
                                        }
                                    }
                                })
                                .onErrorResume(error -> {
                                    log.error("❌ Routing error: {}", error.getMessage(), error);
                                    try {
                                        String errorResponse = createErrorResponse(mcpMessage, error);
                                        log.info("📤 Sending error response via SSE: {}", errorResponse);
                                        
                                        // 尝试通过 SSE 发送错误响应
                                        if (sseSink != null) {
                                            ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                    .data(errorResponse)
                                                    .build();
                                             Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                             if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                 log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                             }
                                            return ServerResponse.accepted()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue("{\"status\":\"accepted\",\"error\":\"Error response sent via SSE\"}");
                                        } else {
                                            return ServerResponse.status(500)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorResponse);
                                        }
                                    } catch (Exception e) {
                                        log.error("❌ Failed to create error response", e);
                                        String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + (mcpMessage != null ? mcpMessage.getId() : "unknown") + "\",\"error\":{\"code\":-32603,\"message\":\"Internal server error\"}}";
                                        
                                        if (sseSink != null) {
                                            ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                                    .data(errorJson)
                                                    .build();
                                             Sinks.EmitResult emitResult = sseSink.tryEmitNext(errorEvent);
                                             if (!emitResult.isSuccess() && emitResult != Sinks.EmitResult.FAIL_TERMINATED && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                                 log.warn("⚠️ Failed to emit SSE error event: sessionId={}, result={}", sessionId, emitResult);
                                             }
                                            return ServerResponse.accepted()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue("{\"status\":\"accepted\",\"error\":\"Error response sent via SSE\"}");
                                        } else {
                                            return ServerResponse.status(500)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorJson);
                                        }
                                    }
                                });
                                }));
                    } catch (Exception e) {
                        log.error("❌ Failed to parse MCP message: {}", e.getMessage(), e);
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error: " + e.getMessage() + "\"}}");
                    }
                })
                .doOnError(error -> log.error("❌ Unexpected error in processMcpMessage: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("❌ Unexpected error in processMcpMessage", error);
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error: " + error.getMessage() + "\"}}");
                });
    }

    /**
     * 从 MCP 消息中提取服务名称
     * 可以从 metadata 或 params 中提取
     */
    private String extractServiceName(McpMessage message) {
        // 优先从 metadata 中获取
        if (message.getMetadata() != null) {
            Object serviceName = message.getMetadata().get("targetService");
            if (serviceName != null) {
                return serviceName.toString();
            }
        }
        
        // 从 targetService 字段获取
        if (message.getTargetService() != null && !message.getTargetService().isEmpty()) {
            return message.getTargetService();
        }
        
        return null;
    }

    /**
     * 将 McpMessage 转换为标准 JSON-RPC 2.0 响应格式
     * 根据方法类型返回不同的格式：
     * - initialize: 直接返回 result（包含 protocolVersion, capabilities, serverInfo）
     * - tools/list: 直接返回 result（包含 tools 数组和 toolsMeta）
     * - tools/call: 返回 result.content 数组格式
     * - resources/list: 直接返回 result（包含 resources 数组）
     * - resources/read: 直接返回 result（包含 contents 数组）
     * - prompts/list: 直接返回 result（包含 prompts 数组）
     * - prompts/get: 直接返回 result（包含 description 和 messages）
     * - resources/templates/list: 直接返回 result（包含 resourceTemplates 数组）
     */
    private String convertToJsonRpcResponse(McpMessage response) throws Exception {
        Map<String, Object> jsonRpcResponse = new java.util.HashMap<>();
        jsonRpcResponse.put("jsonrpc", "2.0");
        jsonRpcResponse.put("id", response.getId());
        
        if (response.getError() != null) {
            // 错误响应
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("code", response.getError().getCode());
            error.put("message", response.getError().getMessage());
            if (response.getError().getData() != null) {
                error.put("data", response.getError().getData());
            }
            jsonRpcResponse.put("error", error);
        } else {
            // 成功响应
            Object result = response.getResult();
            String method = response.getMethod();
            
            if (result != null) {
                if ("initialize".equals(method)) {
                    // 对于 initialize 方法，手动构建 capabilities
                    @SuppressWarnings("unchecked")
                    Map<String, Object> initializeResult = new java.util.HashMap<>((Map<String, Object>) result); // 创建可变拷贝
                    if (initializeResult != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> capabilities = (Map<String, Object>) initializeResult.get("capabilities");
                        if (capabilities == null) {
                            capabilities = new java.util.HashMap<>();
                            initializeResult.put("capabilities", capabilities);
                        } else {
                            capabilities = new java.util.HashMap<>(capabilities); // 创建可变拷贝
                            initializeResult.put("capabilities", capabilities);
                        }
                        
                        // 确保 listChanged 字段为 true
                        Map<String, Object> resourcesCaps = (Map<String, Object>) capabilities.get("resources");
                        if (resourcesCaps == null) {
                            resourcesCaps = new java.util.HashMap<>();
                            capabilities.put("resources", resourcesCaps);
                        } else {
                            resourcesCaps = new java.util.HashMap<>(resourcesCaps); // 创建可变拷贝
                            capabilities.put("resources", resourcesCaps);
                        }
                        resourcesCaps.put("listChanged", true);
                        
                        Map<String, Object> toolsCaps = (Map<String, Object>) capabilities.get("tools");
                        if (toolsCaps == null) {
                            toolsCaps = new java.util.HashMap<>();
                            capabilities.put("tools", toolsCaps);
                        } else {
                            toolsCaps = new java.util.HashMap<>(toolsCaps); // 创建可变拷贝
                            capabilities.put("tools", toolsCaps);
                        }
                        toolsCaps.put("listChanged", true);
                        
                        Map<String, Object> promptsCaps = (Map<String, Object>) capabilities.get("prompts");
                        if (promptsCaps == null) {
                            promptsCaps = new java.util.HashMap<>();
                            capabilities.put("prompts", promptsCaps);
                        } else {
                            promptsCaps = new java.util.HashMap<>(promptsCaps); // 创建可变拷贝
                            capabilities.put("prompts", promptsCaps);
                        }
                        promptsCaps.put("listChanged", true);
                    }
                    jsonRpcResponse.put("result", initializeResult);
                } else if ("tools/list".equals(method) || 
                           "resources/list".equals(method) || 
                           "prompts/list".equals(method) ||
                           "resources/templates/list".equals(method)) {
                    // 对于这些列表方法，如果后端没有返回数据，强制返回空数组
                    if (result == null) {
                        jsonRpcResponse.put("result", new java.util.ArrayList<>());
                    } else {
                        jsonRpcResponse.put("result", result);
                    }
                } else if ("resources/read".equals(method) ||
                           "prompts/get".equals(method)) {
                    // 这些方法：直接返回 result（已经是标准 MCP 格式）
                    jsonRpcResponse.put("result", result);
                } else if ("tools/call".equals(method)) {
                    // tools/call 方法：返回 MCP 标准格式（content 数组）
                    // 如果 result 已经是 Map 且包含 content，直接使用
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultMap = (Map<String, Object>) result;
                        if (resultMap.containsKey("content")) {
                            // 已经是标准格式，直接使用
                            jsonRpcResponse.put("result", result);
                        } else {
                            // 需要包装成 content 数组格式
                            Map<String, Object> mcpResult = new java.util.HashMap<>();
                            java.util.List<Map<String, Object>> content = new java.util.ArrayList<>();
                            
                            Map<String, Object> contentItem = new java.util.HashMap<>();
                            contentItem.put("type", "text");
                            
                            // 将 result 对象序列化为 JSON 字符串
                            String resultJson = objectMapper.writeValueAsString(result);
                            contentItem.put("text", resultJson);
                            
                            content.add(contentItem);
                            mcpResult.put("content", content);
                            mcpResult.put("isError", false);
                            
                            jsonRpcResponse.put("result", mcpResult);
                        }
                    } else {
                        // result 不是 Map，包装成 content 数组格式
                        Map<String, Object> mcpResult = new java.util.HashMap<>();
                        java.util.List<Map<String, Object>> content = new java.util.ArrayList<>();
                        
                        Map<String, Object> contentItem = new java.util.HashMap<>();
                        contentItem.put("type", "text");
                        
                        // 将 result 对象序列化为 JSON 字符串
                        String resultJson = objectMapper.writeValueAsString(result);
                        contentItem.put("text", resultJson);
                        
                        content.add(contentItem);
                        mcpResult.put("content", content);
                        mcpResult.put("isError", false);
                        
                        jsonRpcResponse.put("result", mcpResult);
                    }
                } else {
                    // 其他方法：直接返回 result
                    jsonRpcResponse.put("result", result);
                }
            } else {
                jsonRpcResponse.put("result", null);
            }
        }
        
        return objectMapper.writeValueAsString(jsonRpcResponse);
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(McpMessage originalMessage, Throwable error) throws Exception {
        Map<String, Object> jsonRpcResponse = new java.util.HashMap<>();
        jsonRpcResponse.put("jsonrpc", "2.0");
        jsonRpcResponse.put("id", originalMessage != null ? originalMessage.getId() : "unknown");
        
        Map<String, Object> errorObj = new java.util.HashMap<>();
        errorObj.put("code", -32603); // Internal error
        errorObj.put("message", error.getMessage() != null ? error.getMessage() : "Internal server error");
        jsonRpcResponse.put("error", errorObj);
        
        return objectMapper.writeValueAsString(jsonRpcResponse);
    }
}

