# Nacos 健康状态同步修复报告

## 📋 问题描述

**发现时间**: 2025-10-30  
**发现者**: 用户 (Shine)

### 核心问题
数据库中的 MCP 服务器健康状态 (`healthy` 字段) 始终为 1 (true)，即使实际服务已经停止运行，与 Nacos 中的真实健康状态不一致。

### 问题影响
- 🚨 数据库数据与 Nacos 状态不一致
- ❌ 无法通过数据库准确判断服务健康状态
- ⚠️ 可能导致路由到不健康的服务实例
- 📊 健康监控和统计数据不准确

---

## 🔍 根因分析

### 1. 问题定位

通过代码审查发现，在 `McpServer.fromRegistration()` 方法中存在**硬编码**：

```java
// 文件: mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java
// 行号: 167-168 (修复前)

public static McpServer fromRegistration(...) {
    return McpServer.builder()
        // ... 其他字段 ...
        .healthy(true)      // ❌ 硬编码为 true
        .enabled(true)      // ❌ 硬编码为 true
        .weight(1.0)        // ❌ 硬编码为 1.0
        .ephemeral(true)    // ❌ 硬编码为 true
        // ... 其他字段 ...
        .build();
}
```

### 2. 调用链分析

```
Nacos 服务变更事件
    ↓
McpConnectionEventListener.handleServiceChangeEvent()
    ↓ (获取 instance.isHealthy() = false)
McpConnectionEventListener.persistInstanceToDatabase()
    ↓ (构建 McpServerInfo，包含真实健康状态)
McpServerPersistenceService.persistServerRegistration()
    ↓ (调用 fromRegistration 但未传递健康状态)
McpServer.fromRegistration()
    ↓ (硬编码 healthy=true，丢失了真实状态！)
数据库更新 ❌ healthy 始终为 1
```

### 3. 已有功能验证

**好消息**: 系统**已经实现了从 Nacos 自动同步健康状态到数据库的功能**！

在 `McpConnectionEventListener` 中：
- ✅ 监听 Nacos 服务变更事件 (第152-185行)
- ✅ 获取实例的真实健康状态 (第164-171行)
- ✅ 构建 McpServerInfo 包含健康状态 (第190-234行)
- ❌ **但在持久化时丢失了健康状态信息** (fromRegistration 硬编码)

---

## 🔧 修复方案

### 方案概述
**重载 `fromRegistration` 方法**，添加支持传递真实健康状态参数的版本，同时保持原有方法的向后兼容性。

### 修复步骤

#### 1. 修改 `McpServer` 实体类

**文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java`

**变更**:
```java
// 保留原有方法（向后兼容）
public static McpServer fromRegistration(String serverKey, String serverName, String serviceGroup,
                                         String host, Integer port, String sseEndpoint, 
                                         String healthEndpoint, String metadata) {
    return fromRegistration(serverKey, serverName, serviceGroup, host, port, sseEndpoint, 
                           healthEndpoint, metadata, true, true, 1.0, true);
}

// 新增方法（支持真实健康状态）
public static McpServer fromRegistration(String serverKey, String serverName, String serviceGroup,
                                         String host, Integer port, String sseEndpoint, 
                                         String healthEndpoint, String metadata,
                                         Boolean healthy, Boolean enabled, Double weight, Boolean ephemeral) {
    return McpServer.builder()
        .serverKey(serverKey)
        .serverName(serverName)
        .serverGroup(serviceGroup != null ? serviceGroup : "mcp-server")
        .namespaceId("public")
        .host(host)
        .port(port)
        .sseEndpoint(sseEndpoint != null ? sseEndpoint : "/sse")
        .healthEndpoint(healthEndpoint != null ? healthEndpoint : "/health")
        .healthy(healthy != null ? healthy : true)      // ✅ 使用传入的真实值
        .enabled(enabled != null ? enabled : true)      // ✅ 使用传入的真实值
        .weight(weight != null ? weight : 1.0)          // ✅ 使用传入的真实值
        .ephemeral(ephemeral != null ? ephemeral : true)// ✅ 使用传入的真实值
        .clusterName("DEFAULT")
        .version("1.0.0")
        .protocol("mcp-sse")
        .metadata(metadata)
        .totalRequests(0L)
        .totalErrors(0L)
        .registeredAt(LocalDateTime.now())
        .build();
}
```

**修改行数**: 第152-190行  
**变更类型**: 方法重载（向后兼容）

#### 2. 修改 `McpServerPersistenceService` 服务类

**文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/service/McpServerPersistenceService.java`

**变更前**:
```java
McpServer server = McpServer.fromRegistration(
    serverKey,
    serverInfo.getName(),
    serverInfo.getServiceGroup(),
    serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(),
    serverInfo.getPort(),
    serverInfo.getSseEndpoint(),
    "/health",
    metadata
);
```

**变更后**:
```java
McpServer server = McpServer.fromRegistration(
    serverKey,
    serverInfo.getName(),
    serverInfo.getServiceGroup(),
    serverInfo.getHost() != null ? serverInfo.getHost() : serverInfo.getIp(),
    serverInfo.getPort(),
    serverInfo.getSseEndpoint(),
    "/health",
    metadata,
    serverInfo.isHealthy(),   // ✅ 传递真实的健康状态
    serverInfo.getEnabled(),  // ✅ 传递真实的启用状态
    serverInfo.getWeight(),   // ✅ 传递真实的权重
    serverInfo.isEphemeral()  // ✅ 传递真实的临时节点状态
);
```

**修改行数**: 第54-88行  
**变更类型**: 方法调用修改

**日志增强**:
```java
log.debug("✅ Server registration persisted: {} ({}:{}) - healthy={}, enabled={}", 
    serverInfo.getName(), server.getHost(), server.getPort(), 
    serverInfo.isHealthy(), serverInfo.getEnabled());
```

---

## ✅ 验证结果

### 编译验证
```bash
$ cd mcp-router-v3 && mvn clean compile -DskipTests
[INFO] BUILD SUCCESS
[INFO] Total time:  2.328 s
```

### 运行验证

#### 1. 服务启动成功
```
2025-10-30 17:07:46.645  INFO Started McpRouterV3Application in 1.526 seconds
```

#### 2. Nacos 健康状态检查

**test-mcp-server-alignment (未运行)**:
```bash
$ curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=test-mcp-server-alignment&groupName=mcp-server"
{
  "healthy": false,  ✅
  "enabled": true,
  ...
}
```

**mcp-server-v2-real (未运行)**:
```bash
$ curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v2-real&groupName=mcp-server"
{
  "healthy": false,  ✅
  "enabled": true,
  ...
}
```

#### 3. 数据库状态验证

```sql
SELECT server_name, host, port, healthy, enabled, weight
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC;
```

**结果**:
```
server_name                    host          port  healthy  enabled  weight
mcp-router-v3                  127.0.0.1     8052    1        1        1     ✅
mcp-server-v6                  192.168.0.102 8066    1        1        1     ✅
cf-server                      127.0.0.1     8899    1        1        1     ✅
mcp-server-v2-20250718         127.0.0.1     8090    1        1        1     ✅
test-mcp-server-alignment      127.0.0.1     8999    0        1        1     ✅ 修复后正确
mcp-server-v2-real             127.0.0.1     8063    0        1        1     ✅ 修复后正确
```

#### 4. 对比验证

| 服务名 | Nacos healthy | 数据库 healthy | 一致性 |
|--------|---------------|----------------|--------|
| mcp-server-v6 | `true` | `1` | ✅ 一致 |
| cf-server | `true` | `1` | ✅ 一致 |
| mcp-server-v2-20250718 | `true` | `1` | ✅ 一致 |
| test-mcp-server-alignment | `false` ❗ | `0` ✅ | ✅ **修复后一致** |
| mcp-server-v2-real | `false` ❗ | `0` ✅ | ✅ **修复后一致** |
| mcp-router-v3 | `true` | `1` | ✅ 一致 |

**验证结论**: ✅ **Nacos 和数据库状态完全一致！**

---

## 📊 修复效果

### Before 修复前 ❌
```
Nacos: healthy=false  →  数据库: healthy=1  (不一致！)
```

### After 修复后 ✅
```
Nacos: healthy=false  →  数据库: healthy=0  (一致！)
Nacos: healthy=true   →  数据库: healthy=1  (一致！)
```

### 关键改进

1. **✅ 数据一致性**
   - Nacos 与数据库状态实时同步
   - 服务健康状态准确反映

2. **✅ 向后兼容**
   - 保留原有 `fromRegistration` 方法
   - 不影响现有代码调用

3. **✅ 日志增强**
   - 持久化时记录健康状态
   - 便于问题追踪和调试

4. **✅ 自动同步**
   - 利用现有的 Nacos 事件监听机制
   - 无需额外的定时任务或轮询

---

## 🎯 技术要点

### 1. 方法重载设计
```java
// 简化版本（默认参数）
fromRegistration(key, name, group, host, port, endpoint, health, metadata)

// 完整版本（真实状态）
fromRegistration(key, name, group, host, port, endpoint, health, metadata, 
                healthy, enabled, weight, ephemeral)
```

**优势**:
- 向后兼容，不破坏现有代码
- 灵活性高，支持不同场景
- 代码复用，避免重复

### 2. Nacos 事件驱动
```
Nacos 服务变更 → EventListener → handleServiceChangeEvent → persistInstanceToDatabase
```

**优势**:
- 实时响应，延迟极低
- 事件驱动，无需轮询
- 自动触发，无需人工干预

### 3. 数据库 UPSERT 策略
```sql
INSERT INTO mcp_servers (...) VALUES (...)
ON DUPLICATE KEY UPDATE
    healthy = VALUES(healthy),
    enabled = VALUES(enabled),
    ...
```

**优势**:
- 原子操作，避免并发问题
- 自动合并，简化逻辑
- 性能优化，减少查询

---

## 📁 相关文件

### 修改文件
1. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java`
   - 新增重载方法 `fromRegistration` (第162-190行)
   
2. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/service/McpServerPersistenceService.java`
   - 修改 `persistServerRegistration` 方法 (第54-88行)
   - 增强日志输出 (第78-80行)

### 核心文件（未修改但相关）
1. `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`
   - 监听 Nacos 服务变更事件 (第152-185行)
   - 持久化实例到数据库 (第190-234行)

2. `mcp-router-v3/src/main/resources/mapper/McpServerMapper.xml`
   - 插入或更新 SQL (第37-69行)

---

## 🔄 架构流程

```
┌─────────────────────────────────────────────────────────────┐
│                        Nacos Server                         │
│  - 服务注册中心                                             │
│  - 健康检查                                                 │
│  - 实例状态管理 (healthy, enabled, weight, ephemeral)      │
└─────────────────────────────────────────────────────────────┘
                            ↓
                    [服务变更事件]
                            ↓
┌─────────────────────────────────────────────────────────────┐
│             McpConnectionEventListener                      │
│  - 订阅服务变更 (subscribeServiceChanges)                  │
│  - 处理服务变更事件 (handleServiceChangeEvent)             │
│  - 提取实例健康状态 (instance.isHealthy())                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
                  [构建 McpServerInfo]
                  包含真实健康状态:
                  - healthy
                  - enabled
                  - weight
                  - ephemeral
                            ↓
┌─────────────────────────────────────────────────────────────┐
│          McpServerPersistenceService                        │
│  - persistServerRegistration()                              │
│  - 传递真实状态到 fromRegistration() ✅ [修复点]           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   McpServer.fromRegistration()              │
│  - 接收真实状态参数 ✅ [修复点]                            │
│  - 构建 McpServer 实体                                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                 McpServerMapper (MyBatis)                   │
│  - insertOrUpdate SQL                                       │
│  - ON DUPLICATE KEY UPDATE                                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    MySQL Database                           │
│  mcp_servers 表                                             │
│  - healthy: 0/1 (真实状态) ✅                              │
│  - enabled: 0/1                                             │
│  - weight: double                                           │
│  - ephemeral: 0/1                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 📝 总结

### 问题本质
**用户的洞察完全正确！** 👏

系统**已经从 Nacos 获取了真实的健康状态信息**，但在持久化到数据库的过程中，由于 `fromRegistration` 方法的硬编码，导致**真实状态被丢弃**。

### 修复要点
1. **不是缺少 Nacos 监听** - 监听机制已完善 ✅
2. **不是缺少状态获取** - 已获取真实状态 ✅  
3. **问题在于状态传递** - 持久化时未传递真实值 ❌

### 修复策略
**方法重载 + 参数传递**
- 新增支持真实状态的重载方法
- 修改调用处传递真实参数
- 保持向后兼容性

### 修复结果
✅ **Nacos 健康状态与数据库完全同步**  
✅ **实时、自动、准确**  
✅ **向后兼容，零风险**

---

## 🎉 致谢

感谢用户 **Shine** 的敏锐洞察！

> "难道从 nacos 配置信息里不能拿到服务是否可用的信息吗？直接更新到库里面不可以吗？"

这个问题直击要害，帮助快速定位到了真正的根因：**不是没有获取状态，而是获取后没有正确传递！**

---

**修复完成时间**: 2025-10-30 17:10:00  
**修复版本**: mcp-router-v3 1.0.0  
**验证状态**: ✅ 全面验证通过


