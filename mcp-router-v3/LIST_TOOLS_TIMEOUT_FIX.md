# listServerTools 超时修复

## 问题描述

调用 `GET /mcp/router/tools/virtual-data-analysis` 时出现超时错误：
```
java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 60000ms in 'flatMap' (and no fallback has been configured)
```

## 问题原因

1. **超时时间过长**：
   - `listServerTools` 调用 `listTools(serverInfo)` 时使用默认的 60 秒超时
   - 如果 `zkInfo` 的 RESTful 接口响应慢，会长时间等待

2. **缺少超时保护**：
   - 服务发现（`getAllHealthyServers`）没有超时保护
   - `flatMap` 操作没有超时保护
   - 缺少错误处理和 fallback

3. **错误处理不完善**：
   - 超时后直接抛出异常，没有返回友好的错误信息
   - 没有区分不同类型的错误（超时、连接失败等）

## 修复方案

### 文件
`mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`

### 修改内容

1. **添加服务发现超时**：
   - `getAllHealthyServers` 添加 5 秒超时
   - 避免长时间等待服务发现

2. **缩短工具列表调用超时**：
   - 从默认的 60 秒缩短到 10 秒
   - 使用 `listTools(serverInfo, Duration.ofSeconds(10))` 传递超时参数

3. **添加多层超时保护**：
   - 服务发现：5 秒超时
   - 工具列表调用：10 秒超时
   - 总超时：15 秒（服务发现5秒 + 调用10秒）

4. **改进错误处理**：
   - 添加 `onErrorResume` 处理调用错误
   - 返回错误信息而不是抛出异常
   - 添加 `onErrorReturn` 作为最后的 fallback

### 代码示例

```java
// 修复前
public Mono<Object> listServerTools(String serviceName) {
    return serverRegistry.getAllHealthyServers(serviceName, registryProperties.getServiceGroups())
            .collectList()
            .flatMap(list -> {
                if (list == null || list.isEmpty()) {
                    return Mono.just("目标服务不可用，请稍后重试或联系管理员");
                }
                McpServerInfo serverInfo = list.get(0);
                return mcpClientManager.listTools(serverInfo) // 默认60秒超时
                        .map(result -> (Object) result);
            })
            .doOnSuccess(tools -> log.info("Listed tools for service: {}", serviceName))
            .doOnError(error -> log.error("Failed to list tools for service: {}", serviceName, error));
}

// 修复后
public Mono<Object> listServerTools(String serviceName) {
    return serverRegistry.getAllHealthyServers(serviceName, registryProperties.getServiceGroups())
            .collectList()
            .timeout(Duration.ofSeconds(5)) // 服务发现超时：5秒
            .flatMap(list -> {
                if (list == null || list.isEmpty()) {
                    return Mono.just("目标服务不可用，请稍后重试或联系管理员");
                }
                McpServerInfo serverInfo = list.get(0);
                // 使用10秒超时，避免长时间等待
                return mcpClientManager.listTools(serverInfo, Duration.ofSeconds(10))
                        .map(result -> (Object) result)
                        .timeout(Duration.ofSeconds(10)) // 添加额外的超时保护
                        .onErrorResume(error -> {
                            log.error("Failed to list tools for service: {} (server: {})", 
                                    serviceName, serverInfo.getName(), error);
                            // 返回错误信息而不是抛出异常
                            return Mono.just(Map.of("error", "Failed to list tools: " + error.getMessage()));
                        });
            })
            .timeout(Duration.ofSeconds(15)) // 总超时：15秒
            .doOnSuccess(tools -> log.info("Listed tools for service: {}", serviceName))
            .doOnError(error -> log.error("Failed to list tools for service: {}", serviceName, error))
            .onErrorReturn("目标服务不可用，请稍后重试或联系管理员");
}
```

## 修复效果

### 修复前
- 使用默认的 60 秒超时，响应慢
- 缺少超时保护，可能长时间等待
- 超时后直接抛出异常，用户体验差

### 修复后
- 服务发现：5 秒超时
- 工具列表调用：10 秒超时
- 总超时：15 秒
- 超时后返回友好的错误信息，而不是抛出异常

## 超时设置说明

| 操作 | 超时时间 | 说明 |
|------|---------|------|
| 服务发现 | 5 秒 | 从 Nacos 获取服务列表 |
| 工具列表调用 | 10 秒 | 调用 `zkInfo` 的 RESTful 接口 |
| 总超时 | 15 秒 | 整个操作的超时时间 |

## 注意事项

1. **超时时间选择**：
   - 10 秒对于 RESTful 接口调用来说是合理的
   - 如果 `zkInfo` 响应慢，可能需要优化 `zkInfo` 的性能

2. **错误处理**：
   - 超时后返回错误信息，而不是抛出异常
   - 用户可以看到友好的错误提示

3. **性能优化**：
   - 如果经常超时，可能需要检查 `zkInfo` 的性能
   - 考虑使用连接池或缓存来减少调用时间

## 相关文件
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/service/McpClientManager.java`

