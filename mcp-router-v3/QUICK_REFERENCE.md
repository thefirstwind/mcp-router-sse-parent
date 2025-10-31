# MCP Router V3 - 快速参考指南

## 🚀 快速启动

### 启动应用

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
nohup java -jar target/mcp-router-v3-1.0.0.jar > app.log 2>&1 &
```

### 停止应用

```bash
# 查找进程
ps aux | grep mcp-router-v3 | grep -v grep

# 停止进程 (替换 PID)
kill <PID>
```

### 重启应用

```bash
# 停止
kill $(ps aux | grep mcp-router-v3 | grep -v grep | awk '{print $2}')

# 等待 2 秒
sleep 2

# 启动
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
nohup java -jar target/mcp-router-v3-1.0.0.jar > app.log 2>&1 &
```

---

## 🔍 健康检查

### 应用健康状态

```bash
curl http://localhost:8052/actuator/health
# 期望输出: {"status":"UP"}
```

### 应用信息

```bash
curl http://localhost:8052/actuator/info
```

### 完整验证

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
./verify-persistence.sh
```

---

## 📊 数据库查询

### 连接数据库

```bash
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge
```

### 查看所有服务实例

```sql
SELECT 
    server_name, 
    CONCAT(host, ':', port) as endpoint,
    CASE WHEN healthy = 1 THEN '✅' ELSE '❌' END as status,
    CASE WHEN ephemeral = 1 THEN '临时' ELSE '持久' END as type,
    updated_at
FROM mcp_servers 
WHERE deleted_at IS NULL 
ORDER BY updated_at DESC;
```

### 查看统计信息

```sql
-- 总记录数
SELECT COUNT(*) as total FROM mcp_servers WHERE deleted_at IS NULL;

-- 健康实例数
SELECT COUNT(*) as healthy FROM mcp_servers WHERE deleted_at IS NULL AND healthy = 1;

-- 启用实例数
SELECT COUNT(*) as enabled FROM mcp_servers WHERE deleted_at IS NULL AND enabled = 1;

-- 临时节点数
SELECT COUNT(*) as ephemeral FROM mcp_servers WHERE deleted_at IS NULL AND ephemeral = 1;
```

### 快速统计（一条命令）

```bash
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -N -e "
SELECT 
    CONCAT('总记录: ', COUNT(*), ' | 健康: ', 
    SUM(CASE WHEN healthy = 1 THEN 1 ELSE 0 END), ' | 启用: ',
    SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END), ' | 临时: ',
    SUM(CASE WHEN ephemeral = 1 THEN 1 ELSE 0 END)) 
FROM mcp_servers WHERE deleted_at IS NULL;
" 2>/dev/null
```

---

## 📝 日志查看

### 实时日志

```bash
tail -f app.log
```

### 持久化日志

```bash
# 查看最近的持久化记录
tail -100 app.log | grep "Server persisted"

# 统计持久化成功次数
grep "Server persisted" app.log | wc -l
```

### 错误日志

```bash
# 查看错误
tail -100 app.log | grep -E "ERROR|WARN"

# 检查 MyBatis 警告
tail -500 app.log | grep "VALUES() is deprecated"
```

### 启动日志

```bash
# 查看启动信息
grep "Started McpRouterV3Application" app.log | tail -1
```

---

## 🔧 常见维护任务

### 重新编译

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
mvn clean package -DskipTests
```

### 编译并重启

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3

# 停止应用
kill $(ps aux | grep mcp-router-v3 | grep -v grep | awk '{print $2}')

# 编译
mvn clean package -DskipTests

# 等待编译完成，然后启动
sleep 2
nohup java -jar target/mcp-router-v3-1.0.0.jar > app.log 2>&1 &

# 等待启动
sleep 5

# 验证
./verify-persistence.sh
```

### 清理旧数据

```sql
-- 删除 24 小时前的不健康临时节点
DELETE FROM mcp_servers 
WHERE ephemeral = 1 
  AND healthy = 0 
  AND updated_at < DATE_SUB(NOW(), INTERVAL 24 HOUR);

-- 软删除指定服务
UPDATE mcp_servers 
SET deleted_at = NOW() 
WHERE server_name = 'service-name';
```

---

## 🐛 故障排查

### 应用无法启动

```bash
# 1. 检查端口占用
lsof -i :8052

# 2. 查看启动日志
tail -50 app.log

# 3. 检查 Nacos 连接
curl http://127.0.0.1:8848/nacos/

# 4. 检查 MySQL 连接
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "SELECT 1"
```

### 数据未写入数据库

```bash
# 1. 确认应用正在运行
ps aux | grep mcp-router-v3

# 2. 检查持久化日志
tail -100 app.log | grep "persisted to database"

# 3. 确认数据库连接正常
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -e "SELECT COUNT(*) FROM mcp_servers"

# 4. 检查 MyBatis 配置
grep -A 5 "mybatis:" src/main/resources/application.yml
```

### MyBatis 警告出现

```bash
# 1. 检查日志
tail -500 app.log | grep "VALUES() is deprecated"

# 2. 验证 Mapper XML
grep -n "VALUES(" src/main/resources/mapper/McpServerMapper.xml

# 应该显示: VALUES (...) AS NEW
# 而不是: VALUES(column_name)

# 3. 重新编译
mvn clean package -DskipTests

# 4. 重启应用
```

---

## 📚 配置文件位置

```
mcp-router-v3/
├── src/main/resources/
│   ├── application.yml          # 主配置文件
│   ├── application-dev.yml      # 开发环境配置
│   └── mapper/
│       └── McpServerMapper.xml  # MyBatis SQL 映射
├── target/
│   └── mcp-router-v3-1.0.0.jar # 可执行 JAR
├── app.log                      # 应用日志
└── verify-persistence.sh        # 验证脚本
```

---

## 🔐 关键配置

### 数据库连接

```yaml
# src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/mcp_bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: mcp_user
    password: mcp_user
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### Nacos 配置

```yaml
# src/main/resources/application.yml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: public
        group: mcp-server
```

### MyBatis 配置

```yaml
# src/main/resources/application.yml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.pajk.mcpbridge.persistence.entity
  configuration:
    cache-enabled: false              # 禁用二级缓存
    local-cache-scope: STATEMENT      # STATEMENT 级缓存
    map-underscore-to-camel-case: true
```

---

## 📞 端口说明

| 端口 | 服务 | 说明 |
|------|------|------|
| 8052 | MCP Router V3 | 主应用端口 |
| 8848 | Nacos | 服务发现与配置中心 |
| 3306 | MySQL | 数据持久化 |

---

## 🎯 常用命令速查

```bash
# 快速状态检查
curl -s http://localhost:8052/actuator/health | python3 -m json.tool

# 查看进程
ps aux | grep mcp-router | grep -v grep

# 快速重启
kill $(ps aux | grep mcp-router-v3 | grep -v grep | awk '{print $2}') && sleep 2 && nohup java -jar target/mcp-router-v3-1.0.0.jar > app.log 2>&1 &

# 完整验证
./verify-persistence.sh

# 查看最新日志
tail -20 app.log

# 数据库快速查询
mysql -h127.0.0.1 -P3306 -umcp_user -pmcp_user mcp_bridge -t -e "SELECT server_name, CONCAT(host, ':', port) as endpoint, healthy, ephemeral, DATE_FORMAT(updated_at, '%m-%d %H:%i') as updated FROM mcp_servers WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT 5;"
```

---

## 📖 相关文档

- `MYBATIS_FIX_COMPLETE.md` - 完整修复报告
- `PERSISTENCE_FIX_SUMMARY.md` - 持久化问题修复总结
- `MYBATIS_WARNING_FIX_SUMMARY.md` - MyBatis 警告修复详细分析
- `verify-persistence.sh` - 自动化验证脚本

---

**更新时间**: 2025-10-30  
**维护者**: MCP Router Team  
**版本**: 1.0.0


