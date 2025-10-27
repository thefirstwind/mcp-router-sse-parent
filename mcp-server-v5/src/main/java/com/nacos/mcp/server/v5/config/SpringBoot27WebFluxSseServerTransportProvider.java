package com.nacos.mcp.server.v5.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Spring Boot 2.7.18 兼容的 WebFlux SSE Transport Provider
 * 专门设计用于 Spring Boot 2.7.18 环境。
 */
@Slf4j
public class SpringBoot27WebFluxSseServerTransportProvider implements McpServerTransportProvider {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String messageEndpoint;
    private final String sseEndpoint;
    private volatile boolean running = false;
    private McpServerSession.Factory sessionFactory;
    private final Sinks.Many<String> sseSink = Sinks.many().multicast().onBackpressureBuffer();

    public SpringBoot27WebFluxSseServerTransportProvider(
            ObjectMapper objectMapper,
            String baseUrl,
            String messageEndpoint,
            String sseEndpoint
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.messageEndpoint = messageEndpoint;
        this.sseEndpoint = sseEndpoint;
        log.info("🔧 Creating SpringBoot27WebFluxSseServerTransportProvider with baseUrl: {}, messageEndpoint: {}, sseEndpoint: {}", 
                baseUrl, messageEndpoint, sseEndpoint);
    }

    @Override
    public McpServerTransport createTransport() {
        log.info("🚀 Creating MCP Server Transport for Spring Boot 2.7.18");
        return new SpringBoot27McpServerTransport();
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        log.info("🔧 Setting session factory: {}", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void start() {
        log.info("🚀 Starting SpringBoot27WebFluxSseServerTransportProvider");
        running = true;
    }

    @Override
    public void stop() {
        log.info("🛑 Stopping SpringBoot27WebFluxSseServerTransportProvider");
        running = false;
        sseSink.tryEmitComplete();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void notifyClients(String method, Object params) {
        log.debug("📢 Notifying clients - method: {}, params: {}", method, params);
        try {
            String message = objectMapper.writeValueAsString(params);
            sseSink.tryEmitNext(message);
        } catch (Exception e) {
            log.error("❌ Failed to notify clients", e);
        }
    }

    @Override
    public void closeGracefully() {
        log.info("🛑 Closing SpringBoot27WebFluxSseServerTransportProvider gracefully");
        stop();
    }

    /**
     * 创建 WebFlux 路由器函数
     */
    public RouterFunction<ServerResponse> createRouterFunction() {
        return RouterFunctions.route()
            .GET(sseEndpoint, this::handleSseRequest)
            .POST(messageEndpoint, this::handleMessageRequest)
            .build();
    }

    /**
     * 处理 SSE 请求
     */
    private Mono<ServerResponse> handleSseRequest(ServerRequest request) {
        log.info("📡 Handling SSE request from: {}", request.remoteAddress().orElse(null));
        
        Flux<String> eventFlux = sseSink.asFlux()
            .map(message -> String.format("data: %s\n\n", message))
            .doOnCancel(() -> log.info("❌ SSE connection cancelled"))
            .doOnError(error -> log.error("❌ SSE connection error", error));

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(eventFlux, String.class);
    }

    /**
     * 处理消息请求
     */
    private Mono<ServerResponse> handleMessageRequest(ServerRequest request) {
        return request.bodyToMono(String.class)
            .flatMap(message -> {
                log.info("📥 Received message: {}", message);
                sseSink.tryEmitNext(message);
                return ServerResponse.ok().build();
            });
    }

    /**
     * Spring Boot 2.7.18 兼容的 MCP Server Transport 实现
     */
    private class SpringBoot27McpServerTransport implements McpServerTransport {
        @Override
        public CompletableFuture<Void> send(String message) {
            log.debug("📤 Sending message: {}", message);
            sseSink.tryEmitNext(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            log.info("🛑 Closing transport");
            sseSink.tryEmitComplete();
        }
    }
} 