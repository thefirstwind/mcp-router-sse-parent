package com.nacos.mcp.router.service;

import com.nacos.mcp.router.model.McpResource;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * MCP Resource Service Interface
 * Handles resource access according to MCP specification
 */
public interface McpResourceService {

    /**
     * List all available resources from a specific MCP server
     *
     * @param serverName MCP server name
     * @return list of available resources
     */
    Mono<List<McpResource>> listResources(String serverName);

    /**
     * List all available resources from all connected MCP servers
     *
     * @return list of all available resources
     */
    Mono<List<McpResource>> listAllResources();

    /**
     * Read the content of a specific resource
     *
     * @param serverName MCP server name
     * @param resourceUri resource URI
     * @return resource with content
     */
    Mono<McpResource> readResource(String serverName, String resourceUri);

    /**
     * Subscribe to resource changes (for real-time updates)
     *
     * @param serverName MCP server name
     * @param resourceUri resource URI
     * @return resource stream
     */
    Mono<McpResource> subscribeToResource(String serverName, String resourceUri);

    /**
     * Search resources by pattern
     *
     * @param pattern search pattern
     * @param serverName optional server name filter
     * @return matching resources
     */
    Mono<List<McpResource>> searchResources(String pattern, String serverName);
} 