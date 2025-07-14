package com.nacos.mcp.router.service.impl;

import com.nacos.mcp.router.config.McpRouterProperties;
import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.model.SearchResponse;
import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.service.SearchService;
import com.nacos.mcp.router.service.provider.SearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.chat.client.ChatClient; // Temporarily disabled
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.nacos.mcp.router.service.provider.InMemorySearchProvider;
import com.nacos.mcp.router.service.provider.NacosSearchProvider;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * Search Service Implementation
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final List<SearchProvider> searchProviders;
    private final McpRouterProperties mcpRouterProperties;
    // private final ChatClient chatClient; // Temporarily disabled

    public SearchServiceImpl(List<SearchProvider> searchProviders,
                           McpRouterProperties mcpRouterProperties) {
        this.searchProviders = searchProviders;
        this.mcpRouterProperties = mcpRouterProperties;
        // this.chatClient = chatClient; // Temporarily disabled
    }

    @Override
    public Mono<SearchResponse> searchMcpServers(SearchRequest request) {
        log.info("Searching MCP servers with task: {}, keywords: {}", 
                request.getTaskDescription(), request.getKeywords());

        Instant startTime = Instant.now();

        // Apply default values if not provided
        if (request.getMinSimilarity() == null) {
            request.setMinSimilarity(mcpRouterProperties.getSearch().getMinSimilarity());
        }
        if (request.getLimit() == null) {
            request.setLimit(mcpRouterProperties.getSearch().getResultLimit());
        }

        // Search using all providers, ensuring blocking operations are on a dedicated scheduler
        return Flux.fromIterable(searchProviders)
                .flatMap(provider -> provider.search(request)
                        // This is the key fix: If a provider (like Nacos) might block,
                        // it must be subscribed on a scheduler that can handle it.
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(throwable -> {
                            log.warn("Search provider {} failed: {}",
                                    provider.getClass().getSimpleName(), throwable.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .map(results -> {
                    // Flatten and deduplicate results
                    List<McpServer> allResults = results.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toMap(
                                    McpServer::getName,
                                    server -> server,
                                    (existing, replacement) ->
                                            (existing.getRelevanceScore() != null && replacement.getRelevanceScore() != null && existing.getRelevanceScore() >= replacement.getRelevanceScore())
                                                    ? existing : replacement))
                            .values()
                            .stream()
                            .filter(server -> server.getRelevanceScore() != null && server.getRelevanceScore() >= request.getMinSimilarity())
                            .sorted((a, b) -> {
                                Double scoreA = a.getRelevanceScore() != null ? a.getRelevanceScore() : 0.0;
                                Double scoreB = b.getRelevanceScore() != null ? b.getRelevanceScore() : 0.0;
                                return Double.compare(scoreB, scoreA);
                            })
                            .limit(request.getLimit())
                            .collect(Collectors.toList());

                    // Generate instructions using AI
                    String instructions = generateInstructions(request, allResults);

                    Duration searchDuration = Duration.between(startTime, Instant.now());

                    return SearchResponse.builder()
                            .results(allResults)
                            .totalResults(allResults.size())
                            .instructions(instructions)
                            .metadata(SearchResponse.SearchMetadata.builder()
                                    .query(request.getTaskDescription())
                                    .duration(searchDuration.toMillis())
                                    .providers(searchProviders.stream()
                                            .map(provider -> provider.getClass().getSimpleName())
                                            .collect(Collectors.toList()))
                                    .build())
                            .build();
                });
    }

    @Override
    public Mono<SearchResponse> searchMcpServers(String taskDescription, String... keywords) {
        SearchRequest request = SearchRequest.builder()
                .taskDescription(taskDescription)
                .keywords(Arrays.asList(keywords))
                .build();
        return searchMcpServers(request);
    }

    private String generateInstructions(SearchRequest request, List<McpServer> results) {
        if (results.isEmpty()) {
            return "No MCP servers found for the given task. Please try with different keywords or check if the required servers are registered.";
        }

        // ChatClient temporarily disabled, return basic instructions
        if (true) { // chatClient == null
            return String.format("Found %d MCP servers that can help with your task:\n%s\n\n" +
                    "Consider using the servers in order of relevance score.", 
                    results.size(),
                    results.stream()
                            .map(server -> String.format("- %s: %s (Score: %.2f)", 
                                    server.getName(), server.getDescription(), server.getRelevanceScore()))
                            .collect(Collectors.joining("\n")));
        }

        try {
            String prompt = String.format(
                    "Based on the task description '%s' and the following MCP servers found:\n%s\n\n" +
                    "Please provide clear, step-by-step instructions on how to complete the task using these servers. " +
                    "Include which servers to use and in what order.",
                    request.getTaskDescription(),
                    results.stream()
                            .map(server -> String.format("%s (Score: %.2f)",
                                    server.getDescription(),
                                    server.getRelevanceScore()))
                            .collect(Collectors.joining(", ")));

            // ChatClient temporarily disabled
            return "AI instruction generation temporarily disabled. Please use the servers listed above.";
        } catch (Exception e) {
            log.warn("Failed to generate AI instructions: {}", e.getMessage());
            return String.format("Found %d MCP servers that can help with your task. " +
                    "Consider using the servers in order of relevance score.", results.size());
        }
    }
} 