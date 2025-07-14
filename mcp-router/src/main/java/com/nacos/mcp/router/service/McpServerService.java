package com.nacos.mcp.router.service;

import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.McpServerRegistrationRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP Server Service Interface
 */
public interface McpServerService {

    /**
     * Add a MCP server
     *
     * @param serverName MCP server name
     * @return MCP server information and tools
     */
    Mono<McpServer> addMcpServer(String serverName);

    /**
     * Use a tool from a MCP server
     *
     * @param serverName MCP server name
     * @param toolName tool name
     * @param params tool parameters
     * @return tool execution result
     */
    Mono<Object> useTool(String serverName, String toolName, Map<String, Object> params);

    /**
     * Use a tool from a MCP server
     *
     * @param toolName tool name
     * @param params tool parameters
     * @return tool execution result
     */
    Mono<Object> useTool(String toolName, Map<String, Object> params);

    /**
     * Get MCP server information
     *
     * @param serverName MCP server name
     * @return MCP server information
     */
    Mono<McpServer> getMcpServer(String serverName);

    /**
     * Remove a MCP server
     *
     * @param serverName MCP server name
     * @return operation result
     */
    Mono<Boolean> removeMcpServer(String serverName);

    /**
     * Register a MCP server to Nacos
     *
     * @param request MCP server registration request
     * @return registered MCP server information
     */
    Mono<McpServer> registerMcpServer(McpServerRegistrationRequest request);

    /**
     * Register a MCP server to Nacos with tools
     *
     * @param request MCP server registration request
     * @return registered MCP server information
     */
    Mono<McpServer> registerMcpServerWithTools(McpServerRegistrationRequest request);

    /**
     * Unregister a MCP server from Nacos
     *
     * @param serverName MCP server name
     * @return operation result
     */
    Mono<Boolean> unregisterMcpServer(String serverName);

    /**
     * List all available MCP servers from Nacos
     *
     * @return list of all available MCP servers
     */
    Mono<List<McpServer>> listAllMcpServers();

    /**
     * Ping a MCP server to check if it's online
     *
     * @param serverName MCP server name
     * @return true if server is online, false otherwise
     */
    Mono<Boolean> pingServer(String serverName);

    /**
     * Update server heartbeat
     *
     * @param serverName MCP server name
     * @param timestamp heartbeat timestamp
     * @param status server status
     * @return true if heartbeat updated successfully, false otherwise
     */
    Mono<Boolean> updateServerHeartbeat(String serverName, Long timestamp, String status);

    /**
     * Search MCP servers
     *
     * @param query search query
     * @return list of matching MCP servers
     */
    Mono<List<McpServer>> searchMcpServers(String query);

    /**
     * Record heartbeat for a MCP server
     *
     * @param serverName MCP server name
     * @return operation result
     */
    Mono<Void> recordHeartbeat(String serverName);

    void registerServer(McpServerRegistrationRequest registrationRequest);

    Flux<McpServer> getRegisteredServers();

    Mono<Void> deregisterMcpServer(String serverName);

    Mono<McpServer> getServerByName(String serverName);

    Mono<McpServer> getNextAvailableServer();
} 