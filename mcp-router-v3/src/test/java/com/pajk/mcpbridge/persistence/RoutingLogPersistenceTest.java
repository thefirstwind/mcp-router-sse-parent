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
@SpringBootTest(classes = com.pajk.mcpbridge.core.McpRouterV3Application.class)
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
        RoutingLog log = RoutingLog.builder()
            .requestId(UUID.randomUUID().toString())
            .method("POST")
            .path("/mcp/router/route/test-server")
            .mcpMethod("tools/call")
            .toolName("testTool")
            .requestHeaders("{\"Content-Type\":\"application/json\"}")
            .requestBody("{\"name\":\"test\"}")
            .responseBody("{\"result\":\"success\"}")
            .loadBalanceStrategy("ROUND_ROBIN")
            .serverKey("test-server:localhost:8080")
            .serverName("test-server")
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now().plusNanos(100 * 1_000_000L))
            .duration(100)
            .isSuccess(true)
            .createdAt(LocalDateTime.now())
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
                RoutingLog logEntry = RoutingLog.builder()
                    .requestId(UUID.randomUUID().toString())
                    .method("POST")
                    .path("/mcp/router/route/server-" + (i % 5))
                    .mcpMethod("tools/call")
                    .toolName("testTool" + (i % 5))
                    .requestHeaders("{\"Content-Type\":\"application/json\"}")
                    .requestBody("{\"index\":" + i + "}")
                    .responseBody("{\"result\":\"success\"}")
                    .loadBalanceStrategy("WEIGHTED")
                    .serverKey("server-" + (i % 5) + ":localhost:808" + (i % 5))
                    .serverName("server-" + (i % 5))
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now().plusNanos((50 + i) * 1_000_000L))
                    .duration(50 + i)
                    .isSuccess(true)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                if (i % 10 == 0) {
                    logEntry.markFailure("Test error", 500, "TEST_ERROR", "TEST_TYPE");
                } else {
                    logEntry.markSuccess(50 + i);
                }
                
                return logEntry;
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
        RoutingLog log = RoutingLog.builder()
            .requestId(requestId)
            .method("POST")
            .path("/mcp/router/route/query-server")
            .mcpMethod("tools/call")
            .toolName("queryTool")
            .requestHeaders("{\"Content-Type\":\"application/json\"}")
            .requestBody("{\"test\":\"query\"}")
            .responseBody("{\"result\":\"success\"}")
            .loadBalanceStrategy("LEAST_CONNECTIONS")
            .serverKey("query-server:localhost:9999")
            .serverName("query-server")
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now().plusNanos(200 * 1_000_000L))
            .duration(200)
            .isSuccess(true)
            .createdAt(LocalDateTime.now())
            .build();
        
        log.markSuccess(200);
        routingLogMapper.insert(log);
        
        // 查询
        RoutingLog queried = routingLogMapper.selectByRequestId(requestId);
        assertNotNull("Should find the log", queried);
        assertEquals("Request ID should match", requestId, queried.getRequestId());
        assertEquals("Method should match", "tools/call", queried.getMcpMethod());
        assertTrue("Should be successful", queried.getIsSuccess());
        
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
            RoutingLog logEntry = RoutingLog.builder()
                .requestId(UUID.randomUUID().toString())
                .method("POST")
                .path("/mcp/router/route/error-server")
                .mcpMethod("tools/call")
                .toolName("errorTool")
                .requestHeaders("{\"Content-Type\":\"application/json\"}")
                .requestBody("{}")
                .responseBody("{\"error\":\"test error\"}")
                .loadBalanceStrategy("ROUND_ROBIN")
                .serverKey("error-server:localhost:8080")
                .serverName("error-server")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusNanos(200 * 1_000_000L))
                .duration(200)
                .isSuccess(false)
                .createdAt(LocalDateTime.now())
                .build();
            
            logEntry.markFailure("Test error " + i, 500, "TEST_ERROR_" + i, "TEST_TYPE");
            routingLogMapper.insert(logEntry);
        }
        
        // 查询失败记录
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);
        
        List<RoutingLog> failures = routingLogMapper.selectFailures(startTime, endTime, 10);
        assertNotNull("Failures list should not be null", failures);
        assertTrue("Should find some failures", failures.size() >= 5);
        
        // 验证所有记录都是失败的
        for (RoutingLog logEntry : failures) {
            assertFalse("All logs should be failures", logEntry.getIsSuccess());
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
            RoutingLog logEntry = RoutingLog.builder()
                .requestId(UUID.randomUUID().toString())
                .method("POST")
                .path("/mcp/router/route/event-server")
                .mcpMethod("tools/call")
                .toolName("eventTool" + i)
                .requestHeaders("{\"Content-Type\":\"application/json\"}")
                .requestBody("{\"index\":" + i + "}")
                .responseBody("{\"result\":\"success\"}")
                .loadBalanceStrategy("ROUND_ROBIN")
                .serverKey("event-server:localhost:8080")
                .serverName("event-server")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusNanos(100 * 1_000_000L))
                .duration(100)
                .isSuccess(true)
                .createdAt(LocalDateTime.now())
                .build();
            
            logEntry.markSuccess(100);
            eventPublisher.publishRoutingLog(logEntry);
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





