package com.nacos.mcp.router.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search Response Model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /**
     * Search results
     */
    private List<McpServer> results;

    /**
     * Total number of results found
     */
    private Integer totalResults;

    /**
     * Instructions for completing the task
     */
    private String instructions;

    /**
     * Search metadata
     */
    private SearchMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMetadata {
        /**
         * Search query
         */
        private String query;

        /**
         * Search duration in milliseconds
         */
        private Long duration;

        /**
         * Providers used in search
         */
        private List<String> providers;
    }
} 