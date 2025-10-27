// package com.nacos.mcp.server.v5.config;


// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;

// /**
//  * Spring Boot 2.7.18 兼容的 SSE Transport Provider
//  * 
//  * 这个类的主要目的是让 Spring AI 的 NacosMcpRegistryAutoConfiguration 
//  * 识别为非 StdioServerTransportProvider，从而注册为 SSE 类型到 Nacos
//  * 
//  * 实际的 SSE 处理由 WebFluxConfig 负责
//  * 
//  * 注意：这是一个简化的实现，仅用于类型识别，不提供完整的传输功能
//  */
// @Slf4j
// public class SpringBoot27SseTransportProvider {

//     @Value("${server.port:8065}")
//     private int serverPort;
    
//     @Value("${spring.ai.alibaba.mcp.nacos.ip:127.0.0.1}")
//     private String serverIp;

//     private volatile boolean running = false;

//     public void start() {
//         log.info("🚀 SpringBoot27SseTransportProvider: 启动 SSE 传输层");
//         log.info("📡 实际的 SSE 处理由 WebFluxConfig 的 RouterFunction 负责");
//         log.info("🔗 SSE 端点: http://{}:{}/sse", serverIp, serverPort);
//         log.info("💌 MCP 消息端点: http://{}:{}/mcp/message", serverIp, serverPort);
//         this.running = true;
//     }

//     public void stop() {
//         log.info("🛑 SpringBoot27SseTransportProvider: 停止 SSE 传输层");
//         this.running = false;
//     }

//     public boolean isRunning() {
//         return running;
//     }

//     // 注意：实际实现可能需要更多方法，但为了避免复杂的接口实现
//     // 我们创建一个最小化的版本，只是为了类型识别
//     // 如果编译错误，可能需要根据实际的接口定义添加更多方法
// } 