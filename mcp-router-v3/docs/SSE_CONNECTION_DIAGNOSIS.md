# SSE 连接问题诊断

## 当前状态

从日志分析发现：

1. **应用代码正常**：
   - ✅ 能正确识别 `X-Forwarded-Host` 头（当手动添加时）
   - ✅ 能正确生成 endpoint URL（`http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...`）
   - ✅ 能正确注册会话和 SSE sink

2. **连接问题**：
   - ❌ 通过域名访问时，连接建立后 2 秒左右就被取消
   - ⚠️ 通过 localhost:8051 直接访问（带 X-Forwarded-Host 头）时能正常工作

## 问题分析

### 可能的原因

1. **Nginx 配置未完全生效**：
   - 虽然配置文件存在，但可能没有正确重载
   - 导致 `X-Forwarded-Host` 头没有传递，或者 SSE 连接被提前关闭

2. **Nginx SSE 配置问题**：
   - `proxy_buffering off` 可能没有生效
   - `proxy_read_timeout 300s` 可能没有生效
   - 连接可能在 Nginx 层面被提前关闭

3. **客户端行为**：
   - curl 在收到 endpoint 事件后可能就断开连接（这是正常的）
   - 但 mcp inspector 应该能保持连接

## 已修复的代码

1. **增强日志记录**：
   - 在 `handleSseWithServiceName` 和 `handleSseWithQueryParam` 中记录所有请求头
   - 在 `doOnCancel`、`doOnError`、`doOnComplete` 中记录更多上下文信息
   - 添加 `doOnSubscribe` 来跟踪连接订阅

2. **日志输出**：
   - 现在会记录：`Host`、`X-Forwarded-Host`、`X-Forwarded-Proto`
   - 连接取消时会记录：`sessionId`、`serviceName`、`baseUrl`

## 下一步验证

1. **重启应用**以应用代码更改
2. **重载 Nginx**配置（如果还没有）
3. **测试连接**并查看增强的日志

## 验证命令

```bash
# 1. 重启应用（如果正在运行）
cd mcp-router-v3
# 停止当前实例，然后重新启动

# 2. 测试连接
timeout 5 curl -N http://mcp-bridge.local/sse/mcp-server-v6

# 3. 查看日志
tail -f logs/router-8051.log | grep -E "(SSE connection|Host=|forwardedHost|endpoint|cancelled|subscribed)"
```

## 预期日志输出

正常情况应该看到：
```
📡 SSE connection request: serviceName=mcp-server-v6, ..., Host=mcp-bridge.local, X-Forwarded-Host=mcp-bridge.local, X-Forwarded-Proto=http
✅ SSE connection subscribed: sessionId=..., serviceName=mcp-server-v6, baseUrl=http://mcp-bridge.local
📡 Generated endpoint for SSE connection: ..., baseUrl=http://mcp-bridge.local, ...
```

如果看到 `X-Forwarded-Host=null`，说明 Nginx 没有传递头，需要重载 Nginx 配置。


