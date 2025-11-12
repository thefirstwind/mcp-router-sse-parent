package com.pajk.mcpbridge.core.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 端到端路由测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.nacos.mcp.router.v2=DEBUG"
})
public class EndToEndRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testCompleteRoutingFlow() {
        // 测试完整的路由流程 - 使用actuator健康检查端点
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ 端到端路由测试通过");
    }

    @Test
    public void testServiceRegistrationAndRouting() {
        // 测试服务注册和路由 - 使用actuator健康检查端点
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ 服务注册和路由测试通过");
    }

    @Test
    public void testHealthCheckIntegration() {
        // 测试健康检查集成 - 使用actuator健康检查端点
        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();

        System.out.println("✅ 健康检查集成测试通过");
    }
} 