package com.pajk.mcpbridge.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pajk.mcpbridge.core.model.McpMessage;
import com.pajk.mcpbridge.core.service.McpRequestValidator;
import com.pajk.mcpbridge.core.service.McpRouterService;
import com.pajk.mcpbridge.core.service.McpSessionService;
import com.pajk.mcpbridge.core.service.McpSessionBridgeService;
import com.pajk.mcpbridge.core.service.McpSseTransportProvider;
import com.pajk.mcpbridge.core.transport.TransportPreferenceResolver;
import com.pajk.mcpbridge.core.transport.TransportType;
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
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

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
    
    @Value("${mcp.router.context-path:}")
    private String configuredContextPath;

    private static final String SSE_BASE_PATH = "/sse";
    private static final String STREAMABLE_BASE_PATH = "/mcp";
    private static final java.util.List<String> SESSION_ID_HEADER_CANDIDATES = java.util.List.of(
            "Mcp-Session-Id",
            "mcp-session-id",
            "X-Mcp-Session-Id",
            "x-mcp-session-id"
    );

    private final McpRouterService routerService;
    private final ObjectMapper objectMapper;
    private final McpSessionService sessionService;
    private final McpRequestValidator requestValidator;
    private final McpSessionBridgeService sessionBridgeService;
    private final McpSseTransportProvider sseTransportProvider;
    private final TransportPreferenceResolver transportPreferenceResolver;

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
        this.transportPreferenceResolver = new TransportPreferenceResolver();
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
                SSE_BASE_PATH          // SSE端点（对外暴露为 /sse）
        );

        log.info("✅ MCP Router Server Transport Provider created successfully");
        log.info("📡 SSE endpoint: {}{}", baseUrl, SSE_BASE_PATH);
        log.info("📡 SSE endpoint with service: {}{}/{{serviceName}}", baseUrl, SSE_BASE_PATH);
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
            
            // 拦截传输路由，提取 serviceName 并记录，但使用 Spring AI 的标准实现
            // 支持路径参数方式：GET /mcp/{serviceName}
            // 支持查询参数方式：GET /mcp?serviceName=xxx（用于 MCP Inspector 等工具）
            // 为避免与基于注解的 /mcp 管理类接口冲突（如 /mcp/sessions、/mcp/session/{id} 等），
            // 显式排除这些保留路径，仅对真实的 serviceName 进行匹配
            // 负向前瞻排除：sessions, session, connect, message, broadcast, cleanup, admin
            RouterFunction<ServerResponse> sseRouter = route()
                    // Mirror controller endpoints to avoid conflicts with /mcp/{serviceName}
                    .GET(SSE_BASE_PATH + "/connect", req -> {
                        String clientId = req.queryParam("clientId").orElse("");
                        String metadata = req.queryParam("metadata").orElse(null);
                        Map<String, String> metadataMap = parseSimpleMetadata(metadata);
                        Flux<String> body = sseTransportProvider.connect(clientId, metadataMap)
                                .map(event -> event.data() == null ? "" : event.data());
                        return ServerResponse.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(BodyInserters.fromPublisher(body, String.class));
                    })
                    .POST(SSE_BASE_PATH + "/message/{sessionId}", req -> {
                        String sessionId = req.pathVariable("sessionId");
                        String eventType = req.queryParam("eventType").orElse("");
                        Mono<String> dataMono = req.bodyToMono(String.class);
                        return dataMono.flatMap(data ->
                                sseTransportProvider.sendMessage(sessionId, eventType, data)
                                        .then(ServerResponse.ok().bodyValue("Message sent successfully"))
                        );
                    })
                    .POST(SSE_BASE_PATH + "/message/client/{clientId}", req -> {
                        String clientId = req.pathVariable("clientId");
                        String eventType = req.queryParam("eventType").orElse("");
                        Mono<String> dataMono = req.bodyToMono(String.class);
                        return dataMono.flatMap(data ->
                                sseTransportProvider.sendMessageToClient(clientId, eventType, data)
                                        .then(ServerResponse.ok().bodyValue("Message sent successfully"))
                        );
                    })
                    .POST(SSE_BASE_PATH + "/broadcast", req -> {
                        String eventType = req.queryParam("eventType").orElse("");
                        Mono<String> dataMono = req.bodyToMono(String.class);
                        return dataMono.flatMap(data ->
                                sseTransportProvider.broadcast(eventType, data)
                                        .then(ServerResponse.ok().bodyValue("Message broadcasted successfully"))
                        );
                    })
                    .GET(SSE_BASE_PATH + "/session/{sessionId}", req -> {
                        String sessionId = req.pathVariable("sessionId");
                        com.pajk.mcpbridge.core.model.SseSession session = sseTransportProvider.getSession(sessionId);
                        if (session == null) {
                            return ServerResponse.status(500).build();
                        }
                        return ServerResponse.ok().bodyValue(session);
                    })
                    .GET(SSE_BASE_PATH + "/sessions", req ->
                            ServerResponse.ok().bodyValue(sseTransportProvider.getAllSessions())
                    )
                    .DELETE(SSE_BASE_PATH + "/session/{sessionId}", req -> {
                        String sessionId = req.pathVariable("sessionId");
                        return sseTransportProvider.closeSession(sessionId)
                                .then(ServerResponse.ok().bodyValue("Session closed successfully"));
                    })
                    .POST(SSE_BASE_PATH + "/cleanup", req ->
                            sseTransportProvider.cleanupTimeoutSessions()
                                    .then(ServerResponse.ok().bodyValue("Timeout sessions cleaned up successfully"))
                    )
                    .GET(SSE_BASE_PATH + "/{serviceName}", this::handleSseWithServiceName)
                    .GET(SSE_BASE_PATH, this::handleSseWithQueryParam)
                    .GET(STREAMABLE_BASE_PATH + "/{serviceName}", this::handleStreamableWithServiceName)
                    .POST(STREAMABLE_BASE_PATH + "/{serviceName}", this::handleMcpMessageWithPath)
                    .GET(STREAMABLE_BASE_PATH, this::handleStreamableWithQueryParam)
                    .POST(STREAMABLE_BASE_PATH, this::handleMcpMessage)
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
            log.info("📡 SSE endpoint: GET {} (with optional ?serviceName=xxx query param for MCP Inspector)", SSE_BASE_PATH);
            log.info("📡 SSE endpoint with service: GET {}/{{serviceName}}", SSE_BASE_PATH);
            log.info("📡 Streamable endpoint: GET {} (NDJSON stream)", STREAMABLE_BASE_PATH);
            log.info("📨 Message endpoint: POST /mcp/message?sessionId=xxx (routed by sessionId)");
            log.info("📨 Message endpoint: POST /mcp/{serviceName}/message?sessionId=xxx (routed by path)");
            
            // 预检请求（CORS）显式支持，避免 MCP Inspector 断开
            RouterFunction<ServerResponse> corsOptions = route()
                    .OPTIONS("/sse", req -> ServerResponse.ok().build())
                    .OPTIONS("/sse/{serviceName}", req -> ServerResponse.ok().build())
                    .OPTIONS("/mcp/message", req -> ServerResponse.ok().build())
                    .OPTIONS("/mcp/{serviceName}/message", req -> ServerResponse.ok().build())
                    .OPTIONS(STREAMABLE_BASE_PATH, req -> ServerResponse.ok().build())
                    .OPTIONS(STREAMABLE_BASE_PATH + "/{serviceName}", req -> ServerResponse.ok().build())
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
        String serviceName = request.pathVariable("serviceName");
        TransportType transportType = transportPreferenceResolver.resolve(request);
        if (transportType == TransportType.STREAMABLE) {
            return handleStreamable(request, serviceName, "sse-path");
        }
        SessionContext context = initializeSession("sse-path", request, serviceName, TransportType.SSE);
        Flux<ServerSentEvent<String>> eventFlux = buildEventFlux(context);
        return buildSseResponse(eventFlux);
    }


    /**
     * 处理 SSE 连接请求，从查询参数中提取 serviceName
     * 查询参数方式：GET /sse?serviceName=xxx（用于 MCP Inspector 等工具）
     */
    private Mono<ServerResponse> handleSseWithQueryParam(ServerRequest request) {
        String serviceName = request.queryParam("serviceName").orElse(null);
        TransportType transportType = transportPreferenceResolver.resolve(request);
        if (transportType == TransportType.STREAMABLE) {
            return handleStreamable(request, serviceName, "sse-query");
        }
        SessionContext context = initializeSession("sse-query", request, serviceName, TransportType.SSE);
        Flux<ServerSentEvent<String>> eventFlux = buildEventFlux(context);
        return buildSseResponse(eventFlux);
    }

    private Mono<ServerResponse> handleStreamableWithServiceName(ServerRequest request) {
        String serviceName = request.pathVariable("serviceName");
        return handleStreamable(request, serviceName, "streamable-path");
    }

    private Mono<ServerResponse> handleStreamableWithQueryParam(ServerRequest request) {
        String serviceName = request.queryParam("serviceName").orElse(null);
        return handleStreamable(request, serviceName, "streamable-query");
    }

    private Mono<ServerResponse> handleStreamable(ServerRequest request, String serviceName, String source) {
        SessionContext context = initializeSession(source, request, serviceName, TransportType.STREAMABLE);
        MediaType mediaType = resolveStreamableMediaType(request);
        
        // 创建 sessionId 初始消息（NDJSON格式）
        String sessionIdMessage = buildSessionIdMessage(context.sessionId(), context.messageEndpoint());
        
        // 在流的开头添加 sessionId 消息，然后是正常的事件流
        Flux<String> streamFlux = Flux.concat(
                Flux.just(sessionIdMessage),
                buildEventFlux(context).map(this::toStreamableJson)
        );
        
        return buildStreamableResponse(context, streamFlux, mediaType);
    }
    
    /**
     * 构建 Streamable 协议的 sessionId 初始消息
     * 格式符合 NDJSON 规范，包含 sessionId 和 messageEndpoint
     */
    private String buildSessionIdMessage(String sessionId, String messageEndpoint) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "session");
        payload.put("sessionId", sessionId);
        payload.put("messageEndpoint", messageEndpoint);
        payload.put("transport", "streamable");
        
        try {
            return objectMapper.writeValueAsString(payload) + "\n";
        } catch (Exception e) {
            log.warn("⚠️ Failed to encode session message, using fallback: {}", e.getMessage());
            return String.format("{\"type\":\"session\",\"sessionId\":\"%s\",\"messageEndpoint\":\"%s\",\"transport\":\"streamable\"}\n", 
                    sessionId, messageEndpoint);
        }
    }



    /**
     * 从请求推断 Base URL，优先使用代理头。形式如：http(s)://host[:port][/context-path]
     * 注意：包含 context-path（如果存在）
     */
    private String buildBaseUrlFromRequest(ServerRequest request) {
        try {
            // 提取 context-path（从请求路径中推断）
            String contextPath = extractContextPath(request);
            
            // 优先读取代理相关头（不区分大小写）
            // WebFlux 的 headers() 是大小写不敏感的，但为了保险，也尝试小写
            String forwardedProto = request.headers().firstHeader("X-Forwarded-Proto");
            if (forwardedProto == null) {
                forwardedProto = request.headers().firstHeader("x-forwarded-proto");
            }
            String forwardedHost = request.headers().firstHeader("X-Forwarded-Host");
            if (forwardedHost == null) {
                forwardedHost = request.headers().firstHeader("x-forwarded-host");
            }
            String forwardedPort = request.headers().firstHeader("X-Forwarded-Port");
            if (forwardedPort == null) {
                forwardedPort = request.headers().firstHeader("x-forwarded-port");
            }
            
            // 调试：记录所有请求头
            log.debug("All request headers: {}", request.headers().asHttpHeaders());
            String scheme;
            String hostPort;
            
            log.info("🔍 Building base URL - forwardedProto: {}, forwardedHost: {}, forwardedPort: {}, contextPath: {}, Host: {}", 
                    forwardedProto, forwardedHost, forwardedPort, contextPath, request.headers().firstHeader("Host"));
            
            if (forwardedHost != null && !forwardedHost.isEmpty()) {
                scheme = (forwardedProto != null && !forwardedProto.isEmpty()) ? forwardedProto : "http";
                // X-Forwarded-Host 可能已包含端口
                    hostPort = forwardedHost;
                // 如果 X-Forwarded-Host 不包含端口，且 X-Forwarded-Port 存在，则添加端口
                // 但如果是标准端口（80/443），则不添加
                if (!hostPort.contains(":") && forwardedPort != null && !forwardedPort.isEmpty()) {
                    int port = Integer.parseInt(forwardedPort);
                    // 只有非标准端口才添加
                    if (!((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
                        hostPort = hostPort + ":" + forwardedPort;
                    }
                }
                String baseUrl = scheme + "://" + hostPort;
                // 添加 context-path（如果存在）
                if (contextPath != null && !contextPath.isEmpty()) {
                    baseUrl = baseUrl + contextPath;
                }
                log.debug("Built base URL from forwarded headers: {}", baseUrl);
                return baseUrl;
            }
            
            // 其次使用 Host 头与请求 scheme
            String host = request.headers().firstHeader("Host");
            if (host != null && !host.isEmpty()) {
                String reqScheme = request.uri().getScheme();
                if (reqScheme == null || reqScheme.isEmpty()) {
                    reqScheme = "http";
                }
                // 处理 Host 头中的端口（如果是标准端口，则移除）
                String hostWithoutPort = host;
                if (host.contains(":")) {
                    String[] parts = host.split(":");
                    if (parts.length == 2) {
                        try {
                            int port = Integer.parseInt(parts[1]);
                            // 如果是标准端口，移除端口号
                            if ((reqScheme.equals("http") && port == 80) || 
                                (reqScheme.equals("https") && port == 443)) {
                                hostWithoutPort = parts[0];
                            }
                        } catch (NumberFormatException e) {
                            // 端口号解析失败，保持原样
                        }
                    }
                }
                String baseUrl = reqScheme + "://" + hostWithoutPort;
                // 添加 context-path（如果存在）
                if (contextPath != null && !contextPath.isEmpty()) {
                    baseUrl = baseUrl + contextPath;
            }
                log.debug("Built base URL from Host header: {}", baseUrl);
                return baseUrl;
            }
            
            // 回退到本地配置
            String baseUrl = "http://" + getServerIp();
            int port = getServerPort();
            // 只有非标准端口才添加
            if (port != 80) {
                baseUrl = baseUrl + ":" + port;
            }
            // 添加 context-path（如果存在）
            if (contextPath != null && !contextPath.isEmpty()) {
                baseUrl = baseUrl + contextPath;
            }
            log.debug("Built base URL from local config: {}", baseUrl);
            return baseUrl;
        } catch (Exception e) {
            log.warn("Failed to build base URL from request, fallback to local config", e);
            String baseUrl = "http://" + getServerIp();
            int port = getServerPort();
            if (port != 80) {
                baseUrl = baseUrl + ":" + port;
            }
            // 即使出错，也尝试添加配置的 context-path
            if (configuredContextPath != null && !configuredContextPath.isEmpty()) {
                String contextPath = configuredContextPath.trim();
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                if (contextPath.endsWith("/") && contextPath.length() > 1) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                baseUrl = baseUrl + contextPath;
            }
            log.debug("Built base URL from fallback: {}", baseUrl);
            return baseUrl;
        }
    }

    private SessionContext initializeSession(String connectionSource, ServerRequest request, String serviceName, TransportType transportType) {
        String host = request.headers().firstHeader("Host");
        String forwardedHost = request.headers().firstHeader("X-Forwarded-Host");
        String forwardedProto = request.headers().firstHeader("X-Forwarded-Proto");
        log.info("📡 {} connection request: serviceName={}, path={}, queryParams={}, Host={}, X-Forwarded-Host={}, X-Forwarded-Proto={}",
                connectionSource, serviceName, request.path(), request.queryParams(), host, forwardedHost, forwardedProto);
        String baseUrl = buildBaseUrlFromRequest(request);
        String sessionId = UUID.randomUUID().toString();
        String messageEndpoint = (serviceName != null && !serviceName.isEmpty())
                ? String.format("%s/mcp/%s/message?sessionId=%s", baseUrl, serviceName, sessionId)
                : String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);
        log.info("📡 Generated endpoint: serviceName={}, baseUrl={}, messageEndpoint={}",
                serviceName, baseUrl, messageEndpoint);

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        if (serviceName == null || serviceName.isEmpty()) {
            log.warn("⚠️ No serviceName provided (source={}), path={}", connectionSource, request.path());
        } else {
            try {
                sessionService.registerSessionService(sessionId, serviceName, transportType);
                log.info("✅ Registered service for connection: sessionId={}, serviceName={}", sessionId, serviceName);
            } catch (Exception e) {
                log.warn("⚠️ Failed to register session service: {}, will retry asynchronously", e.getMessage());
                Mono.fromRunnable(() -> sessionService.registerSessionService(sessionId, serviceName, transportType))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                                null,
                                error -> log.warn("⚠️ Failed to register session service asynchronously: {}", error.getMessage())
                        );
            }
        }

        sessionService.registerSseSink(sessionId, sink);
        sessionBridgeService.registerClientSession(sessionId, serviceName, sink);
        log.info("✅ Registered sink & bridge session: sessionId={}, serviceName={}", sessionId, serviceName);

        try {
            sessionService.touch(sessionId);
        } catch (Exception e) {
            log.warn("⚠️ Failed to touch session: {}, will retry asynchronously", e.getMessage());
            Mono.fromRunnable(() -> sessionService.touch(sessionId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            null,
                            error -> log.warn("⚠️ Failed to touch session asynchronously: {}", error.getMessage())
                    );
        }

        return new SessionContext(sessionId, serviceName, baseUrl, messageEndpoint, sink, connectionSource, transportType);
    }

    private Flux<ServerSentEvent<String>> buildEventFlux(SessionContext context) {
        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data(context.messageEndpoint())
                .build();

        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<String>builder()
                        .comment("heartbeat " + System.currentTimeMillis()) // 使用 comment，客户端会忽略，不会报错
                        .build())
                .doOnNext(tick -> {
                    Mono.fromRunnable(() -> sessionService.touch(context.sessionId()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(
                                    null,
                                    error -> log.debug("⚠️ Failed to touch session in heartbeat: {}", error.getMessage())
                            );
                    log.debug("💓 heartbeat: sessionId={}, connectionSource={}", context.sessionId(), context.connectionSource());
                });

        return Flux.concat(
                Flux.just(endpointEvent),
                Flux.merge(
                        context.sink().asFlux()
                                .doOnSubscribe(s -> log.debug("🔌 Sink subscribed: sessionId={}", context.sessionId()))
                                .onBackpressureBuffer(1000),
                        heartbeatFlux
                                .doOnSubscribe(s -> log.debug("💓 Heartbeat subscribed: sessionId={}", context.sessionId()))
                                .onBackpressureBuffer(100)
                )
        )
        .share()
        .doOnSubscribe(subscription -> log.info("✅ Connection subscribed: sessionId={}, serviceName={}, baseUrl={}",
                context.sessionId(), context.serviceName(), context.baseUrl()))
        .doOnCancel(() -> {
            log.warn("❌ Connection cancelled: sessionId={}, serviceName={}, baseUrl={}, reason=client_disconnect",
                    context.sessionId(), context.serviceName(), context.baseUrl());
            if (sessionService.getSseSink(context.sessionId()) != null) {
                sessionService.removeSession(context.sessionId());
                sessionBridgeService.removeClientSession(context.sessionId());
                context.sink().tryEmitComplete();
            } else {
                log.debug("⚠️ Session {} already cleaned up, skip duplicate cancel", context.sessionId());
            }
        })
        .doOnError(error -> {
            log.error("❌ Connection error: sessionId={}, serviceName={}, baseUrl={}",
                    context.sessionId(), context.serviceName(), context.baseUrl(), error);
            if (sessionService.getSseSink(context.sessionId()) != null) {
                sessionService.removeSession(context.sessionId());
                sessionBridgeService.removeClientSession(context.sessionId());
                context.sink().tryEmitError(error);
            } else {
                log.debug("⚠️ Session {} already cleaned up due to error, skip duplicate cleanup", context.sessionId());
            }
        })
        .doOnComplete(() -> log.info("✅ Connection completed: sessionId={}, serviceName={}, source={}",
                context.sessionId(), context.serviceName(), context.connectionSource()));
    }

    private Mono<ServerResponse> buildSseResponse(Flux<ServerSentEvent<String>> eventFlux) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(BodyInserters.fromServerSentEvents(eventFlux));
    }

    private Mono<ServerResponse> buildStreamableResponse(SessionContext context, Flux<String> streamFlux, MediaType mediaType) {
        ServerResponse.BodyBuilder builder = ServerResponse.ok()
                .contentType(mediaType)
                .header("Cache-Control", "no-cache, no-transform")
                .header("Connection", "keep-alive");

        if (context != null && StringUtils.hasText(context.sessionId())) {
            builder.header("Mcp-Session-Id", context.sessionId());
            builder.header("Mcp-Transport", context.transportType().name().toLowerCase());
        }

        return builder.body(BodyInserters.fromPublisher(streamFlux, String.class));
    }

    private MediaType resolveStreamableMediaType(ServerRequest request) {
        String accept = request.headers().firstHeader("Accept");
        log.info("📡 Streamable request Accept header: {}", accept);
        if (accept != null) {
            accept = accept.toLowerCase();
            if (accept.contains("application/x-ndjson+stream")) {
                return MediaType.parseMediaType("application/x-ndjson+stream");
            }
            if (accept.contains("application/x-ndjson")) {
                return MediaType.parseMediaType("application/x-ndjson");
            }
            if (accept.contains("application/json")) {
                return MediaType.APPLICATION_JSON;
            }
        }
        return MediaType.parseMediaType("application/x-ndjson");
    }

    private String toStreamableJson(ServerSentEvent<String> event) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "event");
        payload.put("event", event.event() != null ? event.event() : "message");
        if (event.id() != null) {
            payload.put("id", event.id());
        }
        java.util.Map<String, Object> dataNode = new java.util.LinkedHashMap<>();
        dataNode.put("data", event.data());
        if (event.retry() != null) {
            dataNode.put("retry", event.retry());
        }
        if (event.comment() != null) {
            dataNode.put("comment", event.comment());
        }
        payload.put("payload", dataNode);
        try {
            return objectMapper.writeValueAsString(payload) + "\n";
        } catch (Exception e) {
            log.warn("⚠️ Failed to encode streamable payload, fallback to error stub: {}", e.getMessage());
            return "{\"type\":\"event\",\"event\":\"error\",\"payload\":{\"data\":\"encoding failure\"}}\n";
        }
    }

    private record SessionContext(
            String sessionId,
            String serviceName,
            String baseUrl,
            String messageEndpoint,
            Sinks.Many<ServerSentEvent<String>> sink,
            String connectionSource,
            TransportType transportType) {}
    
    /**
     * 从请求路径中提取 context-path
     * 例如：请求路径是 /mcp-bridge/sse/mcp-server-beta，context-path 是 /mcp-bridge
     * 
     * 注意：在反向代理环境下，request.path() 可能已经去除了 context-path，
     * 所以需要从完整的请求 URI 或代理头中提取。
     */
    private String extractContextPath(ServerRequest request) {
        try {
            // 1. 优先从 X-Forwarded-Prefix 头中获取（反向代理通常设置此头）
            String forwardedPrefix = request.headers().firstHeader("X-Forwarded-Prefix");
            if (forwardedPrefix == null || forwardedPrefix.isEmpty()) {
                forwardedPrefix = request.headers().firstHeader("x-forwarded-prefix");
            }
            if (forwardedPrefix != null && !forwardedPrefix.isEmpty()) {
                String contextPath = forwardedPrefix.trim();
                // 确保以 / 开头
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                // 移除末尾的斜杠
                if (contextPath.endsWith("/") && contextPath.length() > 1) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                log.info("✅ Extracted context-path from X-Forwarded-Prefix: {}", contextPath);
                return contextPath;
            }
            
            // 2. 从完整的请求 URI 路径中提取（如果反向代理保留了完整路径）
            String fullPath = request.uri().getPath();
            String requestPath = request.path();
            
            // 如果完整路径和请求路径不同，说明可能有 context-path
            if (fullPath != null && requestPath != null && 
                !fullPath.equals(requestPath) && fullPath.startsWith(requestPath)) {
                // 计算差异部分，这可能是 context-path
                String diff = fullPath.substring(0, fullPath.length() - requestPath.length());
                if (diff.endsWith("/")) {
                    diff = diff.substring(0, diff.length() - 1);
                }
                if (!diff.isEmpty()) {
                    log.debug("Extracted context-path from URI difference: {}", diff);
                    return diff;
                }
            }
            
            // 3. 从请求路径的第一个段推断（如果路径包含多个段）
            if (requestPath != null && !requestPath.isEmpty() && !requestPath.equals("/")) {
                String path = requestPath;
                // 移除开头的斜杠
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                
                // 查找第一个路径段（context-path）
                // 例如：/mcp-bridge/sse/mcp-server-beta -> mcp-bridge
                String[] segments = path.split("/");
                if (segments.length > 1 && !segments[0].isEmpty()) {
                    String firstSegment = segments[0];
                    
                    // 检查是否是已知的 API 路径（如果是，则不是 context-path）
                    // 已知的 API 路径：sse, mcp
                    // 如果第一个段不是 sse 或 mcp，则可能是 context-path
                    if (!firstSegment.equals("sse") && !firstSegment.equals("mcp")) {
                        String contextPath = "/" + firstSegment;
                        log.debug("Extracted context-path from first segment: {}", contextPath);
                        return contextPath;
                    }
                }
            }
            
            // 4. 从配置文件中获取（mcp.router.context-path）
            if (configuredContextPath != null && !configuredContextPath.isEmpty()) {
                String contextPath = configuredContextPath.trim();
                // 确保以 / 开头
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                // 移除末尾的斜杠
                if (contextPath.endsWith("/") && contextPath.length() > 1) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                log.debug("Extracted context-path from mcp.router.context-path config: {}", contextPath);
                return contextPath;
            }
            
            // 5. 从 Spring 环境变量中获取
            String contextPath = environment.getProperty("server.servlet.context-path");
            if (contextPath != null && !contextPath.isEmpty()) {
                // 确保以 / 开头
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                // 移除末尾的斜杠
                if (contextPath.endsWith("/") && contextPath.length() > 1) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                log.debug("Extracted context-path from Spring config: {}", contextPath);
                return contextPath;
            }
            
            log.debug("No context-path found in request");
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract context-path from request: {}", e.getMessage());
            // 尝试从 Spring 环境变量中获取
            String contextPath = environment.getProperty("server.servlet.context-path");
            if (contextPath != null && !contextPath.isEmpty()) {
                if (!contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                if (contextPath.endsWith("/") && contextPath.length() > 1) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                return contextPath;
            }
            return null;
        }
    }


    /**
     * 处理 MCP 消息请求（使用路径参数方式：/mcp/{serviceName}/message?sessionId=xxx）
     * 从路径变量中提取服务名称，从查询参数中提取 sessionId
     */
    private Mono<ServerResponse> handleMcpMessageWithPath(ServerRequest request) {
        // 从路径变量中提取服务名称
        String serviceName = request.pathVariable("serviceName");
        // 优先从请求头提取 sessionId，兼容 Streamable 官方规范
        String sessionId = resolveSessionId(request);
        
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
        // 优先从请求头提取 sessionId（Mcp-Session-Id），再回退到查询参数
        String sessionId = resolveSessionId(request);
        
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
     * 优先按照 Streamable 官方说明从请求头解析 sessionId，
     * 兼容历史查询参数 ?sessionId= 的使用方式。
     */
    private String resolveSessionId(ServerRequest request) {
        // 1. 尝试从请求头中解析（Streamable 官方规范）
        for (String headerName : SESSION_ID_HEADER_CANDIDATES) {
            String headerValue = request.headers().firstHeader(headerName);
            if (StringUtils.hasText(headerValue)) {
                log.info("✅ Resolved sessionId from header '{}': {}", headerName, headerValue);
                return headerValue;
            }
        }
        
        // 2. 回退到查询参数（兼容历史方式）
        String querySessionId = request.queryParam("sessionId")
                .filter(StringUtils::hasText)
                .orElse(null);
        
        if (querySessionId != null) {
            log.info("✅ Resolved sessionId from query parameter: {}", querySessionId);
            return querySessionId;
        }
        
        // 3. 没有找到 sessionId
        log.warn("⚠️ No sessionId found in request headers or query parameters. " +
                "Client should pass sessionId via 'Mcp-Session-Id' header or '?sessionId=' query parameter. " +
                "Path: {}, Method: {}", 
                request.path(), request.method());
        
        return null;
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
                        if (sessionId != null && !sessionId.isEmpty()) {
                            mcpMessage.setSessionId(sessionId);
                            if (mcpMessage.getMetadata() == null) {
                                mcpMessage.setMetadata(new java.util.HashMap<>());
                            }
                            mcpMessage.getMetadata().put("sessionId", sessionId);
                        }
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
                            // 使用 finalServiceName，如果为 null 则使用 "router" 作为默认值
                            String serviceNameForLog = (finalServiceName != null && !finalServiceName.isEmpty()) 
                                    ? finalServiceName : "router";
                            
                            // 关键修复：先等待 SSE sink 就绪，然后再处理 initialize 请求
                            // 这样可以确保响应能够立即通过 SSE 发送
                            // 激进优化：缩短等待时间到0.5秒，SSE sink注册应该立即完成
                            Mono<Sinks.Many<ServerSentEvent<String>>> sseSinkMono = sessionService.waitForSseSink(sessionId, 0)
                                    .timeout(Duration.ofMillis(500))
                                    .doOnNext(sink -> log.debug("✅ SSE sink found for sessionId={}", sessionId))
                                    .doOnError(error -> log.warn("⚠️ Error waiting for SSE sink: sessionId={}, error={}", sessionId, error.getMessage()))
                                    .switchIfEmpty(Mono.defer(() -> {
                                        // 如果等待超时，记录调试信息
                                        java.util.Set<String> allSessions = sessionService.getAllSessionIds();
                                        log.warn("⚠️ SSE sink not found for sessionId={} after waiting. " +
                                                "Registered sessions: {}. " +
                                                "This may indicate: 1) SSE connection not established yet, " +
                                                "2) sessionId mismatch, or 3) SSE connection already closed.",
                                                sessionId, allSessions);
                                        return Mono.empty();
                                    }));
                            
                            // 先等待 SSE sink 就绪，然后再处理 initialize 请求
                            return sseSinkMono
                                    .flatMap(sseSink -> {
                                        // SSE sink 已就绪，现在处理 initialize 请求
                                        Mono<McpMessage> initializeResponse = routerService.routeRequest(serviceNameForLog, mcpMessage);
                            return initializeResponse
                                    .flatMap(response -> {
                                        try {
                                            // 将 McpMessage 转换为标准 JSON-RPC 2.0 格式
                                            String responseJson = convertToJsonRpcResponse(response);
                                            log.info("📤 Sending initialize response via SSE (length={}): {}", responseJson.length(), responseJson);
                                            
                                                        // SSE sink 已经就绪，直接发送响应
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
                                        } catch (Exception e) {
                                            log.error("❌ Failed to convert initialize response to JSON", e);
                                            String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
                                            
                                            // 尝试通过 SSE 发送错误响应
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
                                                    }
                                                })
                                                .onErrorResume(error -> {
                                                    log.error("❌ Initialize error: {}", error.getMessage(), error);
                                                    try {
                                                        String errorResponse = createErrorResponse(mcpMessage, error);
                                                        log.info("📤 Sending initialize error response via SSE: {}", errorResponse);
                                                        
                                                        // 尝试通过 SSE 发送错误响应
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
                                                    } catch (Exception e) {
                                                        log.error("❌ Failed to create error response", e);
                                                return ServerResponse.status(500)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}");
                                                    }
                                                });
                                    })
                                    .switchIfEmpty(Mono.defer(() -> {
                                        // 如果没有 SSE sink，先处理 initialize 请求，然后回退到 HTTP 响应
                                        Mono<McpMessage> initializeResponse = routerService.routeRequest(serviceNameForLog, mcpMessage);
                                        return initializeResponse
                                                .flatMap(response -> {
                                                    try {
                                                        String responseJson = convertToJsonRpcResponse(response);
                                                        java.util.Set<String> allSessions = sessionService.getAllSessionIds();
                                                        log.warn("⚠️ No SSE sink found for sessionId={}, falling back to HTTP response. " +
                                                                "Registered sessions: {}. " +
                                                                "Possible causes: 1) SSE connection not established, " +
                                                                "2) sessionId mismatch, 3) SSE connection closed.",
                                                                sessionId, allSessions);
                                                        return ServerResponse.ok()
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue(responseJson);
                                                    } catch (Exception e) {
                                                        log.error("❌ Failed to convert initialize response to JSON", e);
                                                        return ServerResponse.status(500)
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}");
                                                    }
                                                });
                                    }))
                                    .onErrorResume(error -> {
                                        log.error("❌ Initialize error: {}", error.getMessage(), error);
                                        try {
                                            String errorResponse = createErrorResponse(mcpMessage, error);
                                            log.info("📤 Sending initialize error response via SSE: {}", errorResponse);
                                            
                                            // 尝试通过 SSE 发送错误响应
                                            return sseSinkMono
                                                    .flatMap(sseSink -> {
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
                                                    })
                                                    .switchIfEmpty(Mono.defer(() -> {
                                                return ServerResponse.status(500)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(errorResponse);
                                                    }));
                                        } catch (Exception e) {
                                            log.error("❌ Failed to create initialize error response", e);
                                            String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + (mcpMessage != null ? mcpMessage.getId() : "unknown") + "\",\"error\":{\"code\":-32603,\"message\":\"Internal server error\"}}";
                                            
                                            return sseSinkMono
                                                    .flatMap(sseSink -> {
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
                                                    })
                                                    .switchIfEmpty(Mono.defer(() -> {
                                                return ServerResponse.status(500)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .bodyValue(errorJson);
                                                    }));
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
                        
                        // 激进优化：缩短等待时间到0.5秒，SSE sink注册应该立即完成
                        Mono<Sinks.Many<ServerSentEvent<String>>> sseSinkMono = sessionService.waitForSseSink(sessionId, 0)
                                .timeout(Duration.ofMillis(500))
                                .doOnNext(sink -> log.debug("✅ SSE sink found for sessionId={}", sessionId))
                                .switchIfEmpty(Mono.defer(() -> {
                                    java.util.Set<String> allSessions = sessionService.getAllSessionIds();
                                    log.warn("⚠️ SSE sink not found for sessionId={} after waiting. Registered sessions: {}",
                                            sessionId, allSessions);
                                    return Mono.empty();
                                }));
                        
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
                                                .timeout(Duration.ofMillis(500))
                                                .flatMap(responseJson -> {
                                                    log.info("✅ Received response from backend server: {}", responseJson);
                                                    
                                                    // 等待 SSE sink 就绪后发送响应
                                                    return sseSinkMono
                                                            .flatMap(sseSink -> {
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
                                                            })
                                                            .switchIfEmpty(Mono.defer(() -> {
                                                        // 如果没有 SSE sink，回退到 HTTP 响应（向后兼容）
                                                                java.util.Set<String> allSessions = sessionService.getAllSessionIds();
                                                                log.warn("⚠️ No SSE sink found for sessionId={}, falling back to HTTP response. " +
                                                                        "Registered sessions: {}. " +
                                                                        "Possible causes: 1) SSE connection not established, " +
                                                                        "2) sessionId mismatch, 3) SSE connection closed.",
                                                                        sessionId, allSessions);
                                                        return ServerResponse.ok()
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .bodyValue(responseJson);
                                                            }));
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
                                                        return sseSinkMono
                                                                .flatMap(sseSink -> {
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
                                                                })
                                                                .switchIfEmpty(Mono.defer(() -> {
                                                            return ServerResponse.status(500)
                                                                    .contentType(MediaType.APPLICATION_JSON)
                                                                    .bodyValue(errorResponse);
                                                                }));
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
                                        // 对于 list 方法，使用较短的超时时间（10秒），避免长时间等待
                                        // 激进优化：缩短超时时间，确保总时间在1秒以内
                                        // tools/list 等 list 方法：500ms（连接300ms + 调用200ms）
                                        Duration timeout = (mcpMessage.getMethod() != null && 
                                                (mcpMessage.getMethod().endsWith("/list") || "tools/call".equals(mcpMessage.getMethod())))
                                                ? Duration.ofMillis(500) : Duration.ofSeconds(60);
                                        routeResult = routerService.routeRequest(targetServiceName, mcpMessage, timeout, Map.of());
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
                                        
                                        // 等待 SSE sink 就绪后发送响应
                                        return sseSinkMono
                                                .flatMap(sseSink -> {
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
                                                })
                                                .switchIfEmpty(Mono.defer(() -> {
                                            // 如果没有 SSE sink，回退到 HTTP 响应（向后兼容）
                                                    java.util.Set<String> allSessions = sessionService.getAllSessionIds();
                                                    log.warn("⚠️ No SSE sink found for sessionId={}, falling back to HTTP response. " +
                                                            "Registered sessions: {}. " +
                                                            "Possible causes: 1) SSE connection not established, " +
                                                            "2) sessionId mismatch, 3) SSE connection closed.",
                                                            sessionId, allSessions);
                                            return ServerResponse.ok()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(responseJson);
                                                }));
                                    } catch (Exception e) {
                                        log.error("❌ Failed to convert response to JSON", e);
                                        String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + mcpMessage.getId() + "\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
                                        
                                        // 尝试通过 SSE 发送错误响应
                                        return sseSinkMono
                                                .flatMap(sseSink -> {
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
                                                })
                                                .switchIfEmpty(Mono.defer(() -> {
                                            return ServerResponse.status(500)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorJson);
                                                }));
                                    }
                                })
                                .onErrorResume(error -> {
                                    log.error("❌ Routing error: {}", error.getMessage(), error);
                                    try {
                                        String errorResponse = createErrorResponse(mcpMessage, error);
                                        log.info("📤 Sending error response via SSE: {}", errorResponse);
                                        
                                        // 尝试通过 SSE 发送错误响应
                                        return sseSinkMono
                                                .flatMap(sseSink -> {
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
                                                })
                                                .switchIfEmpty(Mono.defer(() -> {
                                            return ServerResponse.status(500)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorResponse);
                                                }));
                                    } catch (Exception e) {
                                        log.error("❌ Failed to create error response", e);
                                        String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":\"" + (mcpMessage != null ? mcpMessage.getId() : "unknown") + "\",\"error\":{\"code\":-32603,\"message\":\"Internal server error\"}}";
                                        
                                        return sseSinkMono
                                                .flatMap(sseSink -> {
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
                                                })
                                                .switchIfEmpty(Mono.defer(() -> {
                                            return ServerResponse.status(500)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(errorJson);
                                                }));
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

