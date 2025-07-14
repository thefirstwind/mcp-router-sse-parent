package com.nacos.mcp.router;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "nacos.discovery.server-addr=127.0.0.1:8848",
    "nacos.discovery.enabled=true",
    "nacos.discovery.username=nacos",
    "nacos.discovery.password=nacos"
})
class NacosMcpRouterApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully with real Nacos
    }

    @Test
    void applicationStartsSuccessfully() {
        // Test that the application starts without throwing exceptions
        // This is implicitly tested by the context loading
    }
} 