package com.nacos.mcp.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for the Nacos MCP Router.
 * <p>
 * This application acts as a central router for MCP (Meta-protocol Communication Protocol) requests.
 * It leverages Spring Boot for rapid development and is designed to be a lightweight, efficient
 * gateway for various MCP services.
 * </p>
 * <p>
 * Key features include:
 * <ul>
 *   <li><b>Service Discovery:</b> Integrates with Nacos for dynamic service registration and discovery.</li>
 *   <li><b>AI-Powered Routing:</b> Utilizes Spring AI to intelligently route requests to appropriate MCP services.</li>
 *   <li><b>Asynchronous Processing:</b> Employs asynchronous capabilities for non-blocking request handling.</li>
 * </ul>
 * </p>
 *
 * @author Your Name or Team
 * @version 1.0.0
 * @since 2024-07-01
 * Uses Nacos for service discovery and Spring AI for MCP protocol support
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class NacosMcpRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(NacosMcpRouterApplication.class, args);
    }
} 