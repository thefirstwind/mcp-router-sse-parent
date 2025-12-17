# 阻塞操作修复 - 确保接口高效不阻塞

## 问题描述
`mcp-router-v3` 中的接口会出现阻塞，导致整个项目都不可用。

## 问题分析

### 发现的阻塞操作

1. **`sessionService.getSessionOverview()` 同步调用**
   - **位置**: `McpRouterService.createRoutingLog()`
   - **问题**: 该方法包含数据库查询（`routingLogMapper.selectBySessionId()`）和 Redis 查询（`sessionRepository.findAllSessions()`）
   - **影响**: 每次创建路由日志时都会阻塞，影响所有请求的响应时间

2. **`publishRoutingLog()` 同步执行**
   - **位置**: `McpRouterService.routeRequest()` 的 `doOnSuccess` 和 `doOnError` 回调
   - **问题**: 虽然 `publishRoutingLog` 内部使用非阻塞 Sink，但在响应式流的回调中同步执行，如果 Sink 缓冲区满可能会阻塞
   - **影响**: 影响请求响应时间

## 修复方案

### 1. 移除阻塞的 `getSessionOverview()` 调用

**文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`

**修改**:
- 移除了 `createRoutingLog()` 方法中的 `sessionService.getSessionOverview()` 同步调用
- 移除了 sessionId 推断逻辑（该逻辑依赖 `getSessionOverview()`）
- 添加注释说明：如果需要 sessionId，应该通过请求头或参数显式传递

**效果**:
- 消除了每次创建路由日志时的数据库和 Redis 查询阻塞
- 大幅提升请求响应速度

### 2. 将 `publishRoutingLog()` 改为异步执行

**文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`

**修改**:
- 将 `publishRoutingLog()` 改为使用 `Mono.fromRunnable()` 异步执行
- 使用 `subscribeOn(Schedulers.boundedElastic())` 在弹性线程池中执行
- 添加 100ms 超时保护，避免长时间阻塞
- 使用 `onErrorResume()` 确保错误不会影响主流程
- 使用 `subscribe()` 异步执行，不等待结果

**代码示例**:
```java
private void publishRoutingLog(RoutingLog routingLog) {
    // 异步执行，避免阻塞响应式流
    if (persistenceEventPublisher != null) {
        Mono.fromRunnable(() -> {
            try {
                log.debug("📝 Publishing routing log: requestId={}, isSuccess={}", 
                    routingLog.getRequestId(), routingLog.getIsSuccess());
                persistenceEventPublisher.publishRoutingLog(routingLog);
            } catch (Exception e) {
                // 持久化失败不应影响主流程
                log.warn("Failed to publish routing log", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofMillis(100)) // 100ms 超时，避免长时间阻塞
        .onErrorResume(error -> {
            log.debug("⚠️ Routing log publish timeout or error (non-blocking): {}", error.getMessage());
            return Mono.empty();
        })
        .subscribe(); // 异步执行，不等待结果
    }
}
```

**效果**:
- 路由日志发布不再阻塞响应式流
- 即使日志发布失败，也不会影响请求响应

## 修复效果

### 修复前
- 每次请求都会同步查询数据库和 Redis（通过 `getSessionOverview()`）
- 路由日志发布可能阻塞响应式流
- 接口响应时间受数据库和 Redis 性能影响
- 高并发时可能导致线程池耗尽

### 修复后
- 移除了所有阻塞的数据库和 Redis 查询
- 路由日志发布完全异步，不阻塞主流程
- 接口响应时间不再受持久化操作影响
- 高并发时性能稳定

## 性能优化建议

### 已实现的优化
1. ✅ 移除阻塞的 `getSessionOverview()` 调用
2. ✅ 异步执行 `publishRoutingLog()`
3. ✅ Nacos 查询已使用 `subscribeOn(Schedulers.boundedElastic())` 异步化
4. ✅ Nacos 查询已添加超时保护（200ms）

### 其他非阻塞操作
- `publishRoutingLog()` 内部使用 `Sinks.Many.tryEmitNext()`，这是非阻塞的
- `setResponseBody()` 和 `setErrorResponseBody()` 只是 JSON 序列化，速度很快
- HTTP 调用使用 `WebClient`（非阻塞）

## 注意事项

1. **sessionId 推断已移除**
   - 如果需要 sessionId，应该通过请求头（`sessionId`、`Session-Id`、`X-Session-Id`）或参数显式传递
   - 不再自动推断 sessionId

2. **路由日志发布是异步的**
   - 日志发布失败不会影响请求响应
   - 日志发布有 100ms 超时保护

3. **性能监控**
   - 建议监控接口响应时间，确保修复效果
   - 建议监控路由日志发布成功率

## 相关文件
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpSessionService.java`
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/service/PersistenceEventPublisher.java`

