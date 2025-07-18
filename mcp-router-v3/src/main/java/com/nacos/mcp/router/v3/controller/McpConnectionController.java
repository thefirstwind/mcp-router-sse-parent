package com.nacos.mcp.router.v3.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 连接控制器 - 已禁用
 * 由于架构简化为纯Nacos服务发现，不再接收mcp-server的主动连接请求
 * mcp-router现在通过服务发现找到mcp-server并按需建立连接
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/servers")
@RequiredArgsConstructor
public class McpConnectionController {

    /**
     * 连接端点已禁用 - 返回提示信息
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> handleServerConnect(@RequestBody Map<String, Object> connectionInfo) {
        log.info("📡 Received deprecated connection request - redirecting to service discovery mode");
        
        return ResponseEntity.ok(Map.of(
                "success", false,
                "deprecated", true,
                "message", "Direct connection is no longer supported. Please register your MCP server to Nacos for automatic discovery.",
                "migration", Map.of(
                        "old_mode", "active_connection", 
                        "new_mode", "service_discovery",
                        "action_required", "Remove active connection logic and rely on Nacos service registration"
                )
        ));
    }

    /**
     * 断开连接端点 - 已禁用
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> handleServerDisconnect(@RequestBody Map<String, Object> disconnectionInfo) {
        log.info("📡 Received deprecated disconnection request - no action needed in service discovery mode");
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deprecated", true,
                "message", "Disconnection not needed in service discovery mode. Service will be automatically removed from Nacos when stopped."
        ));
    }
} 