# 会话管理问题修复

## 问题描述

1. **超时问题**：使用 mcp client 请求总是报超时
2. **SSE sink 未找到**：`McpRouterServerConfig` 报错 "No SSE sink found for sessionId, falling back to HTTP response"
3. **会话管理逻辑问题**：消息请求到达时，SSE 连接可能还没有完全建立，导致找不到对应的 sink

## 问题原因

1. **时序问题**：当消息请求（POST /mcp/message）到达时，SSE 连接（GET /sse）可能还在建立中，sink 还没有注册到 `McpSessionService`，导致找不到 sink
2. **缺少等待机制**：代码直接获取 sink，如果不存在就立即回退到 HTTP 响应，没有等待 SSE 连接建立
3. **错误信息不明确**：当找不到 sink 时，没有提供足够的调试信息（如已注册的 sessionId 列表）

## 修复方案

### 1. 添加等待机制

在 `McpSessionService` 中添加了 `waitForSseSink` 方法，用于等待 SSE sink 就绪：

```java
public Mono<Sinks.Many<ServerSentEvent<String>>> waitForSseSink(String sessionId, int maxWaitSeconds) {
    // 如果 sink 已存在，立即返回
    // 否则等待最多 maxWaitSeconds 秒，每 100ms 重试一次
}
```

### 2. 改进消息处理逻辑

在 `McpRouterServerConfig.processMcpMessage` 中，所有获取 SSE sink 的地方都改为使用等待机制：

**修复前**：
```java
Sinks.Many<ServerSentEvent<String>> sseSink = sessionService.getSseSink(sessionId);
if (sseSink != null) {
    // 发送响应
} else {
    // 回退到 HTTP 响应
}
```

**修复后**：
```java
Mono<Sinks.Many<ServerSentEvent<String>>> sseSinkMono = sessionService.waitForSseSink(sessionId, 2)
    .doOnNext(sink -> log.debug("✅ SSE sink found for sessionId={}", sessionId))
    .switchIfEmpty(Mono.defer(() -> {
        // 记录调试信息
        java.util.Set<String> allSessions = sessionService.getAllSessionIds();
        log.warn("⚠️ SSE sink not found for sessionId={} after waiting. Registered sessions: {}", 
                sessionId, allSessions);
        return Mono.empty();
    }));

return sseSinkMono
    .flatMap(sseSink -> {
        // 通过 SSE 发送响应
    })
    .switchIfEmpty(Mono.defer(() -> {
        // 回退到 HTTP 响应
    }));
```

### 3. 增强调试信息

- 添加了 `getAllSessionIds()` 方法，用于获取所有已注册的 sessionId
- 在找不到 sink 时，日志会显示所有已注册的 sessionId，帮助排查 sessionId 不匹配问题
- 提供了可能的原因说明（SSE 连接未建立、sessionId 不匹配、SSE 连接已关闭）

### 4. 统一错误处理

所有错误处理路径都统一使用 `sseSinkMono`，确保一致的等待和回退逻辑。

## 修复的文件

1. **McpSessionService.java**
   - 添加 `waitForSseSink()` 方法
   - 添加 `waitForSseSinkWithRetry()` 私有方法
   - 添加 `getAllSessionIds()` 方法

2. **McpRouterServerConfig.java**
   - `processMcpMessage()` 方法中的所有 SSE sink 获取逻辑
   - `initialize` 方法的响应处理
   - 后端服务器消息的响应处理
   - 路由逻辑的响应处理
   - 所有错误处理路径

## 使用说明

### 等待时间配置

默认等待时间为 2 秒，可以通过修改 `waitForSseSink(sessionId, 2)` 中的第二个参数来调整。

### 日志级别

- **DEBUG**：SSE sink 找到时的日志
- **WARN**：SSE sink 未找到时的日志（包含已注册的 sessionId 列表）

### 排查步骤

如果仍然出现 "No SSE sink found" 错误：

1. **检查 SSE 连接是否建立**：
   ```bash
   grep "SSE connection request" logs/application.log
   grep "Registered SSE sink" logs/application.log
   ```

2. **检查 sessionId 是否匹配**：
   - 查看日志中的 "Registered sessions" 列表
   - 确认消息请求中的 sessionId 是否在列表中

3. **检查 SSE 连接是否已关闭**：
   ```bash
   grep "SSE connection cancelled\|SSE connection completed" logs/application.log
   ```

4. **检查时序问题**：
   - 确认 SSE 连接建立和消息请求的顺序
   - 如果消息请求在 SSE 连接建立之前到达，可能需要增加等待时间

## 测试建议

1. **正常流程测试**：
   - 先建立 SSE 连接
   - 然后发送消息请求
   - 验证响应通过 SSE 正确发送

2. **时序问题测试**：
   - 同时发送 SSE 连接请求和消息请求
   - 验证等待机制是否正常工作

3. **错误场景测试**：
   - 使用不存在的 sessionId 发送消息
   - 验证错误处理和日志输出

## 注意事项

1. **等待时间**：等待时间过长可能影响响应速度，建议根据实际网络环境调整
2. **并发处理**：等待机制使用 Reactor 的异步非阻塞方式，不会阻塞其他请求
3. **资源清理**：如果 SSE 连接关闭，sink 会被自动清理，等待会超时并回退到 HTTP 响应

---

**修复日期**：2025-11-14  
**相关 Issue**：会话管理逻辑问题、超时问题








