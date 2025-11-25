# SSE 域名访问修复 - 最终方案

## 问题描述

通过域名 `http://mcp-bridge.local/sse/mcp-server-v6` 访问时，SSE 连接无法正常工作，生成的 `endpoint` URL 不正确（使用 `localhost:8051` 而不是 `mcp-bridge.local`）。

## 根本原因

1. **代码逻辑错误**：`buildBaseUrlFromRequest` 方法中 `if (forwardedHost != null && !forwardedHost.isEmpty())` 语句缺少大括号，导致代码逻辑不正确。
2. **请求头读取**：虽然 WebFlux 的 `headers()` 是大小写不敏感的，但为了保险，同时尝试大小写两种形式。
3. **Nginx 配置**：Nginx 配置正确，但需要确保配置已生效。

## 修复方案

### 1. 修复代码逻辑错误

**文件**：`mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

**修复点**：
- 修复 `buildBaseUrlFromRequest` 方法中的 `if` 语句，添加大括号。
- 在 `handleSseWithServiceName` 方法中，同时尝试大小写两种形式读取 `X-Forwarded-Host` 和 `X-Forwarded-Proto`。
- 添加详细的调试日志，记录所有请求头。

### 2. 验证 Nginx 配置

确保 Nginx 配置正确并已生效：

```bash
# 检查配置
sudo nginx -t

# 重载配置
sudo nginx -s reload

# 如果不行，重启
sudo nginx -s stop && sudo nginx
```

### 3. 测试验证

**直接测试应用（绕过 Nginx）**：
```bash
curl -N -H "X-Forwarded-Host: mcp-bridge.local" \
     -H "X-Forwarded-Proto: http" \
     -H "X-Forwarded-Port: 80" \
     http://localhost:8051/sse/mcp-server-v6
```

**通过域名测试**：
```bash
curl -N http://mcp-bridge.local/sse/mcp-server-v6
```

**预期结果**：
- 应用日志中应显示 `X-Forwarded-Host: mcp-bridge.local`
- 生成的 `endpoint` 应为 `http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...`

## 验证步骤

1. **重启应用**：
   ```bash
   cd mcp-router-v3
   ./scripts/start-instances.sh restart
   ```

2. **等待应用完全启动**（约 15 秒）

3. **测试连接**：
   ```bash
   timeout 5 curl -N http://mcp-bridge.local/sse/mcp-server-v6
   ```

4. **查看日志**：
   ```bash
   tail -200 logs/router-8051.log | grep -E "(All request headers|SSE connection request|Building base URL|forwardedHost|endpoint)"
   ```

5. **验证 endpoint**：
   - 日志中应显示 `forwardedHost: mcp-bridge.local`
   - 生成的 `endpoint` 应使用 `mcp-bridge.local` 而不是 `localhost:8051`

## 注意事项

1. **应用启动时间**：应用完全启动需要约 15 秒，测试前请等待。
2. **Nginx 配置**：确保 Nginx 配置已正确加载，必要时重启 Nginx。
3. **日志级别**：调试日志使用 `DEBUG` 级别，确保 `application.yml` 中日志级别设置为 `DEBUG` 或更低。

## 相关文件

- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`
- `mcp-router-v3/nginx/nginx.conf`
- `mcp-router-v3/src/main/resources/application.yml`


