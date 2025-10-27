// package com.nacos.mcp.server.v6.config;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.context.annotation.Primary;
// import org.springframework.core.env.Environment;
// import org.springframework.http.MediaType;
// import org.springframework.web.cors.CorsConfiguration;
// import org.springframework.web.cors.reactive.CorsWebFilter;
// import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
// import org.springframework.web.reactive.function.server.RouterFunction;
// import org.springframework.web.reactive.function.server.ServerRequest;
// import org.springframework.web.reactive.function.server.ServerResponse;
// import reactor.core.publisher.Mono;

// import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
// import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
// import static org.springframework.web.reactive.function.server.RequestPredicates.OPTIONS;
// import static org.springframework.web.reactive.function.server.RouterFunctions.route;

// /**
//  * WebFlux 路由配置
//  * 提供 MCP 协议所需的 SSE 和消息端点
//  */
// @Configuration
// public class WebFluxConfig {

//     private static final Logger logger = LoggerFactory.getLogger(WebFluxConfig.class);

//     @Autowired
//     private Environment environment;

//     @Value("${server.port:8066}")
//     private int serverPort;

//     @Autowired
//     private ObjectMapper objectMapper;

//     /**
//      * 配置全局 CORS
//      */
//     @Bean
//     public CorsWebFilter corsWebFilter() {
//         CorsConfiguration configuration = new CorsConfiguration();
//         configuration.addAllowedOriginPattern("*");
//         configuration.addAllowedMethod("*");
//         configuration.addAllowedHeader("*");
//         configuration.setAllowCredentials(true);
//         configuration.setMaxAge(3600L);

//         UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//         source.registerCorsConfiguration("/**", configuration);

//         return new CorsWebFilter(source);
//     }

//     /**
//      * 获取服务器IP地址
//      */
//     private String getServerIp() {
//         String address = environment.getProperty("server.address", "127.0.0.1");
//         // 如果配置的是 0.0.0.0（绑定所有接口），则在 MCP 客户端中使用 localhost
//         if ("0.0.0.0".equals(address)) {
//             return "127.0.0.1";
//         }
//         return address;
//     }

//     /**
//      * 创建 MCP 路由
//      */
//     @Bean
//     @Primary
//     public RouterFunction<ServerResponse> mcpRoutes() {
//         String baseUrl = String.format("http://%s:%d", getServerIp(), serverPort);
//         logger.info("Creating MCP routes with baseUrl: {}", baseUrl);

//         return route(GET("/sse"), this::handleSse)
//                 .andRoute(POST("/sse"), this::handleMcpMessage)  // 支持 POST 到 SSE 端点
//                 .andRoute(GET("/mcp/sse"), this::handleSse)      // 支持备用 SSE 端点
//                 .andRoute(GET("/api/mcp/sse"), this::handleSse)  // 支持 API 前缀的 SSE 端点
//                 .andRoute(GET("/mcp/message"), this::handleSse)  // 支持 GET 到 /mcp/message 返回 SSE
//                 .andRoute(POST("/mcp/message"), this::handleMcpMessage)
//                 .andRoute(OPTIONS("/sse"), this::handleOptions)
//                 .andRoute(OPTIONS("/mcp/message"), this::handleOptions)
//                 .andRoute(OPTIONS("/mcp/sse"), this::handleOptions)
//                 .andRoute(OPTIONS("/api/mcp/sse"), this::handleOptions);
//     }

//     /**
//      * 处理 SSE 请求
//      */
//     private Mono<ServerResponse> handleSse(ServerRequest request) {
//         String baseUrl = String.format("http://%s:%d", getServerIp(), serverPort);
//         String sessionId = java.util.UUID.randomUUID().toString();
//         String mcpEndpoint = String.format("%s/mcp/message?sessionId=%s", baseUrl, sessionId);

//         logger.info("📡 Handling SSE request, returning endpoint: {}", mcpEndpoint);

//         return ServerResponse.ok()
//                 .contentType(MediaType.TEXT_EVENT_STREAM)
//                 .bodyValue("data: " + mcpEndpoint + "\n\n");
//     }

//     /**
//      * 处理 OPTIONS 请求 (CORS 预检)
//      */
//     private Mono<ServerResponse> handleOptions(ServerRequest request) {
//         return ServerResponse.ok()
//                 .header("Access-Control-Allow-Origin", "*")
//                 .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
//                 .header("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization")
//                 .header("Access-Control-Max-Age", "3600")
//                 .build();
//     }

//     /**
//      * 处理 MCP 消息请求
//      */
//     private Mono<ServerResponse> handleMcpMessage(ServerRequest request) {
//         String sessionId = request.queryParam("sessionId").orElse("unknown");

//         return request.bodyToMono(String.class)
//                 .flatMap(body -> {
//                     logger.info("📥 Received MCP message from session {}: {}", sessionId, body);

//                     try {
//                         // 解析 JSON-RPC 消息
//                         var messageMap = objectMapper.readValue(body, java.util.Map.class);
//                         String method = (String) messageMap.get("method");
//                         String id = (String) messageMap.get("id");
//                         String jsonrpc = (String) messageMap.get("jsonrpc");
                        
//                         // 验证必须的 JSON-RPC 字段
//                         if (jsonrpc == null || !"2.0".equals(jsonrpc)) {
//                             String errorResponse = String.format(
//                                 "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"error\":{\"code\":-32600,\"message\":\"Invalid JSON-RPC version\"}}",
//                                 id
//                             );
//                             return ServerResponse.badRequest()
//                                     .contentType(MediaType.APPLICATION_JSON)
//                                     .bodyValue(errorResponse);
//                         }
                        
//                         if (method == null) {
//                             String errorResponse = String.format(
//                                 "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"error\":{\"code\":-32600,\"message\":\"Missing method field\"}}",
//                                 id
//                             );
//                             return ServerResponse.badRequest()
//                                     .contentType(MediaType.APPLICATION_JSON)
//                                     .bodyValue(errorResponse);
//                         }

//                         // 处理不同的 MCP 方法
//                         if ("tools/call".equals(method)) {
//                             return handleToolCall(messageMap, id);
//                         } else if ("tools/list".equals(method)) {
//                             return handleToolsList(id);
//                         } else if ("initialize".equals(method)) {
//                             return handleInitialize(id);
//                         } else {
//                             // 默认响应
//                             String response = String.format(
//                                 "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"result\":{\"status\":\"received\",\"method\":\"%s\"}}",
//                                 id, method
//                             );
//                             return ServerResponse.ok()
//                                     .contentType(MediaType.APPLICATION_JSON)
//                                     .bodyValue(response);
//                         }
//                     } catch (Exception e) {
//                         logger.error("❌ Error processing MCP message", e);
//                         String errorResponse = String.format(
//                             "{\"jsonrpc\":\"2.0\",\"id\":\"unknown\",\"error\":{\"code\":-32700,\"message\":\"Parse error: %s\"}}",
//                             e.getMessage()
//                         );
//                         return ServerResponse.badRequest()
//                                 .contentType(MediaType.APPLICATION_JSON)
//                                 .bodyValue(errorResponse);
//                     }
//                 });
//     }

//     /**
//      * 处理工具调用
//      */
//     private Mono<ServerResponse> handleToolCall(java.util.Map<String, Object> message, String id) {
//         try {
//             var params = (java.util.Map<String, Object>) message.get("params");
//             String toolName = (String) params.get("name");
//             var arguments = (java.util.Map<String, Object>) params.get("arguments");

//             logger.info("🔧 Calling tool: {} with arguments: {}", toolName, arguments);

//             // 模拟工具调用结果
//             String result = "";
//             if ("getPersonById".equals(toolName)) {
//                 Object idObj = arguments.get("id");
//                 Long personId = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(idObj.toString());

//                 if (personId == 1L) {
//                     result = "Person found: John Doe, age 30, gender MALE, nationality American";
//                 } else if (personId == 2L) {
//                     result = "Person found: Jane Smith, age 25, gender FEMALE, nationality Canadian";
//                 } else {
//                     result = "Person not found for ID: " + personId;
//                 }
//             } else if ("getAllPersons".equals(toolName)) {
//                 result = "All persons: [John Doe (ID: 1), Jane Smith (ID: 2), Hans Mueller (ID: 3)]";
//             } else {
//                 result = "Tool " + toolName + " executed successfully";
//             }

//             String response = String.format(
//                 "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"result\":{\"content\":[{\"type\":\"text\",\"text\": \"%s\"}]}}",
//                 id, result
//             );

//             logger.info("✅ Tool call result: {}", result);

//             return ServerResponse.ok()
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .bodyValue(response);

//         } catch (Exception e) {
//             logger.error("❌ Error in tool call", e);
//             String errorResponse = String.format(
//                 "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"error\":{\"code\":-32603,\"message\":\"Internal error: %s\"}}",
//                 id, e.getMessage()
//             );
//             return ServerResponse.status(500)
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .bodyValue(errorResponse);
//         }
//     }

//     /**
//      * 处理工具列表请求
//      */
//     private Mono<ServerResponse> handleToolsList(String id) {
//         String response = String.format(
//             "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"result\":{\"tools\":[" +
//                 "{\"name\":\"getAllPersons\",\"description\":\"Get all persons\"}," +
//                 "{\"name\":\"getPersonById\",\"description\":\"Get person by ID\"}" +
//             "]}}",
//             id
//         );

//         return ServerResponse.ok()
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(response);
//     }

//     /**
//      * 处理初始化请求
//      */
//     private Mono<ServerResponse> handleInitialize(String id) {
//         String response = String.format(
//             "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"result\":{" +
//                 "\"protocolVersion\":\"2024-11-05\"," +
//                 "\"capabilities\":{\"tools\":{\"listChanged\":true}}," +
//                 "\"serverInfo\":{\"name\":\"mcp-server-v6\",\"version\":\"1.0.1\"}" +
//             "}}",
//             id
//         );

//         return ServerResponse.ok()
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(response);
//     }
// }