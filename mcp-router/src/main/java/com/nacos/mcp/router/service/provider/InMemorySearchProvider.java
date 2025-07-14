package com.nacos.mcp.router.service.provider;

import com.nacos.mcp.router.model.McpServer;
import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.service.McpServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

//@Component
@RequiredArgsConstructor
public class InMemorySearchProvider implements SearchProvider {

    private final McpServerService mcpServerService;

    @Override
    public Mono<List<McpServer>> search(SearchRequest request) {
        return mcpServerService.getRegisteredServers()
                .filter(server -> matches(server, request))
                .collectList();
    }

    private boolean matches(McpServer server, SearchRequest request) {
        if (request.getTaskDescription() == null || request.getTaskDescription().isEmpty() || request.getTaskDescription().equals("all")) {
            return true;
        }

        String taskDescription = request.getTaskDescription().toLowerCase();
        boolean matches = server.getName().toLowerCase().contains(taskDescription) ||
               (server.getDescription() != null && server.getDescription().toLowerCase().contains(taskDescription));

        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            for (String keyword : request.getKeywords()) {
                if (server.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                    (server.getDescription() != null && server.getDescription().toLowerCase().contains(keyword.toLowerCase()))) {
                    matches = true;
                    break;
                }
            }
        }

        return matches;
    }

    @Override
    public String getProviderName() {
        return "InMemorySearchProvider";
    }
} 