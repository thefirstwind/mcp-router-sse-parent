# 持久化功能快速修复检查清单

## ✅ 已修复的问题

### 1. ✅ `application.yml` 配置
- [x] 添加 `mcp.persistence.enabled: true`
- [x] 修正 `mybatis.type-aliases-package` 为 `com.pajk.mcpbridge.persistence.entity`
- [x] 配置数据源连接信息

### 2. ✅ `MyBatisConfig.java`
- [x] 统一配置前缀为 `mcp.persistence`

### 3. ✅ `PersistenceEventPublisher.java`  
- [x] 添加 `@ConditionalOnProperty` 注解

### 4. ✅ `RoutingLogBatchWriter.java`
- [x] 添加 `@ConditionalOnProperty` 注解

### 5. ✅ `HealthCheckRecordBatchWriter.java`
- [x] 添加 `@ConditionalOnProperty` 注解

### 6. ✅ 数据库初始化
- [x] 运行 `init-persistence.sh` 成功
- [x] 12 张表创建完成
- [x] 2 个视图创建完成
- [x] 分区表配置正确

## 📋 需要用户执行的步骤

### 步骤 1: 停止当前服务

```bash
# 停止 MCP Router V3 (如果正在运行)
# Ctrl+C 或使用进程管理工具
```

### 步骤 2: 重新编译（如果需要）

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
mvn clean package -DskipTests
```

### 步骤 3: 启动 MCP Router V3

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
mvn spring-boot:run
```

### 步骤 4: 检查启动日志

**必须看到以下日志才算正常：**

```
✅ 数据源配置成功：
INFO o.s.b.a.j.DataSourceHealthContributorAutoConfiguration

✅ MyBatis 初始化成功：
INFO c.p.m.p.c.MyBatisConfig

✅ 持久化事件发布器启动：
INFO c.p.m.p.s.PersistenceEventPublisher : PersistenceEventPublisher initialized with buffer sizes

✅ 批量写入器启动：
INFO c.p.m.p.s.RoutingLogBatchWriter : RoutingLog batch writer started successfully
INFO c.p.m.p.s.HealthCheckRecordBatchWriter : HealthCheckRecord batch writer started successfully
```

### 步骤 5: 确保 MCP Server V6 正在运行

```bash
# 检查 mcp-server-v6 是否在端口 8071 或 8072 运行
lsof -i :8071
lsof -i :8072

# 或者
curl http://localhost:8071/health  # 或 8072
```

### 步骤 6: 运行自动化测试

```bash
cd /Users/shine/projects.mcp-router-sse-parent
./test-persistence.sh
```

**预期输出：**
- ✅ 数据库连接成功
- ✅ 路由请求发送成功
- ✅ 能看到 routing_logs 表中有记录
- ✅ 能看到 health_check_records 表中有记录（可能需要多次调用）
- ✅ 能看到 mcp_servers 表中有服务器信息

### 步骤 7: 手动验证（可选）

#### 7.1 发送测试请求

```bash
curl --location 'http://localhost:8052/mcp/router/route/mcp-server-v6' \
--header 'Content-Type: application/json' \
--data '{
    "id": "test-123",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 1 }
    }
}'
```

#### 7.2 等待 3-5 秒（批量写入间隔）

```bash
sleep 5
```

#### 7.3 查询数据库

```bash
# 查询路由日志
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT request_id, service_name, tool_name, is_success, duration_ms, start_time 
   FROM routing_logs 
   ORDER BY start_time DESC 
   LIMIT 5;"

# 查询健康检查记录
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT server_key, status, response_time_ms, check_time 
   FROM health_check_records 
   ORDER BY check_time DESC 
   LIMIT 5;"

# 查询 MCP 服务器
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT server_key, server_name, host, port, status, first_registered_at, last_heartbeat 
   FROM mcp_servers 
   WHERE server_key LIKE '%mcp-server-v6%';"
```

## ❌ 故障排查

### 如果没有看到持久化日志

**检查 1**: 确认配置文件

```bash
grep -A 4 "mcp:" /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/src/main/resources/application.yml
```

应该看到：
```yaml
mcp:
  persistence:
    enabled: true
```

**检查 2**: 确认类路径

```bash
ls -la /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/
```

### 如果数据库中没有记录

**可能原因 1**: 批量写入还未触发
- **解决**: 等待 3-5 秒，或发送更多请求（500条触发路由日志批量写入）

**可能原因 2**: 事件发布失败
- **解决**: 查看日志是否有 `FAIL_OVERFLOW`、`FAIL_CANCELLED` 等错误

**可能原因 3**: Mapper 映射错误
- **解决**: 检查日志是否有 MyBatis 相关错误

**可能原因 4**: 数据库权限问题
- **解决**: 
```bash
mysql -uroot -proot -e "GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%'; FLUSH PRIVILEGES;"
```

### 如果服务启动失败

**检查日志中的错误类型：**

1. **数据库连接失败**
   ```
   ERROR: Access denied for user 'mcp_user'
   ```
   解决：重新运行 `init-persistence.sh`

2. **找不到 Mapper**
   ```
   ERROR: Could not find resource mapper/RoutingLogMapper.xml
   ```
   解决：检查 `src/main/resources/mapper/` 目录是否存在

3. **Bean 创建失败**
   ```
   ERROR: Error creating bean with name 'routingLogBatchWriter'
   ```
   解决：检查是否所有依赖的 Bean 都正确配置

## 🎯 成功标准

持久化功能正常工作的标志：

✅ **启动阶段**
- 持久化相关 Bean 成功创建
- MyBatis Mapper 扫描成功
- 批量写入器成功启动并订阅事件流

✅ **运行阶段**  
- 每次路由请求都发布持久化事件
- 批量写入定期触发（从日志看到）
- 数据库表中能查询到记录

✅ **数据验证**
- `routing_logs` 表有请求记录
- `health_check_records` 表有健康检查记录
- `mcp_servers` 表有服务器注册信息
- 时间戳正确，数据完整

## 📞 如果还有问题

1. **检查完整日志**
   ```bash
   tail -f logs/mcp-router-v3.log
   ```

2. **查看详细的 SQL 日志**（临时开启）
   在 `application.yml` 中添加：
   ```yaml
   logging:
     level:
       com.pajk.mcpbridge.persistence.mapper: DEBUG
   ```

3. **检查数据库连接**
   ```bash
   mysql -umcp_user -pmcp_user -h127.0.0.1 -P3306 mcp_bridge -e "SELECT 1;"
   ```

4. **查看批量写入统计**（未来可以添加监控端点）
   在代码中可以访问：
   - `PersistenceEventPublisher.getStats()`
   - BatchWriter 的计数器

---

**最后更新**: 2025-10-30
**修复状态**: ✅ 所有问题已修复，等待用户验证


