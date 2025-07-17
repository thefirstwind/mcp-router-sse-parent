package com.nacos.mcp.router.v2.controller;

import com.nacos.mcp.router.v2.model.McpServerInfo;
import com.nacos.mcp.router.v2.model.McpServerConfig;
import com.nacos.mcp.router.v2.registry.McpServerRegistry;
import com.nacos.mcp.router.v2.service.McpConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP服务发现控制器
 */
@Slf4j
@RestController
@RequestMapping("/mcp/servers")
@RequiredArgsConstructor
public class McpServerController {
    
    private final McpServerRegistry mcpServerRegistry;
    private final McpConfigService mcpConfigService;
    
    /**
     * 获取所有健康的MCP服务器
     */
    @GetMapping("/healthy")
    public Flux<McpServerInfo> getHealthyServers(
            @RequestParam(defaultValue = "mcp-server-v2") String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Getting healthy servers for service: {}, group: {}", serviceName, serviceGroup);
        return mcpServerRegistry.getAllHealthyServers(serviceName, serviceGroup);
    }
    
    /**
     * 获取指定服务的所有实例
     */
    @GetMapping("/instances")
    public Flux<McpServerInfo> getAllInstances(
            @RequestParam(defaultValue = "mcp-server-v2") String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Getting all instances for service: {}, group: {}", serviceName, serviceGroup);
        return mcpServerRegistry.getAllInstances(serviceName, serviceGroup);
    }
    
    /**
     * 选择一个健康的服务实例
     */
    @GetMapping("/select")
    public Mono<McpServerInfo> selectHealthyServer(
            @RequestParam(defaultValue = "mcp-server-v2") String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Selecting healthy server for service: {}, group: {}", serviceName, serviceGroup);
        return mcpServerRegistry.selectHealthyServer(serviceName, serviceGroup);
    }
    
    /**
     * 手动注册MCP服务器（测试用）
     */
    @PostMapping("/register")
    public Mono<String> registerServer(@RequestBody McpServerInfo serverInfo) {
        log.info("Manually registering MCP server: {}", serverInfo.getName());
        return mcpServerRegistry.registerServer(serverInfo)
                .then(Mono.just("Server registered successfully"));
    }
    
    /**
     * 注销MCP服务器（测试用）
     */
    @DeleteMapping("/deregister")
    public Mono<String> deregisterServer(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Deregistering MCP server: {}", serviceName);
        return mcpServerRegistry.deregisterServer(serviceName, serviceGroup)
                .then(Mono.just("Server deregistered successfully"));
    }
    
    /**
     * 获取服务器配置信息
     */
    @GetMapping("/config/{serverName}")
    public Mono<McpServerConfig> getServerConfig(
            @PathVariable String serverName,
            @RequestParam(defaultValue = "1.0.0") String version) {
        log.info("Getting server config for: {} version: {}", serverName, version);
        return mcpConfigService.getServerConfig(serverName, version);
    }
    
    /**
     * 发布服务器配置
     */
    @PostMapping("/config/publish")
    public Mono<String> publishServerConfig(@RequestBody McpServerInfo serverInfo) {
        log.info("Publishing server config for: {}", serverInfo.getName());
        return mcpConfigService.publishServerConfig(serverInfo)
                .map(success -> success ? "Server config published successfully" : "Failed to publish server config");
    }
    
    /**
     * 发布工具配置
     */
    @PostMapping("/config/tools/publish")
    public Mono<String> publishToolsConfig(@RequestBody McpServerInfo serverInfo) {
        log.info("Publishing tools config for: {}", serverInfo.getName());
        return mcpConfigService.publishToolsConfig(serverInfo)
                .map(success -> success ? "Tools config published successfully" : "Failed to publish tools config");
    }
    
    /**
     * 发布版本配置
     */
    @PostMapping("/config/version/publish")
    public Mono<String> publishVersionConfig(@RequestBody McpServerInfo serverInfo) {
        log.info("Publishing version config for: {}", serverInfo.getName());
        return mcpConfigService.publishVersionConfig(serverInfo)
                .map(success -> success ? "Version config published successfully" : "Failed to publish version config");
    }
} 