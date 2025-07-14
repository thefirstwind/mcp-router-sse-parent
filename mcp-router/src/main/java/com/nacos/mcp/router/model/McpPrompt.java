package com.nacos.mcp.router.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Prompt Model - aligned with MCP specification
 * Prompts are reusable templates that can be parameterized
 * Based on: https://modelcontextprotocol.io/specification/basic/prompts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpPrompt {

    /**
     * Unique prompt name (required)
     */
    @JsonProperty("name")
    private String name;

    /**
     * Human-readable description (optional)
     */
    @JsonProperty("description")
    private String description;

    /**
     * Arguments schema (optional)
     * Defines what parameters this prompt accepts
     */
    @JsonProperty("arguments")
    private List<PromptArgument> arguments;

    /**
     * Prompt messages (for get_prompt responses)
     */
    @JsonProperty("messages")
    private List<PromptMessage> messages;

    /**
     * Prompt argument definition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptArgument {
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("required")
        private Boolean required = false;
    }

    /**
     * Prompt message definition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptMessage {
        
        @JsonProperty("role")
        private String role; // "user", "assistant", "system"
        
        @JsonProperty("content")
        private PromptContent content;
    }

    /**
     * Prompt content definition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptContent {
        
        @JsonProperty("type")
        private String type; // "text", "image"
        
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("annotations")
        private Map<String, Object> annotations;
    }
} 