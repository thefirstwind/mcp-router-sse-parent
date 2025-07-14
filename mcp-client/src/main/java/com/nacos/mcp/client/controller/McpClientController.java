package com.nacos.mcp.client.controller;

import com.nacos.mcp.client.service.CustomMcpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

/**
 * MCP Client控制器
 * 通过 mcp-router 使用标准MCP协议与MCP服务器通信
 */
@RestController
@RequestMapping("/mcp-client/api/v1")
public class McpClientController {

    private static final Logger logger = LoggerFactory.getLogger(McpClientController.class);

    private final CustomMcpClient customMcpClient;

    public McpClientController(CustomMcpClient customMcpClient) {
        this.customMcpClient = customMcpClient;
    }

    /**
     * 获取工具列表（通过 mcp-router）
     */
    @GetMapping("/tools/list")
    public Mono<ResponseEntity<Map<String, Object>>> listTools() {
        logger.info("Listing available tools from mcp-router");

        return customMcpClient.listTools()
                .map(toolNames -> {
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "tools", toolNames,
                            "toolCount", toolNames.size(),
                            "router", "mcp-router"
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    logger.error("Failed to list tools from mcp-router", error);
                    Map<String, Object> response = Map.of(
                            "success", false,
                            "error", error.getMessage(),
                            "router", "mcp-router"
                    );
                    return Mono.just(ResponseEntity.ok(response));
                });
    }

    /**
     * 调用工具（通过 mcp-router）
     */
    @PostMapping("/tools/call")
    public Mono<ResponseEntity<Map<String, Object>>> callTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("toolName");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.getOrDefault("arguments", Map.of());

        logger.info("Calling tool: {} with arguments: {} via mcp-router", toolName, arguments);

        return customMcpClient.callTool(toolName, arguments)
                .map(result -> {
                    logger.info("Success to call tool, result: {}", JsonParser.toJson(result));
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "tool", toolName,
                            "result", result,  // 直接使用result，不再访问content字段
                            "isError", false,
                            "router", "mcp-router"
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    logger.error("Failed to call tool {} via mcp-router", toolName, error);
                    Map<String, Object> response = Map.of(
                            "success", false,
                            "tool", toolName,
                            "error", error.getMessage(),
                            "router", "mcp-router"
                    );
                    return Mono.just(ResponseEntity.ok(response));
                });
    }

    /**
     * 获取 mcp-router 连接状态
     */
    @GetMapping("/servers/status")
    public Mono<ResponseEntity<Map<String, Object>>> getServerStatus() {
        logger.info("Getting mcp-router connection status");

        // 尝试列出工具来检查连接状态
        return customMcpClient.listTools()
                .map(result -> {
                    Map<String, Object> routerStatus = new HashMap<>();
                    routerStatus.put("status", "connected");
                    routerStatus.put("clientInfo", customMcpClient.getClientInfo());
                    routerStatus.put("toolCount", result.size());

                    Map<String, Object> response = Map.of(
                            "success", true,
                            "router", routerStatus,
                            "timestamp", System.currentTimeMillis()
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    logger.error("Failed to get mcp-router status", error);
                    Map<String, Object> routerStatus = new HashMap<>();
                    routerStatus.put("status", "disconnected");
                    routerStatus.put("error", error.getMessage());

                    Map<String, Object> response = Map.of(
                            "success", false,
                            "router", routerStatus,
                            "timestamp", System.currentTimeMillis()
                    );
                    return Mono.just(ResponseEntity.ok(response));
                });
    }
}