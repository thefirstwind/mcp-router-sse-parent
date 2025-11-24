# MyBatis 警告修复总结

## 问题描述
启动时出现大量 MyBatis mapper 扫描警告：
```
WARN --- [main] o.m.s.mapper.ClassPathMapperScanner : No MyBatis mapper was found in '[com.pajk.mcpbridge.core]' package. Please check your configuration.
```

## 根本原因
`@MapperScan` 注解配置在 `MyBatisConfig` 类上，指向了 `com.pajk.mcpbridge.core` 包，但实际的 mapper 接口位于 `com.pajk.mcpbridge.persistence.mapper` 包中。

## 解决方案

### 修改的文件
`mcp-router-v3/src/main/java/com/pajk/mcpbridge/persistence/config/MyBatisConfig.java`

### 修改内容
将 `@MapperScan` 注解从类级别移到了 `sqlSessionFactory` 方法上，并正确指定了 mapper 包路径：

```java
@Bean
@ConditionalOnMissingBean
@MapperScan(basePackages = "com.pajk.mcpbridge.persistence.mapper")
public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    // ... 配置代码
}
```

### 关键改进
1. **精确的包扫描**：直接指向实际存在 mapper 接口的包
2. **避免重复扫描**：不再扫描不存在 mapper 的 `core` 包
3. **配置更清晰**：`@MapperScan` 与 `SqlSessionFactory` 配置放在一起，更符合逻辑

## 验证结果

### 启动前（有警告）
```
2025-10-30 14:39:52.443  WARN --- [main] o.m.s.mapper.ClassPathMapperScanner : No MyBatis mapper was found in '[com.pajk.mcpbridge.core]' package.
2025-10-30 14:39:52.887  WARN --- [main] o.m.s.mapper.ClassPathMapperScanner : No MyBatis mapper was found in '[com.pajk.mcpbridge.core]' package.
2025-10-30 14:39:53.724  WARN --- [main] o.m.s.mapper.ClassPathMapperScanner : No MyBatis mapper was found in '[com.pajk.mcpbridge.core]' package.
2025-10-30 14:39:54.367  WARN --- [main] o.m.s.mapper.ClassPathMapperScanner : No MyBatis mapper was found in '[com.pajk.mcpbridge.core]' package.
```

### 启动后（无警告）
```
2025-10-30 15:02:59.012  INFO --- [main] c.p.m.core.McpRouterV3Application : Started McpRouterV3Application in 1.486 seconds
```

✅ **MyBatis 扫描警告已完全消除**

## 影响范围
- ✅ 修复了启动时的警告信息
- ✅ 不影响现有功能
- ✅ 提升了配置的准确性和可读性
- ✅ 服务正常运行（PID: 75204）

## 修复时间
2025-10-30 15:02

## 状态
✅ **已完成并验证**

---

## 后续进展：持久化功能实现（2025-10-30 15:30）

### 发现的新问题
在修复 MyBatis 警告后，发现服务发现后没有自动持久化到数据库。

### 根本原因
Spring Boot 组件扫描问题：主应用类 `McpRouterV3Application` 在 `com.pajk.mcpbridge.core` 包中，默认只扫描该包及其子包，但 `McpServerPersistenceService` 在 `com.pajk.mcpbridge.persistence` 包中，不在扫描范围内。

### 解决方案
在主应用类添加显式的组件扫描配置：

```java
@ComponentScan(basePackages = {
    "com.pajk.mcpbridge.core",
    "com.pajk.mcpbridge.persistence"
})
```

### 验证结果

#### 持久化服务初始化成功
```
2025-10-30 15:27:18.400  INFO --- [main] McpServerPersistenceService:
✅ McpServerPersistenceService initialized successfully
📊 Database persistence is ENABLED for MCP server registration
```

#### 服务发现并自动持久化
```
2025-10-30 15:27:18.834  INFO --- [ncesChangeEvent] McpConnectionEventListener:
💾 Attempting to persist instance to database: cf-server@mcp-endpoints - 127.0.0.1:8899

2025-10-30 15:27:18.935  INFO --- [oundedElastic-3] McpConnectionEventListener:
✅ Instance persisted to database: 127.0.0.1:8899
```

#### 数据库验证
```sql
mysql> SELECT server_key, server_name, host, port, healthy FROM mcp_servers;

+------------------------------------+------------------------+-----------+------+---------+
| server_key                          | server_name            | host      | port | healthy |
+------------------------------------+------------------------+-----------+------+---------+
| mcp-router-v3:127.0.0.1:8052       | mcp-router-v3          | 127.0.0.1 | 8052 |       1 |
| mcp-server-v2-20250718:127.0.0.1:8090 | mcp-server-v2-20250718 | 127.0.0.1 | 8090 |       1 |
| cf-server:127.0.0.1:8899           | cf-server              | 127.0.0.1 | 8899 |       1 |
+------------------------------------+------------------------+-----------+------+---------+
```

✅ **服务发现自动持久化功能已成功实现**

### 完整的功能流程
1. **Nacos服务发现** → 发现健康的MCP服务实例
2. **事件监听触发** → `McpConnectionEventListener` 接收服务变化事件
3. **异步持久化** → 使用 Reactor 异步调用 `McpServerPersistenceService`
4. **数据库存储** → 通过 MyBatis 将服务信息存入 MySQL
5. **自动维护** → 定时更新健康状态、清理过期数据

详细信息请参阅：[PERSISTENCE_IMPLEMENTATION_SUMMARY.md](./PERSISTENCE_IMPLEMENTATION_SUMMARY.md)

