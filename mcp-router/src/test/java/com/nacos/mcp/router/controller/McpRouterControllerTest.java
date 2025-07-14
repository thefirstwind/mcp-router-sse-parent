// package com.nacos.mcp.router.controller;

// import com.nacos.mcp.router.model.McpServer;
// import com.nacos.mcp.router.model.McpServerRegistrationRequest;
// import com.nacos.mcp.router.model.SearchRequest;
// import com.nacos.mcp.router.model.SearchResponse;
// import com.nacos.mcp.router.service.McpServerService;
// import com.nacos.mcp.router.service.McpResourceService;
// import com.nacos.mcp.router.service.McpPromptService;
// import com.nacos.mcp.router.service.SearchService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.reactive.server.WebTestClient;
// import reactor.core.publisher.Mono;

// import java.time.LocalDateTime;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import java.util.Map;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.when;

// @WebFluxTest(McpRouterController.class)
// class McpRouterControllerTest {

//     @Autowired
//     private WebTestClient webTestClient;

//     @MockBean
//     private McpServerService mcpServerService;

//     @MockBean
//     private SearchService searchService;

//     @MockBean
//     private McpResourceService mcpResourceService;

//     @MockBean
//     private McpPromptService mcpPromptService;

//     private McpServerRegistrationRequest validRequest;
//     private McpServer sampleServer;

//     @BeforeEach
//     void setUp() {
//         validRequest = McpServerRegistrationRequest.builder()
//                 .serverName("mcp-test-server")
//                 .ip("localhost")
//                 .port(9001)
//                 .transportType("stdio")
//                 .description("A test MCP server")
//                 .version("1.0.0")
//                 .healthPath("/health")
//                 .enabled(true)
//                 .weight(1.0)
//                 .cluster("DEFAULT")
//                 .build();

//         sampleServer = McpServer.builder()
//                 .name("mcp-test-server")
//                 .description("A test MCP server")
//                 .transportType("stdio")
//                 .status(McpServer.ServerStatus.REGISTERED)
//                 .registrationTime(LocalDateTime.now())
//                 .build();
//     }

//     @Test
//     void registerMcpServer_ValidRequest_ReturnsSuccess() {
//         when(mcpServerService.registerMcpServer(any(McpServerRegistrationRequest.class)))
//                 .thenReturn(Mono.just(sampleServer));

//         webTestClient.post()
//                 .uri("/api/mcp/servers/{serverName}/register", validRequest.getServerName())
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(validRequest)
//                 .exchange()
//                 .expectStatus().is2xxSuccessful()
//                 .expectBody()
//                 .jsonPath("$.name").isEqualTo("mcp-test-server")
//                 .jsonPath("$.status").isEqualTo("REGISTERED");
//     }

//     @Test
//     void registerMcpServer_InvalidRequest_ReturnsBadRequest() {
//         McpServerRegistrationRequest invalidRequest = McpServerRegistrationRequest.builder()
//                 .serverName("") // Invalid empty serverName
//                 .build();

//         webTestClient.post()
//                 .uri("/api/mcp/servers/mcp-test-server/register")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(invalidRequest)
//                 .exchange()
//                 .expectStatus().isBadRequest();
//     }

//     @Test
//     void registerMcpServer_ServiceError_ReturnsInternalServerError() {
//         when(mcpServerService.registerMcpServer(any(McpServerRegistrationRequest.class)))
//                 .thenReturn(Mono.error(new RuntimeException("Service error")));

//         webTestClient.post()
//                 .uri("/api/mcp/servers/{serverName}/register", validRequest.getServerName())
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(validRequest)
//                 .exchange()
//                 .expectStatus().isEqualTo(500);
//     }

//     @Test
//     void listAllMcpServers_Success_ReturnsServerList() {
//         List<McpServer> servers = Arrays.asList(sampleServer);
//         when(mcpServerService.listAllMcpServers()).thenReturn(Mono.just(servers));

//         webTestClient.get()
//                 .uri("/api/mcp/servers")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectBody()
//                 .jsonPath("$").isArray()
//                 .jsonPath("$[0].name").isEqualTo("mcp-test-server");
//     }

//     @Test
//     void listAllMcpServers_EmptyList_ReturnsEmptyArray() {
//         when(mcpServerService.listAllMcpServers()).thenReturn(Mono.just(Collections.emptyList()));

//         webTestClient.get()
//                 .uri("/api/mcp/servers")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectBody()
//                 .jsonPath("$").isArray()
//                 .jsonPath("$").isEmpty();
//     }

//     @Test
//     void listAllMcpServers_ServiceError_ReturnsInternalServerError() {
//         when(mcpServerService.listAllMcpServers())
//                 .thenReturn(Mono.error(new RuntimeException("Service error")));

//         webTestClient.get()
//                 .uri("/api/mcp/servers")
//                 .exchange()
//                 .expectStatus().isEqualTo(500)
//                 .expectBody()
//                 .jsonPath("$.error").isEqualTo("Service error");
//     }

//     @Test
//     void unregisterMcpServer_ValidServerId_ReturnsSuccess() {
//         when(mcpServerService.unregisterMcpServer(anyString())).thenReturn(Mono.just(true));

//         webTestClient.delete()
//                 .uri("/api/mcp/unregister/test-server")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectBody()
//                 .jsonPath("$.success").isEqualTo(true)
//                 .jsonPath("$.message").isEqualTo("Server unregistered successfully");
//     }

//     @Test
//     void unregisterMcpServer_NonExistentServer_ReturnsSuccess() {
//         when(mcpServerService.unregisterMcpServer(anyString())).thenReturn(Mono.just(false));

//         webTestClient.delete()
//                 .uri("/api/mcp/unregister/non-existent")
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectBody()
//                 .jsonPath("$.success").isEqualTo(false)
//                 .jsonPath("$.message").isEqualTo("Server unregistered successfully");
//     }

//     @Test
//     void unregisterMcpServer_ServiceError_ReturnsInternalServerError() {
//         when(mcpServerService.unregisterMcpServer(anyString()))
//                 .thenReturn(Mono.error(new RuntimeException("Service error")));

//         webTestClient.delete()
//                 .uri("/api/mcp/unregister/test-server")
//                 .exchange()
//                 .expectStatus().isEqualTo(500)
//                 .expectBody()
//                 .jsonPath("$.success").isEqualTo(false)
//                 .jsonPath("$.error").isEqualTo("Service error");
//     }

//     @Test
//     void search_ValidRequest_ReturnsSearchResults() {
//         SearchRequest searchRequest = SearchRequest.builder()
//                 .taskDescription("test task")
//                 .limit(10)
//                 .build();

//         SearchResponse searchResponse = SearchResponse.builder()
//                 .results(Collections.emptyList())
//                 .totalResults(0)
//                 .instructions("No results found")
//                 .metadata(SearchResponse.SearchMetadata.builder()
//                         .query("test task")
//                         .duration(100L)
//                         .providers(Arrays.asList("nacos"))
//                         .build())
//                 .build();

//         when(searchService.searchMcpServers(any(SearchRequest.class))).thenReturn(Mono.just(searchResponse));

//         webTestClient.post()
//                 .uri("/api/mcp/search")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(searchRequest)
//                 .exchange()
//                 .expectStatus().isOk()
//                 .expectBody()
//                 .jsonPath("$.totalResults").isEqualTo(0)
//                 .jsonPath("$.metadata.query").isEqualTo("test task");
//     }

//     @Test
//     void search_EmptyQuery_ReturnsBadRequest() {
//         SearchRequest searchRequest = SearchRequest.builder()
//                 .taskDescription("") // Empty query
//                 .build();

//         webTestClient.post()
//                 .uri("/api/mcp/search")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(searchRequest)
//                 .exchange()
//                 .expectStatus().isBadRequest();
//     }

//     @Test
//     void search_ServiceError_ReturnsBadRequest() {
//         SearchRequest searchRequest = SearchRequest.builder()
//                 .taskDescription("test task")
//                 .build();

//         when(searchService.searchMcpServers(any(SearchRequest.class)))
//                 .thenReturn(Mono.error(new RuntimeException("Search service error")));

//         webTestClient.post()
//                 .uri("/api/mcp/search")
//                 .contentType(MediaType.APPLICATION_JSON)
//                 .bodyValue(searchRequest)
//                 .exchange()
//                 .expectStatus().isBadRequest();
//     }

//     @Test
//     void receiveHeartbeat_ValidServerName_ReturnsOk() {
//         when(mcpServerService.recordHeartbeat(anyString())).thenReturn(Mono.empty());

//         webTestClient.post()
//                 .uri("/api/mcp/servers/test-server/heartbeat")
//                 .exchange()
//                 .expectStatus().isOk();
//     }
// } 