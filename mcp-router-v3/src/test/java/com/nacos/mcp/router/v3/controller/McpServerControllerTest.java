package com.nacos.mcp.router.v3.controller;

import com.nacos.mcp.router.v3.model.McpServerInfo;
import com.nacos.mcp.router.v3.model.McpServerConfig;
import com.nacos.mcp.router.v3.registry.McpServerRegistry;
import com.nacos.mcp.router.v3.service.McpConfigService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * McpServerController 真实测试
 * 测试路径: /mcp/servers/*
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.nacos.mcp.router.v2=DEBUG"
})
public class McpServerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private McpServerRegistry mcpServerRegistry;

    @MockBean
    private McpConfigService mcpConfigService;

    @Test
    public void testGetHealthyServers() {
        // Mock数据
        McpServerInfo server1 = createTestServer("test-server-1", "192.168.1.100", 8001);
        McpServerInfo server2 = createTestServer("test-server-2", "192.168.1.101", 8002);
        
        when(mcpServerRegistry.getAllHealthyServers("mcp-server-v2", "mcp-server"))
                .thenReturn(Flux.just(server1, server2));

        // 测试 GET /mcp/servers/healthy
        webTestClient.get()
                .uri("/mcp/servers/healthy")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(McpServerInfo.class)
                .hasSize(2);

        // 测试带参数的请求
        webTestClient.get()
                .uri("/mcp/servers/healthy?serviceName=custom-service&serviceGroup=custom-group")
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ GET /mcp/servers/healthy 测试通过");
    }

    @Test
    public void testGetAllInstances() {
        // Mock数据
        McpServerInfo server1 = createTestServer("instance-1", "192.168.1.100", 8001);
        
        when(mcpServerRegistry.getAllInstances(anyString(), anyString()))
                .thenReturn(Flux.just(server1));

        // 测试 GET /mcp/servers/instances
        webTestClient.get()
                .uri("/mcp/servers/instances")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(McpServerInfo.class)
                .hasSize(1);

        System.out.println("✅ GET /mcp/servers/instances 测试通过");
    }

    @Test
    public void testSelectHealthyServer() {
        // Mock数据
        McpServerInfo selectedServer = createTestServer("selected-server", "192.168.1.200", 8003);
        
        when(mcpServerRegistry.selectHealthyServer(anyString(), anyString()))
                .thenReturn(Mono.just(selectedServer));

        // 测试 GET /mcp/servers/select
        webTestClient.get()
                .uri("/mcp/servers/select")
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpServerInfo.class);

        System.out.println("✅ GET /mcp/servers/select 测试通过");
    }

    @Test
    public void testRegisterServer() {
        // Mock数据
        McpServerInfo newServer = createTestServer("new-server", "192.168.1.300", 8004);
        
        when(mcpServerRegistry.registerServer(any(McpServerInfo.class)))
                .thenReturn(Mono.empty());

        // 测试 POST /mcp/servers/register
        webTestClient.post()
                .uri("/mcp/servers/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(newServer)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Server registered successfully");

        System.out.println("✅ POST /mcp/servers/register 测试通过");
    }

    @Test
    public void testDeregisterServer() {
        when(mcpServerRegistry.deregisterServer(anyString(), anyString()))
                .thenReturn(Mono.empty());

        // 测试 DELETE /mcp/servers/deregister
        webTestClient.delete()
                .uri("/mcp/servers/deregister?serviceName=test-service&serviceGroup=test-group")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Server deregistered successfully");

        System.out.println("✅ DELETE /mcp/servers/deregister 测试通过");
    }

    @Test
    public void testGetServerConfig() {
        // Mock数据
        McpServerConfig config = new McpServerConfig();
        config.setName("test-server");
        config.setVersion("1.0.0");
        
        when(mcpConfigService.getServerConfig(anyString(), anyString()))
                .thenReturn(Mono.just(config));

        // 测试 GET /mcp/servers/config/{serverName}
        webTestClient.get()
                .uri("/mcp/servers/config/test-server?version=1.0.0")
                .exchange()
                .expectStatus().isOk()
                .expectBody(McpServerConfig.class);

        System.out.println("✅ GET /mcp/servers/config/{serverName} 测试通过");
    }

    @Test
    public void testPublishServerConfig() {
        McpServerInfo serverInfo = createTestServer("config-server", "192.168.1.400", 8005);
        
        when(mcpConfigService.publishServerConfig(any()))
                .thenReturn(Mono.just(true));

        // 测试 POST /mcp/servers/config/publish
        webTestClient.post()
                .uri("/mcp/servers/config/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(serverInfo)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Server config published successfully");

        System.out.println("✅ POST /mcp/servers/config/publish 测试通过");
    }

    @Test
    public void testPublishToolsConfig() {
        McpServerInfo serverInfo = createTestServer("tools-server", "192.168.1.500", 8006);
        
        when(mcpConfigService.publishToolsConfig(any()))
                .thenReturn(Mono.just(true));

        // 测试 POST /mcp/servers/config/tools/publish
        webTestClient.post()
                .uri("/mcp/servers/config/tools/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(serverInfo)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Tools config published successfully");

        System.out.println("✅ POST /mcp/servers/config/tools/publish 测试通过");
    }

    @Test
    public void testPublishVersionConfig() {
        McpServerInfo serverInfo = createTestServer("version-server", "192.168.1.600", 8007);
        
        when(mcpConfigService.publishVersionConfig(any()))
                .thenReturn(Mono.just(true));

        // 测试 POST /mcp/servers/config/version/publish
        webTestClient.post()
                .uri("/mcp/servers/config/version/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(serverInfo)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Version config published successfully");

        System.out.println("✅ POST /mcp/servers/config/version/publish 测试通过");
    }

    @Test
    public void testConfigPublishFailure() {
        McpServerInfo serverInfo = createTestServer("fail-server", "192.168.1.700", 8008);
        
        when(mcpConfigService.publishServerConfig(any()))
                .thenReturn(Mono.just(false));

        // 测试配置发布失败场景
        webTestClient.post()
                .uri("/mcp/servers/config/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(serverInfo)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Failed to publish server config");

        System.out.println("✅ 配置发布失败场景测试通过");
    }

    @Test
    public void testEmptyServiceList() {
        when(mcpServerRegistry.getAllHealthyServers(anyString(), anyString()))
                .thenReturn(Flux.empty());

        // 测试空服务列表
        webTestClient.get()
                .uri("/mcp/servers/healthy")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(McpServerInfo.class)
                .hasSize(0);

        System.out.println("✅ 空服务列表测试通过");
    }

    // 辅助方法：创建测试服务器
    private McpServerInfo createTestServer(String name, String ip, int port) {
        return McpServerInfo.builder()
                .name(name)
                .ip(ip)
                .port(port)
                .version("1.0.0")
                .serviceGroup("mcp-server")
                .weight(1.0)
                .status("UP")
                .sseEndpoint("/sse")
                .build();
    }
} 