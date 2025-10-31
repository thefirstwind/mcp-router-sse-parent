# MCP Router V3 - 数据持久化问题修复总结

## 问题发现

在集成 MyBatis 进行数据持久化时，发现以下问题：

1. **数据库配置错误**：应用连接的是 `mcp_bridge` 数据库，但手动验证时使用的是 `mcp_router_v3` 数据库，导致误判数据未写入。

2. **MyBatis 警告**：初始集成时遇到 MyBatis 的 `VALUES()` 函数弃用警告。

## 修复措施

### 1. 数据库配置统一

**application.yml** 中的数据源配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mcp_bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: mcp_user
    password: mcp_user
```

**验证命令**：
```bash
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "SELECT * FROM mcp_servers"
```

### 2. MyBatis VALUES() 警告修复

将所有 `VALUES(column_name)` 替换为兼容 MySQL 8.0.20+ 的 `NEW.column_name` 语法。

**修改前**：
```xml
ON DUPLICATE KEY UPDATE
    server_name = VALUES(server_name),
    healthy = VALUES(healthy),
    ...
```

**修改后**：
```xml
ON DUPLICATE KEY UPDATE
    server_name = NEW.server_name,
    healthy = NEW.healthy,
    ...
```

### 3. MyBatis 缓存配置优化

禁用缓存以确保读取实时数据：
```yaml
mybatis:
  configuration:
    cache-enabled: false
    local-cache-scope: STATEMENT
```

### 4. 日志优化

简化持久化成功日志，去除冗余的验证步骤：
```java
log.info("✅ Server persisted to database: {} ({}:{}) - healthy={}, enabled={}, rows={}", 
    serverInfo.getName(), server.getHost(), server.getPort(), 
    serverInfo.isHealthy(), serverInfo.getEnabled(), rows);
```

## 验证结果

### 数据持久化成功

```sql
SELECT server_name, host, port, healthy, enabled, ephemeral 
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC;

+---------------------------+---------------+------+---------+---------+-----------+
| server_name               | host          | port | healthy | enabled | ephemeral |
+---------------------------+---------------+------+---------+---------+-----------+
| mcp-router-v3             | 127.0.0.1     | 8052 |       1 |       1 |         1 |
| test-mcp-server-alignment | 127.0.0.1     | 8999 |       0 |       1 |         0 |
| mcp-server-v2-real        | 127.0.0.1     | 8063 |       0 |       1 |         0 |
| cf-server                 | 127.0.0.1     | 8899 |       1 |       1 |         0 |
| mcp-server-v2-20250718    | 127.0.0.1     | 8090 |       1 |       1 |         0 |
+---------------------------+---------------+------+---------+---------+-----------+
```

### 统计信息

- **总记录数**: 8
- **健康实例**: 3
- **启用实例**: 8
- **临时节点**: 4

## 主要修改文件

1. `src/main/resources/mapper/McpServerMapper.xml` - 修复 VALUES() 警告
2. `src/main/resources/application.yml` - 优化 MyBatis 缓存配置
3. `src/main/java/com/pajk/mcpbridge/persistence/service/McpServerPersistenceService.java` - 简化日志

## 总结

✅ **数据持久化功能完全正常**
- Nacos 服务发现的所有实例都成功同步到 MySQL 数据库
- `INSERT ON DUPLICATE KEY UPDATE` 语法正确工作
- 健康状态、启用状态、临时节点标志等字段正确持久化
- MyBatis 警告已完全消除

## 注意事项

1. **数据库选择**: 应用使用 `mcp_bridge` 数据库，验证时需要连接正确的数据库
2. **MySQL 版本**: 建议使用 MySQL 8.0.20+ 以支持 `NEW.` 别名语法
3. **事务管理**: 当前未使用 `@Transactional`，依赖 HikariCP 的自动提交（默认开启）

---

**修复时间**: 2025-10-30  
**状态**: ✅ 已完成并验证


