package com.pajk.mcpbridge.persistence.service;

import com.pajk.mcpbridge.persistence.entity.RoutingLog;
import com.pajk.mcpbridge.persistence.mapper.RoutingLogMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由日志批量写入服务
 * 
 * 设计原则:
 * 1. 批量写入 - 2秒窗口或500条记录触发写入
 * 2. 异步处理 - 使用独立线程池，不阻塞主流程
 * 3. 故障降级 - 数据库失败时记录到日志文件
 * 
 * @author MCP Router Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
public class RoutingLogBatchWriter {
    
    private final RoutingLogMapper routingLogMapper;
    private final PersistenceEventPublisher eventPublisher;
    
    private Disposable subscription;
    
    // 性能统计
    private final AtomicLong batchCount = new AtomicLong(0);
    private final AtomicLong recordCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    
    // 配置参数
    private static final int BATCH_SIZE = 500;
    private static final Duration BATCH_WINDOW = Duration.ofSeconds(2);
    
    /**
     * 启动批量写入订阅
     */
    @PostConstruct
    public void start() {
        log.info("Starting RoutingLog batch writer with batchSize={}, window={}", BATCH_SIZE, BATCH_WINDOW);
        log.info("Sampling strategy: Success=100%, Failure=100% (Full recording enabled)");
        
        try {
            subscription = eventPublisher.getRoutingLogSink()
                .asFlux()
                .doOnSubscribe(sub -> log.info("✅ RoutingLog batch writer subscribed to event stream"))
                .doOnNext(routingLog -> log.trace("📥 Received routing log: {}", routingLog))
                .cast(RoutingLog.class)
                .filter(this::shouldSample) // 应用采样策略
                .doOnNext(routingLog -> log.trace("✅ Routing log passed sampling filter: {}", routingLog.getRequestId()))
                .bufferTimeout(BATCH_SIZE, BATCH_WINDOW)
                .filter(batch -> !batch.isEmpty())
                .doOnNext(batch -> log.debug("📦 Batching {} routing logs for write", batch.size()))
                .flatMap(this::writeBatch, 1) // 并发度为1，保证顺序
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    count -> log.debug("✅ Batch write completed: {} records", count),
                    error -> {
                        log.error("❌ Batch write error in RoutingLogBatchWriter", error);
                        failureCount.incrementAndGet();
                    },
                    () -> log.warn("⚠️ RoutingLog batch writer stream completed (unexpected)")
                );
            
            log.info("✅ RoutingLog batch writer started successfully and subscribed to event stream");
        } catch (Exception e) {
            log.error("❌ Failed to start RoutingLog batch writer", e);
            throw new RuntimeException("Failed to start RoutingLog batch writer", e);
        }
    }
    
    /**
     * 采样策略：成功请求100%，失败请求100%（临时调整为全量记录）
     */
    private boolean shouldSample(RoutingLog log) {
        // 失败请求：100% 记录
        if (log.getIsSuccess() == null || !log.getIsSuccess()) {
            return true;
        }
        
        // 成功请求：100% 采样（原来是10%）
        return true; // Math.random() < 0.1;
    }
    
    /**
     * 停止批量写入订阅
     */
    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("RoutingLog batch writer stopped. Stats: batches={}, records={}, failures={}",
                batchCount.get(), recordCount.get(), failureCount.get());
        }
    }
    
    /**
     * 批量写入数据库
     */
    private Flux<Integer> writeBatch(List<RoutingLog> logs) {
        return Flux.defer(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 执行批量插入
                int count = routingLogMapper.batchInsert(logs);
                
                long duration = System.currentTimeMillis() - startTime;
                
                // 更新统计
                batchCount.incrementAndGet();
                recordCount.addAndGet(count);
                
                log.info("Batch insert successful: {} records in {}ms", count, duration);
                
                // 性能告警
                if (duration > 1000) {
                    log.warn("Slow batch insert detected: {}ms for {} records", duration, count);
                }
                
                return Flux.just(count);
                
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Batch insert failed for {} records", logs.size(), e);
                
                // 详细错误信息，帮助定位 TypeHandler 问题
                if (e.getMessage() != null && (e.getMessage().contains("Data too long") ||
                        e.getMessage().contains("Data truncation") ||
                        e.getMessage().contains("column") && e.getMessage().contains("too large"))) {
                    log.error("⚠️ Database column size limit exceeded. This usually means the truncation TypeHandler did not run. " +
                            "Please check: 1) MyBatisConfig#setTypeHandlersPackage 是否生效, " +
                            "2) RoutingLogMapper.xml 的 insert/batchInsert 是否显式指定了 TypeHandler, " +
                            "3) 部署包中是否包含最新的 TypeHandler 类。 " +
                            "Sample log entry sizes: requestHeaders={}, requestBody={}, responseHeaders={}, responseBody={}",
                            logs.stream().mapToInt(log -> log.getRequestHeaders() != null ? log.getRequestHeaders().getBytes().length : 0).max().orElse(0),
                            logs.stream().mapToInt(log -> log.getRequestBody() != null ? log.getRequestBody().getBytes().length : 0).max().orElse(0),
                            logs.stream().mapToInt(log -> log.getResponseHeaders() != null ? log.getResponseHeaders().getBytes().length : 0).max().orElse(0),
                            logs.stream().mapToInt(log -> log.getResponseBody() != null ? log.getResponseBody().getBytes().length : 0).max().orElse(0));
                }
                
                // 降级处理：记录到日志文件
                fallbackToLog(logs);
                
                return Flux.empty();
            }
        });
    }
    
    /**
     * 降级处理：将数据记录到日志文件
     */
    private void fallbackToLog(List<RoutingLog> logs) {
        log.warn("Fallback: Writing {} routing logs to file", logs.size());
        
        for (RoutingLog log : logs) {
            logToFile(log);
        }
    }
    
    /**
     * 将单条记录写入日志文件
     */
    private void logToFile(RoutingLog routingLog) {
        // 使用特殊的日志标记，便于后续数据恢复
        log.warn("PERSISTENCE_FALLBACK|ROUTING_LOG|requestId={}|method={}|serverKey={}|isSuccess={}|duration={}|createdAt={}",
            routingLog.getRequestId(),
            routingLog.getMethod(),
            routingLog.getServerKey(),
            routingLog.getIsSuccess(),
            routingLog.getDuration(),
            routingLog.getCreatedAt()
        );
    }
    
    /**
     * 获取性能统计
     */
    public BatchWriterStats getStats() {
        return new BatchWriterStats(
            batchCount.get(),
            recordCount.get(),
            failureCount.get()
        );
    }
    
    /**
     * 批量写入统计
     */
    public record BatchWriterStats(
        long batchCount,
        long recordCount,
        long failureCount
    ) {
        public double avgBatchSize() {
            return batchCount == 0 ? 0 : (double) recordCount / batchCount;
        }
        
        public double failureRate() {
            return batchCount == 0 ? 0 : (double) failureCount / batchCount * 100;
        }
    }
}

