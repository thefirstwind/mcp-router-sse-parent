package com.nacos.mcp.router.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool Model - aligned with MCP specification
 * Based on: https://modelcontextprotocol.io/specification/basic/tools
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpTool {

    /**
     * Tool name (required)
     * Must be unique within the server
     */
    @JsonProperty("name")
    private String name;

    /**
     * Tool description (required)
     * Human-readable description of what the tool does
     */
    @JsonProperty("description")
    private String description;

    /**
     * Input schema (required)
     * JSON Schema describing the expected parameters
     * Must be an object with "type": "object"
     */
    @JsonProperty("inputSchema")
    private InputSchema inputSchema;

    /**
     * Input Schema definition following JSON Schema specification
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputSchema {
        
        @JsonProperty("type")
        private String type = "object";
        
        @JsonProperty("properties")
        private Map<String, Property> properties;
        
        @JsonProperty("required")
        private List<String> required;
        
        @JsonProperty("additionalProperties")
        private Boolean additionalProperties = false;
    }

    /**
     * Property definition for input schema
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Property {
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("enum")
        private List<String> enumValues;
        
        @JsonProperty("default")
        private Object defaultValue;
        
        @JsonProperty("format")
        private String format;
        
        @JsonProperty("minimum")
        private Number minimum;
        
        @JsonProperty("maximum")
        private Number maximum;
        
        @JsonProperty("items")
        private Property items; // For array types
    }
} 