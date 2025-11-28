# MCP Inspector 连接问题排查指南

## 问题描述

MCP Inspector 连接生产环境的 streamable 协议时出现问题。

## 可能的原因

### 1. Endpoint URL 缺少 ContextPath

MCP Inspector 连接后，返回的 endpoint URL 可能不包含 `/mcp-bridge` contextPath，导致后续请求失败。

**检查方法**：
- 查看应用日志中的 `Generated endpoint` 日志
- 确认 `baseUrl` 是否包含 `/mcp-bridge`

**解决方案**：
- 确保 Ingress 正确传递 `X-Forwarded-Prefix: /mcp-bridge` 头
- 检查 `extractContextPath` 方法是否正确读取该头

### 2. Content-Type 不匹配

MCP Inspector 可能期望特定的 Content-Type。

**检查方法**：
- 查看请求的 `Accept` 头
- 查看响应的 `Content-Type` 头

**解决方案**：
- 确保返回 `application/x-ndjson+stream` 或 `application/x-ndjson`
- 检查 `resolveStreamableMediaType` 方法的逻辑

### 3. NDJSON 格式问题

MCP Inspector 可能对 NDJSON 格式有特定要求。

**检查方法**：
- 查看返回的第一条消息格式
- 确认是否符合 MCP Inspector 的期望格式

**解决方案**：
- 检查 `toStreamableJson` 方法的输出格式
- 确保每条消息以 `\n` 结尾

### 4. CORS 问题

虽然 streamable 协议通常不需要 CORS，但某些情况下可能仍需要。

**检查方法**：
- 查看浏览器控制台的 CORS 错误
- 检查响应头中是否有 CORS 相关头

**解决方案**：
- 在 `buildStreamableResponse` 中添加必要的 CORS 头（如果需要）

### 5. `Mcp-Session-Id` 头缺失

Streamable 官方规范要求服务器在初次响应时返回 `Mcp-Session-Id` 头，客户端在后续 POST `/mcp/.../message` 时需要携带该头。如果 Ingress 或代码没有透传/解析这个头，会导致 Router 无法定位会话，日志中会出现 `Session not found`。

**检查方法**：
- 通过 `curl -i` 建立 streamable 连接，确认响应头中包含 `Mcp-Session-Id`。
- 在 Router 日志中查看 `Resolved sessionId from header` 调试信息，确认消息请求能够读取该头。

**解决方案**：
- v3 Router 已在 `buildStreamableResponse` 中回写该头，并在消息路由里优先解析 `Mcp-Session-Id`。如果仍失败，确认网关没有过滤此自定义头。

## 排查步骤

1. **检查应用日志**：
   ```bash
   kubectl logs -f <mcp-router-pod> | grep -E "streamable|endpoint|MCP Inspector"
   ```

2. **检查 Ingress 配置**：
   ```bash
   kubectl get ingress mcp-router-ingress -o yaml | grep -A 5 "X-Forwarded-Prefix"
   ```

3. **测试连接**：
   ```bash
   curl -N -H 'Accept: application/x-ndjson+stream' \
        'http://mcp-bridge.example.com/mcp-bridge/mcp/mcp-server-v6' \
        -v 2>&1 | head -50
   ```

4. **检查返回的 endpoint URL**：
   - 查看第一条消息中的 `endpoint` 字段
   - 确认 URL 是否包含 `/mcp-bridge`

## 常见问题

### Q: MCP Inspector 连接后没有收到任何消息

A: 检查：
1. 连接是否成功建立（查看日志中的 "Connection subscribed"）
2. endpoint 事件是否发送（查看日志中的 "endpoint" 事件）
3. 是否有错误日志

### Q: Endpoint URL 不正确

A: 检查：
1. `X-Forwarded-Prefix` 头是否正确传递
2. `extractContextPath` 方法是否正确读取
3. `buildBaseUrlFromRequest` 是否正确拼接 contextPath

### Q: 401 Unauthorized 错误

A: 这通常不是应用层的问题，而是：
1. Kubernetes Ingress 的认证配置
2. Service Mesh 的认证策略
3. 其他中间件的认证拦截

## 相关代码

- `McpRouterServerConfig.handleStreamable()` - Streamable 连接处理
- `McpRouterServerConfig.buildBaseUrlFromRequest()` - Base URL 生成
- `McpRouterServerConfig.extractContextPath()` - ContextPath 提取
- `McpRouterServerConfig.toStreamableJson()` - NDJSON 格式转换

