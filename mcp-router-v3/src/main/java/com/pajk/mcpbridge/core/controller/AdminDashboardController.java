package com.pajk.mcpbridge.core.controller;

import com.pajk.mcpbridge.core.service.McpSessionService;
import com.pajk.mcpbridge.core.service.McpSessionService.SessionOverview;
import com.pajk.mcpbridge.persistence.entity.RoutingLog;
import com.pajk.mcpbridge.persistence.mapper.RoutingLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理面板后台 API
 * - /admin         -> 重定向到静态 UI (/admin/index.html)
 * - /admin/api/... -> 提供仪表盘数据
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final McpSessionService sessionService;
    private final RoutingLogMapper routingLogMapper;

    @GetMapping({"", "/"})
    public Mono<ResponseEntity<Void>> redirectToDashboard() {
        return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/admin/index.html"))
                .build());
    }

    @GetMapping(value = "/index.html", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<Resource>> loadIndexHtml() {
        return Mono.fromSupplier(() -> {
            Resource resource = new ClassPathResource("static/admin/index.html");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(resource);
        });
    }

    @GetMapping("/api/summary")
    public Mono<DashboardSummary> summary() {
        return Mono.fromCallable(() -> buildSummary(LocalDateTime.now()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/sessions")
    public Mono<List<SessionOverview>> sessions() {
        return Mono.fromCallable(sessionService::getSessionOverview)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/logs")
    public Mono<List<RoutingLogSummary>> logs() {
        return Mono.fromCallable(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    List<RoutingLog> logs = routingLogMapper.selectByTimeRange(now.minusHours(1), now, 20);
                    return logs.stream()
                            .map(RoutingLogSummary::from)
                            .collect(Collectors.toList());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/sessions/{sessionId}/logs")
    public Mono<List<SessionLogDetail>> sessionLogs(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> routingLogMapper.selectBySessionId(sessionId, 50).stream()
                        .map(SessionLogDetail::from)
                        .collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DashboardSummary buildSummary(LocalDateTime now) {
        List<SessionOverview> sessions = sessionService.getSessionOverview();
        int sessionCount = (int) sessions.stream().filter(SessionOverview::active).count();
        LocalDateTime oneHourAgo = now.minusHours(1);
        Double successRate = null;
        try {
            successRate = routingLogMapper.calculateSuccessRate(oneHourAgo, now);
        } catch (Exception e) {
            log.warn("Failed to calculate routing success rate", e);
        }
        return new DashboardSummary(
                sessionCount,
                successRate != null ? successRate : 0.0,
                now
        );
    }

    public record DashboardSummary(int sessionCount, double successRate, LocalDateTime generatedAt) {}

    public record RoutingLogSummary(
            String requestId,
            String mcpMethod,
            String serverName,
            boolean success,
            Integer duration,
            LocalDateTime startTime
    ) {
        static RoutingLogSummary from(RoutingLog log) {
            return new RoutingLogSummary(
                    log.getRequestId(),
                    log.getMcpMethod(),
                    log.getServerName() != null ? log.getServerName() : log.getServerKey(),
                    Boolean.TRUE.equals(log.getIsSuccess()),
                    log.getDuration(),
                    log.getStartTime()
            );
        }
    }

    public record SessionLogDetail(
            String requestId,
            String method,
            String path,
            String mcpMethod,
            String requestBody,
            String responseBody,
            Integer responseStatus,
            LocalDateTime startTime
    ) {
        static SessionLogDetail from(RoutingLog log) {
            return new SessionLogDetail(
                    log.getRequestId(),
                    log.getMethod(),
                    log.getPath(),
                    log.getMcpMethod(),
                    safeTruncate(log.getRequestBody(), 4096),
                    safeTruncate(log.getResponseBody(), 4096),
                    log.getResponseStatus(),
                    log.getStartTime()
            );
        }
    }

    private static String safeTruncate(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        return value.length() > maxChars ? value.substring(0, maxChars) + "..." : value;
    }
}

