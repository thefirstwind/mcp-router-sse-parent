package com.pajk.mcpbridge.persistence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 持久化事件发布器
 * 负责接收各类需要持久化的事件，并通过 Reactive Streams 异步推送到批量写入器
 * 
 * 设计原则:
 * 1. 完全非阻塞 - 使用 Sinks.many() 实现异步发布
 * 2. 失败安全 - 发布失败时降级到日志记录
 * 3. 性能监控 - 统计发布成功率和失败率
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
public class PersistenceEventPublisher {
    
    // 路由日志事件流
    private final Sinks.Many<Object> routingLogSink;
    
    // 健康检查记录事件流
    private final Sinks.Many<Object> healthCheckSink;
    
    // 错误日志事件流
    private final Sinks.Many<Object> errorLogSink;
    
    // 性能监控计数器
    private final AtomicLong publishSuccessCount = new AtomicLong(0);
    private final AtomicLong publishFailureCount = new AtomicLong(0);
    
    public PersistenceEventPublisher() {
        // 创建多播 Sink，支持多个订阅者
        // 使用 onBackpressureBuffer 策略：如果消费者跟不上，缓冲数据
        // 注意：multicast() Sink 如果没有订阅者，事件会被丢弃
        // 批量写入器必须在 @PostConstruct 时订阅，否则事件会丢失
        this.routingLogSink = Sinks.many().multicast().onBackpressureBuffer(10000);
        this.healthCheckSink = Sinks.many().multicast().onBackpressureBuffer(1000);
        this.errorLogSink = Sinks.many().multicast().onBackpressureBuffer(1000);
        
        log.info("PersistenceEventPublisher initialized with buffer sizes: routing=10000, health=1000, error=1000");
        log.info("⚠️  Note: Events will be dropped if no subscribers are attached. Ensure batch writers are started.");
    }
    
    /**
     * 发布路由日志事件
     * 
     * @param routingLog 路由日志对象
     */
    public void publishRoutingLog(Object routingLog) {
        try {
            Sinks.EmitResult result = routingLogSink.tryEmitNext(routingLog);
            
            if (result.isSuccess()) {
                publishSuccessCount.incrementAndGet();
                log.trace("✅ Published routing log event successfully");
            } else {
                handleEmitFailure("RoutingLog", result, routingLog);
            }
        } catch (Exception e) {
            log.error("Failed to publish routing log event", e);
            publishFailureCount.incrementAndGet();
        }
    }
    
    /**
     * 发布健康检查记录事件
     * 
     * @param healthCheckRecord 健康检查记录对象
     */
    public void publishHealthCheck(Object healthCheckRecord) {
        try {
            Sinks.EmitResult result = healthCheckSink.tryEmitNext(healthCheckRecord);
            
            if (result.isSuccess()) {
                publishSuccessCount.incrementAndGet();
                log.trace("✅ Published health check event successfully");
            } else {
                handleEmitFailure("HealthCheck", result, healthCheckRecord);
            }
        } catch (Exception e) {
            log.error("Failed to publish health check event", e);
            publishFailureCount.incrementAndGet();
        }
    }
    
    /**
     * 发布错误日志事件
     * 
     * @param errorLog 错误日志对象
     */
    public void publishErrorLog(Object errorLog) {
        try {
            Sinks.EmitResult result = errorLogSink.tryEmitNext(errorLog);
            
            if (result.isSuccess()) {
                publishSuccessCount.incrementAndGet();
            } else {
                handleEmitFailure("ErrorLog", result, errorLog);
            }
        } catch (Exception e) {
            log.error("Failed to publish error log event", e);
            publishFailureCount.incrementAndGet();
        }
    }
    
    /**
     * 获取路由日志事件流（供批量写入器订阅）
     */
    public Sinks.Many<Object> getRoutingLogSink() {
        return routingLogSink;
    }
    
    /**
     * 获取健康检查事件流（供批量写入器订阅）
     */
    public Sinks.Many<Object> getHealthCheckSink() {
        return healthCheckSink;
    }
    
    /**
     * 获取错误日志事件流（供批量写入器订阅）
     */
    public Sinks.Many<Object> getErrorLogSink() {
        return errorLogSink;
    }
    
    /**
     * 获取性能统计
     */
    public PersistenceStats getStats() {
        return new PersistenceStats(
            publishSuccessCount.get(),
            publishFailureCount.get()
        );
    }
    
    /**
     * 处理事件发布失败的情况
     */
    private void handleEmitFailure(String eventType, Sinks.EmitResult result, Object event) {
        publishFailureCount.incrementAndGet();
        
        switch (result) {
            case FAIL_OVERFLOW:
                log.warn("❌ {} event buffer overflow, event dropped. Check if batch writer is consuming events fast enough.", eventType);
                break;
            case FAIL_CANCELLED:
                log.warn("❌ {} event sink cancelled, event dropped. Check if batch writer subscription is active.", eventType);
                break;
            case FAIL_TERMINATED:
                log.error("❌ {} event sink terminated, event dropped. Batch writer may have stopped.", eventType);
                break;
            case FAIL_NON_SERIALIZED:
                log.error("❌ {} event emit non-serialized, event dropped: {}", eventType, event);
                break;
            case FAIL_ZERO_SUBSCRIBER:
                log.error("❌ {} event emit failed: NO SUBSCRIBERS! Batch writer may not be started or subscription failed. Event: {}", eventType, event);
                break;
            default:
                log.error("❌ {} event emit failed with result: {}, event: {}", eventType, result, event);
        }
    }
    
    /**
     * 性能统计数据
     */
    public record PersistenceStats(
        long successCount,
        long failureCount
    ) {
        public long totalCount() {
            return successCount + failureCount;
        }
        
        public double successRate() {
            long total = totalCount();
            return total == 0 ? 0.0 : (double) successCount / total * 100;
        }
    }
}

