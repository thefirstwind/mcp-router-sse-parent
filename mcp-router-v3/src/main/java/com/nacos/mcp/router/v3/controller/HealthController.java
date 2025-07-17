package com.nacos.mcp.router.v3.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查控制器
 * 提供基础的健康检查接口
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now(),
                "service", "mcp-router-v3",
                "version", "1.0.0"
        ));
    }

    @GetMapping("/ready")
    public Mono<Map<String, Object>> ready() {
        return Mono.just(Map.of(
                "status", "READY",
                "timestamp", LocalDateTime.now()
        ));
    }
} 