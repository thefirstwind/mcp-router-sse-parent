package com.nacos.mcp.router.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Health Check Controller
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class HealthController {


    /**
     * Root Health check endpoint (for general health check)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis(),
            "service", "nacos-mcp-router",
            "version", "1.0.0"
        );
        return ResponseEntity.ok(healthStatus);
    }

    /**
     * Root Application info endpoint
     */
    @GetMapping("/info") 
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> appInfo = Map.of(
            "name", "Nacos MCP Router",
            "version", "1.0.0",
            "description", "MCP Router with Nacos Service Discovery",
            "spring-ai", "1.0.0",
            "nacos", "3.0.1"
        );
        return ResponseEntity.ok(appInfo);
    }
} 