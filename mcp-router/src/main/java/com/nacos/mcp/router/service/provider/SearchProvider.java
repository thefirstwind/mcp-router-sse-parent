package com.nacos.mcp.router.service.provider;

import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.model.McpServer;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Search Provider Interface
 */
public interface SearchProvider {

    /**
     * Search MCP servers
     *
     * @param request search request
     * @return list of MCP servers
     */
    Mono<List<McpServer>> search(SearchRequest request);

    /**
     * Get provider name
     *
     * @return provider name
     */
    String getProviderName();
} 