# 持久化功能排查指南

## 问题描述

`health_check_records` 和 `routing_logs` 两张表没有新记录写入。

## 可能的原因

### 1. 持久化功能未启用

**检查配置**：
```yaml
mcp:
  persistence:
    enabled: true  # 必须为 true
```

**验证方法**：
- 查看启动日志，应该看到：
  ```
  PersistenceEventPublisher initialized with buffer sizes: routing=10000, health=1000, error=1000
  Starting HealthCheckRecord batch writer...
  Starting RoutingLog batch writer...
  ```

### 2. 批量写入器未订阅事件流

**问题**：`Sinks.many().multicast()` 创建的 Sink 如果没有订阅者，事件会被丢弃。

**检查日志**：
- 应该看到：
  ```
  ✅ HealthCheckRecord batch writer subscribed to event stream
  ✅ RoutingLog batch writer subscribed to event stream
  ```

**如果没有看到这些日志**：
- 批量写入器可能启动失败
- 检查是否有异常日志

### 3. 事件发布失败

**检查日志**：
- 如果看到 `❌ RoutingLog event emit failed: NO SUBSCRIBERS!`，说明批量写入器没有订阅
- 如果看到 `❌ event buffer overflow`，说明消费者处理太慢
- 如果看到 `❌ event sink terminated`，说明批量写入器已停止

### 4. 数据库连接问题

**检查**：
- 数据库连接是否正常
- 表结构是否正确
- 是否有权限写入

**验证方法**：
```sql
-- 检查表是否存在
SHOW TABLES LIKE 'health_check_records';
SHOW TABLES LIKE 'routing_logs';

-- 检查表结构
DESC health_check_records;
DESC routing_logs;

-- 检查是否有写入权限
INSERT INTO health_check_records (server_key, check_time, check_type, status) 
VALUES ('test', NOW(), 'MCP', 'HEALTHY');
```

### 5. 批量写入失败但未记录

**检查日志**：
- 应该看到 `Batch insert successful` 或 `Batch insert failed`
- 如果没有任何日志，说明事件没有被发布或订阅

## 排查步骤

### 步骤 1：检查配置

```bash
# 检查 application.yml
grep -A 5 "mcp:" src/main/resources/application.yml
```

确保 `mcp.persistence.enabled: true`

### 步骤 2：检查启动日志

```bash
# 查看启动日志
grep -E "PersistenceEventPublisher|batch writer" logs/application.log

# 应该看到：
# PersistenceEventPublisher initialized
# Starting HealthCheckRecord batch writer
# Starting RoutingLog batch writer
# ✅ HealthCheckRecord batch writer subscribed
# ✅ RoutingLog batch writer subscribed
```

### 步骤 3：检查事件发布

```bash
# 查看是否有事件发布日志
grep -E "Publishing|Published|event emit" logs/application.log

# 应该看到：
# 📝 Publishing routing log: requestId=...
# ✅ Published routing log event successfully
```

### 步骤 4：检查事件订阅

```bash
# 查看是否有事件接收日志（需要启用 TRACE 日志）
grep -E "Received|Batching" logs/application.log

# 应该看到：
# 📥 Received routing log: ...
# 📦 Batching X routing logs for write
```

### 步骤 5：检查批量写入

```bash
# 查看批量写入日志
grep -E "Batch insert|Batch write" logs/application.log

# 应该看到：
# Batch insert successful: X records in Yms
```

### 步骤 6：检查错误日志

```bash
# 查看所有错误
grep -E "ERROR|FAIL|❌" logs/application.log | grep -i "persist\|batch\|routing\|health"
```

## 调试方法

### 方法 1：启用详细日志

在 `application.yml` 中添加：

```yaml
logging:
  level:
    com.pajk.mcpbridge.persistence: DEBUG
    com.pajk.mcpbridge.core.service.McpRouterService: DEBUG
    com.pajk.mcpbridge.core.service.HealthCheckService: DEBUG
```

### 方法 2：检查统计信息

如果代码中有统计接口，可以调用查看：

```java
// 检查 PersistenceEventPublisher 统计
PersistenceEventPublisher.PersistenceStats stats = persistenceEventPublisher.getStats();
System.out.println("Published: " + stats.successCount() + ", Failed: " + stats.failureCount());

// 检查批量写入器统计
RoutingLogBatchWriter.BatchWriterStats routingStats = routingLogBatchWriter.getStats();
System.out.println("Batches: " + routingStats.batches() + ", Records: " + routingStats.records());
```

### 方法 3：手动触发测试

```bash
# 触发一个路由请求
curl -X POST "http://localhost:8052/mcp/mcp-server-v6/message?sessionId=test" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":"1"}'

# 触发健康检查
curl -X POST "http://localhost:8052/mcp/monitor/check?serviceName=mcp-server-v6"
```

然后检查日志和数据库。

## 修复后的改进

### 1. 增强日志输出

- 添加了订阅确认日志
- 添加了事件接收日志（TRACE 级别）
- 添加了批量写入日志
- 添加了失败原因详细日志

### 2. 改进错误处理

- 添加了 `FAIL_ZERO_SUBSCRIBER` 错误处理
- 改进了错误消息，明确指出问题原因

### 3. 添加订阅确认

- 在批量写入器启动时添加了 `doOnSubscribe` 回调
- 确保订阅成功后才认为启动完成

## 常见问题

### Q1: 为什么事件被丢弃？

**A**: `multicast()` Sink 如果没有订阅者，事件会被丢弃。确保批量写入器在 `@PostConstruct` 时成功订阅。

### Q2: 为什么批量写入器没有启动？

**A**: 检查：
1. `mcp.persistence.enabled` 是否为 `true`
2. 是否有启动异常日志
3. Bean 是否被正确创建

### Q3: 为什么有事件发布但没有写入？

**A**: 检查：
1. 批量写入器是否订阅成功
2. 是否有批量写入错误日志
3. 数据库连接是否正常
4. 表结构是否正确

### Q4: 如何验证修复是否生效？

**A**: 
1. 重启应用
2. 查看启动日志，确认批量写入器订阅成功
3. 触发一些请求
4. 等待批量写入窗口（2-5秒）
5. 检查数据库是否有新记录

---

**最后更新**: 2025-11-12
**相关文件**: 
- `PersistenceEventPublisher.java`
- `HealthCheckRecordBatchWriter.java`
- `RoutingLogBatchWriter.java`











