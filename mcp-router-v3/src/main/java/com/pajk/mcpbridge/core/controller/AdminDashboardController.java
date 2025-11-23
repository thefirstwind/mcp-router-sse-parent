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
import org.springframework.web.bind.annotation.RequestParam;
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
    public Mono<List<SessionOverview>> sessions(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "12") int hours) {
        return Mono.fromCallable(() -> {
                    List<SessionOverview> allSessions = sessionService.getSessionOverview();
                    if (sessionId != null && !sessionId.isEmpty()) {
                        return allSessions.stream()
                                .filter(s -> sessionId.equals(s.sessionId()))
                                .collect(Collectors.toList());
                    }
                    return allSessions.stream()
                            .filter(s -> {
                                if (serviceName != null && !serviceName.isEmpty()) {
                                    return serviceName.equals(s.serviceName());
                                }
                                return true;
                            })
                            .limit(10)
                            .collect(Collectors.toList());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/logs")
    public Mono<List<RoutingLogSummary>> logs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(defaultValue = "48") int hours) {
        return Mono.fromCallable(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime startTime = now.minusHours(hours);
                    // 限制最多返回 50 条记录
                    List<RoutingLog> logs = routingLogMapper.selectByTimeRange(startTime, now, 50);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        logs = logs.stream()
                                .filter(log -> serviceName.equals(log.getServerName()) || serviceName.equals(log.getServerKey()))
                                .collect(Collectors.toList());
                    }
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
    
    /**
     * 获取 RESTful 接口请求列表
     * 
     * @param serviceName 服务名称（可选）
     * @param mcpMethod MCP 方法（可选，如 "tools/call", "tools/list"）
     * @param hasSessionId sessionId 是否为空（可选，"true"=有sessionId, "false"=无sessionId, 其他=不筛选）
     * @param hours 查询最近 N 小时的数据，默认 1 小时
     * @param limit 限制返回数量，默认 100
     * @return RESTful 请求列表
     */
    @GetMapping("/api/restful-requests")
    public Mono<List<RestfulRequestSummary>> restfulRequests(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String mcpMethod,
            @RequestParam(required = false) String hasSessionId,
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(defaultValue = "100") int limit) {
        return Mono.fromCallable(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime startTime = now.minusHours(hours);
                    Boolean sessionIdFilter = null;
                    if (hasSessionId != null && !hasSessionId.isEmpty()) {
                        sessionIdFilter = Boolean.parseBoolean(hasSessionId);
                    }
                    List<RoutingLog> logs = routingLogMapper.selectRestfulRequests(
                            serviceName, mcpMethod, sessionIdFilter, startTime, now, limit);
                    return logs.stream()
                            .map(RestfulRequestSummary::from)
                            .collect(Collectors.toList());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取 RESTful 请求详情（完整内容，不截取）
     */
    @GetMapping("/api/restful-requests/{requestId}")
    public Mono<SessionLogDetail> restfulRequestDetail(@PathVariable String requestId) {
        return Mono.fromCallable(() -> {
                    RoutingLog log = routingLogMapper.selectByRequestId(requestId);
                    if (log == null) {
                        return null;
                    }
                    // RESTful 请求详情返回完整内容，不截取
                    return new SessionLogDetail(
                            log.getRequestId(),
                            log.getMethod(),
                            log.getPath(),
                            log.getMcpMethod(),
                            log.getRequestBody(), // 不截取 requestBody
                            log.getResponseBody(), // 不截取 responseBody，完整显示
                            log.getResponseStatus(),
                            log.getStartTime()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DashboardSummary buildSummary(LocalDateTime now) {
        List<SessionOverview> sessions = sessionService.getSessionOverview();
        int sessionCount = (int) sessions.stream().filter(SessionOverview::active).count();
        // 默认使用 48 小时计算成功率
        LocalDateTime startTime = now.minusHours(48);
        Double successRate = null;
        try {
            successRate = routingLogMapper.calculateSuccessRate(startTime, now);
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
            String sessionId,
            String mcpMethod,
            String serverName,
            boolean success,
            Integer duration,
            LocalDateTime startTime
    ) {
        static RoutingLogSummary from(RoutingLog log) {
            return new RoutingLogSummary(
                    log.getRequestId(),
                    log.getSessionId(),
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
    
    public record RestfulRequestSummary(
            String requestId,
            String method,
            String path,
            String serverName,
            String mcpMethod,
            String toolName,
            boolean success,
            Integer responseStatus,
            Integer duration,
            LocalDateTime startTime,
            String clientIp
    ) {
        static RestfulRequestSummary from(RoutingLog log) {
            return new RestfulRequestSummary(
                    log.getRequestId(),
                    log.getMethod(),
                    log.getPath(),
                    log.getServerName() != null ? log.getServerName() : log.getServerKey(),
                    log.getMcpMethod(),
                    log.getToolName(),
                    Boolean.TRUE.equals(log.getIsSuccess()),
                    log.getResponseStatus(),
                    log.getDuration(),
                    log.getStartTime(),
                    log.getClientIp()
            );
        }
    }
}

