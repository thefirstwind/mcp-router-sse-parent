# 🚀 MCP Router 快速启动指南

## 📋 目录
- [系统要求](#系统要求)
- [快速启动](#快速启动)
- [配置说明](#配置说明)
- [验证测试](#验证测试)
- [常见问题](#常见问题)

---

## 💻 系统要求

### 必需组件
- **Java**: JDK 17+
- **Maven**: 3.8+
- **MySQL**: 8.0+
- **Nacos**: 2.0+ (可选，用于服务发现)

### 环境准备
```bash
# 检查 Java 版本
java -version  # 应显示 17 或更高版本

# 检查 Maven 版本
mvn -version   # 应显示 3.8 或更高版本

# 检查 MySQL 运行状态
mysql --version
```

---

## 🚀 快速启动

### 1. 数据库初始化

```bash
# 登录 MySQL
mysql -h127.0.0.1 -P3306 -uroot -p

# 创建数据库和用户
CREATE DATABASE IF NOT EXISTS mcp_bridge;
CREATE USER IF NOT EXISTS 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';
FLUSH PRIVILEGES;

# 导入表结构
USE mcp_bridge;
SOURCE /path/to/mcp-router-v3/database/schema_complete_optimized.sql;
```

### 2. 配置文件

编辑 `mcp-router-v3/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mcp_bridge
    username: mcp_user
    password: mcp_user
    
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848  # 如果使用 Nacos
        ephemeral: true               # 关键配置：临时实例

mcp:
  persistence:
    enabled: true
    ephemeral:
      enabled: true
```

### 3. 启动 Router

```bash
# 进入项目目录
cd /path/to/mcp-router-v3

# 启动（前台运行）
mvn spring-boot:run

# 或启动（后台运行）
nohup mvn spring-boot:run > logs/router.log 2>&1 &
```

### 4. 启动 Server 实例

```bash
# 进入 Server 目录
cd /path/to/mcp-server-v6

# 启动实例1（端口 8071）
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8071"

# 启动实例2（端口 8072）- 新终端窗口
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8072"
```

---

## ⚙️ 配置说明

### 核心配置项

#### 1. 临时实例配置
```yaml
mcp:
  persistence:
    ephemeral:
      enabled: true                      # 启用临时实例支持
      cleanup:
        startup-timeout: 5               # 启动时清理超时时间（分钟）
        health-check-timeout: 5          # 心跳检测超时时间（分钟）
        periodic-interval: 120000        # 定期检查间隔（毫秒）
        retention-days: 7                # 离线实例保留天数
```

#### 2. Nacos 配置
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: public                # 命名空间
        group: DEFAULT_GROUP             # 分组
        ephemeral: true                  # 临时实例标识
        service: ${spring.application.name}
```

#### 3. 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mcp_bridge
    username: mcp_user
    password: mcp_user
    driver-class-name: com.mysql.cj.jdbc.Driver
    
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deletedAt
      logic-delete-value: now()
      logic-not-delete-value: 'NULL'
```

---

## ✅ 验证测试

### 1. 检查服务启动

```bash
# 查看 Router 日志
tail -f /path/to/mcp-router-v3/logs/router.log

# 期望看到：
# ✅ Started McpRouterV3Application in X.XXX seconds
# ✅ Marked X ephemeral instances as unhealthy for service...
```

### 2. 检查数据库注册

```sql
-- 查看所有注册实例
SELECT server_key, server_name, host, port, healthy, ephemeral, registered_at
FROM mcp_servers
WHERE deleted_at IS NULL
ORDER BY ephemeral DESC, registered_at DESC;

-- 期望结果：
-- mcp-router-v3:127.0.0.1:8052     | ephemeral=1
-- mcp-server-v6:192.168.0.102:8071 | ephemeral=1
-- mcp-server-v6:192.168.0.102:8072 | ephemeral=1
```

### 3. 健康检查

```bash
# Router 健康检查（如果有 health endpoint）
curl http://localhost:8080/health

# Server 健康检查
curl http://localhost:8071/actuator/health
curl http://localhost:8072/actuator/health
```

### 4. 测试重启恢复

```bash
# 1. 记录当前实例
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge \
  -e "SELECT server_key, healthy, ephemeral FROM mcp_servers WHERE deleted_at IS NULL"

# 2. 终止 Router
jps | grep McpRouterV3Application | awk '{print $1}' | xargs kill -9

# 3. 等待10秒

# 4. 重启 Router
cd /path/to/mcp-router-v3
mvn spring-boot:run

# 5. 验证清理结果
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge \
  -e "SELECT server_key, healthy, ephemeral FROM mcp_servers WHERE deleted_at IS NULL"

# 期望：旧的 Router 实例被标记为 unhealthy=0
#      Server 实例恢复为 healthy=1
```

---

## 🔍 常见问题

### Q1: Router 启动失败 - 数据库连接错误
**错误信息:**
```
Communications link failure
```

**解决方案:**
1. 检查 MySQL 是否运行: `mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user`
2. 检查防火墙设置
3. 验证 `application.yml` 中的连接信息

---

### Q2: 实例注册失败 - ephemeral 字段错误
**错误信息:**
```
Unknown column 'ephemeral' in 'field list'
```

**解决方案:**
确保使用最新的数据库表结构:
```sql
USE mcp_bridge;
DESC mcp_servers;  -- 检查是否有 ephemeral 列

-- 如果没有，执行：
ALTER TABLE mcp_servers ADD COLUMN ephemeral TINYINT(1) DEFAULT 1 COMMENT '是否为临时实例';
CREATE INDEX idx_ephemeral_healthy ON mcp_servers(ephemeral, healthy);
```

---

### Q3: 重启后旧实例未清理
**现象:** Router 重启后，数据库中仍有旧的临时实例记录

**排查步骤:**
1. 检查日志是否有清理记录:
```bash
grep "Marked.*ephemeral instances as unhealthy" logs/router.log
```

2. 手动触发清理:
```sql
-- 标记超过5分钟未更新的临时实例
UPDATE mcp_servers
SET healthy = 0, updated_at = NOW()
WHERE ephemeral = 1
  AND deleted_at IS NULL
  AND TIMESTAMPDIFF(MINUTE, updated_at, NOW()) > 5;
```

---

### Q4: Nacos 连接失败
**错误信息:**
```
Request nacos server failed
```

**解决方案:**
1. 检查 Nacos 是否运行: `curl http://127.0.0.1:8848/nacos`
2. 如果不使用 Nacos，禁用服务发现:
```yaml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
```

---

### Q5: 性能问题 - 频繁数据库查询
**现象:** 日志中大量数据库查询日志

**优化建议:**
1. 调整心跳检测间隔:
```yaml
mcp:
  persistence:
    ephemeral:
      cleanup:
        periodic-interval: 300000  # 从2分钟改为5分钟
```

2. 启用查询缓存（适用于读多写少场景）

3. 优化索引:
```sql
-- 创建复合索引
CREATE INDEX idx_ephemeral_healthy_updated 
ON mcp_servers(ephemeral, healthy, updated_at);
```

---

## 📊 监控指标

### 关键指标

| 指标名称 | 说明 | 正常范围 |
|---------|------|---------|
| 启动时间 | Router 完全启动耗时 | < 3秒 |
| 临时实例数量 | 当前临时实例总数 | 视业务规模 |
| 不健康实例数量 | unhealthy=0 的实例数 | < 5% |
| 心跳检测延迟 | 实例崩溃到标记的时间 | < 30秒 |
| 数据库连接数 | 当前活跃连接数 | < 20 |

### 监控 SQL

```sql
-- 1. 临时实例统计
SELECT 
    ephemeral,
    COUNT(*) as total,
    SUM(CASE WHEN healthy = 1 THEN 1 ELSE 0 END) as healthy_count,
    SUM(CASE WHEN healthy = 0 THEN 1 ELSE 0 END) as unhealthy_count
FROM mcp_servers
WHERE deleted_at IS NULL
GROUP BY ephemeral;

-- 2. 最近注册的实例
SELECT server_key, server_name, host, port, registered_at
FROM mcp_servers
WHERE deleted_at IS NULL
ORDER BY registered_at DESC
LIMIT 10;

-- 3. 不健康实例列表
SELECT server_key, server_name, last_health_check, updated_at
FROM mcp_servers
WHERE healthy = 0 AND deleted_at IS NULL
ORDER BY updated_at DESC;

-- 4. 心跳延迟分析
SELECT 
    server_name,
    AVG(TIMESTAMPDIFF(SECOND, last_request_time, last_health_check)) as avg_heartbeat_delay_sec
FROM mcp_servers
WHERE deleted_at IS NULL AND last_request_time IS NOT NULL
GROUP BY server_name;
```

---

## 🛠️ 运维命令

### 日常维护

```bash
# 1. 查看运行状态
jps | grep -E "McpRouter|McpServer"

# 2. 优雅停止
kill -15 <PID>

# 3. 强制停止
kill -9 <PID>

# 4. 清理日志（保留最近7天）
find /path/to/logs -name "*.log" -mtime +7 -delete

# 5. 数据库备份
mysqldump -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge > backup_$(date +%Y%m%d).sql
```

### 故障恢复

```bash
# 1. 全量重启
# 停止所有服务
jps | grep -E "McpRouter|McpServer" | awk '{print $1}' | xargs kill -9

# 清理临时数据
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge \
  -e "UPDATE mcp_servers SET healthy=0 WHERE ephemeral=1 AND deleted_at IS NULL"

# 重新启动
cd /path/to/mcp-router-v3 && mvn spring-boot:run &
sleep 10
cd /path/to/mcp-server-v6 && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8071" &

# 2. 数据恢复（从备份）
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge < backup_20250730.sql
```

---

## 📚 相关文档

- [数据库表结构设计](database/schema_complete_optimized.sql)
- [重启场景验证报告](RESTART_VERIFICATION_REPORT.md)
- [持久化分析文档](MCP_ROUTER_V3_PERSISTENCE_ANALYSIS_OPTIMIZED.md)

---

## 📞 支持与反馈

如有问题，请：
1. 检查日志文件：`tail -f logs/router.log`
2. 查询数据库状态：`SELECT * FROM mcp_servers WHERE deleted_at IS NULL`
3. 参考[常见问题](#常见问题)章节

---

**最后更新:** 2025-10-30  
**版本:** 1.0.0


