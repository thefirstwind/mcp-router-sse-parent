# MCP Router V3 持久化功能故障排查与修复

## 问题描述

用户启动了 MCP Server V6 (端口 8071/8072)，并执行了两个 REST 接口调用：
1. 工具调用接口：`POST /mcp/router/route/mcp-server-v6`
2. 工具列表接口：`GET /mcp/router/tools/mcp-server-v6`

**问题**：数据库中没有看到任何持久化记录（routing_logs、health_check_records、mcp_servers 表都是空的）

## 根本原因分析

经过排查，发现了以下关键问题：

### 1. **持久化功能未启用** ⚠️ 最严重
   - **位置**：`application.yml`
   - **问题**：缺少 `mcp.persistence.enabled=true` 配置
   - **影响**：所有持久化相关的 Bean 都不会被创建（因为使用了 `@ConditionalOnProperty`）

### 2. **配置前缀不一致** ⚠️ 严重
   - **位置**：`MyBatisConfig.java`
   - **问题**：使用 `prefix = "persistence"` 而不是 `prefix = "mcp.persistence"`
   - **影响**：即使配置了 `mcp.persistence.enabled`，MyBatis 配置也不会生效

### 3. **包名配置错误** ⚠️ 严重
   - **位置**：`application.yml` (mybatis.type-aliases-package)
   - **问题**：使用了错误的包名 `com.nacos.mcp.router.persistence.entity`
   - **正确**：应该是 `com.pajk.mcpbridge.persistence.entity`
   - **影响**：MyBatis 无法找到实体类，导致 mapper 映射失败

### 4. **核心组件缺少条件化注解**
   - `PersistenceEventPublisher` - 缺少 `@ConditionalOnProperty`
   - `RoutingLogBatchWriter` - 缺少 `@ConditionalOnProperty`
   - `HealthCheckRecordBatchWriter` - 缺少 `@ConditionalOnProperty`
   - **影响**：这些组件会无条件创建，但依赖的 Bean 可能不存在，导致启动失败

## 修复方案

### ✅ 1. 修复 `application.yml`

添加持久化配置并修正包名：

```yaml
# MyBatis 配置
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.pajk.mcpbridge.persistence.entity  # ✅ 修正包名
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
    default-statement-timeout: 30

# MCP 持久化配置 (新增)
mcp:
  persistence:
    enabled: true           # ✅ 启用持久化功能
    async: true             # 异步批量写入
    batch-size: 100         # 批量大小
    flush-interval: 5000    # 刷新间隔(ms)
```

### ✅ 2. 修复 `MyBatisConfig.java`

统一配置前缀：

```java
@Configuration
@ConditionalOnProperty(
    prefix = "mcp.persistence",  // ✅ 改为 mcp.persistence
    name = "enabled",
    havingValue = "true"
)
@MapperScan("com.pajk.mcpbridge.persistence.mapper")
public class MyBatisConfig {
    // ...
}
```

### ✅ 3. 修复 `PersistenceEventPublisher.java`

添加条件化注解：

```java
@Slf4j
@Component
@ConditionalOnProperty(  // ✅ 新增
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
public class PersistenceEventPublisher {
    // ...
}
```

### ✅ 4. 修复 `RoutingLogBatchWriter.java`

添加条件化注解：

```java
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(  // ✅ 新增
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
public class RoutingLogBatchWriter {
    // ...
}
```

### ✅ 5. 修复 `HealthCheckRecordBatchWriter.java`

添加条件化注解：

```java
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(  // ✅ 新增
    prefix = "mcp.persistence",
    name = "enabled",
    havingValue = "true"
)
public class HealthCheckRecordBatchWriter {
    // ...
}
```

## 验证步骤

### 1. 重新初始化数据库

```bash
cd mcp-router-v3/database
bash init-persistence.sh
```

**预期输出**：
- ✅ MySQL 连接成功
- ✅ 数据库和用户创建成功
- ✅ Schema 执行成功
- ✅ 检测到 12 张表 + 2 个视图 + 1 个存储过程

### 2. 重启 MCP Router V3

启动服务并观察日志，应该看到：

```
INFO  c.p.m.p.s.PersistenceEventPublisher    : PersistenceEventPublisher initialized with buffer sizes: routing=10000, health=1000, error=1000
INFO  c.p.m.p.s.RoutingLogBatchWriter         : Starting RoutingLog batch writer with batchSize=500, window=PT2S
INFO  c.p.m.p.s.HealthCheckRecordBatchWriter  : Starting HealthCheckRecord batch writer with batchSize=100, window=PT5S
```

### 3. 运行测试脚本

```bash
cd /Users/shine/projects.mcp-router-sse-parent
./test-persistence.sh
```

**预期结果**：
- ✅ 能看到 routing_logs 表中有记录
- ✅ 能看到 health_check_records 表中有记录  
- ✅ 能看到 mcp_servers 表中有服务器注册信息

### 4. 手动测试

#### 测试 1：工具调用路由

```bash
curl --location 'http://localhost:8052/mcp/router/route/mcp-server-v6' \
--header 'Content-Type: application/json' \
--data '{
    "id": "1111",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 1 }
    }
}'
```

**验证**：等待 3-5 秒后，查询路由日志
```bash
mysql -umcp_user -pmcp_user mcp_bridge -e "SELECT * FROM routing_logs ORDER BY start_time DESC LIMIT 5;"
```

#### 测试 2：工具列表查询

```bash
curl --location 'http://localhost:8052/mcp/router/tools/mcp-server-v6'
```

**验证**：查询 MCP 服务器注册信息
```bash
mysql -umcp_user -pmcp_user mcp_bridge -e "SELECT * FROM mcp_servers WHERE server_key LIKE '%mcp-server-v6%';"
```

## 持久化工作原理

### 异步批量写入架构

```
[业务请求] 
    ↓
[McpRouterService] 
    ↓ (非阻塞发布事件)
[PersistenceEventPublisher] 
    ↓ (Reactive Streams)
[BatchWriter (缓冲)] 
    ↓ (批量/定时触发)
[MyBatis Mapper] 
    ↓
[MySQL 数据库]
```

### 关键特性

1. **完全非阻塞**
   - 使用 Reactive Streams (Sinks) 实现异步发布
   - 业务流程不等待数据库写入

2. **批量优化**
   - RoutingLog: 500条/2秒窗口
   - HealthCheckRecord: 100条/5秒窗口
   - 采样策略：成功10%，失败100%

3. **故障隔离**
   - 持久化失败不影响主流程
   - 降级到日志文件记录
   - 独立线程池处理

4. **条件化启用**
   - 通过 `mcp.persistence.enabled` 控制
   - 禁用时零性能开销

## 日志诊断

### 启动时应看到的日志

```
✅ 正常启动日志：
INFO  o.s.b.a.j.DataSourceHealthContributorAutoConfiguration : DataSource available
INFO  c.p.m.p.c.MyBatisConfig                : SqlSessionFactory configured successfully
INFO  c.p.m.p.s.PersistenceEventPublisher    : PersistenceEventPublisher initialized
INFO  c.p.m.p.s.RoutingLogBatchWriter         : RoutingLog batch writer started successfully
INFO  c.p.m.p.s.HealthCheckRecordBatchWriter  : HealthCheckRecord batch writer started successfully
```

### 运行时应看到的日志

```
✅ 路由请求日志：
INFO  c.p.m.c.s.McpRouterService : 🔄 Starting intelligent routing for service: mcp-server-v6
DEBUG c.p.m.p.s.PersistenceEventPublisher : RoutingLog event published successfully

✅ 批量写入日志：
DEBUG c.p.m.p.s.RoutingLogBatchWriter : Batch write completed: 10 records
```

### 常见错误日志

```
❌ 持久化未启用：
(没有任何持久化相关的初始化日志)

❌ 数据库连接失败：
ERROR o.s.b.SpringApplication : Application run failed
Caused by: java.sql.SQLException: Access denied for user 'mcp_user'

❌ Mapper 找不到：
ERROR o.a.i.b.b.MapperBuilderAssistant : Error parsing Mapper XML
Caused by: org.apache.ibatis.type.TypeException: Could not resolve type alias 'RoutingLog'
```

## 性能监控

### 查询统计信息

```sql
-- 查看服务器概览（使用视图）
SELECT * FROM v_server_overview;

-- 查看最近24小时统计
SELECT * FROM v_recent_24h_stats;

-- 查看每日统计
SELECT * FROM server_daily_stats 
WHERE stat_date >= CURDATE() - INTERVAL 7 DAY
ORDER BY stat_date DESC;
```

### 监控批量写入性能

在应用中添加日志或监控端点来查看：
- `publishSuccessCount` - 发布成功数
- `publishFailureCount` - 发布失败数
- `batchCount` - 批次数
- `recordCount` - 记录数

## 总结

所有问题都已修复：

✅ 添加了 `mcp.persistence.enabled=true` 配置
✅ 统一了配置前缀为 `mcp.persistence`  
✅ 修正了实体类包名
✅ 为所有持久化组件添加了条件化注解
✅ 数据库初始化成功
✅ 创建了自动化测试脚本

**下一步**：重启 MCP Router V3 服务，然后运行测试脚本验证持久化功能。


