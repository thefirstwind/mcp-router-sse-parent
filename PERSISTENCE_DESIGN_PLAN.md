# MCP Router V3 持久化功能节点设计方案

> **版本**: v1.0  
> **创建日期**: 2025-10-30  
> **状态**: 设计阶段 - 待评审  
> **基础**: MyBatis + WebFlux 非阻塞设计

---

## 📋 目录

1. [设计原则](#设计原则)
2. [功能节点分类](#功能节点分类)
3. [核心持久化节点](#核心持久化节点)
4. [辅助持久化节点](#辅助持久化节点)
5. [性能持久化节点](#性能持久化节点)
6. [实施优先级](#实施优先级)
7. [技术方案](#技术方案)
8. [数据流设计](#数据流设计)
9. [风险评估](#风险评估)

---

## 设计原则

### 🎯 核心原则

1. **非阻塞优先**: 所有持久化操作必须异步执行，不阻塞主线程
2. **降级保护**: 数据库故障不影响核心路由功能
3. **批量优化**: 高频操作使用批量写入
4. **分区策略**: 大表使用时间分区，自动归档历史数据
5. **最小侵入**: 持久化逻辑与业务逻辑解耦

### 📊 性能要求

| 指标 | 目标值 | 说明 |
|------|--------|------|
| **持久化延迟** | <2ms | 异步写入，不阻塞主流程 |
| **批量写入** | 500条/批 | 减少数据库压力 |
| **查询响应** | <50ms | 包含索引优化 |
| **数据丢失率** | <0.001% | 多层降级保护 |
| **吞吐量** | 5000+ TPS | 支持高并发 |

---

## 功能节点分类

根据 MCP Router V3 的架构，持久化功能节点分为以下几类：

```
持久化功能节点
├── 核心节点（必须实现）
│   ├── 路由日志持久化
│   ├── 健康检查记录持久化
│   ├── SSE会话管理持久化
│   └── 服务器注册信息持久化
│
├── 辅助节点（增强功能）
│   ├── 连接池统计持久化
│   ├── 负载均衡决策持久化
│   ├── 熔断器状态持久化
│   └── 工具调用记录持久化
│
└── 性能节点（监控分析）
    ├── 性能指标持久化
    ├── 业务指标持久化
    ├── 错误日志持久化
    └── 审计日志持久化
```

---

## 核心持久化节点

### 1. 路由日志持久化

#### 📍 触发点
- **位置**: `McpRouterService.routeRequest()` / `McpRouterService.smartRoute()`
- **时机**: 每次路由请求开始和结束
- **频率**: 极高（每个请求）

#### 🎯 持久化内容

```java
// 数据表: routing_logs
{
  // 请求标识
  "request_id": "req_20251030_123456_abc123",
  "trace_id": "trace_xyz789",
  "parent_id": null,
  
  // 路由信息
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "server_name": "mcp-server-v3",
  "load_balance_strategy": "WEIGHTED_ROUND_ROBIN",
  
  // 请求信息
  "method": "POST",
  "path": "/mcp/router/route/mcp-server-v3",
  "mcp_method": "tools/call",
  "tool_name": "getPersonById",
  "request_body": "{\"params\":{\"name\":\"getPersonById\",\"arguments\":{\"id\":1}}}",
  "request_size": 256,
  
  // 响应信息
  "response_status": 200,
  "response_body": "{\"result\":{\"id\":1,\"name\":\"John\"}}",
  "response_size": 512,
  "is_success": true,
  
  // 时间信息
  "start_time": "2025-10-30 12:34:56.123",
  "end_time": "2025-10-30 12:34:56.178",
  "duration": 55,               // 总耗时(ms)
  "queue_time": 2,              // 排队时间
  "connect_time": 8,            // 连接时间
  "process_time": 45,           // 处理时间
  
  // 客户端信息
  "client_id": "client_001",
  "client_ip": "192.168.1.50",
  "user_agent": "MCP-Client/1.0",
  
  // 状态标识
  "is_cached": false,
  "is_retry": false,
  "retry_count": 0,
  "error_code": null,
  "error_message": null
}
```

#### 🔧 实现策略

**非阻塞异步写入**:
```java
// 在 McpRouterService 中
public Mono<McpMessage> routeRequest(String serviceName, McpMessage message, Duration timeout) {
    String requestId = generateRequestId();
    long startTime = System.currentTimeMillis();
    
    // 创建路由日志记录
    RoutingLog routingLog = RoutingLog.builder()
        .requestId(requestId)
        .serviceName(serviceName)
        .mcpMethod(message.getMethod())
        .toolName(extractToolName(message))
        .startTime(new Date(startTime))
        .build();
    
    return discoverHealthyInstances(serviceName)
        .flatMap(candidates -> {
            // ... 路由逻辑 ...
            return routeToServerWithMonitoring(selectedServer, message, timeout)
                .doOnSuccess(response -> {
                    // 🔥 异步持久化成功日志（不阻塞）
                    routingLog.setEndTime(new Date());
                    routingLog.setDuration((int)(System.currentTimeMillis() - startTime));
                    routingLog.setIsSuccess(true);
                    routingLog.setResponseBody(response.toString());
                    
                    persistRoutingLog(routingLog)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(
                            result -> log.debug("✅ Routing log persisted: {}", requestId),
                            error -> log.warn("⚠️ Failed to persist routing log: {}", error.getMessage())
                        );
                })
                .doOnError(error -> {
                    // 🔥 异步持久化失败日志（不阻塞）
                    routingLog.setEndTime(new Date());
                    routingLog.setDuration((int)(System.currentTimeMillis() - startTime));
                    routingLog.setIsSuccess(false);
                    routingLog.setErrorMessage(error.getMessage());
                    
                    persistRoutingLog(routingLog)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
                });
        });
}

// 异步持久化方法
private Mono<Void> persistRoutingLog(RoutingLog log) {
    return Mono.fromRunnable(() -> {
        try {
            routingLogMapper.insert(log);
        } catch (Exception e) {
            // 降级：写入本地日志文件
            fallbackLogger.warn("DB insert failed, writing to file: {}", log);
        }
    }).then();
}
```

**批量写入优化**:
```java
// 使用 Sinks 缓冲区
private final Sinks.Many<RoutingLog> logBuffer = Sinks.many().multicast().onBackpressureBuffer();

@PostConstruct
public void initBatchWriter() {
    logBuffer.asFlux()
        .buffer(Duration.ofSeconds(2), 500)  // 2秒或500条触发
        .flatMap(logs -> Mono.fromRunnable(() -> {
            if (!logs.isEmpty()) {
                routingLogMapper.batchInsert(logs);
                log.info("✅ Batch persisted {} routing logs", logs.size());
            }
        }).subscribeOn(Schedulers.boundedElastic()))
        .subscribe();
}

private Mono<Void> persistRoutingLog(RoutingLog log) {
    return Mono.fromRunnable(() -> logBuffer.tryEmitNext(log));
}
```

#### 📊 分区策略

```sql
-- 按天分区，自动归档
CREATE TABLE routing_logs_20251030 PARTITION OF routing_logs
  FOR VALUES FROM ('2025-10-30 00:00:00') TO ('2025-10-31 00:00:00');

-- 自动创建下一天分区（定时任务）
-- 自动归档30天前数据到历史表
```

---

### 2. 健康检查记录持久化

#### 📍 触发点
- **位置**: `HealthCheckService.performHealthCheck()`
- **时机**: 每次健康检查完成后
- **频率**: 中等（每15-60秒一次）

#### 🎯 持久化内容

```java
// 数据表: health_check_records
{
  "server_id": 123,
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "check_type": "COMBINED",           // NACOS / MCP / COMBINED
  "check_result": "SUCCESS",          // SUCCESS / FAILURE / TIMEOUT / ERROR
  "check_time": "2025-10-30 12:34:56",
  "response_time": 15,                // 响应时间(ms)
  "check_details": {
    "nacos_healthy": true,
    "mcp_reachable": true,
    "tool_count": 5,
    "memory_usage": "512MB",
    "cpu_usage": "23%"
  },
  "error_message": null,
  "consecutive_failures": 0
}
```

#### 🔧 实现策略

**延时批量写入** (健康检查频率较低，可以适度延迟):
```java
// 在 HealthCheckService 中
public Mono<HealthStatus> performHealthCheck(McpServerInfo server) {
    HealthCheckRecord record = new HealthCheckRecord();
    record.setServerKey(server.getServerKey());
    record.setCheckTime(new Date());
    
    return checkNacosHealth(server)
        .flatMap(nacosHealthy -> checkMcpHealth(server)
            .map(mcpHealthy -> {
                record.setCheckResult(nacosHealthy && mcpHealthy ? "SUCCESS" : "FAILURE");
                record.setResponseTime((int)(System.currentTimeMillis() - startTime));
                
                // 🔥 异步批量持久化
                healthCheckBuffer.tryEmitNext(record);
                
                return buildHealthStatus(nacosHealthy, mcpHealthy);
            }));
}

// 批量写入（每5秒或100条）
@PostConstruct
public void initHealthCheckBatchWriter() {
    healthCheckBuffer.asFlux()
        .buffer(Duration.ofSeconds(5), 100)
        .flatMap(records -> persistHealthCheckBatch(records))
        .subscribe();
}
```

**采样策略** (减少存储压力):
```java
// 只持久化关键事件
if (record.getCheckResult().equals("FAILURE") || 
    record.getConsecutiveFailures() > 0 ||
    record.getResponseTime() > 1000 ||
    shouldSample(0.1)) {  // 10%采样率
    healthCheckBuffer.tryEmitNext(record);
}
```

---

### 3. SSE会话管理持久化

#### 📍 触发点
- **位置**: `McpSseTransportProvider.connect()` / `disconnect()`
- **时机**: 会话建立、活跃、断开
- **频率**: 中等

#### 🎯 持久化内容

```java
// 数据表: sse_session_records
{
  "session_id": "6d0df1d4-cd4c-4df0-b6c2-989de3c52d32",
  "client_id": "test-client-001",
  "status": "CONNECTED",              // CONNECTING / CONNECTED / DISCONNECTED / ERROR / TIMEOUT
  "created_time": "2025-10-30 12:00:00",
  "last_active_time": "2025-10-30 12:30:00",
  "disconnected_time": null,
  "session_duration": 1800,           // 秒
  "message_count": 150,
  "error_count": 2,
  "bytes_sent": 1048576,
  "bytes_received": 524288,
  "reconnect_count": 1,
  "client_ip": "192.168.1.50",
  "metadata": {
    "version": "1.0",
    "platform": "web"
  }
}
```

#### 🔧 实现策略

**状态变更时持久化**:
```java
// 在 McpSseTransportProvider 中
public Flux<ServerSentEvent<String>> connect(String clientId, Map<String, String> metadata) {
    String sessionId = UUID.randomUUID().toString();
    
    return Flux.create(emitter -> {
        SseSession session = createSession(sessionId, clientId, metadata);
        activeSessions.put(sessionId, session);
        
        // 🔥 持久化会话创建事件
        persistSseSessionEvent(session, "CONNECTED")
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        
        emitter.onDispose(() -> {
            session.setStatus(SessionStatus.DISCONNECTED);
            session.setDisconnectedTime(LocalDateTime.now());
            
            // 🔥 持久化会话断开事件
            persistSseSessionEvent(session, "DISCONNECTED")
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
            
            activeSessions.remove(sessionId);
        });
    });
}

// 定期更新活跃会话状态（每30秒）
@Scheduled(fixedDelay = 30000)
public void updateActiveSessionStats() {
    activeSessions.values().forEach(session -> {
        SseSessionRecord record = toRecord(session);
        sseSessionBuffer.tryEmitNext(record);
    });
}
```

---

### 4. 服务器注册信息持久化

#### 📍 触发点
- **位置**: `McpServerRegistry.registerServer()` / `deregisterServer()`
- **时机**: 服务注册、注销、状态变更
- **频率**: 低

#### 🎯 持久化内容

```java
// 数据表: mcp_servers
{
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "server_name": "mcp-server-v3",
  "service_group": "mcp-server",
  "host": "192.168.1.100",
  "port": 8063,
  "sse_endpoint": "/sse",
  "message_endpoint": "/mcp/message",
  "healthy": true,
  "enabled": true,
  "weight": 100,
  "metadata": {
    "version": "1.0.0",
    "tools": ["getPersonById", "getAllPersons"],
    "capabilities": ["tool_call", "resource"]
  },
  "registered_time": "2025-10-30 12:00:00",
  "last_heartbeat": "2025-10-30 12:35:00",
  "heartbeat_interval": 30
}
```

#### 🔧 实现策略

**同步持久化** (注册操作频率低，可以同步):
```java
// 在 McpServerRegistry 中
public Mono<Void> registerServer(McpServerInfo serverInfo) {
    return Mono.fromRunnable(() -> {
        // 先写数据库
        mcpServerMapper.insertOrUpdate(serverInfo);
        log.info("✅ Server registered to DB: {}", serverInfo.getServerKey());
    })
    .subscribeOn(Schedulers.boundedElastic())
    .then(Mono.fromRunnable(() -> {
        // 再更新内存缓存
        serverCache.put(serverInfo.getServerKey(), serverInfo);
    }))
    .onErrorResume(error -> {
        log.error("❌ Failed to register server to DB: {}", error.getMessage());
        // 降级：仅更新缓存
        serverCache.put(serverInfo.getServerKey(), serverInfo);
        return Mono.empty();
    });
}
```

---

## 辅助持久化节点

### 5. 连接池统计持久化

#### 📍 触发点
- **位置**: `McpClientManager` 连接池管理
- **时机**: 定期采样（每60秒）
- **频率**: 低

#### 🎯 持久化内容

```java
// 数据表: connection_pool_stats
{
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "sample_time": "2025-10-30 12:35:00",
  "total_connections": 10,
  "active_connections": 3,
  "idle_connections": 7,
  "pending_requests": 2,
  "pool_usage_rate": 30.0,           // 使用率%
  "avg_wait_time": 5,                // 平均等待时间(ms)
  "max_wait_time": 25,
  "connection_timeout_count": 0,
  "connection_error_count": 1
}
```

#### 🔧 实现策略

```java
@Scheduled(fixedDelay = 60000)  // 每60秒
public void collectConnectionPoolStats() {
    mcpClientManager.getAllServerKeys().forEach(serverKey -> {
        ConnectionPoolStats stats = mcpClientManager.getPoolStatsForServer(serverKey);
        
        connectionPoolStatsBuffer.tryEmitNext(stats);
    });
}
```

---

### 6. 负载均衡决策持久化

#### 📍 触发点
- **位置**: `LoadBalancer.selectServer()`
- **时机**: 每次负载均衡选择
- **频率**: 高（但可采样）

#### 🎯 持久化内容

```java
// 数据表: load_balance_decisions
{
  "decision_time": "2025-10-30 12:34:56",
  "service_name": "mcp-server-v3",
  "strategy": "WEIGHTED_ROUND_ROBIN",
  "candidate_count": 3,
  "candidates": [
    {
      "server_key": "server-1",
      "weight": 100,
      "score": 85.5,
      "active_connections": 3
    }
  ],
  "selected_server": "server-1",
  "selection_reason": "highest_score",
  "decision_duration": 2            // 决策耗时(ms)
}
```

#### 🔧 实现策略

**采样持久化** (1%采样率):
```java
public McpServerInfo selectServer(List<McpServerInfo> candidates) {
    McpServerInfo selected = weightedRoundRobin(candidates);
    
    // 1%采样
    if (shouldSample(0.01)) {
        LoadBalanceDecision decision = buildDecision(candidates, selected);
        lbDecisionBuffer.tryEmitNext(decision);
    }
    
    return selected;
}
```

---

### 7. 熔断器状态持久化

#### 📍 触发点
- **位置**: `CircuitBreakerService` 状态变更
- **时机**: CLOSED -> OPEN, OPEN -> HALF_OPEN, HALF_OPEN -> CLOSED
- **频率**: 极低

#### 🎯 持久化内容

```java
// 数据表: circuit_breaker_states
{
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "state": "OPEN",                  // CLOSED / OPEN / HALF_OPEN
  "previous_state": "CLOSED",
  "state_changed_time": "2025-10-30 12:34:56",
  "failure_count": 5,
  "failure_rate": 50.0,             // 失败率%
  "trigger_reason": "FAILURE_THRESHOLD_EXCEEDED",
  "reset_timeout": 60,              // 重置超时(秒)
  "half_open_success_count": 0,
  "metrics": {
    "total_calls": 10,
    "failed_calls": 5,
    "slow_calls": 2
  }
}
```

#### 🔧 实现策略

**实时持久化** (状态变更立即持久化):
```java
private void changeState(CircuitBreakerState newState) {
    CircuitBreakerState oldState = this.state;
    this.state = newState;
    
    // 🔥 立即持久化状态变更（同步）
    CircuitBreakerStateRecord record = CircuitBreakerStateRecord.builder()
        .serverKey(serverKey)
        .state(newState.name())
        .previousState(oldState.name())
        .stateChangedTime(new Date())
        .failureCount(failureCount.get())
        .build();
    
    Mono.fromRunnable(() -> circuitBreakerMapper.insert(record))
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> log.error("Failed to persist circuit breaker state: {}", e.getMessage()))
        .subscribe();
}
```

---

### 8. 工具调用记录持久化

#### 📍 触发点
- **位置**: `McpClientManager.callTool()`
- **时机**: 每次工具调用
- **频率**: 高

#### 🎯 持久化内容

```java
// 数据表: tool_call_records
{
  "call_id": "call_20251030_123456",
  "request_id": "req_20251030_123456_abc123",  // 关联路由日志
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "tool_name": "getPersonById",
  "arguments": {
    "id": 1
  },
  "result": {
    "id": 1,
    "name": "John Doe",
    "age": 30
  },
  "is_success": true,
  "error_message": null,
  "start_time": "2025-10-30 12:34:56.123",
  "end_time": "2025-10-30 12:34:56.178",
  "duration": 55,
  "retry_count": 0
}
```

#### 🔧 实现策略

**批量写入**:
```java
private Mono<ToolCallResult> callToolWithPersistence(String toolName, Map<String, Object> args) {
    ToolCallRecord record = new ToolCallRecord();
    record.setToolName(toolName);
    record.setArguments(args);
    record.setStartTime(new Date());
    
    return mcpClient.callTool(toolName, args)
        .doOnSuccess(result -> {
            record.setEndTime(new Date());
            record.setIsSuccess(true);
            record.setResult(result);
            
            // 🔥 批量持久化
            toolCallBuffer.tryEmitNext(record);
        })
        .doOnError(error -> {
            record.setEndTime(new Date());
            record.setIsSuccess(false);
            record.setErrorMessage(error.getMessage());
            
            toolCallBuffer.tryEmitNext(record);
        });
}
```

---

## 性能持久化节点

### 9. 性能指标持久化

#### 📍 触发点
- **位置**: 各关键业务节点
- **时机**: 定期聚合（每分钟）
- **频率**: 低

#### 🎯 持久化内容

```java
// 数据表: performance_metrics
{
  "metric_time": "2025-10-30 12:35:00",
  "time_window": "1min",            // 1min / 5min / 15min / 1hour
  
  // 路由指标
  "total_requests": 1200,
  "successful_requests": 1150,
  "failed_requests": 50,
  "avg_response_time": 45.5,
  "p50_response_time": 35,
  "p95_response_time": 120,
  "p99_response_time": 250,
  "max_response_time": 500,
  
  // 吞吐量指标
  "requests_per_second": 20.0,
  "bytes_in_per_second": 20480,
  "bytes_out_per_second": 40960,
  
  // 错误率
  "error_rate": 4.17,               // %
  "timeout_rate": 0.83,
  
  // 资源指标
  "cpu_usage": 45.5,
  "memory_usage": 1024,             // MB
  "gc_count": 5,
  "gc_time": 150                    // ms
}
```

#### 🔧 实现策略

**定时聚合持久化**:
```java
@Scheduled(fixedDelay = 60000)  // 每分钟
public void aggregateAndPersistMetrics() {
    PerformanceMetrics metrics = metricsCollector.collectAndAggregate();
    
    Mono.fromRunnable(() -> performanceMetricsMapper.insert(metrics))
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
}
```

---

### 10. 错误日志持久化

#### 📍 触发点
- **位置**: 全局异常处理器
- **时机**: 发生错误时
- **频率**: 中等

#### 🎯 持久化内容

```java
// 数据表: error_logs
{
  "error_id": "err_20251030_123456",
  "error_time": "2025-10-30 12:34:56",
  "error_level": "ERROR",           // WARN / ERROR / FATAL
  "error_type": "CONNECTION_TIMEOUT",
  "error_message": "Connection to server timed out",
  "stack_trace": "...",
  "request_id": "req_20251030_123456_abc123",
  "server_key": "mcp-server-v3:192.168.1.100:8063",
  "affected_operation": "tools/call",
  "context": {
    "tool_name": "getPersonById",
    "retry_count": 3
  }
}
```

#### 🔧 实现策略

**异步写入**:
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleException(Exception e) {
        ErrorLog errorLog = buildErrorLog(e);
        
        // 🔥 异步持久化错误日志
        Mono.fromRunnable(() -> errorLogMapper.insert(errorLog))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
        
        return Mono.just(ResponseEntity.status(500).body(toErrorResponse(e)));
    }
}
```

---

## 实施优先级

### 🥇 P0 - 必须实现（第一阶段）

| 节点 | 理由 | 预计工作量 |
|------|------|-----------|
| **路由日志持久化** | 核心业务，诊断必备 | 3人日 |
| **健康检查记录持久化** | 监控基础 | 2人日 |
| **服务器注册信息持久化** | 服务管理基础 | 2人日 |
| **错误日志持久化** | 故障排查必备 | 1人日 |

**总计**: 8人日

### 🥈 P1 - 重要增强（第二阶段）

| 节点 | 理由 | 预计工作量 |
|------|------|-----------|
| **SSE会话管理持久化** | 连接管理增强 | 2人日 |
| **工具调用记录持久化** | 业务分析 | 2人日 |
| **性能指标持久化** | 性能监控 | 3人日 |

**总计**: 7人日

### 🥉 P2 - 优化完善（第三阶段）

| 节点 | 理由 | 预计工作量 |
|------|------|-----------|
| **连接池统计持久化** | 资源优化 | 1人日 |
| **负载均衡决策持久化** | 算法优化 | 2人日 |
| **熔断器状态持久化** | 稳定性分析 | 1人日 |

**总计**: 4人日

---

## 技术方案

### 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                         │
│          (WebFlux Reactive - Non-blocking)                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                            │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│   │ RouterService│  │HealthService │  │ ClientManager│    │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│          │                  │                  │             │
│          ▼                  ▼                  ▼             │
│   ┌─────────────────────────────────────────────────┐      │
│   │      Persistence Event Publisher                │      │
│   │      (Sinks.Many - 缓冲区)                      │      │
│   └──────────────────┬──────────────────────────────┘      │
└────────────────────────┼────────────────────────────────────┘
                         │ 异步、批量
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Persistence Layer (Async)                      │
│   ┌────────────────────────────────────────────┐           │
│   │    Batch Writer (每2秒或500条)              │           │
│   │    - 批量写入                                │           │
│   │    - 自动降级                                │           │
│   │    - 失败重试                                │           │
│   └────────────────┬───────────────────────────┘           │
│                    │                                         │
│                    ▼                                         │
│   ┌────────────────────────────────────────────┐           │
│   │         MyBatis Mapper                     │           │
│   │    (JDBC - Blocking, 运行在独立线程池)      │           │
│   └────────────────┬───────────────────────────┘           │
└────────────────────┼────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                   MySQL Database                            │
│   - 分区表 (按天/月)                                         │
│   - 索引优化                                                 │
│   - 自动归档                                                 │
└─────────────────────────────────────────────────────────────┘
```

### 🔧 核心组件

#### 1. Persistence Event Publisher

```java
@Component
public class PersistenceEventPublisher {
    
    // 各类型事件的缓冲区
    private final Sinks.Many<RoutingLog> routingLogSink;
    private final Sinks.Many<HealthCheckRecord> healthCheckSink;
    private final Sinks.Many<SseSessionRecord> sseSessionSink;
    // ... 其他缓冲区
    
    @PostConstruct
    public void initBatchWriters() {
        // 路由日志批量写入器 (2秒或500条)
        routingLogSink.asFlux()
            .buffer(Duration.ofSeconds(2), 500)
            .flatMap(this::batchPersistRoutingLogs)
            .subscribe();
        
        // 健康检查批量写入器 (5秒或100条)
        healthCheckSink.asFlux()
            .buffer(Duration.ofSeconds(5), 100)
            .flatMap(this::batchPersistHealthChecks)
            .subscribe();
        
        // ... 其他批量写入器
    }
    
    // 发布路由日志事件
    public void publishRoutingLog(RoutingLog log) {
        routingLogSink.tryEmitNext(log);
    }
    
    // 批量持久化路由日志
    private Mono<Void> batchPersistRoutingLogs(List<RoutingLog> logs) {
        return Mono.fromRunnable(() -> {
            try {
                routingLogMapper.batchInsert(logs);
                log.info("✅ Batch persisted {} routing logs", logs.size());
            } catch (Exception e) {
                log.error("❌ Failed to persist routing logs", e);
                // 降级处理
                fallbackToDisk(logs);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }
}
```

#### 2. Fallback Strategy (降级策略)

```java
@Component
public class PersistenceFallbackHandler {
    
    private final DiskBasedQueue<RoutingLog> diskQueue;
    
    // 降级到磁盘
    public void fallbackToDisk(List<?> records) {
        diskQueue.enqueueAll(records);
        log.warn("⚠️ Fallback: {} records written to disk", records.size());
    }
    
    // 定期重试磁盘中的数据
    @Scheduled(fixedDelay = 60000)
    public void retryDiskRecords() {
        List<RoutingLog> records = diskQueue.dequeueUpTo(100);
        if (!records.isEmpty()) {
            routingLogMapper.batchInsert(records);
            log.info("✅ Retried {} records from disk", records.size());
        }
    }
}
```

#### 3. Partition Manager (分区管理)

```java
@Component
public class PartitionManager {
    
    // 定期创建未来分区（每天凌晨2点）
    @Scheduled(cron = "0 0 2 * * ?")
    public void createFuturePartitions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        
        partitionMapper.createRoutingLogPartition(tomorrow);
        partitionMapper.createHealthCheckPartition(tomorrow);
        
        log.info("✅ Created partitions for {}", tomorrow);
    }
    
    // 定期归档旧分区（每周日凌晨3点）
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void archiveOldPartitions() {
        LocalDate archiveBefore = LocalDate.now().minusDays(30);
        
        partitionMapper.archiveRoutingLogPartition(archiveBefore);
        partitionMapper.archiveHealthCheckPartition(archiveBefore);
        
        log.info("✅ Archived partitions before {}", archiveBefore);
    }
}
```

---

## 数据流设计

### 📊 写入流程

```
业务请求
   │
   ├─→ 同步：执行核心业务逻辑
   │        (路由、健康检查、连接管理等)
   │        ↓
   │     返回响应给客户端
   │
   └─→ 异步：构造持久化事件
            ↓
         发布到 Sinks 缓冲区
            ↓
         批量聚合 (2-5秒 或 100-500条)
            ↓
         批量写入数据库 (独立线程池)
            ↓
         成功 ✓ / 失败 → 降级到磁盘
```

### 🔍 查询流程

```
查询请求
   ↓
走索引查询
   ↓
分区裁剪 (如果有时间范围)
   ↓
返回结果 (<50ms)
```

### 📈 监控流程

```
定时任务 (每分钟)
   ↓
聚合内存指标
   ↓
持久化到 performance_metrics 表
   ↓
对外提供监控 API
```

---

## 风险评估

### ⚠️ 主要风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| **数据库连接池耗尽** | 高 | 中 | 独立线程池、连接池监控、降级策略 |
| **批量写入失败** | 中 | 低 | 磁盘降级、自动重试 |
| **分区管理失败** | 中 | 低 | 定时任务监控、告警 |
| **大表查询慢** | 中 | 中 | 索引优化、分区裁剪、查询限制 |
| **磁盘空间不足** | 高 | 中 | 自动归档、告警 |

### 🛡️ 降级策略

1. **L1 降级**: 数据库写入失败 → 写入本地磁盘队列
2. **L2 降级**: 磁盘队列满 → 丢弃非关键日志（保留错误日志）
3. **L3 降级**: 极端情况 → 仅输出到标准日志文件

---

## 总结

### ✅ 设计亮点

1. **完全非阻塞**: 所有持久化操作异步执行，不影响主流程
2. **批量优化**: 减少数据库压力，提升吞吐量
3. **多层降级**: 数据库故障不影响系统可用性
4. **分区策略**: 历史数据自动归档，查询性能稳定
5. **灵活采样**: 高频操作采样持久化，平衡性能与存储

### 📊 预期收益

- ✅ **故障诊断**: 完整的请求链路追踪
- ✅ **性能分析**: 细粒度的性能指标
- ✅ **业务洞察**: 工具调用统计、用户行为分析
- ✅ **容量规划**: 历史数据支撑扩容决策
- ✅ **合规审计**: 完整的操作审计日志

### 🚀 下一步

1. **评审通过** → 进入编码阶段
2. **P0功能** → 优先实现核心持久化节点
3. **压力测试** → 验证性能指标
4. **逐步迭代** → P1、P2功能增强

---

**文档版本**: v1.0  
**创建日期**: 2025-10-30  
**作者**: MCP Router V3 Team  
**状态**: 待评审 ✋

