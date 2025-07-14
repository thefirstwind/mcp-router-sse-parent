package com.nacos.mcp.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * MCP Router Configuration Properties
 */
@Data
@Validated
@ConfigurationProperties(prefix = "mcp.router")
public class McpRouterProperties {

    /**
     * Working mode: router or proxy
     */
    private String mode = "router";

    /**
     * Transport type: stdio, sse, streamable_http
     */
    private String transportType = "stdio";

    /**
     * Proxied MCP server name (used in proxy mode)
     */
    private String proxiedMcpName;

    /**
     * Compass API configuration
     */
    private Compass compass = new Compass();

    /**
     * Search configuration
     */
    private Search search = new Search();

    @Data
    public static class Compass {
        /**
         * Compass API base URL
         */
        private String apiBase = "https://registry.mcphub.io";
    }

    @Data
    public static class Search {
        /**
         * Minimum similarity score for search results
         */
        @DecimalMin(value = "0.0", message = "Minimum similarity must be >= 0.0")
        @DecimalMax(value = "1.0", message = "Minimum similarity must be <= 1.0")
        private Double minSimilarity = 0.5;

        /**
         * Maximum number of search results to return
         */
        @Min(value = 1, message = "Result limit must be >= 1")
        private Integer resultLimit = 10;
    }
} 