package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.service.SmartMcpRouterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 智能工具调用控制器
 * 提供简化的工具调用接口，只需要工具名称和参数
 */
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
@Tag(name = "Smart Tool API", description = "智能工具调用API - 自动发现和负载均衡")
public class SmartToolController {

    private final static Logger log = LoggerFactory.getLogger(SmartToolController.class);
    private final SmartMcpRouterService smartMcpRouterService;

    /**
     * 智能工具调用 - 自动发现服务器
     * 
     * 用法示例：
     * POST /api/v1/tools/call
     * {
     *   "toolName": "getAllPersons",
     *   "arguments": {}
     * }
     */
    @PostMapping("/call")
    @Operation(summary = "智能工具调用", 
               description = "根据工具名称自动发现支持的服务器，使用负载均衡选择最优节点")
    public Mono<ResponseEntity<Object>> callTool(
            @RequestBody ToolCallRequest request) {
        
        log.info("🎯 Received smart tool call: {}", request.getToolName());
        
        return smartMcpRouterService.callTool(request.getToolName(), request.getArguments())
                .map(result -> ResponseEntity.ok((Object) Map.of(
                        "success", true,
                        "toolName", request.getToolName(),
                        "result", result,
                        "timestamp", System.currentTimeMillis()
                )))
                .onErrorResume(error -> Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                        "success", false,
                        "toolName", request.getToolName(),
                        "error", error.getMessage(),
                        "timestamp", System.currentTimeMillis()
                ))));
    }

    /**
     * 指定服务器的工具调用
     * 
     * 用法示例：
     * POST /api/v1/tools/call/specific
     * {
     *   "serverName": "mcp-server-v2",
     *   "toolName": "addPerson", 
     *   "arguments": {"name": "张三", "age": 25}
     * }
     */
    @PostMapping("/call/specific")
    @Operation(summary = "指定服务器工具调用", 
               description = "在指定的服务器上调用工具，会验证服务器是否支持该工具")
    public Mono<ResponseEntity<Object>> callToolOnServer(
            @RequestBody SpecificToolCallRequest request) {
        
        log.info("🎯 Received directed tool call: {} on server {}", 
                request.getToolName(), request.getServerName());
        
        return smartMcpRouterService.callTool(request.getServerName(), request.getToolName(), request.getArguments())
                .map(result -> ResponseEntity.ok((Object) Map.of(
                        "success", true,
                        "serverName", request.getServerName(),
                        "toolName", request.getToolName(),
                        "result", result,
                        "timestamp", System.currentTimeMillis()
                )))
                .onErrorResume(error -> Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                        "success", false,
                        "serverName", request.getServerName(),
                        "toolName", request.getToolName(),
                        "error", error.getMessage(),
                        "timestamp", System.currentTimeMillis()
                ))));
    }

    /**
     * 检查工具是否可用
     */
    @GetMapping("/check/{toolName}")
    @Operation(summary = "检查工具可用性", description = "检查指定工具是否有可用的服务器支持")
    public Mono<ResponseEntity<Object>> checkToolAvailability(
            @Parameter(description = "工具名称") @PathVariable String toolName) {
        
        return smartMcpRouterService.isToolAvailable(toolName)
                .map(available -> ResponseEntity.ok(Map.of(
                        "toolName", toolName,
                        "available", available,
                        "timestamp", System.currentTimeMillis()
                )));
    }

    /**
     * 获取工具的可用服务器列表
     */
    @GetMapping("/servers/{toolName}")
    @Operation(summary = "获取工具的服务器列表", description = "获取支持指定工具的所有服务器列表")
    public Mono<ResponseEntity<Object>> getServersForTool(
            @Parameter(description = "工具名称") @PathVariable String toolName) {
        
        return smartMcpRouterService.getServersForTool(toolName)
                .map(servers -> ResponseEntity.ok(Map.of(
                        "toolName", toolName,
                        "servers", servers,
                        "count", servers.size(),
                        "timestamp", System.currentTimeMillis()
                )));
    }

    /**
     * 获取所有可用工具列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取所有可用工具", description = "获取当前所有健康服务器支持的工具列表")
    public Mono<ResponseEntity<Object>> listAvailableTools() {
        
        return smartMcpRouterService.listAvailableTools()
                .map(tools -> ResponseEntity.ok(Map.of(
                        "tools", tools,
                        "count", tools.size(),
                        "timestamp", System.currentTimeMillis()
                )));
    }

    /**
     * 工具调用请求对象
     */
    public static class ToolCallRequest {
        private String toolName;
        private Map<String, Object> arguments;

        // Getters and Setters
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
    }

    /**
     * 指定服务器工具调用请求对象
     */
    public static class SpecificToolCallRequest {
        private String serverName;
        private String toolName;
        private Map<String, Object> arguments;

        // Getters and Setters
        public String getServerName() { return serverName; }
        public void setServerName(String serverName) { this.serverName = serverName; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
    }
} 