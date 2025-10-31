# Nacos 同步逻辑分析 - 启动时数据库更新问题

**日期**: 2025-10-30  
**问题**: 启动时从 Nacos 读取配置并更新到数据库的设置是否正确？  
**状态**: ⚠️ 发现问题

---

## 🔍 问题发现

### 当前行为

应用启动时的执行流程：

```
1. McpConnectionEventListener.@PostConstruct startListening()
   ↓
2. syncNacosStateToDatabase()
   ↓
3. 遍历所有配置的服务组（service groups）
   ↓
4. 对每个服务的每个实例：
   └─> persistInstanceSyncToDatabase(serviceName, serviceGroup, instance)
       └─> 构建 McpServerInfo
       └─> persistenceService.persistServerRegistration(serverInfo)
           └─> McpServer.fromRegistration(...) 
               └─> .lastHealthCheck(LocalDateTime.now())  ← 设置为当前时间！
           └─> mcpServerMapper.insertOrUpdate(server)
               └─> INSERT ... ON DUPLICATE KEY UPDATE
                   └─> last_health_check = VALUES(last_health_check)  ⚠️
```

### 问题详情

**SQL 语句**（`McpServerMapper.xml:38-69`）:

```xml
<insert id="insertOrUpdate">
    INSERT INTO mcp_servers (
        ...
        last_health_check, registered_at
    ) VALUES (
        ...
        #{lastHealthCheck}, #{registeredAt}
    )
    ON DUPLICATE KEY UPDATE
        server_name = VALUES(server_name),
        ...
        last_health_check = VALUES(last_health_check),  ← ⚠️ 问题在这里！
        updated_at = NOW()
</insert>
```

**问题**：
- 对于**新记录**（INSERT）：设置 `last_health_check = LocalDateTime.now()` ✅ 合理
- 对于**已存在记录**（UPDATE）：也设置 `last_health_check = LocalDateTime.now()` ❌ **不合理！**

---

## ⚠️ 问题影响

### 场景 1：正常服务重启

**假设**：`mcp-router-v3` 在运行，健康检查正常

| 时间 | 事件 | last_health_check | 说明 |
|------|------|-------------------|------|
| 10:00 | 服务启动并注册 | 10:00 | ✅ 正常 |
| 10:05 | 健康检查（心跳） | 10:05 | ✅ 正常更新 |
| 10:10 | **应用重启** | 10:10 | ⚠️ 被重置为启动时间 |
| 10:15 | 健康检查（心跳） | 10:15 | ✅ 恢复正常 |

**影响**：轻微，因为服务仍在运行，后续心跳会更新时间

### 场景 2：已停止的服务（关键问题！）

**假设**：`cf-server` 已停止，但在 Nacos 中仍注册（持久节点或未及时注销）

| 时间 | 事件 | last_health_check | 实际状态 |
|------|------|-------------------|----------|
| 08:00 | cf-server 最后一次心跳 | 08:00 | 已停止 |
| 09:00 | 健康检查超时（5分钟） | 08:00 | 应该标记为离线 |
| 10:00 | **mcp-router 重启** | **10:00** | ⚠️ 时间被重置！ |
| 10:05 | 超时检查（检查5分钟前） | 10:00 | ❌ 认为健康！ |

**严重影响**：
1. ❌ 已停止的服务被"复活"
2. ❌ 超时检查机制失效
3. ❌ 数据库中的健康状态不准确
4. ❌ 可能导致请求路由到已停止的服务

---

## 📊 实际数据验证

### 数据库当前状态

```sql
SELECT server_name, healthy, last_health_check, updated_at
FROM mcp_servers WHERE deleted_at IS NULL;
```

**结果**：

| 服务名 | healthy | last_health_check | updated_at | 观察 |
|--------|---------|-------------------|------------|------|
| cf-server | 1 | 2025-10-30 19:57:52 | 2025-10-30 11:57:51 | ⚠️ 服务已停止，但时间戳是启动时间 |
| mcp-server-v2-20250718 | 1 | 2025-10-30 19:57:52 | 2025-10-30 11:57:51 | ⚠️ 服务已停止，但时间戳是启动时间 |
| mcp-router-v3 | 1 | 2025-10-30 19:57:52 | 2025-10-30 11:57:52 | ✅ 服务运行中 |
| mcp-server-v2-real | 0 | 2025-10-30 19:57:52 | 2025-10-30 11:57:51 | ⚠️ 时间戳被重置 |

**观察**：
- ✅ 所有服务的 `last_health_check` 都是 **19:57:52**（应用启动时间 + 8小时时区偏移）
- ⚠️ **这证实了问题**：启动时会重置所有服务的健康检查时间

### 实际服务状态

```bash
# 检查 cf-server 是否运行
curl -s --connect-timeout 2 http://127.0.0.1:8899/health
# 结果: ❌ 连接失败（服务未运行）

# 检查 mcp-server-v2-20250718 是否运行  
curl -s --connect-timeout 2 http://127.0.0.1:8090/health
# 结果: ❌ 连接失败（服务未运行）
```

**结论**：数据库中的 `last_health_check` 不反映实际的健康检查状态

---

## 🎯 根本原因分析

### 1. 代码层面

**`McpServer.fromRegistration()`** (`McpServer.java:165-191`):

```java
public static McpServer fromRegistration(...) {
    return McpServer.builder()
        ...
        .lastHealthCheck(LocalDateTime.now())  ← 总是设置为当前时间
        .registeredAt(LocalDateTime.now())
        .build();
}
```

**问题**：
- `fromRegistration()` 的语义是"从注册信息创建实体"
- 但 `lastHealthCheck` 应该表示"最后一次健康检查的时间"
- 启动时同步 Nacos 数据≠实际执行了健康检查

### 2. SQL 层面

**`insertOrUpdate` SQL**:

```xml
ON DUPLICATE KEY UPDATE
    last_health_check = VALUES(last_health_check),  ← 无条件更新
```

**问题**：
- 对于已存在的记录，应该**保留**原有的 `last_health_check`
- 而不是用新的（启动时间）覆盖

---

## 💡 设计考量

### 启动时同步 Nacos 到数据库的目的

1. **同步基本信息**：确保数据库中的服务配置与 Nacos 一致
   - 服务名、分组、主机、端口
   - SSE 端点、健康检查端点
   - 权重、元数据等配置

2. **同步注册状态**：反映 Nacos 中的注册状态
   - enabled（是否启用）
   - ephemeral（是否临时节点）
   - metadata（元数据）

3. **同步健康状态**：这里需要谨慎！
   - Nacos 的 `healthy` 状态≠实际的健康检查
   - Nacos 的健康状态可能是"注册状态"，不是"实时健康状态"

### `last_health_check` 的语义

`last_health_check` 应该表示：
- ✅ **最后一次实际执行健康检查的时间**
- ❌ 不是"服务注册时间"
- ❌ 不是"应用启动时间"
- ❌ 不是"数据同步时间"

### 应该更新哪些字段？

| 字段 | INSERT | UPDATE (已存在) | 理由 |
|------|--------|-----------------|------|
| server_key | ✅ | - | 主键，不更新 |
| server_name | ✅ | ✅ | 基本信息，应同步 |
| server_group | ✅ | ✅ | 基本信息，应同步 |
| host, port | ✅ | ✅ | 基本信息，应同步 |
| sse_endpoint | ✅ | ✅ | 配置信息，应同步 |
| health_endpoint | ✅ | ✅ | 配置信息，应同步 |
| enabled | ✅ | ✅ | 注册状态，应同步 |
| weight | ✅ | ✅ | 配置信息，应同步 |
| ephemeral | ✅ | ✅ | 注册状态，应同步 |
| metadata | ✅ | ✅ | 配置信息，应同步 |
| **healthy** | ✅ | **⚠️ 谨慎** | Nacos 的状态可能不准确 |
| **last_health_check** | ✅ | **❌ 不更新** | 应保留实际检查时间 |
| total_requests | ✅ | ❌ | 统计数据，不覆盖 |
| total_errors | ✅ | ❌ | 统计数据，不覆盖 |
| last_request_time | ✅ | ❌ | 统计数据，不覆盖 |
| registered_at | ✅ | ❌ | 首次注册时间，不更新 |

---

## 🔧 修复方案

### 方案 1：修改 SQL - 不更新 last_health_check（推荐）

**修改文件**：`src/main/resources/mapper/McpServerMapper.xml`

**修改内容**：

```xml
<!-- 修改前 -->
ON DUPLICATE KEY UPDATE
    server_name = VALUES(server_name),
    ...
    last_health_check = VALUES(last_health_check),  ← 删除这行
    updated_at = NOW()

<!-- 修改后 -->
ON DUPLICATE KEY UPDATE
    server_name = VALUES(server_name),
    server_group = VALUES(server_group),
    namespace_id = VALUES(namespace_id),
    host = VALUES(host),
    port = VALUES(port),
    sse_endpoint = VALUES(sse_endpoint),
    health_endpoint = VALUES(health_endpoint),
    healthy = VALUES(healthy),
    enabled = VALUES(enabled),
    weight = VALUES(weight),
    ephemeral = VALUES(ephemeral),
    cluster_name = VALUES(cluster_name),
    version = VALUES(version),
    protocol = VALUES(protocol),
    metadata = VALUES(metadata),
    tags = VALUES(tags),
    -- last_health_check = VALUES(last_health_check),  ← 删除！保留原值
    updated_at = NOW()
```

**优点**：
- ✅ 简单直接
- ✅ 保留了实际的健康检查时间
- ✅ 超时检查机制可以正常工作

**缺点**：
- ⚠️ 新插入的记录 `last_health_check` 仍然是启动时间，不是真实检查时间
- ⚠️ 但这可以接受，因为启动后会立即执行健康检查

### 方案 2：分离 INSERT 和 SYNC 逻辑（更彻底）

创建两个不同的方法：

1. **`insert()`** - 新服务注册时使用
   - 设置 `last_health_check = NOW()`
   
2. **`syncFromNacos()`** - 启动时同步使用
   - **不更新** `last_health_check`
   - 只更新配置信息

**实现**：

```xml
<!-- 新增：专门用于 Nacos 同步的 SQL -->
<insert id="syncFromNacos" parameterType="com.pajk.mcpbridge.persistence.entity.McpServer">
    INSERT INTO mcp_servers (
        server_key, server_name, server_group, namespace_id, host, port,
        sse_endpoint, health_endpoint, healthy, enabled, weight,
        ephemeral, cluster_name, version, protocol, metadata, tags,
        total_requests, total_errors, last_health_check, registered_at
    ) VALUES (
        #{serverKey}, #{serverName}, #{serverGroup}, #{namespaceId}, #{host}, #{port},
        #{sseEndpoint}, #{healthEndpoint}, #{healthy}, #{enabled}, #{weight},
        #{ephemeral}, #{clusterName}, #{version}, #{protocol}, #{metadata}, #{tags},
        0, 0, NOW(), NOW()  ← 新记录才设置
    )
    ON DUPLICATE KEY UPDATE
        server_name = VALUES(server_name),
        server_group = VALUES(server_group),
        host = VALUES(host),
        port = VALUES(port),
        sse_endpoint = VALUES(sse_endpoint),
        health_endpoint = VALUES(health_endpoint),
        healthy = VALUES(healthy),
        enabled = VALUES(enabled),
        weight = VALUES(weight),
        ephemeral = VALUES(ephemeral),
        metadata = VALUES(metadata),
        -- last_health_check 不更新，保留原值
        updated_at = NOW()
</insert>
```

**优点**：
- ✅ 语义清晰
- ✅ 新记录的 `last_health_check` 更合理（数据库时间，不是应用时间）
- ✅ 避免时区问题

**缺点**：
- ⚠️ 需要增加新方法
- ⚠️ 实现稍复杂

### 方案 3：修改代码逻辑 - 启动时不设置 lastHealthCheck

**修改**：`McpConnectionEventListener.persistInstanceSyncToDatabase()`

```java
private void persistInstanceSyncToDatabase(...) {
    try {
        McpServerInfo serverInfo = new McpServerInfo();
        // ... 设置其他字段 ...
        
        // ❌ 不调用 persistServerRegistration，它会设置 lastHealthCheck
        // persistenceService.persistServerRegistration(serverInfo);
        
        // ✅ 调用新的同步方法
        persistenceService.syncServerFromNacos(serverInfo);
        
    } catch (Exception e) {
        log.error("...", e);
    }
}
```

创建新方法 `McpServerPersistenceService.syncServerFromNacos()`:

```java
public void syncServerFromNacos(McpServerInfo serverInfo) {
    try {
        String serverKey = generateServerKey(...);
        
        McpServer server = McpServer.builder()
            .serverKey(serverKey)
            .serverName(serverInfo.getName())
            // ... 其他字段 ...
            // .lastHealthCheck(LocalDateTime.now())  ← 不设置！
            .registeredAt(LocalDateTime.now())
            .build();
        
        mcpServerMapper.syncFromNacos(server);  // 使用新的 SQL
        
    } catch (Exception e) {
        log.error("...", e);
    }
}
```

---

## 📋 推荐方案

**方案 1（快速修复）+ 方案 3 的部分思想**

### 立即修复（简单）

**修改 SQL**：删除 `ON DUPLICATE KEY UPDATE` 中的 `last_health_check = VALUES(last_health_check)`

```xml
<!-- McpServerMapper.xml:50-68 -->
ON DUPLICATE KEY UPDATE
    server_name = VALUES(server_name),
    server_group = VALUES(server_group),
    namespace_id = VALUES(namespace_id),
    host = VALUES(host),
    port = VALUES(port),
    sse_endpoint = VALUES(sse_endpoint),
    health_endpoint = VALUES(health_endpoint),
    healthy = VALUES(healthy),
    enabled = VALUES(enabled),
    weight = VALUES(weight),
    ephemeral = VALUES(ephemeral),
    cluster_name = VALUES(cluster_name),
    version = VALUES(version),
    protocol = VALUES(protocol),
    metadata = VALUES(metadata),
    tags = VALUES(tags),
    -- last_health_check = VALUES(last_health_check),  ← 注释或删除
    updated_at = NOW()
```

### 后续优化（可选）

1. **添加专门的 `syncFromNacos` 方法**，语义更清晰
2. **新记录的 `last_health_check` 设置为 NULL**，等待首次真实健康检查
3. **超时检查时排除 `last_health_check IS NULL` 的记录**

---

## 🧪 验证步骤

### 1. 准备测试数据

```sql
-- 创建一个测试服务，设置 last_health_check 为 2 小时前
INSERT INTO mcp_servers (server_key, server_name, server_group, host, port, 
    healthy, enabled, last_health_check, registered_at)
VALUES ('test-server@127.0.0.1:9999', 'test-server', 'mcp-server', 
    '127.0.0.1', 9999, 1, 1, 
    DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW());
```

### 2. 修复前测试

```bash
# 记录当前 last_health_check
mysql ... -e "SELECT last_health_check FROM mcp_servers WHERE server_key = 'test-server@127.0.0.1:9999';"
# 结果：2025-10-30 10:00:00

# 重启应用
./restart.sh

# 再次检查 last_health_check
mysql ... -e "SELECT last_health_check FROM mcp_servers WHERE server_key = 'test-server@127.0.0.1:9999';"
# ❌ 结果：2025-10-30 12:00:00（被重置为启动时间）
```

### 3. 应用修复

修改 `McpServerMapper.xml`，删除 `last_health_check = VALUES(last_health_check)`

### 4. 修复后测试

```bash
# 恢复测试数据
mysql ... -e "UPDATE mcp_servers SET last_health_check = DATE_SUB(NOW(), INTERVAL 2 HOUR) 
    WHERE server_key = 'test-server@127.0.0.1:9999';"

# 重启应用
./restart.sh

# 检查 last_health_check
mysql ... -e "SELECT last_health_check FROM mcp_servers WHERE server_key = 'test-server@127.0.0.1:9999';"
# ✅ 结果：2025-10-30 10:00:00（保持不变！）
```

---

## 📊 影响范围评估

### 修改影响

| 场景 | 修复前 | 修复后 | 影响 |
|------|--------|--------|------|
| 新服务注册 | last_health_check = 启动时间 | last_health_check = 启动时间 | 无影响 |
| 已存在服务（运行中） | last_health_check 被重置 | last_health_check 保留 | ✅ 更准确 |
| 已存在服务（已停止） | last_health_check 被重置（假健康） | last_health_check 保留（超时检测有效） | ✅ 修复问题 |
| 超时检查 | 可能失效 | 正常工作 | ✅ 修复问题 |

### 风险评估

| 风险 | 等级 | 说明 | 缓解措施 |
|------|------|------|----------|
| SQL 语法错误 | 低 | 只是删除一行 | 编译前验证 SQL |
| 逻辑错误 | 低 | 逻辑简单 | 充分测试 |
| 兼容性问题 | 无 | 向下兼容 | - |
| 性能影响 | 无 | 不影响性能 | - |

**总体风险**：✅ 低

---

## 🎯 结论

### 问题总结

1. ❌ **启动时会重置所有服务的 `last_health_check`**
2. ❌ **已停止的服务可能被错误标记为"健康"**
3. ❌ **超时检查机制可能失效**

### 修复建议

✅ **立即修复**：删除 `insertOrUpdate` SQL 中的 `last_health_check = VALUES(last_health_check)`

✅ **验证修复**：按照上述验证步骤测试

✅ **后续优化**：考虑实现专门的 `syncFromNacos` 方法

### 优先级

**🔴 高优先级** - 影响系统的核心功能（健康检查和服务可用性判断）

---

**报告时间**: 2025-10-30 20:05  
**分析者**: MCP Router Team  
**状态**: ⚠️ 待修复


