package com.pajk.mcpbridge.persistence.service;

import com.pajk.mcpbridge.persistence.entity.HealthCheckRecord;
import com.pajk.mcpbridge.persistence.mapper.HealthCheckRecordMapper;
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
 * 健康检查记录批量写入服务
 * 
 * 设计原则:
 * 1. 批量写入 - 5秒窗口或100条记录触发写入
 * 2. 采样策略 - 成功检查10%采样，失败检查100%记录
 * 3. 异步处理 - 使用独立线程池
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
public class HealthCheckRecordBatchWriter {
    
    private final HealthCheckRecordMapper healthCheckRecordMapper;
    private final PersistenceEventPublisher eventPublisher;
    
    private Disposable subscription;
    
    // 性能统计
    private final AtomicLong batchCount = new AtomicLong(0);
    private final AtomicLong recordCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong sampledCount = new AtomicLong(0);
    
    // 配置参数
    private static final int BATCH_SIZE = 100;
    private static final Duration BATCH_WINDOW = Duration.ofSeconds(5);
    
    /**
     * 启动批量写入订阅
     */
    @PostConstruct
    public void start() {
        log.info("Starting HealthCheckRecord batch writer with batchSize={}, window={}", BATCH_SIZE, BATCH_WINDOW);
        
        try {
            subscription = eventPublisher.getHealthCheckSink()
                .asFlux()
                .doOnSubscribe(sub -> log.info("✅ HealthCheckRecord batch writer subscribed to event stream"))
                .doOnNext(record -> log.trace("📥 Received health check record: {}", record))
                .cast(HealthCheckRecord.class)
                .bufferTimeout(BATCH_SIZE, BATCH_WINDOW)
                .filter(batch -> !batch.isEmpty())
                .doOnNext(batch -> log.debug("📦 Batching {} health check records for write", batch.size()))
                .flatMap(this::writeBatch, 1)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    count -> log.debug("✅ Batch write completed: {} records", count),
                    error -> {
                        log.error("❌ Batch write error in HealthCheckRecordBatchWriter", error);
                        failureCount.incrementAndGet();
                    },
                    () -> log.warn("⚠️ HealthCheckRecord batch writer stream completed (unexpected)")
                );
            
            log.info("✅ HealthCheckRecord batch writer started successfully and subscribed to event stream");
        } catch (Exception e) {
            log.error("❌ Failed to start HealthCheckRecord batch writer", e);
            throw new RuntimeException("Failed to start HealthCheckRecord batch writer", e);
        }
    }
    
    /**
     * 停止批量写入订阅
     */
    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("HealthCheckRecord batch writer stopped. Stats: batches={}, records={}, failures={}",
                batchCount.get(), recordCount.get(), failureCount.get());
        }
    }
    
    /**
     * 批量写入数据库
     */
    private Flux<Integer> writeBatch(List<HealthCheckRecord> records) {
        return Flux.defer(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 执行批量插入
                int count = healthCheckRecordMapper.batchInsert(records);
                
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
                log.error("Batch insert failed for {} records", records.size(), e);
                
                // 降级处理：记录到日志文件
                fallbackToLog(records);
                
                return Flux.empty();
            }
        });
    }
    
    /**
     * 降级处理：将数据记录到日志文件
     */
    private void fallbackToLog(List<HealthCheckRecord> records) {
        log.warn("Fallback: Writing {} health check records to file", records.size());
        
        for (HealthCheckRecord record : records) {
            logToFile(record);
        }
    }
    
    /**
     * 将单条记录写入日志文件
     */
    private void logToFile(HealthCheckRecord record) {
        log.warn("PERSISTENCE_FALLBACK|HEALTH_CHECK|serverKey={}|checkTime={}|status={}|responseTime={}|errorMessage={}",
            record.getServerKey(),
            record.getCheckTime(),
            record.getStatus(),
            record.getResponseTime(),
            record.getErrorMessage()
        );
    }
    
    /**
     * 获取性能统计
     */
    public BatchWriterStats getStats() {
        return new BatchWriterStats(
            batchCount.get(),
            recordCount.get(),
            failureCount.get(),
            sampledCount.get()
        );
    }
    
    /**
     * 批量写入统计
     */
    public record BatchWriterStats(
        long batchCount,
        long recordCount,
        long failureCount,
        long sampledCount
    ) {
        public double avgBatchSize() {
            return batchCount == 0 ? 0 : (double) recordCount / batchCount;
        }
        
        public double failureRate() {
            return batchCount == 0 ? 0 : (double) failureCount / batchCount * 100;
        }
        
        public double samplingRate() {
            return recordCount == 0 ? 0 : (double) sampledCount / recordCount * 100;
        }
    }
}

