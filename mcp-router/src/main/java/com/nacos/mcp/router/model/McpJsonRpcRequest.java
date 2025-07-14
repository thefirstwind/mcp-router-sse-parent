package com.nacos.mcp.router.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Request Model for MCP Protocol
 * Based on: https://www.jsonrpc.org/specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpJsonRpcRequest {

    /**
     * JSON-RPC version (must be "2.0")
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    /**
     * Request method name
     */
    @JsonProperty("method")
    private String method;

    /**
     * Request parameters (optional)
     */
    @JsonProperty("params")
    private Object params;

    /**
     * Request ID (optional for notifications)
     */
    @JsonProperty("id")
    private Object id;

    /**
     * Check if this is a notification (no response expected)
     */
    public boolean isNotification() {
        return id == null;
    }
} 