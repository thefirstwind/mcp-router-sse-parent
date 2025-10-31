package com.pajk.mcpbridge.persistence;

import com.pajk.mcpbridge.persistence.entity.RoutingLog;
import com.pajk.mcpbridge.persistence.mapper.RoutingLogMapper;
import com.pajk.mcpbridge.persistence.service.PersistenceEventPublisher;
import com.pajk.mcpbridge.persistence.service.RoutingLogBatchWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * 路由日志持久化功能测试
 * 
 * 测试目标:
 * 1. Mapper 基本功能
 * 2. 批量写入功能
 * 3. 查询功能
 * 4. 性能指标
 * 
 * @author MCP Router Team
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class RoutingLogPersistenceTest {
    
    @Autowired(required = false)
    private RoutingLogMapper routingLogMapper;
    
    @Autowired(required = false)
    private PersistenceEventPublisher eventPublisher;
    
    @Autowired(required = false)
    private RoutingLogBatchWriter batchWriter;
    
    @Test
    public void testBasicInsert() {
        if (routingLogMapper == null) {
            System.out.println("⚠️  RoutingLogMapper not available, skipping test");
            return;
        }
        
        // 创建测试数据
        RoutingLog log = RoutingLog.newBuilder()
            .requestId(UUID.randomUUID().toString())
            .method("tools/call")
            .params("{\"name\":\"test\"}")
            .routingStrategy("ROUND_ROBIN")
            .targetServer("test-server:localhost:8080")
            .build();
        
        log.markSuccess(100);
        
        // 插入
        int result = routingLogMapper.insert(log);
        assertEquals("Insert should affect 1 row", 1, result);
        assertNotNull("ID should be generated", log.getId());
        
        System.out.println("✅ Basic insert test passed. Generated ID: " + log.getId());
    }
    
    @Test
    public void testBatchInsert() {
        if (routingLogMapper == null) {
            System.out.println("⚠️  RoutingLogMapper not available, skipping test");
            return;
        }
        
        // 创建100条测试数据
        List<RoutingLog> logs = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> {
                RoutingLog log = RoutingLog.newBuilder()
                    .requestId(UUID.randomUUID().toString())
                    .method("tools/call")
                    .params("{\"index\":" + i + "}")
                    .routingStrategy("WEIGHTED")
                    .targetServer("server-" + (i % 5) + ":localhost:808" + (i % 5))
                    .build();
                
                if (i % 10 == 0) {
                    log.markFailure("Test error", 500);
                } else {
                    log.markSuccess(50 + i);
                }
                
                return log;
            })
            .toList();
        
        // 批量插入
        long startTime = System.currentTimeMillis();
        int result = routingLogMapper.batchInsert(logs);
        long duration = System.currentTimeMillis() - startTime;
        
        assertEquals("Batch insert should affect 100 rows", 100, result);
        System.out.println("✅ Batch insert test passed. 100 records in " + duration + "ms");
        
        // 性能验证
        assertTrue("Batch insert should be fast", duration < 1000);
    }
    
    @Test
    public void testQueryByRequestId() {
        if (routingLogMapper == null) {
            System.out.println("⚠️  RoutingLogMapper not available, skipping test");
            return;
        }
        
        // 插入测试数据
        String requestId = UUID.randomUUID().toString();
        RoutingLog log = RoutingLog.newBuilder()
            .requestId(requestId)
            .method("tools/call")
            .params("{\"test\":\"query\"}")
            .routingStrategy("LEAST_CONNECTIONS")
            .targetServer("query-server:localhost:9999")
            .build();
        
        log.markSuccess(200);
        routingLogMapper.insert(log);
        
        // 查询
        RoutingLog queried = routingLogMapper.selectByRequestId(requestId);
        assertNotNull("Should find the log", queried);
        assertEquals("Request ID should match", requestId, queried.getRequestId());
        assertEquals("Method should match", "tools/call", queried.getMethod());
        assertTrue("Should be successful", queried.getSuccess());
        
        System.out.println("✅ Query by request ID test passed");
    }
    
    @Test
    public void testQueryByTimeRange() {
        if (routingLogMapper == null) {
            System.out.println("⚠️  RoutingLogMapper not available, skipping test");
            return;
        }
        
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(5);
        
        List<RoutingLog> logs = routingLogMapper.selectByTimeRange(startTime, endTime, 10);
        assertNotNull("Result should not be null", logs);
        
        System.out.println("✅ Query by time range test passed. Found " + logs.size() + " logs");
    }
    
    @Test
    public void testSelectFailures() {
        if (routingLogMapper == null) {
            System.out.println("⚠️  RoutingLogMapper not available, skipping test");
            return;
        }
        
        // 插入一些失败的记录
        for (int i = 0; i < 5; i++) {
            RoutingLog log = RoutingLog.newBuilder()
                .requestId(UUID.randomUUID().toString())
                .method("tools/call")
                .params("{}")
                .routingStrategy("ROUND_ROBIN")
                .targetServer("error-server:localhost:8080")
                .build();
            
            log.markFailure("Test error " + i, 500);
            routingLogMapper.insert(log);
        }
        
        // 查询失败记录
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
        
        List<RoutingLog> failures = routingLogMapper.selectFailures(startTime, endTime, 10);
        assertNotNull("Failures list should not be null", failures);
        assertTrue("Should find some failures", failures.size() >= 5);
        
        // 验证所有记录都是失败的
        for (RoutingLog log : failures) {
            assertFalse("All logs should be failures", log.getSuccess());
        }
        
        System.out.println("✅ Select failures test passed. Found " + failures.size() + " failures");
    }
    
    @Test
    public void testCalculateSuccessRate() {
        if (routingLogMapper == null) {
            System.out.println("⚠️  RoutingLogMapper not available, skipping test");
            return;
        }
        
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();
        
        Double successRate = routingLogMapper.calculateSuccessRate(startTime, endTime);
        assertNotNull("Success rate should not be null", successRate);
        assertTrue("Success rate should be between 0 and 100", 
            successRate >= 0 && successRate <= 100);
        
        System.out.println("✅ Calculate success rate test passed. Rate: " + 
            String.format("%.2f%%", successRate));
    }
    
    @Test
    public void testEventPublisher() {
        if (eventPublisher == null) {
            System.out.println("⚠️  PersistenceEventPublisher not available, skipping test");
            return;
        }
        
        // 发布10条事件
        for (int i = 0; i < 10; i++) {
            RoutingLog log = RoutingLog.newBuilder()
                .requestId(UUID.randomUUID().toString())
                .method("tools/call")
                .params("{\"index\":" + i + "}")
                .routingStrategy("ROUND_ROBIN")
                .targetServer("event-server:localhost:8080")
                .build();
            
            log.markSuccess(100);
            eventPublisher.publishRoutingLog(log);
        }
        
        // 获取统计
        PersistenceEventPublisher.PersistenceStats stats = eventPublisher.getStats();
        assertTrue("Should have published events", stats.successCount() >= 10);
        
        System.out.println("✅ Event publisher test passed. Stats: " + 
            stats.successCount() + " success, " + stats.failureCount() + " failures");
    }
    
    @Test
    public void testBatchWriterStats() throws InterruptedException {
        if (batchWriter == null) {
            System.out.println("⚠️  RoutingLogBatchWriter not available, skipping test");
            return;
        }
        
        // 等待一下让批处理器处理之前的数据
        Thread.sleep(3000);
        
        // 获取统计
        RoutingLogBatchWriter.BatchWriterStats stats = batchWriter.getStats();
        assertNotNull("Stats should not be null", stats);
        
        System.out.println("✅ Batch writer stats test passed.");
        System.out.println("   Batches: " + stats.batchCount());
        System.out.println("   Records: " + stats.recordCount());
        System.out.println("   Failures: " + stats.failureCount());
        System.out.println("   Avg batch size: " + String.format("%.2f", stats.avgBatchSize()));
        System.out.println("   Failure rate: " + String.format("%.2f%%", stats.failureRate()));
    }
}


