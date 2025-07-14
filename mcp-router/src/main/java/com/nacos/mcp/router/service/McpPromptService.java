package com.nacos.mcp.router.service;

import com.nacos.mcp.router.model.McpPrompt;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP Prompt Service Interface
 * Handles prompt templates according to MCP specification
 */
public interface McpPromptService {

    /**
     * List all available prompts from a specific MCP server
     *
     * @param serverName MCP server name
     * @return list of available prompts
     */
    Mono<List<McpPrompt>> listPrompts(String serverName);

    /**
     * List all available prompts from all connected MCP servers
     *
     * @return list of all available prompts
     */
    Mono<List<McpPrompt>> listAllPrompts();

    /**
     * Get a specific prompt with its content
     *
     * @param serverName MCP server name
     * @param promptName prompt name
     * @param arguments optional prompt arguments
     * @return prompt with resolved content
     */
    Mono<McpPrompt> getPrompt(String serverName, String promptName, Map<String, Object> arguments);

    /**
     * Execute a prompt (fill template with arguments)
     *
     * @param serverName MCP server name
     * @param promptName prompt name
     * @param arguments prompt arguments
     * @return rendered prompt messages
     */
    Mono<List<McpPrompt.PromptMessage>> executePrompt(String serverName, String promptName, Map<String, Object> arguments);

    /**
     * Search prompts by pattern
     *
     * @param pattern search pattern
     * @param serverName optional server name filter
     * @return matching prompts
     */
    Mono<List<McpPrompt>> searchPrompts(String pattern, String serverName);

    /**
     * Validate prompt arguments
     *
     * @param promptName prompt name
     * @param arguments provided arguments
     * @return validation result
     */
    Mono<Boolean> validatePromptArguments(String promptName, Map<String, Object> arguments);
}