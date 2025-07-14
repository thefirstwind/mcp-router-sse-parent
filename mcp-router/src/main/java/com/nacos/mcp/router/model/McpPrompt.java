package com.nacos.mcp.router.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MCP Prompt Model
 */
@Data
@Builder
public class McpPrompt {
    private String name;
    private String description;
    private String template;
    private List<PromptMessage> messages;

    @Data
    @Builder
    public static class PromptMessage {
        private String role;
        private String content;
    }
} 