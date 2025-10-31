# 健康检查数据准确性问题分析与修复建议

## 📋 问题总结

**用户报告**: `cf-server` 和 `mcp-server-v2-20250718` 在数据库中标记为健康（`healthy=1`），但实际服务并未运行。

## 🔍 问题验证

### 数据库状态 vs 实际状态

| 服务名 | 端点 | 数据库 healthy | 实际状态 | last_health_check | 结论 |
|--------|------|----------------|----------|-------------------|------|
| cf-server | 127.0.0.1:8899 | ✅ 1 | ❌ 无响应 | NULL | ⚠️ 数据不准确 |
| mcp-server-v2-20250718 | 127.0.0.1:8090 | ✅ 1 | ❌ 无响应 | NULL | ⚠️ 数据不准确 |
| mcp-router-v3 | 127.0.0.1:8052 | ✅ 1 | ✅ 正常 | NULL | ⚠️ 应更新检查时间 |

### 验证命令

```bash
# 查询数据库状态
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
SELECT server_name, CONCAT(host, ':', port) AS endpoint, 
       healthy, last_health_check, updated_at 
FROM mcp_servers 
WHERE server_name IN ('cf-server', 'mcp-server-v2-20250718') 
AND deleted_at IS NULL;"

# 实际健康检查
curl -s http://127.0.0.1:8899/health  # cf-server - 失败
curl -s http://127.0.0.1:8090/health  # mcp-server-v2-20250718 - 失败
curl -s http://127.0.0.1:8052/health  # mcp-router-v3 - 成功
```

## 🐛 根本原因分析

### 1. 缺少初始健康检查时间

**代码位置**: `McpServer.fromRegistration()` (McpServer.java:165-190)

```java
public static McpServer fromRegistration(...) {
    return McpServer.builder()
        .serverKey(serverKey)
        .serverName(serverName)
        // ... 其他字段 ...
        .registeredAt(LocalDateTime.now())
        .build();
        // ❌ 缺少 .lastHealthCheck(LocalDateTime.now())
}
```

**问题**: 从 Nacos 同步服务注册信息时，没有设置 `lastHealthCheck` 字段，导致数据库中该字段为 `NULL`。

### 2. 定时健康检查被禁用

**代码位置**: `HealthCheckService.performHealthCheck()` (HealthCheckService.java:223-229)

```java
// @Scheduled(fixedRate = 30000) // 禁用轮询，使用事件驱动
public void performHealthCheck() {
    log.info("🚫 Scheduled health check disabled - using event-driven connection monitoring instead");
    // 事件驱动机制通过 McpConnectionEventListener 实现
}
```

**问题**: 
- 定时健康检查被禁用，依赖事件驱动机制
- 事件驱动只在服务状态变化时触发，不会主动检查已注册但未运行的服务
- 对于已停止的服务，无法及时发现并更新健康状态

### 3. 健康状态来源于 Nacos

**代码位置**: `McpServerPersistenceService.persistServerRegistration()` (McpServerPersistenceService.java:71)

```java
McpServer server = McpServer.fromRegistration(
    serverKey, serverInfo.getName(), serverInfo.getServiceGroup(),
    serverInfo.getHost(), serverInfo.getPort(), serverInfo.getSseEndpoint(),
    "/health",  // healthEndpoint
    metadata,
    serverInfo.isHealthy(),  // ❌ 直接使用 Nacos 的健康状态
    serverInfo.getEnabled(),
    serverInfo.getWeight(),
    serverInfo.isEphemeral()
);
```

**问题**:
- `healthy` 字段直接来自 Nacos (`serverInfo.isHealthy()`)
- Nacos 可能认为服务健康（已注册），但实际服务未运行
- 数据库只是"镜像"Nacos 的状态，没有独立验证

### 4. 缺少主动健康检查机制

**现状**:
- ✅ 有健康检查的代码 (`HealthCheckService`)
- ✅ 有健康检查超时标记机制 (`checkAndMarkTimeoutServers()`)
- ❌ 但定时检查被禁用
- ❌ 事件驱动只能被动响应，无法主动发现问题

**超时检查逻辑** (McpServerPersistenceService.java:282-302):
```java
@Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
public void checkAndMarkTimeoutServers() {
    // 查询超过5分钟未健康检查的服务器
    List<McpServer> timeoutServers = mcpServerMapper.selectServersByHealthCheckTimeout(5);
    
    if (!timeoutServers.isEmpty()) {
        int rows = mcpServerMapper.batchMarkOffline(serverKeys, LocalDateTime.now());
        log.warn("⚠️ Marked {} servers as offline due to health check timeout", rows);
    }
}
```

**问题**: 这个逻辑依赖于 `last_health_check` 字段，但该字段从未被设置（始终为 NULL），所以超时检查永远不会触发！

## 🛠️ 修复方案

### 方案 1: 立即修复 - 添加初始健康检查时间（推荐）

**修改文件**: `src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java`

**修改位置**: `fromRegistration` 方法

```java
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
        .healthy(healthy != null ? healthy : true)
        .enabled(enabled != null ? enabled : true)
        .weight(weight != null ? weight : 1.0)
        .ephemeral(ephemeral != null ? ephemeral : true)
        .clusterName("DEFAULT")
        .version("1.0.0")
        .protocol("mcp-sse")
        .metadata(metadata)
        .totalRequests(0L)
        .totalErrors(0L)
        .lastHealthCheck(LocalDateTime.now())  // ✅ 添加这一行
        .registeredAt(LocalDateTime.now())
        .build();
}
```

**效果**:
- ✅ 新注册的服务会设置初始健康检查时间
- ✅ 超时检查机制可以正常工作
- ✅ 5分钟未更新的服务会被自动标记为离线

### 方案 2: 增强 - 启用定期主动健康检查

**修改文件**: `src/main/java/com/pajk/mcpbridge/core/service/HealthCheckService.java`

**选项 A: 启用简单定时检查**

```java
@Scheduled(fixedRate = 60000)  // 每分钟检查一次
public void performHealthCheck() {
    log.debug("Performing scheduled health check");
    
    // 获取所有启用的服务
    serverRegistry.getAllHealthyServers("*", "*")
        .cast(McpServerInfo.class)
        .flatMap(server -> {
            String serverKey = buildServerKey(server);
            
            // 简单的 HTTP 健康检查
            return checkServerHealth(server)
                .doOnSuccess(healthy -> {
                    // 更新数据库
                    if (persistenceEventPublisher != null) {
                        persistenceEventPublisher.publishServerHealthCheck(serverKey, healthy);
                    }
                })
                .onErrorReturn(false);
        })
        .subscribe();
}
```

**选项 B: 更智能的检查策略**

```java
@Scheduled(fixedRate = 60000)  // 每分钟检查一次
public void performSmartHealthCheck() {
    log.debug("Performing smart health check");
    
    // 只检查超过 30 秒未检查的服务
    mcpServerMapper.selectServersNeedHealthCheck(30)  // 需要新增 SQL
        .forEach(server -> {
            checkAndUpdateHealth(server);
        });
}
```

### 方案 3: 临时修复 - 手动修正当前数据

**修正命令**:

```bash
# 1. 为所有服务设置初始健康检查时间
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
UPDATE mcp_servers 
SET last_health_check = updated_at 
WHERE last_health_check IS NULL 
AND deleted_at IS NULL;"

# 2. 手动标记不健康的服务
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
UPDATE mcp_servers 
SET healthy = 0, updated_at = NOW() 
WHERE server_name IN ('cf-server', 'mcp-server-v2-20250718') 
AND deleted_at IS NULL;"

# 3. 验证修正结果
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
SELECT server_name, CONCAT(host, ':', port) AS endpoint, 
       healthy, last_health_check, updated_at 
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC;"
```

## 📊 影响分析

### 当前问题的影响

| 影响项 | 严重程度 | 说明 |
|--------|----------|------|
| 路由准确性 | 🔴 高 | 可能将请求路由到不健康的服务 |
| 监控准确性 | 🔴 高 | 监控数据不准确，误导运维 |
| 超时检查 | 🟡 中 | 超时检查机制失效（依赖 last_health_check） |
| 数据可信度 | 🟡 中 | 数据库数据不可信，需实时验证 |

### 修复后的改善

| 改善项 | 方案 1 | 方案 2 | 方案 3 |
|--------|--------|--------|--------|
| 设置初始检查时间 | ✅ | ✅ | ✅ |
| 超时检查可用 | ✅ | ✅ | ❌ (临时) |
| 主动健康检查 | ❌ | ✅ | ❌ |
| 实时准确性 | 🟡 中 | ✅ 高 | 🟡 中 |
| 代码复杂度 | 低 | 中 | 无 |

## 🎯 推荐修复策略

### 短期修复（立即实施）

1. **修复代码** - 方案 1
   - 修改 `McpServer.fromRegistration()` 添加 `.lastHealthCheck(LocalDateTime.now())`
   - 重新编译部署

2. **修正数据** - 方案 3
   - 执行 SQL 修正现有数据
   - 手动标记不健康的服务

### 中期增强（规划实施）

3. **启用定期检查** - 方案 2
   - 实现智能健康检查策略
   - 只检查需要检查的服务（减少开销）
   - 更新 `last_health_check` 和 `healthy` 字段

### 长期优化（未来考虑）

4. **完善监控**
   - 添加健康检查指标暴露
   - 集成 Prometheus/Grafana
   - 告警机制

5. **优化架构**
   - 评估事件驱动 vs 定时检查的权衡
   - 考虑混合策略（事件驱动 + 兜底检查）

## 📝 实施步骤

### Step 1: 代码修复

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3

# 1. 编辑文件
vim src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java
# 在 fromRegistration 方法中添加 .lastHealthCheck(LocalDateTime.now())

# 2. 编译
mvn clean package -DskipTests

# 3. 停止应用
kill $(ps aux | grep mcp-router-v3 | grep -v grep | awk '{print $2}')

# 4. 启动应用
nohup java -jar target/mcp-router-v3-1.0.0.jar > app.log 2>&1 &
```

### Step 2: 数据修正

```bash
# 修正现有数据
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge << 'SQL'
-- 1. 为所有服务设置初始健康检查时间
UPDATE mcp_servers 
SET last_health_check = updated_at 
WHERE last_health_check IS NULL 
AND deleted_at IS NULL;

-- 2. 标记不健康的服务
UPDATE mcp_servers 
SET healthy = 0, updated_at = NOW() 
WHERE server_name IN ('cf-server', 'mcp-server-v2-20250718') 
AND deleted_at IS NULL;
SQL
```

### Step 3: 验证修复

```bash
# 1. 检查应用日志
tail -50 app.log | grep -E "error|warn|health" -i

# 2. 验证数据库
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
SELECT server_name, CONCAT(host, ':', port) AS endpoint, 
       healthy, last_health_check, updated_at 
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC LIMIT 10;"

# 3. 等待超时检查（2分钟后）
sleep 120
tail -20 app.log | grep "timeout\|offline"
```

### Step 4: 监控效果

```bash
# 创建监控脚本
cat > monitor_health.sh << 'EOF'
#!/bin/bash
while true; do
    echo "=== $(date) ==="
    mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
    SELECT 
        COUNT(*) as total,
        SUM(CASE WHEN healthy = 1 THEN 1 ELSE 0 END) as healthy,
        SUM(CASE WHEN healthy = 0 THEN 1 ELSE 0 END) as unhealthy,
        SUM(CASE WHEN last_health_check IS NULL THEN 1 ELSE 0 END) as no_check
    FROM mcp_servers 
    WHERE deleted_at IS NULL;" | column -t
    sleep 60
done
EOF

chmod +x monitor_health.sh
```

## 🔬 测试验证

### 测试用例 1: 新服务注册

```bash
# 期望: 新注册的服务应该有 last_health_check
# 1. 注册一个新服务到 Nacos
# 2. 检查数据库
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
SELECT server_name, last_health_check, created_at 
FROM mcp_servers 
WHERE server_name = 'test-service' 
AND deleted_at IS NULL;"

# 验证: last_health_check 不应为 NULL，且接近 created_at
```

### 测试用例 2: 超时检查

```bash
# 期望: 超过5分钟未更新的服务应被标记为离线
# 1. 手动设置一个服务的 last_health_check 为 6 分钟前
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
UPDATE mcp_servers 
SET last_health_check = DATE_SUB(NOW(), INTERVAL 6 MINUTE) 
WHERE server_name = 'cf-server';"

# 2. 等待超时检查任务执行（最多2分钟）
sleep 120

# 3. 检查是否被标记为离线
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
SELECT server_name, healthy, last_health_check, deleted_at 
FROM mcp_servers 
WHERE server_name = 'cf-server';"

# 验证: deleted_at 不应为 NULL（已标记离线）
```

### 测试用例 3: 定期检查（如实施方案2）

```bash
# 期望: 每分钟所有服务的 last_health_check 应更新
# 1. 记录当前时间
NOW=$(date)

# 2. 等待1分钟
sleep 60

# 3. 检查 last_health_check 是否更新
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "
SELECT server_name, last_health_check 
FROM mcp_servers 
WHERE deleted_at IS NULL 
AND last_health_check > '$NOW';"

# 验证: 应该有服务的 last_health_check 在过去1分钟内更新
```

## 📚 相关文档

- **代码文件**:
  - `src/main/java/com/pajk/mcpbridge/persistence/entity/McpServer.java`
  - `src/main/java/com/pajk/mcpbridge/persistence/service/McpServerPersistenceService.java`
  - `src/main/java/com/pajk/mcpbridge/core/service/HealthCheckService.java`
  - `src/main/resources/mapper/McpServerMapper.xml`

- **数据库表**: `mcp_servers`

- **相关机制**:
  - 服务注册持久化
  - 健康检查超时标记 (`checkAndMarkTimeoutServers`)
  - 定期清理过期服务 (`cleanupExpiredOfflineServers`)

## ✅ 总结

### 问题根源

1. **代码缺陷**: `fromRegistration` 未设置 `lastHealthCheck`
2. **机制失效**: 定时健康检查被禁用，依赖事件驱动
3. **数据来源**: 健康状态来自 Nacos，未独立验证

### 核心修复

- ✅ 在 `fromRegistration` 中添加 `.lastHealthCheck(LocalDateTime.now())`
- ✅ 手动修正现有数据的 `last_health_check`
- ✅ 标记实际不健康的服务

### 建议增强

- 🔄 考虑启用定期健康检查（轻量级）
- 🔄 完善监控和告警
- 🔄 优化健康检查策略（智能检查）

---

**文档版本**: 1.0.0  
**创建时间**: 2025-10-30  
**作者**: MCP Router Team  
**状态**: 待实施


