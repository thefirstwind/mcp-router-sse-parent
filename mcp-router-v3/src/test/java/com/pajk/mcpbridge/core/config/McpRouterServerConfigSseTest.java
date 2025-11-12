package com.pajk.mcpbridge.core.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.junit.Assert.assertTrue;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class McpRouterServerConfigSseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    public void sseEndpointShouldEmitEndpointWithServicePath() {
        String serviceName = "sample-service";

        FluxExchangeResult<String> result = webTestClient.get()
                .uri("/sse/" + serviceName)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        String firstChunk = result.getResponseBody()
                .timeout(Duration.ofSeconds(5))
                .blockFirst();

        log.info("First SSE chunk: {}", firstChunk);
        // Should include endpoint event and message URL containing the serviceName path
        assertTrue(firstChunk != null && firstChunk.contains("/mcp/" + serviceName + "/message?sessionId="));
    }

    @Test
    public void optionsPreflightShouldBeHandled() {
        webTestClient.method(org.springframework.http.HttpMethod.OPTIONS)
                .uri("/sse")
                .exchange()
                .expectStatus().isOk();

        webTestClient.method(org.springframework.http.HttpMethod.OPTIONS)
                .uri("/sse/test-service")
                .exchange()
                .expectStatus().isOk();

        webTestClient.method(org.springframework.http.HttpMethod.OPTIONS)
                .uri("/mcp/message")
                .exchange()
                .expectStatus().isOk();

        webTestClient.method(org.springframework.http.HttpMethod.OPTIONS)
                .uri("/mcp/test-service/message")
                .exchange()
                .expectStatus().isOk();
    }
}


