# Context-Path 修复说明

## 问题描述

在测试环境中，通过 `http://srv.test.pajkdc.com/mcp-bridge/sse/mcp-server-beta` 建立 SSE 连接时，返回的 `endpoint` 缺少了 context-path（`/mcp-bridge`）。

**错误的 endpoint**:
```
http://srv.test.pajkdc.com:80/mcp/mcp-server-beta/message?sessionId=xxx
```

**正确的 endpoint**:
```
http://srv.test.pajkdc.com/mcp-bridge/mcp/mcp-server-beta/message?sessionId=xxx
```

## 修复内容

### 1. 增强 context-path 提取逻辑

修复了 `extractContextPath` 方法，支持多种方式提取 context-path（按优先级排序）：

1. **从 `X-Forwarded-Prefix` 头中获取**（推荐）
   - 反向代理（如 Nginx）通常设置此头
   - 如果设置了此头，会优先使用

2. **从完整的请求 URI 路径中提取**
   - 如果反向代理保留了完整路径，会从 URI 差异中提取

3. **从请求路径的第一个段推断**
   - 如果路径是 `/mcp-bridge/sse/mcp-server-beta`，会提取 `/mcp-bridge`

4. **从配置文件中获取**（新增）
   - 支持通过 `mcp.router.context-path` 配置项手动指定

5. **从 Spring 环境变量中获取**
   - 从 `server.servlet.context-path` 配置中获取

### 2. 添加配置项支持

在 `application.yml` 中添加了 `mcp.router.context-path` 配置项，允许手动指定 context-path：

```yaml
mcp:
  router:
    context-path: /mcp-bridge  # 手动指定 context-path
```

### 3. 增强日志输出

添加了详细的调试日志，方便排查问题：
- `buildBaseUrlFromRequest` 方法会输出构建 baseUrl 的详细信息
- `extractContextPath` 方法会输出提取 context-path 的来源
- endpoint 生成时会输出完整的 endpoint URL

## 配置方式

### 方式 1：通过配置文件（推荐用于测试环境）

在 `application.yml` 中添加：

```yaml
mcp:
  router:
    context-path: /mcp-bridge
```

### 方式 2：通过反向代理设置 `X-Forwarded-Prefix` 头（推荐用于生产环境）

在 Nginx 配置中添加：

```nginx
location /mcp-bridge {
    proxy_pass http://localhost:8052;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port $server_port;
    proxy_set_header X-Forwarded-Prefix /mcp-bridge;  # 设置 context-path
}
```

### 方式 3：通过 Spring Boot 配置

在 `application.yml` 中添加：

```yaml
server:
  servlet:
    context-path: /mcp-bridge
```

## 验证方法

1. **查看日志**
   - 启动应用后，查看日志中的 `📡 Generated endpoint for SSE connection` 信息
   - 确认 `baseUrl` 和 `messageEndpoint` 是否包含正确的 context-path

2. **测试 SSE 连接**
   ```bash
   curl -N -H "Accept: text/event-stream" \
     http://srv.test.pajkdc.com/mcp-bridge/sse/mcp-server-beta
   ```
   
   检查返回的 `endpoint` 事件中的 URL 是否包含 `/mcp-bridge`

3. **检查调试日志**
   - 启用 DEBUG 日志级别：`logging.level.com.pajk.mcpbridge: DEBUG`
   - 查看 `Building base URL` 和 `Extracted context-path` 相关的日志

## 注意事项

1. **端口处理**
   - 如果是标准端口（80/443），URL 中不会包含端口号
   - 如果是非标准端口，URL 中会包含端口号

2. **优先级**
   - `X-Forwarded-Prefix` 头 > 配置文件 `mcp.router.context-path` > Spring 配置 `server.servlet.context-path` > 从请求路径推断

3. **反向代理配置**
   - 如果使用反向代理，建议设置 `X-Forwarded-Prefix` 头，这是最可靠的方式
   - 确保反向代理正确传递了所有必要的请求头

## 测试环境配置示例

对于测试环境 `http://srv.test.pajkdc.com/mcp-bridge`，建议在 `application.yml` 中添加：

```yaml
mcp:
  router:
    context-path: /mcp-bridge
```

或者在 Nginx 配置中设置：

```nginx
proxy_set_header X-Forwarded-Prefix /mcp-bridge;
```

---

**修复日期**: 2025-11-12
**相关文件**: `McpRouterServerConfig.java`





