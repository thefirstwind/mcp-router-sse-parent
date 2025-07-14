package com.nacos.mcp.router.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Map;

/**
 * MCP Server Registration Request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerRegistrationRequest {

    /**
     * Server name (used as Nacos service name)
     */
    @NotBlank(message = "Server name is required")
    @Pattern(regexp = "^mcp-[a-zA-Z0-9-_]+$", message = "Server name must start with 'mcp-' and contain only alphanumeric characters, hyphens, and underscores")
    private String serverName;

    /**
     * Server IP address
     */
    @NotBlank(message = "IP address is required")
    private String ip;

    /**
     * Server port
     */
    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be >= 1")
    @Max(value = 65535, message = "Port must be <= 65535")
    private Integer port;

    /**
     * Transport type
     */
    @NotBlank(message = "Transport type is required")
    @Pattern(regexp = "^(stdio|sse|streamable_http)$", message = "Transport type must be one of: stdio, sse, streamable_http")
    private String transportType;

    /**
     * Server description
     */
    private String description;

    /**
     * Server version
     */
    @Builder.Default
    private String version = "1.0.0";

    /**
     * Install command (for stdio servers)
     */
    private String installCommand;

    /**
     * Custom metadata
     */
    private Map<String, String> metadata;

    /**
     * Health check path
     */
    @Builder.Default
    private String healthPath = "/health";

    /**
     * Whether the instance is enabled
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Instance weight for load balancing
     */
    @DecimalMin(value = "0.01", message = "Weight must be >= 0.01")
    @DecimalMax(value = "100.0", message = "Weight must be <= 100.0")
    @Builder.Default
    private Double weight = 1.0;

    /**
     * Cluster name
     */
    @Builder.Default
    private String cluster = "DEFAULT";

    // Endpoint information
    private String baseUrl;
    private String mcpEndpoint;
    private String healthEndpoint;
    private String endpoint;
    
    // Server capabilities
    private Map<String, Object> capabilities;

    // A list of tools that the server provides.
    private List<McpTool> tools;
} 