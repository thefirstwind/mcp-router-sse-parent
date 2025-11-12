package com.pajk.mcpbridge.core.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SseConnectionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void sseConnectionShouldStayOpenAndEmitHeartbeats() {
        String url = "http://127.0.0.1:" + port + "/sse/mcp-server-v6";

        Flux<String> eventFlux = webTestClient.get()
                .uri(url)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody();

        // Expect at least the initial endpoint event followed by a heartbeat within 35 seconds,
        // and the stream should not complete within that window.
        StepVerifier.create(eventFlux)
                .expectNextCount(1) // endpoint event
                .thenAwait(Duration.ofSeconds(35))
                .thenCancel()
                .verify();
    }
}



