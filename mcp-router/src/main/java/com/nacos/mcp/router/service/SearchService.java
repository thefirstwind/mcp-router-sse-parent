package com.nacos.mcp.router.service;

import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.model.SearchResponse;
import reactor.core.publisher.Mono;

/**
 * Search Service Interface
 */
public interface SearchService {

    /**
     * Search MCP servers by task description and keywords
     *
     * @param request search request
     * @return search response
     */
    Mono<SearchResponse> searchMcpServers(SearchRequest request);

    /**
     * Search MCP servers by task description and keywords
     *
     * @param taskDescription task description
     * @param keywords keywords
     * @return search response
     */
    Mono<SearchResponse> searchMcpServers(String taskDescription, String... keywords);
} 