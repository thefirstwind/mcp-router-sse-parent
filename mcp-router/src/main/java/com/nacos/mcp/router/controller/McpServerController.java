package com.nacos.mcp.router.controller;

import com.nacos.mcp.router.model.McpServerInfo;
import com.nacos.mcp.router.service.McpServerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP Server 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/server-registry")
public class McpServerController {

    private final McpServerRegistry serverRegistry;

    public McpServerController(McpServerRegistry serverRegistry) {
        this.serverRegistry = serverRegistry;
    }

    /**
     * 获取所有健康的服务列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<McpServerInfo>> listServers() {
        try {
            List<McpServerInfo> servers = serverRegistry.getHealthyServers();
            return ResponseEntity.ok(servers);
        } catch (Exception e) {
            log.error("Failed to get server list", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取指定服务信息
     */
    @GetMapping("/instance/{instanceId}")
    public ResponseEntity<McpServerInfo> getServer(@PathVariable String instanceId) {
        try {
            McpServerInfo serverInfo = serverRegistry.getServerInfo(instanceId);
            if (serverInfo != null) {
                return ResponseEntity.ok(serverInfo);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to get server info for instanceId: {}", instanceId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 