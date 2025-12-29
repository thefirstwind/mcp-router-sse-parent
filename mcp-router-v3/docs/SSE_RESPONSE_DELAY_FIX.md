# SSE 响应延迟问题修复

## 问题描述

通过域名访问 `http://mcp-bridge.local/sse/mcp-server-v6`，建立 SSE 连接后，从收到 `endpoint` 事件到获取 `tools/list` 响应之间的时间非常慢，无法容忍。

## 问题分析

从测试和代码分析发现：

1. **SSE 连接建立正常**：能收到 `endpoint` 事件
2. **后续响应缺失**：`initialize` 和 `tools/list` 响应没有通过 SSE 返回
3. **可能原因**：
   - `waitForSseSink` 等待时间太短（2秒），SSE 连接可能还没完全建立
   - 客户端发送请求时，SSE sink 可能还没注册完成
   - 时序问题：请求到达时，SSE sink 还没准备好

## 修复方案

### 1. 增加 waitForSseSink 等待时间

将 `waitForSseSink` 的等待时间从 2 秒增加到 5 秒，确保 SSE sink 有足够时间注册：

**文件**：`mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

**修改点**：
- `initialize` 请求处理：`waitForSseSink(sessionId, 2)` → `waitForSseSink(sessionId, 5)`
- 其他消息请求处理：`waitForSseSink(sessionId, 2)` → `waitForSseSink(sessionId, 5)`

### 2. 验证 SSE sink 注册顺序

确保 SSE sink 在 SSE 连接建立时立即注册，而不是异步注册。

## 验证

修复后，测试完整的 MCP 客户端流程：

```bash
# 1. 建立 SSE 连接
curl -N --resolve mcp-bridge.local:80:127.0.0.1 http://mcp-bridge.local/sse/mcp-server-v6

# 应该看到：
# event:endpoint
# data:http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...

# 2. 发送 initialize 请求（在另一个终端）
SESSION_ID="从 endpoint 中提取的 sessionId"
curl -X POST "http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}'

# 3. 在 SSE 连接中应该看到 initialize 响应

# 4. 发送 tools/list 请求
curl -X POST "http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/list","params":{}}'

# 5. 在 SSE 连接中应该看到 tools/list 响应
```

## 预期效果

- SSE 连接建立后，`initialize` 响应应该在几秒内通过 SSE 返回
- `tools/list` 响应应该在几秒内通过 SSE 返回
- 总响应时间应该在可接受范围内（< 10 秒）

## 技术细节

### SSE sink 注册流程

1. **SSE 连接建立**：`handleSseWithServiceName` 或 `handleSseWithQueryParam`
2. **注册 SSE sink**：`sessionService.registerSseSink(sessionId, sink)`
3. **客户端发送请求**：POST `/mcp/mcp-server-v6/message?sessionId=xxx`
4. **等待 SSE sink**：`sessionService.waitForSseSink(sessionId, 5)`
5. **发送响应**：`sseSink.tryEmitNext(sseEvent)`

### 时序问题

如果客户端在 SSE sink 注册完成前就发送请求，`waitForSseSink` 需要等待。增加等待时间可以解决这个问题。

## 注意事项

- 等待时间不能太长，否则会影响用户体验
- 5 秒是一个平衡点，既能处理时序问题，又不会让用户等待太久
- 如果问题仍然存在，可能需要检查 SSE sink 注册逻辑




















