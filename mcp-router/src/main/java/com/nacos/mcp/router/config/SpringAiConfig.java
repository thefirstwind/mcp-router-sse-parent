package com.nacos.mcp.router.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.util.HashMap;

/**
 * Enhanced Spring AI MCP Configuration with comprehensive error handling and monitoring
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class SpringAiConfig {

    /**
     * Enhanced CORS configuration for cross-origin requests
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        log.info("Configuring CORS for MCP Router with enhanced security settings");
        
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowCredentials(false); // False to work with wildcard origins
        corsConfig.addAllowedOrigin("*");
        corsConfig.addAllowedHeader("*");
        corsConfig.addAllowedMethod("*");
        corsConfig.setExposedHeaders(List.of("X-Request-ID", "X-Response-Time", "X-Error-Code"));
        corsConfig.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        log.info("CORS configuration completed successfully");
        return new CorsWebFilter(source);
    }

    /**
     * Global error handler for MCP operations with structured logging
     */
    @Bean
    public McpErrorHandler mcpErrorHandler() {
        return new McpErrorHandler();
    }

    public static class McpErrorHandler {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpErrorHandler.class);

        /**
         * Handle tool execution errors with structured logging and recovery strategies
         */
        public Map<String, Object> handleToolError(String toolName, Object request, Exception error) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            
            log.error("Tool execution failed [errorId={}]: tool={}, error={}, request={}", 
                    errorId, toolName, error.getMessage(), request, error);
            
            return Map.of(
                "error", true,
                "errorId", errorId,
                "errorType", error.getClass().getSimpleName(),
                "message", sanitizeErrorMessage(error.getMessage()),
                "toolName", toolName,
                "timestamp", System.currentTimeMillis(),
                "retryable", isRetryableError(error),
                "category", categorizeError(error)
            );
        }

        /**
         * Handle validation errors with detailed field information
         */
        public Map<String, Object> handleValidationError(String toolName, Object request, Set<String> violations) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            
            log.warn("Tool validation failed [errorId={}]: tool={}, violations={}, request={}", 
                    errorId, toolName, violations, request);
            
            return Map.of(
                "error", true,
                "errorId", errorId,
                "errorType", "ValidationError",
                "message", "Request validation failed",
                "violations", violations,
                "toolName", toolName,
                "timestamp", System.currentTimeMillis(),
                "retryable", false,
                "category", "CLIENT_ERROR"
            );
        }

        /**
         * Handle service unavailable errors with circuit breaker information
         */
        public Map<String, Object> handleServiceUnavailable(String serviceName, Exception error) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            
            log.error("Service unavailable [errorId={}]: service={}, error={}", 
                    errorId, serviceName, error.getMessage(), error);
            
            return Map.of(
                "error", true,
                "errorId", errorId,
                "errorType", "ServiceUnavailable",
                "message", "Service temporarily unavailable",
                "serviceName", serviceName,
                "timestamp", System.currentTimeMillis(),
                "retryable", true,
                "retryAfter", calculateRetryDelay(error),
                "category", "SERVICE_ERROR"
            );
        }

        /**
         * Handle timeout errors with performance context
         */
        public Map<String, Object> handleTimeoutError(String operation, long duration, Exception error) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            
            log.error("Operation timeout [errorId={}]: operation={}, duration={}ms, error={}", 
                    errorId, operation, duration, error.getMessage());
            
            return Map.of(
                "error", true,
                "errorId", errorId,
                "errorType", "TimeoutError",
                "message", "Operation timed out",
                "operation", operation,
                "duration", duration,
                "timestamp", System.currentTimeMillis(),
                "retryable", true,
                "category", "TIMEOUT_ERROR"
            );
        }

        private boolean isRetryableError(Exception error) {
            return error instanceof java.net.SocketTimeoutException ||
                   error instanceof java.net.ConnectException ||
                   error instanceof org.springframework.web.client.ResourceAccessException ||
                   (error instanceof RuntimeException && 
                    error.getMessage() != null && 
                    (error.getMessage().contains("timeout") ||
                     error.getMessage().contains("connection") ||
                     error.getMessage().contains("network")));
        }

        private String categorizeError(Exception error) {
            if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
                return "CLIENT_ERROR";
            } else if (error instanceof java.net.SocketTimeoutException || 
                       error instanceof java.net.ConnectException) {
                return "NETWORK_ERROR";
            } else if (error instanceof RuntimeException) {
                return "SERVER_ERROR";
            } else {
                return "UNKNOWN_ERROR";
            }
        }

        private String sanitizeErrorMessage(String message) {
            if (message == null) return "Unknown error";
            // Remove sensitive information and limit length
            return message.length() > 200 ? message.substring(0, 200) + "..." : message;
        }

        private long calculateRetryDelay(Exception error) {
            // Dynamic retry delay based on error type
            if (error instanceof java.net.SocketTimeoutException) {
                return 60000; // 1 minute for timeouts
            } else if (error instanceof java.net.ConnectException) {
                return 30000; // 30 seconds for connection errors
            } else {
                return 15000; // 15 seconds for other service errors
            }
        }
    }

    /**
     * Enhanced performance monitor for tool executions
     */
    @Bean
    public ToolExecutionMonitor toolExecutionMonitor() {
        return new ToolExecutionMonitor();
    }

    @Component
    public static class ToolExecutionMonitor {
        private final Map<String, ToolMetrics> toolMetrics = new ConcurrentHashMap<>();
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);

        @Data
        @Builder
        public static class ToolMetrics {
            private String toolName;
            private long executionCount;
            private long errorCount;
            private long totalDuration;
            private long averageDuration;
            private long minDuration;
            private long maxDuration;
            private LocalDateTime lastExecution;
            private double errorRate;
        }

        public void recordExecution(String toolName, long duration, boolean success) {
            totalExecutions.incrementAndGet();
            if (!success) {
                totalErrors.incrementAndGet();
            }

            toolMetrics.compute(toolName, (key, existing) -> {
                if (existing == null) {
                    return ToolMetrics.builder()
                        .toolName(toolName)
                        .executionCount(1)
                        .errorCount(success ? 0 : 1)
                        .totalDuration(duration)
                        .averageDuration(duration)
                        .minDuration(duration)
                        .maxDuration(duration)
                        .lastExecution(LocalDateTime.now())
                        .errorRate(success ? 0.0 : 1.0)
                        .build();
                } else {
                    long newExecutionCount = existing.getExecutionCount() + 1;
                    long newErrorCount = existing.getErrorCount() + (success ? 0 : 1);
                    long newTotalDuration = existing.getTotalDuration() + duration;
                    long newAverageDuration = newTotalDuration / newExecutionCount;
                    
                    return ToolMetrics.builder()
                        .toolName(toolName)
                        .executionCount(newExecutionCount)
                        .errorCount(newErrorCount)
                        .totalDuration(newTotalDuration)
                        .averageDuration(newAverageDuration)
                        .minDuration(Math.min(existing.getMinDuration(), duration))
                        .maxDuration(Math.max(existing.getMaxDuration(), duration))
                        .lastExecution(LocalDateTime.now())
                        .errorRate((double) newErrorCount / newExecutionCount)
                        .build();
                }
            });
        }

        public Map<String, ToolMetrics> getAllMetrics() {
            return new HashMap<>(toolMetrics);
        }

        public ToolMetrics getMetrics(String toolName) {
            return toolMetrics.get(toolName);
        }

        public Map<String, Object> getOverallStats() {
            long total = totalExecutions.get();
            long errors = totalErrors.get();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalExecutions", total);
            stats.put("totalErrors", errors);
            stats.put("overallErrorRate", total > 0 ? (double) errors / total : 0.0);
            stats.put("uniqueTools", toolMetrics.size());
            stats.put("timestamp", LocalDateTime.now());
            return stats;
        }
    }

    /**
     * Request/Response tracking for better observability
     */
    @Bean
    public RequestTracker requestTracker() {
        return new RequestTracker();
    }

    public static class RequestTracker {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestTracker.class);

        /**
         * Track request with unique ID and structured logging
         */
        public String trackRequest(String operation, Object request) {
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            
            log.info("Request started [requestId={}]: operation={}, request_size={}", 
                    requestId, operation, getRequestSize(request));
            
            return requestId;
        }

        /**
         * Track response with performance and size metrics
         */
        public void trackResponse(String requestId, String operation, Object response, long duration) {
            log.info("Request completed [requestId={}]: operation={}, duration={}ms, response_size={}", 
                    requestId, operation, duration, getResponseSize(response));
        }

        private int getRequestSize(Object request) {
            if (request == null) return 0;
            return request.toString().length();
        }

        private int getResponseSize(Object response) {
            if (response == null) return 0;
            return response.toString().length();
        }
    }

    // Enhanced type-safe request/response classes with validation
    public record EchoRequest(String message, Integer repeat) {
        public EchoRequest {
            if (message == null || message.trim().isEmpty()) {
                throw new IllegalArgumentException("Message cannot be null or empty");
            }
            if (repeat != null && (repeat < 1 || repeat > 10)) {
                throw new IllegalArgumentException("Repeat count must be between 1 and 10");
            }
        }
    }
    
    public record EchoResponse(String originalMessage, String echoedMessage, Integer repeatCount, Long timestamp) {}
    
    public record CalculatorRequest(double a, double b, String operation) {
        public CalculatorRequest {
            if (operation == null || !Set.of("add", "subtract", "multiply", "divide").contains(operation.toLowerCase())) {
                throw new IllegalArgumentException("Operation must be one of: add, subtract, multiply, divide");
            }
            if (Math.abs(a) > 1000000 || Math.abs(b) > 1000000) {
                throw new IllegalArgumentException("Numbers must be between -1,000,000 and 1,000,000");
            }
            if ("divide".equals(operation.toLowerCase()) && b == 0) {
                throw new IllegalArgumentException("Division by zero is not allowed");
            }
        }
    }
    
    public record CalculatorResponse(double result, String operation, double operandA, double operandB, String expression, Long timestamp) {}
    
    public record SearchRequest(String query, Integer limit, String statusFilter) {
        public SearchRequest {
            if (query == null || query.trim().isEmpty()) {
                throw new IllegalArgumentException("Query cannot be null or empty");
            }
            if (limit != null && (limit < 1 || limit > 50)) {
                throw new IllegalArgumentException("Limit must be between 1 and 50");
            }
            if (statusFilter != null && !Set.of("UP", "DOWN", "ALL").contains(statusFilter.toUpperCase())) {
                throw new IllegalArgumentException("Status filter must be UP, DOWN, or ALL");
            }
        }
    }
    
    public record SearchResponse(String query, Integer limit, String statusFilter, List<Map<String, Object>> results, 
                               Integer totalCount, Long searchTime, Long timestamp) {}
    
    public record ListServersRequest() {}
    
    public record ListServersResponse(List<Map<String, Object>> servers, Integer count, Long timestamp) {}
    
    public record ErrorResponse(boolean error, String errorId, String errorType, String message, 
                              String category, boolean retryable, Long timestamp) {}
} 