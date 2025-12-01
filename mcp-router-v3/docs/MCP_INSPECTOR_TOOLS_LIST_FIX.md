# MCP Inspector tools/list 问题修复

## 问题描述

使用 mcp inspector 通过域名 `http://mcp-bridge.local` 连接时：
- ✅ 能建立 SSE 连接
- ❌ 无法获取 `tools/list` 结果

## 问题原因

1. **Nginx 配置未更新**：实际的 Nginx 配置文件 `/opt/homebrew/etc/nginx/servers/mcp-bridge.conf` 是旧版本，缺少：
   - `X-Forwarded-Host` 头
   - SSE 特定配置（`proxy_buffering off`, `proxy_read_timeout 300s` 等）

2. **SSE 连接超时**：由于 Nginx 配置不正确，SSE 连接在几秒后超时断开，导致：
   - endpoint 事件无法正确传递
   - `tools/list` 请求的响应无法通过 SSE 发送回客户端

## 解决方案

### 步骤 1: 更新 Nginx 配置

运行自动更新脚本：

```bash
cd mcp-router-v3
./scripts/update-nginx-config.sh
```

或者手动更新：

```bash
# 1. 复制配置文件
sudo cp mcp-router-v3/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# 2. 测试配置
sudo nginx -t

# 3. 重载 Nginx
sudo nginx -s reload
```

### 步骤 2: 验证配置

检查关键配置项：

```bash
grep -E "X-Forwarded-Host|proxy_buffering|proxy_read_timeout" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf
```

应该看到：
- `proxy_set_header X-Forwarded-Host $host;`
- `proxy_buffering off;`
- `proxy_read_timeout 300s;`

### 步骤 3: 测试连接

```bash
# 测试 SSE 连接
curl -N -m 5 http://mcp-bridge.local/sse/mcp-server-v6

# 应该能看到 endpoint 事件
# event:endpoint
# data:http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...
```

### 步骤 4: 测试 tools/list

使用测试脚本：

```bash
cd mcp-router-v3
./scripts/test-mcp-inspector-flow.sh
```

## 技术细节

### Endpoint URL 生成

应用代码会根据请求来源生成不同的 endpoint URL：

1. **通过 IP 访问**：`http://localhost:8051/mcp/mcp-server-v6/message?sessionId=...`
2. **通过域名访问**：`http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...`

### tools/list 请求流程

1. 客户端建立 SSE 连接，收到 `endpoint` 事件
2. 客户端发送 `initialize` 请求到 endpoint URL
3. 客户端发送 `tools/list` 请求到 endpoint URL
4. 服务器处理 `tools/list` 请求（使用路由逻辑，不通过后端桥接）
5. 服务器通过 SSE sink 发送响应回客户端

### 关键代码逻辑

`tools/list` 请求会被标记为 `forceRouteInsteadOfBridge`，直接使用路由逻辑：

```java
boolean isListMethod = "tools/list".equals(mcpMessage.getMethod()) ||
        "resources/list".equals(mcpMessage.getMethod()) ||
        "prompts/list".equals(mcpMessage.getMethod());
boolean forceRouteInsteadOfBridge = isListMethod || "tools/call".equals(mcpMessage.getMethod());
```

响应通过 SSE sink 发送：

```java
return sseSinkMono
    .flatMap(sseSink -> {
        ServerSentEvent<String> sseEvent = ServerSentEvent.<String>builder()
                .data(responseJson)
                .build();
        sseSink.tryEmitNext(sseEvent);
        return ServerResponse.accepted()
                .bodyValue("{\"status\":\"accepted\",\"message\":\"Request accepted, response will be sent via SSE\"}");
    });
```

## 验证清单

- [ ] Nginx 配置已更新并重载
- [ ] SSE 连接能正常建立（不超时）
- [ ] endpoint URL 使用正确的域名
- [ ] `initialize` 请求能正常处理
- [ ] `tools/list` 请求能正常处理并返回结果
- [ ] mcp inspector 能正常显示工具列表

## 常见问题

### Q: 为什么通过 IP 访问正常，但通过域名访问不行？

A: 因为 Nginx 配置未更新，缺少 `X-Forwarded-Host` 头，应用无法识别原始域名，生成的 endpoint URL 可能不正确。

### Q: SSE 连接为什么超时？

A: Nginx 默认的 `proxy_read_timeout` 是 60 秒，但对于 SSE 长连接，需要设置为 300 秒或更长。同时需要禁用缓冲（`proxy_buffering off`）。

### Q: tools/list 响应为什么收不到？

A: 如果 SSE 连接已断开，响应无法通过 SSE sink 发送。需要确保 SSE 连接保持活跃。








