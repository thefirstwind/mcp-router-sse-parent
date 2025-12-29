# Nacos 查询优化 - 解决域名访问慢问题

## 问题描述

通过域名访问时，`tools/list` 等操作需要 7 秒多才能返回，其中连接时间 5 秒，首字节时间 7 秒。

## 问题分析

从测试结果和代码分析发现：
1. **Nacos 查询是同步阻塞的**：`namingService.selectInstances()` 是同步阻塞调用，会阻塞响应式流
2. **缓存未命中时每次都查询 Nacos**：如果缓存过期或未命中，会同步查询 Nacos，导致延迟
3. **没有超时保护**：Nacos 查询没有超时设置，如果 Nacos 服务不可用或响应慢，会长时间等待

## 修复方案

### 1. 将 Nacos 查询移到弹性线程池

使用 `subscribeOn(Schedulers.boundedElastic())` 将阻塞的 Nacos 查询移到弹性线程池，避免阻塞主响应式线程。

### 2. 添加超时保护

为 Nacos 查询添加 2 秒超时，避免长时间等待。如果超时或出错，返回空列表，不影响主流程。

### 3. 优化错误处理

使用 `onErrorReturn()` 确保即使 Nacos 查询失败，也不会中断整个流程。

## 修改位置

`mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/registry/McpServerRegistry.java`

### 关键修改

```java
// 使用 subscribeOn 将阻塞操作移到弹性线程池，避免阻塞主线程
return Mono.fromCallable(() -> {
    try {
        List<Instance> instances = namingService.selectInstances(serviceName, serviceGroup, true);
        // ... 处理逻辑
        return healthyList;
    } catch (Exception e) {
        log.warn("⚠️ Failed to get healthy servers for service: {} (Nacos未启用是正常的): {}", serviceName, e.getMessage());
        return List.<McpServerInfo>of();
    }
})
.subscribeOn(Schedulers.boundedElastic()) // 将阻塞的 Nacos 查询移到弹性线程池
.timeout(Duration.ofSeconds(2)) // 添加 2 秒超时，避免长时间等待
.onErrorReturn(List.<McpServerInfo>of()) // 超时或错误时返回空列表
.flatMapMany(Flux::fromIterable);
```

## 预期效果

- Nacos 查询不再阻塞主响应式线程
- 如果 Nacos 查询超时（2秒），会立即返回空列表，不会等待 5-7 秒
- 正常情况下，如果缓存命中，响应时间应该在几毫秒内
- 即使 Nacos 不可用，也不会导致整个系统阻塞

## 注意事项

- 如果 Nacos 查询超时，会返回空列表，可能导致"服务不可用"的错误，但这是预期的行为
- 缓存 TTL 是 30 秒，大部分请求应该能命中缓存，不会触发 Nacos 查询
- 如果 Nacos 服务不可用，建议检查 Nacos 配置和连接

## 验证

重启应用后，通过域名访问并调用 `tools/list`，应该能在几秒内返回结果（正常情况下应该在几百毫秒到几秒之间，取决于缓存是否命中）。




















