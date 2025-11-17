# SSE连接修复总结

## 问题描述

MCP Inspector 和 mcp-router-v3 通过 `http://127.0.0.1:8052/sse/mcp-server-v6` 建立 SSE 连接后立即断开。

## 根本原因分析

1. **控制器返回类型不正确**: 返回 `Flux<String>` 而不是 `Flux<ServerSentEvent<String>>`，导致 Spring 无法正确序列化 SSE 事件
2. **心跳订阅管理不当**: 心跳订阅没有被正确保存和清理，可能被垃圾回收导致连接断开
3. **连接管理问题**: `Flux.create` 的实现可能导致连接立即完成

## 修复内容

### 1. 修复控制器返回类型 ✅

**文件**: `McpSseController.java`

- **问题**: 返回 `Flux<String>`，无法正确序列化为SSE格式
- **修复**: 改为返回 `Flux<ServerSentEvent<String>>`，确保Spring正确序列化SSE事件

```java
@GetMapping(value = "/mcp-server-v6", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> connectInspector(...) {
    // 返回 ServerSentEvent 而不是 String
    return sseTransportProvider.connect(effectiveClientId, metadataMap)
        .doOnSubscribe(...)
        .doOnComplete(...)
        .doOnError(...)
        .doOnCancel(...);
}
```

### 2. 修复心跳机制 ✅

**文件**: `McpSseTransportProvider.java`

- **问题**: 心跳订阅没有正确管理，可能被垃圾回收导致连接断开
- **修复**: 
  - 保存心跳订阅到 `Disposable`，在连接关闭时正确清理
  - 改进心跳发送逻辑，检查会话状态和存在性
  - 心跳间隔：30秒
  - 超时时间：10分钟（600秒）

```java
// 启动心跳，保存订阅
Disposable heartbeatSubscription = startHeartbeat(session);

// 处理连接关闭
emitter.onDispose(() -> {
    // 取消心跳订阅
    if (heartbeatSubscription != null && !heartbeatSubscription.isDisposed()) {
        heartbeatSubscription.dispose();
    }
    // ... 其他清理逻辑
});
```

### 3. 修复连接管理 ✅

**文件**: `McpSseTransportProvider.java`

- **问题**: `Flux.create` 的实现可能导致连接立即完成
- **修复**:
  - 正确管理 sink 订阅和心跳订阅
  - 在连接关闭时清理所有订阅
  - 改进错误处理，防止错误导致连接断开

```java
return Flux.create(emitter -> {
    // 创建Sinks.Many用于消息传输
    Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
    
    // 创建SSE会话
    SseSession session = createSession(sessionId, clientId, metadata, sink);
    activeSessions.put(sessionId, session);
    
    // 订阅sink的消息并发送给客户端，保存订阅以便清理
    Disposable sinkSubscription = sink.asFlux()
        .subscribe(...);
    
    // 启动心跳，保存订阅
    Disposable heartbeatSubscription = startHeartbeat(session);
    
    // 处理连接关闭
    emitter.onDispose(() -> {
        // 清理所有订阅
        if (heartbeatSubscription != null && !heartbeatSubscription.isDisposed()) {
            heartbeatSubscription.dispose();
        }
        if (sinkSubscription != null && !sinkSubscription.isDisposed()) {
            sinkSubscription.dispose();
        }
        // ... 其他清理逻辑
    });
});
```

### 4. 路由配置中的心跳 ✅

**文件**: `McpRouterServerConfig.java`

- **问题**: 路由配置中的心跳实现也需要正确管理
- **修复**: 使用 `Flux.interval` 创建心跳流，并正确合并到事件流中

```java
// 创建心跳流保持连接
Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
    .map(tick -> ServerSentEvent.<String>builder()
        .comment("heartbeat")
        .build())
    .doOnNext(tick -> {
        sessionService.touch(sessionId);
        log.debug("💓 SSE heartbeat: sessionId={}", sessionId);
    });

// 合并 endpoint 消息、sink 的消息流和心跳流
Flux<ServerSentEvent<String>> eventFlux = Flux.concat(
    Flux.just(endpointEvent),
    Flux.merge(
        sink.asFlux(),  // 通过 sink 发送的响应消息
        heartbeatFlux   // 心跳流
    )
);
```

## 配置参数

- **会话超时时间**: `DEFAULT_TIMEOUT_MS = 600_000` (10分钟)
- **心跳间隔**: `HEARTBEAT_INTERVAL_MS = 30_000` (30秒)

## 端点说明

### 主要SSE端点

1. **`/sse/mcp-server-v6`** - MCP Inspector 兼容端点
   - 由 `McpRouterServerConfig.handleSseWithServiceName` 处理（函数式路由优先级更高）
   - 或由 `McpSseController.connectInspector` 处理（如果函数式路由未匹配）

2. **`/sse/{serviceName}`** - 路径参数方式
   - 由 `McpRouterServerConfig.handleSseWithServiceName` 处理

3. **`/sse?serviceName=xxx`** - 查询参数方式
   - 由 `McpRouterServerConfig.handleSseWithQueryParam` 处理

4. **`/sse/connect?clientId=xxx`** - 直接连接端点
   - 由 `McpSseController.connect` 处理

## 测试方法

### 方法1: Python测试脚本（推荐）

```bash
# 完整测试（10分钟）
cd mcp-router-v3
python3 test-sse-connection.py

# 快速测试（60秒）
python3 test-sse-connection.py --quick

# 自定义测试时间（5分钟）
python3 test-sse-connection.py --duration 300

# 指定服务器URL
python3 test-sse-connection.py --url http://127.0.0.1:8052
```

### 方法2: Shell脚本测试

```bash
# 完整测试（10分钟）
cd mcp-router-v3
./test-sse-connection.sh

# 快速测试（30秒）
./quick-test-sse.sh

# 自定义测试时间（5分钟）
./test-sse-connection.sh http://127.0.0.1:8052 300
```

### 方法3: 使用curl手动测试

```bash
# 建立SSE连接
curl -N -H "Accept: text/event-stream" \
     -H "Cache-Control: no-cache" \
     "http://127.0.0.1:8052/sse/mcp-server-v6"
```

### 方法4: 使用MCP Inspector

1. 启动 mcp-router-v3 服务
2. 打开 MCP Inspector
3. 连接到 `http://127.0.0.1:8052/sse/mcp-server-v6`
4. 观察连接是否保持10分钟以上

## 预期结果

1. ✅ 连接成功建立，收到 `connected` 或 `endpoint` 事件
2. ✅ 每30秒收到一次 `heartbeat` 事件（注释形式或事件形式）
3. ✅ 连接保持至少10分钟不断开
4. ✅ 10分钟内没有主动断开连接
5. ✅ 日志中显示心跳正常发送

## 验证要点

- [ ] 连接建立后立即收到 `connected` 或 `endpoint` 事件
- [ ] 每30秒收到一次心跳事件（可能是注释形式 `: heartbeat` 或事件形式 `event: heartbeat`）
- [ ] 连接保持10分钟以上
- [ ] 没有出现连接错误或异常断开
- [ ] 日志中显示心跳正常发送

## 故障排查

### 如果连接仍然立即断开

1. **检查服务器日志**
   ```bash
   # 查看SSE相关日志
   tail -f logs/application.log | grep -i sse
   ```

2. **检查网络代理/网关超时**
   - Nginx: 检查 `proxy_read_timeout` 配置（应 >= 600秒）
   - API Gateway: 检查响应超时设置
   - 负载均衡器: 检查空闲连接超时

3. **检查Spring WebFlux配置**
   - 确认没有设置全局响应超时
   - 确认CORS配置正确

4. **检查客户端**
   - 确认客户端没有设置连接超时
   - 确认客户端正确处理SSE事件流

### 如果心跳没有收到

1. **检查心跳订阅是否启动**
   - 查看日志中是否有 "Sent heartbeat" 或 "💓 SSE heartbeat" 消息

2. **检查会话状态**
   - 会话状态应该是 `CONNECTED` 或 `CONNECTING`
   - 如果状态是 `DISCONNECTED`，心跳会停止

3. **检查sink是否正常**
   - 查看日志中是否有 "Failed to emit heartbeat" 警告

## 相关文件

- `McpSseController.java`: SSE控制器（注解式路由）
- `McpRouterServerConfig.java`: SSE路由配置（函数式路由）
- `McpSseTransportProvider.java`: SSE传输提供者
- `SseSession.java`: SSE会话模型
- `test-sse-connection.py`: Python测试脚本
- `test-sse-connection.sh`: Shell测试脚本
- `quick-test-sse.sh`: 快速测试脚本（30秒）
- `README_SSE_TEST.md`: 测试说明文档

## 技术细节

### SSE事件格式

Spring的 `ServerSentEvent` 会序列化为标准的SSE格式：

```
event: connected
data: {"sessionId":"xxx","clientId":"xxx"}

: heartbeat

event: heartbeat
data: {"timestamp":"2024-01-01T12:00:00"}
```

### 心跳实现

心跳有两种实现方式：

1. **注释形式**（`McpRouterServerConfig`）:
   ```java
   ServerSentEvent.<String>builder()
       .comment("heartbeat")
       .build()
   ```
   序列化为：`: heartbeat\n\n`

2. **事件形式**（`McpSseTransportProvider`）:
   ```java
   ServerSentEvent.<String>builder()
       .event("heartbeat")
       .data("{\"timestamp\":\"" + LocalDateTime.now() + "\"}")
       .build()
   ```
   序列化为：`event: heartbeat\ndata: {...}\n\n`

两种方式都可以保持连接活跃。

## 总结

所有修复已完成，代码已通过编译检查。主要修复包括：

1. ✅ 修复控制器返回类型
2. ✅ 修复心跳机制和订阅管理
3. ✅ 修复连接管理和清理逻辑
4. ✅ 创建测试脚本和文档

现在可以运行测试脚本验证 SSE 连接是否能保持10分钟不断开。

