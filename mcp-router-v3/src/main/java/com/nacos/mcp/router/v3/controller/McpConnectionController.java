package com.nacos.mcp.router.v3.controller;

import com.nacos.mcp.router.v3.service.McpConnectionEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 连接控制器
 * 接收来自 MCP Server 的连接请求
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/servers")
@RequiredArgsConstructor
public class McpConnectionController {

    private final McpConnectionEventListener connectionEventListener;

    /**
     * 接收 MCP Server 的连接请求
     */
    @PostMapping("/connect")
    public Mono<ResponseEntity<Map<String, Object>>> handleServerConnect(
            @RequestBody Map<String, Object> connectionInfo) {
        
        log.info("📡 Received connection request from MCP Server: {}", connectionInfo);
        
        return Mono.fromCallable(() -> {
            try {
                String serverId = (String) connectionInfo.get("serverId");
                String serverName = (String) connectionInfo.get("serverName");
                Integer serverPort = (Integer) connectionInfo.get("serverPort");
                String capabilities = (String) connectionInfo.get("capabilities");
                
                if (serverId == null || serverName == null || serverPort == null) {
                    log.warn("⚠️ Invalid connection request, missing required fields");
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "success", false,
                                    "message", "Missing required fields: serverId, serverName, serverPort"
                            ));
                }
                
                // 构建响应
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Connection request received");
                response.put("routerId", "mcp-router-v3");
                response.put("timestamp", System.currentTimeMillis());
                response.put("serverId", serverId);
                
                log.info("✅ Connection request accepted from server: {} ({})", serverName, serverId);
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("❌ Error handling connection request", e);
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                "success", false,
                                "message", "Internal server error: " + e.getMessage()
                        ));
            }
        });
    }

    /**
     * 断开连接请求
     */
    @PostMapping("/disconnect")
    public Mono<ResponseEntity<Map<String, Object>>> handleServerDisconnect(
            @RequestBody Map<String, Object> disconnectionInfo) {
        
        log.info("📡 Received disconnection request from MCP Server: {}", disconnectionInfo);
        
        return Mono.fromCallable(() -> {
            try {
                String serverId = (String) disconnectionInfo.get("serverId");
                String serverName = (String) disconnectionInfo.get("serverName");
                
                if (serverId == null || serverName == null) {
                    log.warn("⚠️ Invalid disconnection request, missing required fields");
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "success", false,
                                    "message", "Missing required fields: serverId, serverName"
                            ));
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Disconnection request received");
                response.put("serverId", serverId);
                response.put("timestamp", System.currentTimeMillis());
                
                log.info("✅ Disconnection request accepted from server: {} ({})", serverName, serverId);
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("❌ Error handling disconnection request", e);
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                "success", false,
                                "message", "Internal server error: " + e.getMessage()
                        ));
            }
        });
    }

    /**
     * 获取当前所有连接状态
     */
    @GetMapping("/connections")
    public Mono<ResponseEntity<Map<String, Object>>> getAllConnections() {
        return Mono.fromCallable(() -> {
            try {
                var connections = connectionEventListener.getAllConnections();
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("total", connections.size());
                response.put("connections", connections);
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("❌ Error getting connections", e);
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                "success", false,
                                "message", "Internal server error: " + e.getMessage()
                        ));
            }
        });
    }

    /**
     * 检查指定服务器的连接状态
     */
    @GetMapping("/connections/{serverName}")
    public Mono<ResponseEntity<Map<String, Object>>> getServerConnection(
            @PathVariable String serverName) {
        
        return Mono.fromCallable(() -> {
            try {
                var connectionInfo = connectionEventListener.getConnection(serverName);
                boolean isConnected = connectionEventListener.isServerConnected(serverName);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("serverName", serverName);
                response.put("connected", isConnected);
                response.put("connectionInfo", connectionInfo);
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("❌ Error getting connection for server: {}", serverName, e);
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                "success", false,
                                "message", "Internal server error: " + e.getMessage()
                        ));
            }
        });
    }
} 