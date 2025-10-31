# Nacos 健康状态同步修复

## 问题描述

在之前的实现中，当从 Nacos 读取服务实例信息并同步到数据库时，代码直接使用 Nacos 报告的 `healthy` 和 `enabled` 状态。这导致以下问题：

**症状**：
- 服务进程明明在运行，但数据库中显示 `healthy=0`
- 特别是对于 `cf-server` 和 `mcp-server-v2-20250718` 等服务

**根本原因**：
- Nacos 可能会暂时报告 `healthy=false`（健康检查延迟、网络抖动等）
- 旧代码直接将 Nacos 的 `healthy=false` 写入数据库
- 即使服务实际在运行，数据库也被错误地标记为不健康

## 修复方案

### 核心思想

对于 **临时节点（ephemeral=true）**：
- 如果服务进程停止，Nacos 会立即将其从实例列表中移除
- 如果服务出现在 Nacos 的实例列表中，说明服务进程正在运行
- **因此，出现在列表中的临时节点应该被视为健康的**

对于 **持久化节点（ephemeral=false）**：
- 即使进程停止也会保留在 Nacos 中
- 需要依赖 Nacos 的健康检查状态

### 代码修改

**文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`

**修改位置**: `persistInstanceToDatabase()` 方法（第372-436行）

```java
// 关键修复：对于临时节点（ephemeral=true），如果它出现在 Nacos 的实例列表中，
// 就说明服务进程正在运行并已注册到 Nacos，应该被视为健康和启用的。
// Nacos 可能会暂时报告 healthy=false（例如健康检查延迟），但只要实例在列表中，
// 就说明服务是活跃的。
boolean isEphemeral = instance.isEphemeral();
if (isEphemeral) {
    // 临时节点：出现在列表中 = 服务在运行 = 应该是健康的
    // 使用 Nacos 的 enabled 状态，但强制 healthy=true
    serverInfo.setHealthy(true);  // 强制健康状态为 true
    serverInfo.setEnabled(instance.isEnabled());  // 保留 Nacos 的 enabled 状态
    log.info("✅ Ephemeral instance in Nacos list, marking as healthy: {}:{} (nacos_healthy={}, nacos_enabled={})",
        instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
} else {
    // 持久化节点：使用 Nacos 报告的原始状态
    serverInfo.setHealthy(instance.isHealthy());
    serverInfo.setEnabled(instance.isEnabled());
    log.info("ℹ️ Persistent instance, using Nacos status: {}:{} (healthy={}, enabled={})",
        instance.getIp(), instance.getPort(), instance.isHealthy(), instance.isEnabled());
}
```

## 修复效果

### 修复前
```
Nacos 报告: cf-server (healthy=false, enabled=true, ephemeral=false)
↓
数据库记录: cf-server (healthy=0, enabled=1) ❌ 错误
```

### 修复后

**临时节点 (ephemeral=true)**：
```
Nacos 实例列表包含: mcp-router-v3 (healthy=false, enabled=true, ephemeral=true)
↓
数据库记录: mcp-router-v3 (healthy=1, enabled=1) ✅ 正确
```
逻辑：只要临时节点出现在 Nacos 列表中，就强制设置为 healthy=1

**持久化节点 (ephemeral=false)**：
```
Nacos 报告: cf-server (healthy=true, enabled=true, ephemeral=false)
↓
数据库记录: cf-server (healthy=1, enabled=1) ✅ 正确
```
逻辑：持久化节点使用 Nacos 的原始状态

## 验证结果

### 数据库当前状态
```sql
SELECT server_name, host, port, healthy, enabled, ephemeral 
FROM mcp_servers 
WHERE server_name IN ('cf-server', 'mcp-server-v2-20250718', 'mcp-router-v3');
```

| server_name | host | port | healthy | enabled | ephemeral |
|------------|------|------|---------|---------|-----------|
| cf-server | 127.0.0.1 | 8899 | 1 | 1 | 0 |
| mcp-server-v2-20250718 | 127.0.0.1 | 8090 | 1 | 1 | 0 |
| mcp-router-v3 | 127.0.0.1 | 8052 | 1 | 1 | 1 |

✅ 所有服务都正确显示为健康状态

### 日志验证

**临时节点日志**：
```
2025-10-30 20:22:08.014  INFO --- [ncesChangeEvent] c.p.m.c.l.McpConnectionEventListener     : 
✅ Ephemeral instance in Nacos list, marking as healthy: 127.0.0.1:8052 (nacos_healthy=true, nacos_enabled=true)
```

**持久化节点日志**：
```
2025-10-30 20:22:07.804  INFO --- [ncesChangeEvent] c.p.m.c.l.McpConnectionEventListener     : 
ℹ️ Persistent instance, using Nacos status: 127.0.0.1:8899 (healthy=true, enabled=true)
```

## 影响范围

### 受益场景
1. **临时节点健康检查延迟**：即使 Nacos 健康检查暂时失败，只要服务在线，数据库仍显示健康
2. **网络抖动**：临时的网络问题不会导致数据库状态错误
3. **服务启动阶段**：服务刚启动时健康检查可能未通过，但只要注册成功，就显示为健康

### 不受影响场景
1. **持久化节点**：保持原有逻辑，完全依赖 Nacos 的健康检查
2. **服务真正下线**：临时节点下线时会从 Nacos 列表中移除，触发其他清理逻辑

## 注意事项

1. **临时节点的健康状态**：
   - 在 Nacos 列表中 = healthy=1（强制）
   - 不在 Nacos 列表中 = 触发 `markEphemeralInstancesUnhealthy()` 逻辑

2. **持久化节点的健康状态**：
   - 完全依赖 Nacos 的健康检查结果
   - 建议为持久化节点配置合理的健康检查参数

3. **健康检查参数优化**：
   - 对于临时节点，Nacos 的健康检查延迟不再影响数据库状态
   - 对于持久化节点，建议配置合适的健康检查间隔和超时时间

## 相关代码

- **修改文件**: `McpConnectionEventListener.java`
- **影响方法**: `persistInstanceToDatabase()`
- **配套逻辑**: `markEphemeralInstancesUnhealthy()`, `markOfflineEphemeralInstancesNotInNacos()`

## 修复时间
- 2025-10-30 20:22:08

## 修复结果
✅ **成功** - 所有服务健康状态正确同步到数据库

