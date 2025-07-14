package com.nacos.mcp.router.service.provider;

import com.nacos.mcp.router.model.SearchRequest;
import com.nacos.mcp.router.model.McpServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "nacos.discovery.server-addr=127.0.0.1:8848",
    "nacos.discovery.enabled=true",
    "nacos.discovery.username=nacos",
    "nacos.discovery.password=nacos"
})
@Slf4j
public class NacosSearchProviderSimpleTest {

    @Autowired
    private NacosSearchProvider nacosSearchProvider;

    @Test
    public void testSearchBasic() {
        log.info("Testing NacosSearchProvider.search() with basic query...");
        
        SearchRequest request = new SearchRequest();
        request.setTaskDescription("Search for MCP servers");
        request.setLimit(10);
        
        Mono<List<McpServer>> searchMono = nacosSearchProvider.search(request);
        List<McpServer> results = searchMono.block(Duration.ofSeconds(10));
        
        assertThat(results).isNotNull();
        log.info("Search completed. Found {} results", results.size());
        
        results.forEach(result -> {
            log.info("Search result: {} - {}", result.getName(), result.getDescription());
        });
    }

    @Test
    public void testSearchWithEmptyQuery() {
        log.info("Testing NacosSearchProvider.search() with empty query...");
        
        SearchRequest request = new SearchRequest();
        request.setTaskDescription("");
        request.setLimit(5);
        
        Mono<List<McpServer>> searchMono = nacosSearchProvider.search(request);
        List<McpServer> results = searchMono.block(Duration.ofSeconds(10));
        
        assertThat(results).isNotNull();
        
        log.info("Empty query search completed. Found {} results", results.size());
    }

    @Test
    public void testSearchWithNullRequest() {
        log.info("Testing NacosSearchProvider.search() with null request...");
        
        Mono<List<McpServer>> searchMono = nacosSearchProvider.search(null);
        List<McpServer> results = searchMono.block(Duration.ofSeconds(10));
        
        assertThat(results).isNotNull();
        assertThat(results.size()).isEqualTo(0);
        
        log.info("Null request search completed gracefully");
    }

    @Test
    public void testSearchWithLimitedSize() {
        log.info("Testing NacosSearchProvider.search() with size limit...");
        
        SearchRequest request = new SearchRequest();
        request.setTaskDescription("Find MCP servers");
        request.setLimit(2);
        
        Mono<List<McpServer>> searchMono = nacosSearchProvider.search(request);
        List<McpServer> results = searchMono.block(Duration.ofSeconds(10));
        
        assertThat(results).isNotNull();
        assertThat(results.size()).isLessThanOrEqualTo(2);
        
        log.info("Limited size search completed. Found {} results (max 2)", results.size());
    }

    @Test
    public void testSearchWithSpecificTerm() {
        log.info("Testing NacosSearchProvider.search() with specific term...");
        
        SearchRequest request = new SearchRequest();
        request.setTaskDescription("Search for file-related MCP servers");
        request.setLimit(10);
        
        Mono<List<McpServer>> searchMono = nacosSearchProvider.search(request);
        List<McpServer> results = searchMono.block(Duration.ofSeconds(10));
        
        assertThat(results).isNotNull();
        
        log.info("Specific term search completed. Found {} results for file-related servers", results.size());
        
        if (!results.isEmpty()) {
            results.forEach(result -> {
                log.info("Found result: {} - {}", result.getName(), result.getDescription());
            });
        }
    }
} 