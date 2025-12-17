# 虚拟项目 HTTP 调用修复

## 问题描述

调用 `GET /mcp/router/tools/virtual-data-analysis` 时出现超时错误：
```
java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 10000ms in 'map' (and no fallback has been configured)
```

## 问题原因

1. **使用 SSE 客户端连接虚拟项目**：
   - `mcp-router-v3` 使用 SSE 客户端（`WebFluxSseClientTransport`）连接 `zkInfo`
   - 尝试调用 `client.initialize()`，但 `zkInfo` 的 SSE 端点可能不支持标准的 MCP initialize 协议
   - 初始化超时（200ms），但连接仍然创建

2. **调用 listTools() 超时**：
   - 使用 SSE 客户端调用 `listTools()`，但 `zkInfo` 的 RESTful 接口 `/mcp/message` 响应慢或阻塞
   - 10 秒后超时

3. **应该使用 HTTP POST 而不是 SSE**：
   - 对于虚拟项目（`virtual-*`），应该直接使用 HTTP POST 调用 `/mcp/message` 端点
   - 而不是使用 SSE 客户端建立连接

## 修复方案

### 文件
`mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpClientManager.java`

### 修改内容

1. **检测虚拟项目**：
   - 在 `listTools()` 方法中检测服务名是否以 `virtual-` 开头
   - 如果是虚拟项目，使用 HTTP POST 直接调用 RESTful 接口

2. **添加 `listToolsViaHttp()` 方法**：
   - 直接使用 HTTP POST 调用 `/mcp/message` 端点
   - 发送 `tools/list` 请求
   - 使用 ObjectMapper 将响应转换为 `McpSchema.ListToolsResult`

### 代码示例

```java
// 修复前
public Mono<McpSchema.ListToolsResult> listTools(McpServerInfo serverInfo, Duration timeout) {
    // 总是使用 SSE 客户端
    return getOrCreateMcpClient(serverInfo)
            .timeout(connectionTimeout)
            .flatMap(client -> client.listTools().timeout(callTimeout));
}

// 修复后
public Mono<McpSchema.ListToolsResult> listTools(McpServerInfo serverInfo, Duration timeout) {
    // 对于虚拟项目（virtual-*），直接使用 HTTP POST 调用 RESTful 接口
    if (serverInfo.getName() != null && serverInfo.getName().startsWith("virtual-")) {
        return listToolsViaHttp(serverInfo, timeout);
    }
    
    // 对于其他服务，使用 SSE 客户端
    return getOrCreateMcpClient(serverInfo)
            .timeout(connectionTimeout)
            .flatMap(client -> client.listTools().timeout(callTimeout));
}

private Mono<McpSchema.ListToolsResult> listToolsViaHttp(McpServerInfo serverInfo, Duration timeout) {
    // 直接使用 HTTP POST 调用 /mcp/message 端点
    String serverBaseUrl = buildServerUrl(serverInfo);
    String sessionId = UUID.randomUUID().toString();
    
    Map<String, Object> requestBody = Map.of(
        "jsonrpc", "2.0",
        "id", "tools-list-" + System.currentTimeMillis(),
        "method", "tools/list",
        "params", Map.of()
    );
    
    return webClientBuilder
            .baseUrl(serverBaseUrl)
            .build()
            .post()
            .uri("/mcp/message?sessionId=" + sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(timeout)
            .map(response -> {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                if (result == null) {
                    throw new RuntimeException("Invalid tools/list response: no result");
                }
                // 使用 ObjectMapper 直接将 result Map 转换为 ListToolsResult
                return objectMapper.convertValue(result, McpSchema.ListToolsResult.class);
            });
}
```

## 修复效果

### 修复前
- 使用 SSE 客户端连接虚拟项目
- 初始化超时（200ms）
- `listTools()` 调用超时（10秒）
- 响应慢，用户体验差

### 修复后
- 虚拟项目直接使用 HTTP POST 调用 RESTful 接口
- 不需要建立 SSE 连接
- 响应更快，超时时间可控
- 用户体验更好

## 调用方式对比

### SSE 客户端方式（修复前）
1. 建立 SSE 连接
2. 调用 `initialize()`（可能超时）
3. 调用 `listTools()`（通过 SSE）
4. 响应慢，容易超时

### HTTP POST 方式（修复后）
1. 直接发送 HTTP POST 请求到 `/mcp/message`
2. 发送 `tools/list` 请求
3. 直接返回 JSON 响应
4. 响应快，超时可控

## 注意事项

1. **虚拟项目识别**：
   - 服务名以 `virtual-` 开头的服务被视为虚拟项目
   - 例如：`virtual-data-analysis`

2. **其他服务**：
   - 非虚拟项目仍然使用 SSE 客户端
   - 保持原有行为

3. **超时设置**：
   - HTTP POST 调用使用传入的超时参数（默认 10 秒）
   - 可以根据需要调整

## 相关文件
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpClientManager.java`
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`

