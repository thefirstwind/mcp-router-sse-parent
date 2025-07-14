package com.nacos.mcp.router.controller;

import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.model.SearchResponse;
import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.McpServerRegistrationRequest;
import com.nacos.mcp.router.model.McpResource;
import com.nacos.mcp.router.model.McpPrompt;
import com.nacos.mcp.router.service.SearchService;
import com.nacos.mcp.router.service.McpServerService;
import com.nacos.mcp.router.service.McpResourceService;
import com.nacos.mcp.router.service.McpPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import java.util.Map;
import java.util.List;

/**
 * MCP Router REST API Controller
 */
@RestController
@RequestMapping("/api/mcp")
@Validated
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.PUT, RequestMethod.OPTIONS})
public class McpRouterController {

    private final SearchService searchService;
    private final McpServerService mcpServerService;
    private final McpResourceService mcpResourceService;
    private final McpPromptService mcpPromptService;

    /**
     * Search MCP servers
     */
    @PostMapping("/search")
    public Mono<ResponseEntity<SearchResponse>> searchMcpServers(@Valid @RequestBody SearchRequest request) {
        log.info("Searching MCP servers with request: {}", request);
        return searchService.searchMcpServers(request)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Search failed: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Search MCP servers by task description and keywords
     */
    @GetMapping("/search")
    public Mono<ResponseEntity<SearchResponse>> searchMcpServers(
            @RequestParam("taskDescription") String taskDescription,
            @RequestParam(value = "keywords", required = false) String keywords) {
        
        String[] keywordArray = keywords != null ? keywords.split(",") : new String[0];
        log.info("Searching MCP servers with task: {}, keywords: {}", taskDescription, keywords);
        
        return searchService.searchMcpServers(taskDescription, keywordArray)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Search failed: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Add a MCP server
     */
    @PostMapping("/servers/{serverName}")
    public Mono<ResponseEntity<McpServer>> addMcpServer(@PathVariable("serverName") String serverName) {
        log.info("Adding MCP server: {}", serverName);
        return mcpServerService.addMcpServer(serverName)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to add MCP server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Get MCP server information
     */
    @GetMapping("/servers/{serverName}")
    public Mono<ResponseEntity<McpServer>> getMcpServer(@PathVariable("serverName") String serverName) {
        log.info("Getting MCP server: {}", serverName);
        return mcpServerService.getMcpServer(serverName)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to get MCP server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * Remove a MCP server
     */
    @DeleteMapping("/servers/{serverName}")
    public Mono<ResponseEntity<Map<String, Object>>> removeMcpServer(@PathVariable("serverName") String serverName) {
        log.info("Removing MCP server: {}", serverName);
        return mcpServerService.removeMcpServer(serverName)
                .map(success -> ResponseEntity.ok(Map.<String, Object>of("success", success)))
                .onErrorResume(throwable -> {
                    log.error("Failed to remove MCP server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.<String, Object>of("success", false, "error", throwable.getMessage())));
                });
    }

    /**
     * Use a tool from a MCP server
     */
    @PostMapping("/servers/{serverName}/tools/{toolName}")
    public Mono<ResponseEntity<Object>> useTool(
            @PathVariable("serverName") String serverName,
            @PathVariable("toolName") String toolName,
            @RequestBody Map<String, Object> params) {
        
        log.info("Using tool {} on server {} with params: {}", toolName, serverName, params);
        return mcpServerService.useTool(serverName, toolName, params)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to use tool {} on server {}: {}", toolName, serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.<String, Object>of("error", throwable.getMessage())));
                });
    }

    /**
     * Register a MCP server to Nacos
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody McpServerRegistrationRequest registrationRequest) {
        log.info("Received registration request: {}", registrationRequest);
        mcpServerService.registerServer(registrationRequest);
        return ResponseEntity.ok("Server registered successfully");
    }

    /**
     * Unregister a MCP server from Nacos
     */
    @DeleteMapping("/unregister/{serverName}")
    public Mono<ResponseEntity<Map<String, Object>>> unregisterMcpServer(@PathVariable("serverName") String serverName) {
        log.info("Unregistering MCP server: {}", serverName);
        return mcpServerService.unregisterMcpServer(serverName)
                .map(success -> ResponseEntity.ok(Map.<String, Object>of("success", success, "message", "Server unregistered successfully")))
                .onErrorResume(throwable -> {
                    log.error("Failed to unregister MCP server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.status(500)
                            .body(Map.<String, Object>of("success", false, "error", throwable.getMessage())));
                });
    }

    /**
     * List all available MCP servers
     */
    @GetMapping("/servers")
    public Mono<ResponseEntity<List<McpServer>>> listAllMcpServers() {
        log.info("Listing all available MCP servers");
        return mcpServerService.listAllMcpServers()
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to list MCP servers: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // ==================== RESOURCES API ====================

    /**
     * List all resources from a specific MCP server
     */
    @GetMapping("/servers/{serverName}/resources")
    public Mono<ResponseEntity<List<McpResource>>> listServerResources(@PathVariable("serverName") String serverName) {
        log.info("Listing resources for MCP server: {}", serverName);
        return mcpResourceService.listResources(serverName)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to list resources for server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * List all resources from all MCP servers
     */
    @GetMapping("/resources")
    public Mono<ResponseEntity<List<McpResource>>> listAllResources() {
        log.info("Listing all available resources");
        return mcpResourceService.listAllResources()
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to list all resources: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * Read a specific resource
     */
    @GetMapping("/servers/{serverName}/resources/{resourceUri}")
    public Mono<ResponseEntity<McpResource>> readResource(
            @PathVariable("serverName") String serverName,
            @PathVariable("resourceUri") String resourceUri) {
        log.info("Reading resource {} from server {}", resourceUri, serverName);
        return mcpResourceService.readResource(serverName, resourceUri)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to read resource {} from server {}: {}", resourceUri, serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * Search resources
     */
    @GetMapping("/resources/search")
    public Mono<ResponseEntity<List<McpResource>>> searchResources(
            @RequestParam("pattern") String pattern,
            @RequestParam(value = "serverName", required = false) String serverName) {
        log.info("Searching resources with pattern '{}' in server '{}'", pattern, serverName);
        return mcpResourceService.searchResources(pattern, serverName)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to search resources: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // ==================== PROMPTS API ====================

    /**
     * List all prompts from a specific MCP server
     */
    @GetMapping("/servers/{serverName}/prompts")
    public Mono<ResponseEntity<List<McpPrompt>>> listServerPrompts(@PathVariable("serverName") String serverName) {
        log.info("Listing prompts for MCP server: {}", serverName);
        return mcpPromptService.listPrompts(serverName)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to list prompts for server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * List all prompts from all MCP servers
     */
    @GetMapping("/prompts")
    public Mono<ResponseEntity<List<McpPrompt>>> listAllPrompts() {
        log.info("Listing all available prompts");
        return mcpPromptService.listAllPrompts()
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to list all prompts: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * Get a specific prompt
     */
    @GetMapping("/servers/{serverName}/prompts/{promptName}")
    public Mono<ResponseEntity<McpPrompt>> getPrompt(
            @PathVariable("serverName") String serverName,
            @PathVariable("promptName") String promptName,
            @RequestParam(required = false) Map<String, Object> arguments) {
        log.info("Getting prompt {} from server {} with arguments: {}", promptName, serverName, arguments);
        return mcpPromptService.getPrompt(serverName, promptName, arguments)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to get prompt {} from server {}: {}", promptName, serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * Execute a prompt (fill template with arguments)
     */
    @PostMapping("/servers/{serverName}/prompts/{promptName}/execute")
    public Mono<ResponseEntity<List<McpPrompt.PromptMessage>>> executePrompt(
            @PathVariable("serverName") String serverName,
            @PathVariable("promptName") String promptName,
            @RequestBody(required = false) Map<String, Object> arguments) {
        log.info("Executing prompt {} from server {} with arguments: {}", promptName, serverName, arguments);
        return mcpPromptService.executePrompt(serverName, promptName, arguments)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to execute prompt {} from server {}: {}", promptName, serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Search prompts
     */
    @GetMapping("/prompts/search")
    public Mono<ResponseEntity<List<McpPrompt>>> searchPrompts(
            @RequestParam("pattern") String pattern,
            @RequestParam(value = "serverName", required = false) String serverName) {
        log.info("Searching prompts with pattern '{}' in server '{}'", pattern, serverName);
        return mcpPromptService.searchPrompts(pattern, serverName)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Failed to search prompts: {}", throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @PostMapping("/servers/{serverName}/register-with-tools")
    public Mono<ResponseEntity<McpServer>> registerMcpServerWithTools(
            @PathVariable String serverName,
            @Valid @RequestBody McpServerRegistrationRequest request) {
        log.info("Received atomic registration request for server: {}", serverName);
        return mcpServerService.registerMcpServer(request)
                .map(server -> ResponseEntity.status(201).body(server))
                .onErrorResume(throwable -> {
                    log.error("Failed to register MCP server {}: {}", serverName, throwable.getMessage());
                    return Mono.just(ResponseEntity.status(500)
                            .body(McpServer.builder()
                                    .name(serverName)
                                    .status(McpServer.ServerStatus.ERROR)
                                    .build()));
                });
    }

    @PostMapping("/servers/{serverName}/heartbeat")
    public Mono<ResponseEntity<Void>> receiveHeartbeat(@PathVariable String serverName) {
        log.info("Received heartbeat from server: {}", serverName);
        return mcpServerService.recordHeartbeat("mcp-" + serverName)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Failed to process heartbeat for server {}: {}", serverName, e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }
}