# 域名访问慢问题修复

## 问题描述

通过域名建立 SSE 连接和调用 `tools/list` 时，响应时间较长（接近 20 秒）。

## 问题分析

从日志分析发现：
1. **应用处理时间正常**：`tools/list` 的实际处理时间只有 15-18ms
2. **Redis 操作是同步阻塞的**：在 SSE 连接建立时，`registerSessionService` 和 `touch` 方法会调用 Redis，这些操作是同步的，会阻塞 SSE 连接建立的响应
3. **域名解析正常**：`/etc/hosts` 中有配置，DNS 解析很快

### 延迟来源

1. **`registerSessionService`**：调用 `sessionRepository.saveSessionMeta()`，执行多个 Redis 操作（`hsetAll`, `expire`, `sadd`, `expire`），都是同步阻塞的
2. **`touch`**：调用 `sessionRepository.updateLastActive()`，执行多个 Redis 操作（`hset`, `hset`, `expire`），都是同步阻塞的
3. **心跳中的 `touch`**：每 30 秒执行一次，也会阻塞

## 修复方案

### 异步化 Redis 操作

将 SSE 连接建立时的 Redis 操作改为异步执行，避免阻塞连接建立的响应：

1. **`registerSessionService`**：使用 `Mono.fromRunnable()` 在 `Schedulers.boundedElastic()` 上异步执行
2. **`touch`**：同样异步化，包括心跳中的 `touch` 操作

### 修改位置

1. **`handleSseWithServiceName`**：
   - `registerSessionService` 异步化
   - `touch` 异步化

2. **`handleSseWithQueryParam`**：
   - `registerSessionService` 异步化
   - `touch` 异步化

3. **心跳流**：
   - `touch` 异步化

## 修改文件

`mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

### 关键修改

```java
// 异步执行 Redis 操作，避免阻塞 SSE 连接建立
Mono.fromRunnable(() -> sessionService.registerSessionService(sessionId, serviceName))
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
                null,
                error -> log.warn("⚠️ Failed to register session service asynchronously: {}", error.getMessage())
        );

// 异步触发会话活跃，避免阻塞
Mono.fromRunnable(() -> sessionService.touch(sessionId))
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
                null,
                error -> log.warn("⚠️ Failed to touch session asynchronously: {}", error.getMessage())
        );
```

## 预期效果

- SSE 连接建立不再被 Redis 操作阻塞，响应时间从可能的几百毫秒降低到几毫秒
- `tools/list` 等操作的响应时间不再受 Redis 操作影响
- 心跳操作也不会阻塞主流程

## 注意事项

- Redis 操作失败不会影响 SSE 连接建立，只会记录警告日志
- 会话元数据可能稍微延迟写入 Redis，但不影响功能
- 如果 Redis 连接有问题，会在后台异步处理，不会阻塞请求

## 验证

重启应用后，通过域名建立 SSE 连接并调用 `tools/list`，应该能在几毫秒到几秒内返回结果（正常情况下应该在几毫秒到几百毫秒之间）。





