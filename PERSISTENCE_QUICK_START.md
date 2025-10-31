# MCP Router V3 持久化功能 - 快速上手指南

> 🎯 **目标**：3 分钟内完成持久化功能的验证

## 🚀 快速启动（3 步）

### 第 1 步：重启服务 (1分钟)

```bash
cd /Users/shine/projects.mcp-router-sse-parent
./restart-mcp-router-v3.sh
```

**期待输出**：
```
✅ MCP Router V3 重启完成！
✅ PersistenceEventPublisher 已初始化
✅ RoutingLogBatchWriter 已初始化
✅ HealthCheckRecordBatchWriter 已初始化
✅ SqlSessionFactory 已配置
```

### 第 2 步：测试接口 (1分钟)

```bash
# 发送测试请求
curl --location 'http://localhost:8052/mcp/router/route/mcp-server-v6' \
--header 'Content-Type: application/json' \
--data '{
    "id": "quick-test",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 1 }
    }
}'
```

### 第 3 步：验证数据库 (1分钟)

```bash
# 等待5秒（批量写入延迟）
sleep 5

# 查询路由日志
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT request_id, service_name, tool_name, is_success 
   FROM routing_logs 
   WHERE request_id = 'quick-test';"
```

**期待输出**：
```
+-------------+--------------+---------------+------------+
| request_id  | service_name | tool_name     | is_success |
+-------------+--------------+---------------+------------+
| quick-test  | mcp-server-v6| getPersonById |          1 |
+-------------+--------------+---------------+------------+
```

✅ **成功！** 如果看到这条记录，说明持久化功能正常工作！

---

## 🧪 完整测试（可选）

如果你想要更全面的测试，运行自动化测试脚本：

```bash
cd /Users/shine/projects.mcp-router-sse-parent
./test-persistence.sh
```

这个脚本会：
1. ✅ 检查数据库连接
2. ✅ 清空测试数据
3. ✅ 发送多个测试请求
4. ✅ 验证所有表的数据
5. ✅ 生成统计报告

---

## 📊 查看数据

### 查询最近的路由日志

```bash
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT 
    request_id,
    service_name,
    tool_name,
    is_success,
    duration_ms,
    DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') as start_time
   FROM routing_logs 
   ORDER BY start_time DESC 
   LIMIT 10;"
```

### 查询健康检查记录

```bash
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT 
    server_key,
    status,
    response_time_ms,
    DATE_FORMAT(check_time, '%Y-%m-%d %H:%i:%s') as check_time
   FROM health_check_records 
   ORDER BY check_time DESC 
   LIMIT 10;"
```

### 查询 MCP 服务器列表

```bash
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT 
    server_key,
    server_name,
    host,
    port,
    status,
    DATE_FORMAT(last_heartbeat, '%Y-%m-%d %H:%i:%s') as last_heartbeat
   FROM mcp_servers 
   ORDER BY last_heartbeat DESC;"
```

### 使用统计视图

```bash
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT * FROM v_server_overview;"
```

```bash
mysql -umcp_user -pmcp_user mcp_bridge -e \
  "SELECT * FROM v_recent_24h_stats;"
```

---

## ❌ 故障排查

### 问题 1: 服务启动失败

**症状**：restart 脚本报告服务启动超时

**解决**：查看启动日志
```bash
tail -50 /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/logs/mcp-router-v3.log
```

**常见错误**：
1. 数据库连接失败 → 检查 MySQL 是否运行，用户密码是否正确
2. 端口 8052 被占用 → 停止占用端口的进程

### 问题 2: 数据库中没有数据

**症状**：查询结果为空

**检查清单**：

1. **持久化组件是否初始化？**
```bash
grep -i "PersistenceEventPublisher\|RoutingLogBatchWriter" \
  /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3/logs/mcp-router-v3.log
```

如果没有输出，说明持久化组件未启动。检查：
- `application.yml` 中 `mcp.persistence.enabled` 是否为 `true`
- 是否重新编译了代码
- 是否重启了服务

2. **是否等待了足够的时间？**

批量写入有延迟：
- 路由日志：2秒窗口或500条
- 健康检查：5秒窗口或100条

建议等待 5-10 秒后再查询。

3. **请求是否成功？**

检查请求响应：
```bash
curl --location 'http://localhost:8052/mcp/router/route/mcp-server-v6' \
--header 'Content-Type: application/json' \
--data '{
    "id": "test-1",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 1 }
    }
}' -v
```

如果返回 404 或 500，说明路由失败，不会产生持久化记录。

4. **MCP Server V6 是否运行？**

```bash
lsof -i :8071  # 或 8072
```

如果没有输出，需要先启动 mcp-server-v6。

### 问题 3: 看到 "No MyBatis mapper was found" 警告

**症状**：日志中有这个警告
```
WARN o.m.s.mapper.ClassPathMapperScanner : No MyBatis mapper was found in '[com.pajk.mcpbridge.core]' package
```

**原因**：MyBatis 自动配置未被正确排除

**解决**：检查 `McpRouterV3Application.java` 中是否有：
```java
@SpringBootApplication(exclude = {MybatisAutoConfiguration.class})
```

如果没有，说明代码修改未生效，需要：
1. 重新编译：`mvn clean compile -DskipTests`
2. 重启服务

---

## 📖 配置说明

### 持久化配置项

在 `application.yml` 中：

```yaml
mcp:
  persistence:
    enabled: true          # 是否启用持久化（默认 true）
    async: true            # 是否异步写入（默认 true）
    batch-size: 100        # 批量大小（默认 100）
    flush-interval: 5000   # 刷新间隔毫秒（默认 5000）
```

### 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mcp_bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: mcp_user
    password: mcp_user
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### MyBatis 配置

```yaml
mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.pajk.mcpbridge.persistence.entity
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
    default-statement-timeout: 30
```

---

## 🎯 性能说明

### 批量写入策略

1. **路由日志** (`routing_logs`)
   - 批量大小：500 条
   - 时间窗口：2 秒
   - 采样率：成功 10%，失败 100%

2. **健康检查记录** (`health_check_records`)
   - 批量大小：100 条
   - 时间窗口：5 秒
   - 采样率：成功 10%，失败 100%

3. **MCP 服务器** (`mcp_servers`)
   - 实时更新（首次注册和心跳更新）

### 性能特性

- ✅ **零阻塞**：使用 Reactive Streams，业务流程不等待数据库写入
- ✅ **批量优化**：减少数据库 I/O 次数，提高吞吐量
- ✅ **故障隔离**：持久化失败不影响主流程，降级到日志记录
- ✅ **条件化加载**：禁用时零性能开销

---

## 📚 相关文档

- **根本原因分析**：`PERSISTENCE_ROOT_CAUSE_ANALYSIS.md`
- **完整故障排查**：`PERSISTENCE_TROUBLESHOOTING.md`
- **修复检查清单**：`QUICK_FIX_CHECKLIST.md`
- **数据库初始化**：`mcp-router-v3/database/init-persistence.sh`

---

## 🎉 成功标准

持久化功能正常工作的标志：

✅ **启动阶段**
- 日志中看到 PersistenceEventPublisher 初始化
- 日志中看到 RoutingLogBatchWriter 启动
- 日志中看到 HealthCheckRecordBatchWriter 启动
- 日志中看到 SqlSessionFactory 配置成功
- **没有** "No MyBatis mapper was found" 警告

✅ **运行阶段**
- 每次路由请求都能在数据库中查到记录（可能有3-5秒延迟）
- 健康检查记录定期更新
- MCP 服务器信息实时同步

✅ **数据验证**
- `routing_logs` 表有请求记录
- `health_check_records` 表有健康检查记录
- `mcp_servers` 表有服务器注册信息
- 时间戳正确，数据完整

---

**最后更新**: 2025-10-30  
**版本**: 1.0  
**作者**: MCP Router Team


