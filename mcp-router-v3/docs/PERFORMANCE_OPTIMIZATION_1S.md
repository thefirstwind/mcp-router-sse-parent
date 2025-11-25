# 性能优化：1秒内响应

## 目标

将 SSE 连接建立到获取 `tools/list` 响应的总时间优化到 **1秒以内**。

## 优化策略

### 1. SSE Sink 等待优化

**问题**：`waitForSseSink` 使用延迟重试机制，导致等待时间过长。

**优化**：
- 立即检查 SSE sink（不延迟）
- 如果 `maxWaitSeconds=0`，立即返回（不等待）
- 超时时间设置为 0.5 秒
- 重试延迟从 100ms 缩短到 10ms

**文件**：`McpSessionService.java`

```java
// 立即检查，不延迟
Sinks.Many<ServerSentEvent<String>> sink = sessionIdToSseSink.get(sessionId);
if (sink != null) {
    return Mono.just(sink);
}
// 如果 maxWaitSeconds 为 0，立即返回空（不等待）
if (maxWaitSeconds <= 0) {
    return Mono.empty();
}
```

### 2. Redis 操作同步化

**问题**：Redis 操作使用异步执行，导致 SSE sink 注册后，Redis 操作可能还没完成。

**优化**：
- SSE 连接建立时，同步执行 `registerSessionService` 和 `touch`
- 如果同步执行失败，才异步重试
- 确保 SSE sink 注册和 Redis 操作几乎同时完成

**文件**：`McpRouterServerConfig.java`

```java
// 同步执行，确保立即完成
try {
    sessionService.registerSessionService(sessionId, serviceName);
    sessionService.touch(sessionId);
} catch (Exception e) {
    // 失败时才异步重试
    Mono.fromRunnable(() -> sessionService.registerSessionService(sessionId, serviceName))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(...);
}
```

### 3. 超时时间大幅缩短

**优化项**：

| 操作 | 原超时 | 新超时 | 文件 |
|------|--------|--------|------|
| waitForSseSink | 2秒 | 0.5秒 | `McpRouterServerConfig.java` |
| tools/list 等方法 | 3秒 | 0.5秒 | `McpRouterServerConfig.java` |
| McpClientManager list方法 | 3秒 | 0.5秒 | `McpClientManager.java` |
| Nacos查询 | 1秒 | 0.2秒 | `McpServerRegistry.java` |
| 后端服务响应 | 10秒 | 0.5秒 | `McpRouterServerConfig.java` |

### 4. 执行顺序优化

**问题**：原代码先处理 `initialize` 请求，然后等待 SSE sink，导致响应准备好但无法发送。

**优化**：
- 先等待 SSE sink 就绪（最多 0.5 秒）
- SSE sink 就绪后，再处理 `initialize` 请求
- 响应准备好后，立即通过已就绪的 SSE sink 发送

## 预期时间分解

- **SSE sink 等待**: < 0.5秒（通常立即完成，因为同步注册）
- **Nacos 查询**: < 0.2秒
- **后端服务响应**: < 0.5秒
- **Redis 操作**: 立即完成（同步）
- **总计**: < 1秒

## 风险与注意事项

1. **超时时间过短**：
   - 如果后端服务响应慢，可能导致超时
   - 如果 Nacos 查询慢，可能导致服务发现失败
   - 建议监控超时率，必要时调整

2. **同步 Redis 操作**：
   - 如果 Redis 连接慢，可能阻塞 SSE 连接建立
   - 已添加异常处理，失败时异步重试
   - 建议监控 Redis 响应时间

3. **SSE sink 注册时序**：
   - 通过同步注册确保立即可用
   - 如果注册失败，会回退到 HTTP 响应

## 验证

```bash
# 1. 重新编译
cd mcp-router-v3
mvn clean compile -DskipTests

# 2. 重启应用
./scripts/start-instances.sh restart

# 3. 测试响应时间
time curl -N --resolve mcp-bridge.local:80:127.0.0.1 http://mcp-bridge.local/sse/mcp-server-v6
```

在另一个终端发送 `initialize` 和 `tools/list` 请求，总时间应该在 1 秒以内。

## 如果仍然慢

如果优化后仍然超过 1 秒，可能需要：

1. **检查后端服务性能**：后端 `mcp-server-v6` 的响应时间
2. **检查网络延迟**：本地网络和 Redis/Nacos 的连接延迟
3. **使用缓存**：缓存 `tools/list` 等列表方法的响应
4. **预加载**：在 SSE 连接建立时预加载 `tools/list` 等数据


