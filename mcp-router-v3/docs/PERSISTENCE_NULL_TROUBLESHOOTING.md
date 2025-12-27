# PersistenceEventPublisher is null 排查指南

## 问题描述

测试环境报错：`PersistenceEventPublisher is null, routing log not published`

## 问题原因

`PersistenceEventPublisher` 使用了 `@ConditionalOnProperty` 注解，只有当配置 `mcp.persistence.enabled=true` 时才会创建 Bean。如果配置不正确，Bean 不会被创建，导致注入为 `null`。

## 排查步骤

### 步骤 1：检查配置文件

**检查 `application.yml` 或 `application.properties`**：

```yaml
# application.yml
mcp:
  persistence:
    enabled: true  # 必须为 true
    async: true
    batch-size: 100
    flush-interval: 5000
```

或者：

```properties
# application.properties
mcp.persistence.enabled=true
mcp.persistence.async=true
mcp.persistence.batch-size=100
mcp.persistence.flush-interval=5000
```

**常见问题**：
- ❌ 配置项缺失
- ❌ `enabled` 值不是 `true`（如 `True`、`TRUE`、`1` 等）
- ❌ YAML 缩进错误
- ❌ 配置文件路径不对

### 步骤 2：检查环境变量

Spring Boot 支持通过环境变量覆盖配置。检查是否有环境变量覆盖了配置：

```bash
# 检查环境变量
env | grep -i persistence
env | grep -i mcp

# 可能的环境变量格式：
# MCP_PERSISTENCE_ENABLED=false  # 这会覆盖配置文件
# MCP.PERSISTENCE.ENABLED=false  # 这种格式也可能生效
```

**注意**：环境变量的优先级高于配置文件，如果设置了 `MCP_PERSISTENCE_ENABLED=false`，即使配置文件中是 `true` 也会被覆盖。

### 步骤 3：检查启动日志

查看应用启动日志，确认 `PersistenceEventPublisher` 是否被创建：

```bash
# 查找初始化日志
grep -i "PersistenceEventPublisher initialized" logs/application.log

# 应该看到：
# PersistenceEventPublisher initialized with buffer sizes: routing=10000, health=1000, error=1000
```

**如果没有看到这条日志**，说明 Bean 没有被创建。

### 步骤 4：检查批量写入器启动日志

检查批量写入器是否启动：

```bash
# 查找批量写入器启动日志
grep -i "batch writer started" logs/application.log

# 应该看到：
# HealthCheckRecord batch writer started successfully
# RoutingLog batch writer started successfully
```

**如果没有看到这些日志**，说明批量写入器也没有启动，进一步确认持久化功能未启用。

### 步骤 5：检查 Spring Bean 注册

如果应用支持 Actuator，可以通过 `/actuator/beans` 端点检查：

```bash
curl http://localhost:8052/actuator/beans | grep -i PersistenceEventPublisher
```

或者添加临时日志来检查：

```java
@PostConstruct
public void checkPersistenceEventPublisher() {
    if (persistenceEventPublisher == null) {
        log.error("❌ PersistenceEventPublisher is NULL! Check configuration: mcp.persistence.enabled");
    } else {
        log.info("✅ PersistenceEventPublisher is available");
    }
}
```

### 步骤 6：检查配置文件加载顺序

Spring Boot 配置文件加载顺序：
1. `application.yml` / `application.properties`
2. `application-{profile}.yml` / `application-{profile}.properties`
3. 环境变量
4. 命令行参数

检查是否有 profile 特定的配置文件覆盖了默认配置：

```bash
# 检查激活的 profile
grep -i "spring.profiles.active" application*.yml application*.properties

# 检查 profile 特定配置
cat application-test.yml  # 如果存在
cat application-prod.yml  # 如果存在
```

### 步骤 7：检查配置类扫描

确认 `PersistenceEventPublisher` 所在的包是否被 Spring 扫描：

```java
// 检查主类
@SpringBootApplication
@EnableAutoConfiguration
public class McpRouterV3Application {
    // ...
}
```

`PersistenceEventPublisher` 在 `com.pajk.mcpbridge.persistence.service` 包下，确保该包被扫描。

## 解决方案

### 方案 1：修复配置文件（推荐）

在测试环境的配置文件中添加或修正：

```yaml
mcp:
  persistence:
    enabled: true  # 确保为 true
```

### 方案 2：通过环境变量设置

如果无法修改配置文件，可以通过环境变量设置：

```bash
export MCP_PERSISTENCE_ENABLED=true
# 然后重启应用
```

或者在启动脚本中：

```bash
MCP_PERSISTENCE_ENABLED=true java -jar mcp-router-v3.jar
```

### 方案 3：通过命令行参数

```bash
java -jar mcp-router-v3.jar --mcp.persistence.enabled=true
```

### 方案 4：修改代码（不推荐，仅用于调试）

如果确实需要禁用持久化功能，可以修改代码，让它在 `null` 时优雅降级：

```java
private void publishRoutingLog(RoutingLog routingLog) {
    if (persistenceEventPublisher != null) {
        try {
            persistenceEventPublisher.publishRoutingLog(routingLog);
        } catch (Exception e) {
            log.warn("Failed to publish routing log", e);
        }
    } else {
        // 可选：记录到日志文件作为降级方案
        log.debug("PersistenceEventPublisher not available, routing log not persisted: {}", routingLog.getRequestId());
    }
}
```

## 验证修复

修复后，重启应用并检查：

1. **启动日志**：
   ```bash
   grep -i "PersistenceEventPublisher initialized" logs/application.log
   ```

2. **批量写入器日志**：
   ```bash
   grep -i "batch writer started" logs/application.log
   ```

3. **运行时日志**：
   ```bash
   # 不应该再看到 "PersistenceEventPublisher is null" 的警告
   grep -i "PersistenceEventPublisher is null" logs/application.log
   ```

4. **数据库验证**：
   ```sql
   -- 触发一些请求后，检查数据库是否有新记录
   SELECT COUNT(*) FROM routing_logs WHERE start_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE);
   SELECT COUNT(*) FROM health_check_records WHERE check_time >= DATE_SUB(NOW(), INTERVAL 1 MINUTE);
   ```

## 快速检查脚本

创建一个检查脚本 `check-persistence.sh`：

```bash
#!/bin/bash

echo "=== 检查持久化配置 ==="

# 1. 检查配置文件
echo "1. 检查 application.yml:"
grep -A 3 "mcp:" application.yml | grep -A 2 "persistence:" || echo "❌ 未找到 mcp.persistence 配置"

# 2. 检查环境变量
echo "2. 检查环境变量:"
env | grep -i "MCP.*PERSISTENCE" || echo "✅ 未设置相关环境变量"

# 3. 检查启动日志
echo "3. 检查启动日志:"
if [ -f logs/application.log ]; then
    grep -i "PersistenceEventPublisher initialized" logs/application.log && echo "✅ PersistenceEventPublisher 已初始化" || echo "❌ PersistenceEventPublisher 未初始化"
    grep -i "batch writer started" logs/application.log && echo "✅ 批量写入器已启动" || echo "❌ 批量写入器未启动"
else
    echo "⚠️  日志文件不存在"
fi

# 4. 检查运行时错误
echo "4. 检查运行时错误:"
if [ -f logs/application.log ]; then
    grep -i "PersistenceEventPublisher is null" logs/application.log | tail -5 || echo "✅ 未发现 null 错误"
else
    echo "⚠️  日志文件不存在"
fi

echo "=== 检查完成 ==="
```

## 常见问题 FAQ

### Q1: 为什么本地环境正常，测试环境不行？

**A**: 可能原因：
- 测试环境使用了不同的配置文件（如 `application-test.yml`）
- 测试环境设置了环境变量覆盖配置
- 测试环境的配置文件格式有问题（YAML 缩进等）

### Q2: 配置文件中是 `true`，但还是 null？

**A**: 检查：
- 环境变量是否覆盖了配置
- 是否有 profile 特定配置覆盖
- YAML 缩进是否正确
- 配置项名称是否正确（注意大小写和层级）

### Q3: 如何临时禁用持久化功能？

**A**: 设置 `mcp.persistence.enabled=false`，但注意这会导致所有持久化功能不可用。

### Q4: 持久化功能禁用后，应用还能正常运行吗？

**A**: 可以。持久化功能是可选的，禁用后：
- 路由功能正常
- 健康检查正常
- 只是不会将日志写入数据库
- 会看到 "PersistenceEventPublisher is null" 的警告日志

---

**最后更新**: 2025-11-14
**相关文件**: 
- `PersistenceEventPublisher.java`
- `McpRouterService.java`
- `HealthCheckService.java`
- `application.yml`



















