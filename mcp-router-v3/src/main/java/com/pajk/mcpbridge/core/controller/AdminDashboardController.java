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
            @RequestParam(required = false) String transportType,
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
                                    if (!serviceName.equals(s.serviceName())) {
                                        return false;
                                    }
                                }
                                if (transportType != null && !transportType.isEmpty()) {
                                    String sessionTransportType = s.transportType();
                                    if (sessionTransportType == null) {
                                        // 如果没有 transportType，默认认为是 SSE（向后兼容）
                                        if (!"SSE".equalsIgnoreCase(transportType)) {
                                            return false;
                                        }
                                    } else if (!transportType.equalsIgnoreCase(sessionTransportType)) {
                                        return false;
                                    }
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
    public Mono<RestfulRequestPageResponse> restfulRequests(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String mcpMethod,
            @RequestParam(required = false) String hasSessionId,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Mono.fromCallable(() -> {
                    // 参数校验
                    final int finalPageNo = pageNo < 1 ? 1 : pageNo;
                    final int finalPageSize = (pageSize < 1 || pageSize > 100) 
                            ? Math.min(Math.max(pageSize, 1), 100) 
                            : pageSize;
                    
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime startTime = now.minusHours(hours);
                    LocalDateTime endTime = now;
                    
                    Boolean sessionIdFilter = null;
                    if (hasSessionId != null && !hasSessionId.isEmpty()) {
                        sessionIdFilter = Boolean.parseBoolean(hasSessionId);
                    }
                    
                    // 先查询总数
                    Long totalCount = routingLogMapper.countRestfulRequests(
                            serviceName, mcpMethod, sessionIdFilter, startTime, endTime);
                    
                    // 计算分页参数
                    int offset = (finalPageNo - 1) * finalPageSize;
                    int limit = finalPageSize;
                    
                    // 查询分页数据
                    List<RoutingLog> logs = routingLogMapper.selectRestfulRequestsWithPagination(
                            serviceName, mcpMethod, sessionIdFilter, startTime, endTime, offset, limit);
                    
                    // 计算总页数
                    int totalPages = (int) Math.ceil((double) totalCount / finalPageSize);
                    
                    log.info("RESTful请求分页查询: pageNo={}, pageSize={}, totalCount={}, totalPages={}, 返回记录数={}", 
                            finalPageNo, finalPageSize, totalCount, totalPages, logs.size());
                    
                    // 构建响应
                    RestfulRequestPageResponse response = new RestfulRequestPageResponse();
                    response.setPageNo(finalPageNo);
                    response.setPageSize(finalPageSize);
                    response.setTotalCount(totalCount != null ? totalCount : 0);
                    response.setTotalPages(totalPages);
                    response.setData(logs.stream()
                            .map(RestfulRequestSummary::from)
                            .collect(Collectors.toList()));
                    
                    return response;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 统计 RESTful 接口请求数量（使用 COUNT(0) 提高效率）
     * 
     * @param serviceName 服务名称（可选）
     * @param mcpMethod MCP 方法（可选，如 "tools/call", "tools/list"）
     * @param hasSessionId sessionId 是否为空（可选，"true"=有sessionId, "false"=无sessionId, 其他=不筛选）
     * @param hours 查询最近 N 小时的数据，默认 24 小时
     * @return RESTful 请求数量
     */
    @GetMapping("/api/restful-requests/count")
    public Mono<Long> restfulRequestsCount(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String mcpMethod,
            @RequestParam(required = false) String hasSessionId,
            @RequestParam(defaultValue = "24") int hours) {
        return Mono.fromCallable(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime startTime = now.minusHours(hours);
                    Boolean sessionIdFilter = null;
                    if (hasSessionId != null && !hasSessionId.isEmpty()) {
                        sessionIdFilter = Boolean.parseBoolean(hasSessionId);
                    }
                    Long count = routingLogMapper.countRestfulRequests(
                            serviceName, mcpMethod, sessionIdFilter, startTime, now);
                    return count != null ? count : 0L;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取 RESTful 请求详情（完整内容，不截取）
     */
    @GetMapping("/api/restful-requests/{requestId}")
    public Mono<SessionLogDetail> restfulRequestDetail(@PathVariable String requestId) {
        return Mono.fromCallable(() -> {
                    RoutingLog routingLog = routingLogMapper.selectByRequestId(requestId);
                    if (routingLog == null) {
                        return null;
                    }
                    
                    // 限制 requestBody 和 responseBody 的大小，防止解压缩和序列化阻塞
                    // 最大大小：500KB（与前端限制一致）
                    final int MAX_BODY_SIZE = 500 * 1024;
                    
                    String requestBody = routingLog.getRequestBody();
                    String responseBody = routingLog.getResponseBody();
                    
                    // 截断过大的内容，防止阻塞
                    if (requestBody != null && requestBody.length() > MAX_BODY_SIZE) {
                        int originalSize = requestBody.length();
                        requestBody = requestBody.substring(0, MAX_BODY_SIZE) + 
                                "\n\n...[内容过大，已截断，仅显示前 " + (MAX_BODY_SIZE / 1024) + "KB]";
                        log.warn("Request body truncated for requestId: {}, original size: {} bytes", 
                                requestId, originalSize);
                    }
                    
                    if (responseBody != null && responseBody.length() > MAX_BODY_SIZE) {
                        int originalSize = responseBody.length();
                        responseBody = responseBody.substring(0, MAX_BODY_SIZE) + 
                                "\n\n...[内容过大，已截断，仅显示前 " + (MAX_BODY_SIZE / 1024) + "KB]";
                        log.warn("Response body truncated for requestId: {}, original size: {} bytes", 
                                requestId, originalSize);
                    }
                    
                    return new SessionLogDetail(
                            routingLog.getRequestId(),
                            routingLog.getMethod(),
                            routingLog.getPath(),
                            routingLog.getMcpMethod(),
                            requestBody,
                            responseBody,
                            routingLog.getResponseStatus(),
                            routingLog.getStartTime()
                    );
                })
                .timeout(java.time.Duration.ofSeconds(10)) // 10秒超时保护
                .onErrorResume(throwable -> {
                    log.error("Failed to get restful request detail for requestId: {}", requestId, throwable);
                    return Mono.empty();
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
            Long id,
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
            String clientId,
            String clientIp,
            String userAgent
    ) {
        static RestfulRequestSummary from(RoutingLog log) {
            return new RestfulRequestSummary(
                    log.getId(),
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
                    log.getClientId(),
                    log.getClientIp(),
                    log.getUserAgent()
            );
        }
    }

    /**
     * RESTful 请求分页响应
     */
    public static class RestfulRequestPageResponse {
        private int pageNo;
        private int pageSize;
        private long totalCount;
        private int totalPages;
        private List<RestfulRequestSummary> data;

        public int getPageNo() {
            return pageNo;
        }

        public void setPageNo(int pageNo) {
            this.pageNo = pageNo;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }

        public List<RestfulRequestSummary> getData() {
            return data;
        }

        public void setData(List<RestfulRequestSummary> data) {
            this.data = data;
        }
    }
}

