package com.pajk.mcpbridge.core.controller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * HealthController 真实测试
 * 测试路径: /health
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.ai.alibaba.mcp.nacos.registry.enabled=false",
        "logging.level.com.nacos.mcp.router.v2=DEBUG"
})
public class HealthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void testHealthEndpoint() {
        // 测试 GET /health
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("mcp-router-v3")
                .jsonPath("$.version").isEqualTo("1.0.0")
                .jsonPath("$.timestamp").exists();

        System.out.println("✅ GET /health 测试通过");
    }

    @Test
    public void testReadyEndpoint() {
        // 测试 GET /health/ready
        webTestClient.get()
                .uri("/health/ready")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("READY")
                .jsonPath("$.timestamp").exists();

        System.out.println("✅ GET /health/ready 测试通过");
    }

    @Test
    public void testHealthPerformance() {
        // 性能测试
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10; i++) {
            webTestClient.get()
                    .uri("/health")
                    .exchange()
                    .expectStatus().isOk();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("10次健康检查耗时: " + duration + "ms");
        
        assert duration < 1000 : "健康检查响应时间过长";
        System.out.println("✅ 健康检查性能测试通过");
    }

    @Test
    public void testNonExistentEndpoint() {
        // 测试不存在的端点
        webTestClient.get()
                .uri("/health/nonexistent")
                .exchange()
                .expectStatus().isNotFound();

        System.out.println("✅ 404测试通过");
    }
} 