package com.nacos.mcp.router.v3.controller;

import com.nacos.mcp.router.v3.model.McpServerInfo;
import com.nacos.mcp.router.v3.registry.McpServerRegistry;
import com.nacos.mcp.router.v3.service.McpConfigService;
import com.nacos.mcp.router.v3.service.McpServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;

/**
 * MCP服务发现控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/servers")
@RequiredArgsConstructor
@Validated
public class McpServerController {
    
    private final McpServerRegistry mcpServerRegistry;
    private final McpConfigService mcpConfigService;
    private final McpServerService mcpServerService;
    
    /**
     * 获取所有MCP服务器（匹配验证脚本期望的接口）
     */
    @GetMapping
    public Flux<McpServerInfo> getAllMcpServers(
            @RequestParam(defaultValue = "*") String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Getting all MCP servers for service: {}, group: {}", serviceName, serviceGroup);
        return mcpServerService.getHealthyServers(serviceName, serviceGroup);
    }

    /**
     * 按服务组获取MCP服务器
     */
    @GetMapping("/group/{serviceGroup}")
    public Flux<McpServerInfo> getServersByGroup(@PathVariable String serviceGroup) {
        log.info("Getting servers by group: {}", serviceGroup);
        return mcpServerService.getHealthyServers("*", serviceGroup);
    }
    
    /**
     * 获取指定服务的所有实例
     */
    @GetMapping("/instances")
    public Flux<McpServerInfo> getAllInstances(
            @RequestParam(defaultValue = "mcp-server-v2") String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Getting all instances for service: {}, group: {}", serviceName, serviceGroup);
        return mcpServerService.getAllInstances(serviceName, serviceGroup);
    }
    
    /**
     * 选择一个健康的服务实例
     */
    @GetMapping("/select")
    public Mono<McpServerInfo> selectHealthyServer(
            @RequestParam(defaultValue = "mcp-server-v2") String serviceName,
            @RequestParam(defaultValue = "mcp-server") String serviceGroup) {
        log.info("Selecting healthy server for service: {}, group: {}", serviceName, serviceGroup);
        return mcpServerService.selectHealthyServer(serviceName, serviceGroup);
    }
    
    /**
     * 手动注册MCP服务器（测试用）
     *
     * 支持自定义 toolsMeta 字段扩展，如：
     * {
     *   "name": "demo-server",
     *   "version": "1.0.0",
     *   ...
     *   "toolsMeta": {
     *     "enabled": true,
     *     "labels": ["gray", "beta"],
     *     "region": "cn-east",
     *     "capabilities": ["TOOL", "AI"],
     *     "tags": ["test", "prod"],
     *     "gray": true,
     *     "env": "dev"
     *   }
     * }
     */
    @PostMapping("/register")
    public Mono<String> registerServer(@Valid @RequestBody McpServerInfo serverInfo) {
        // 额外手动校验（如有需要）
        if (serverInfo.getName() == null || serverInfo.getName().isBlank()) {
            return Mono.error(new IllegalArgumentException("服务名称(name)不能为空"));
        }
        if (serverInfo.getIp() == null || serverInfo.getIp().isBlank()) {
            return Mono.error(new IllegalArgumentException("服务IP(ip/host)不能为空"));
        }
        if (serverInfo.getPort() == 0) {
            return Mono.error(new IllegalArgumentException("服务端口(port)不能为空"));
        }
        if (serverInfo.getVersion() == null || serverInfo.getVersion().isBlank()) {
            return Mono.error(new IllegalArgumentException("服务版本(version)不能为空"));
        }
        // metadata可选，支持任意扩展
        log.info("Manually registering MCP server: {}", serverInfo.getName());
        return mcpServerService.registerServer(serverInfo)
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
        return mcpServerService.deregisterServer(serviceName, serviceGroup)
                .then(Mono.just("Server deregistered successfully"));
    }
    
    /**
     * 获取服务器配置信息
     *
     * 若未找到，返回结构化错误对象：
     * {
     *   "error": { "code": 10002, "message": "未找到目标服务配置，请检查服务是否注册" },
     *   "timestamp": 1688888888888
     * }
     */
    @GetMapping("/config/{id}")
    public Mono<Object> getServerConfig(
            @PathVariable String id,
            @RequestParam(defaultValue = "1.0.0") String version) {
        log.info("Getting server config for id: {} version: {}", id, version);
        return mcpServerService.getServerConfig(id, version);
    }

    /**
     * 获取完整服务配置信息（含工具能力）
     *
     * 若未找到，返回结构化错误对象，结构同上。
     */
    @GetMapping("/config/full/{id}")
    public Mono<Object> getFullServerConfig(
            @PathVariable String id,
            @RequestParam(defaultValue = "1.0.0") String version) {
        log.info("Getting full server config for id: {} version: {}", id, version);
        return mcpServerService.getFullServerConfig(id, version);
    }

    /**
     * 获取服务所有版本号
     *
     * 若未找到，返回结构化错误对象，结构同上。
     */
    @GetMapping("/config/versions/{id}")
    public Mono<Object> getServerVersions(@PathVariable String id) {
        log.info("Getting server versions for id: {}", id);
        return mcpServerService.getServerVersions(id);
    }
    
    /**
     * 发布服务器配置
     */
    @PostMapping("/config/publish")
    public Mono<String> publishServerConfig(@RequestBody McpServerInfo serverInfo) {
        log.info("Publishing server config for: {}", serverInfo.getName());
        return mcpServerService.publishServerConfig(serverInfo);
    }
    
    /**
     * 发布工具配置
     *
     * 支持自定义 toolsMeta 字段扩展，详见 /register 示例。
     */
    @PostMapping("/config/tools/publish")
    public Mono<String> publishToolsConfig(@RequestBody McpServerInfo serverInfo) {
        log.info("Publishing tools config for: {}", serverInfo.getName());
        return mcpServerService.publishToolsConfig(serverInfo);
    }
    
    /**
     * 发布版本配置
     */
    @PostMapping("/config/version/publish")
    public Mono<String> publishVersionConfig(@RequestBody McpServerInfo serverInfo) {
        log.info("Publishing version config for: {}", serverInfo.getName());
        return mcpServerService.publishVersionConfig(serverInfo);
    }
} 