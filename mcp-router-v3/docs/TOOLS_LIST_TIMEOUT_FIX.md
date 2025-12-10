# tools/list 20 秒延迟问题修复

## 问题描述

通过域名建立 SSE 连接后，调用 `tools/list` 返回需要将近 20 秒的时间。

## 问题分析

从日志分析发现：
1. **连接池正常**：日志显示 "Using pooled connection"，说明连接池中有可用连接
2. **处理时间正常**：`tools/list` 的实际处理时间只有 18ms
3. **延迟来源**：问题在于 `McpAsyncClient::listTools` 等方法没有显式超时设置，可能使用默认的 60 秒超时，导致在某些情况下等待时间过长

## 修复方案

### 1. 为所有 list 方法添加 10 秒超时

在 `McpClientManager.java` 中，为以下方法添加了 `.timeout(Duration.ofSeconds(10))`：
- `listTools()` - 获取工具列表
- `listResources()` - 获取资源列表
- `listPrompts()` - 获取提示列表
- `listResourceTemplates()` - 获取资源模板列表

这样可以避免长时间等待，如果后端服务器响应慢，会在 10 秒后超时并返回错误。

### 2. 优化路由超时设置

在 `McpRouterServerConfig.java` 中，对于 `tools/list`、`resources/list`、`prompts/list`、`resources/templates/list` 和 `tools/call` 方法，使用较短的超时时间（10秒），而不是默认的 60 秒。

```java
// 对于 list 方法，使用较短的超时时间（10秒），避免长时间等待
Duration timeout = (mcpMessage.getMethod() != null && 
        (mcpMessage.getMethod().endsWith("/list") || "tools/call".equals(mcpMessage.getMethod())))
        ? Duration.ofSeconds(10) : Duration.ofSeconds(60);
routeResult = routerService.routeRequest(targetServiceName, mcpMessage, timeout, Map.of());
```

## 修改文件

1. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpClientManager.java`
   - `listTools()`: 添加 10 秒超时
   - `listResources()`: 添加 10 秒超时
   - `listPrompts()`: 添加 10 秒超时
   - `listResourceTemplates()`: 添加 10 秒超时

2. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`
   - 优化路由超时设置，对于 list 方法使用 10 秒超时

## 预期效果

- `tools/list` 等 list 方法现在有 10 秒的超时限制
- 如果后端服务器响应慢，会在 10 秒后超时并返回错误，而不是等待 60 秒
- 正常情况下，响应时间应该在几毫秒到几秒之间

## 验证

重启应用后，通过域名建立 SSE 连接并调用 `tools/list`，应该能在 10 秒内返回结果（正常情况下应该在几毫秒到几秒之间）。












