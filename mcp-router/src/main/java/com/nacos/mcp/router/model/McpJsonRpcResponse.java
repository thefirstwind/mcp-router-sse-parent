package com.nacos.mcp.router.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Response Model for MCP Protocol
 * Based on: https://www.jsonrpc.org/specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpJsonRpcResponse {

    /**
     * JSON-RPC version (always "2.0")
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    /**
     * Response result (present on success)
     */
    @JsonProperty("result")
    private Object result;

    /**
     * Error information (present on error)
     */
    @JsonProperty("error")
    private JsonRpcError error;

    /**
     * Request ID (matches the request)
     */
    @JsonProperty("id")
    private Object id;

    /**
     * JSON-RPC Error definition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonRpcError {
        
        @JsonProperty("code")
        private Integer code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("data")
        private Object data;
    }

    /**
     * Create success response
     */
    public static McpJsonRpcResponse success(Object result, Object id) {
        return McpJsonRpcResponse.builder()
                .result(result)
                .id(id)
                .build();
    }

    /**
     * Create error response
     */
    public static McpJsonRpcResponse error(Integer code, String message, Object id) {
        return McpJsonRpcResponse.builder()
                .error(JsonRpcError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .id(id)
                .build();
    }

    /**
     * Standard JSON-RPC error codes
     */
    public static class ErrorCodes {
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
        
        // MCP specific error codes
        public static final int MCP_TOOL_ERROR = -32001;
        public static final int MCP_RESOURCE_ERROR = -32002;
        public static final int MCP_PROMPT_ERROR = -32003;
        public static final int MCP_SERVER_ERROR = -32004;
        
        // Legacy compatibility
        public static final int TOOL_NOT_FOUND = -32001;
        public static final int RESOURCE_NOT_FOUND = -32002; 
        public static final int PROMPT_NOT_FOUND = -32003;
    }
}