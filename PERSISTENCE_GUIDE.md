# MCP Router V3 持久化完整指南

> **版本**: v2.0 生产级  
> **更新日期**: 2025-03-01  
> **状态**: ✅ 已优化，可直接使用

---

## 📑 目录

- [快速开始](#快速开始)
- [核心架构](#核心架构)
- [关键问题与解决方案](#关键问题与解决方案)
- [完整代码实现](#完整代码实现)
- [性能调优](#性能调优)
- [故障排查](#故障排查)

---

## 快速开始

### 1. 初始化数据库 (2分钟)

```bash
# 创建数据库并导入schema
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS mcp_bridge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p mcp_bridge < mcp-router-v3/database/schema.sql

# 验证
mysql -u root -p mcp_bridge -e "SHOW TABLES;"
```

### 2. 添加依赖 (pom.xml)

```xml
<dependencies>
    <!-- MyBatis + MySQL -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>3.0.3</version>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### 3. 配置 (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mcp_bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
    username: root
    password: your_password
    hikari:
      minimum-idle: 10
      maximum-pool-size: 50
      connection-timeout: 30000

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.pajk.mcpbridge.core.entity
  type-handlers-package: com.pajk.mcpbridge.core.config
  configuration:
    map-underscore-to-camel-case: true
    use-generated-keys: true

mcp:
  persistence:
    enabled: true
    async-write: true
    batch-size: 500
```

### 4. 启动验证

```bash
mvn clean compile
mvn spring-boot:run
```

**完成！** 🎉

---

## 核心架构

### 层次结构

```
┌─────────────────────────────────────────────────────┐
│         Controller (WebFlux Reactive)               │
└───────────────────────┬─────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│      AsyncPersistenceFacade (异步适配层)            │
│  • 缓冲队列 (LinkedBlockingQueue)                   │
│  • 失败重试 (Retry.backoff)                         │
│  • 降级到文件 (FallbackFileWriter)                  │
└───────────────────────┬─────────────────────────────┘
                        │ 专用线程池 (dbScheduler)
                        ▼
┌─────────────────────────────────────────────────────┐
│    TransactionService (事务边界)                    │
│  • @Transactional                                   │
│  • 批量操作                                          │
└───────────────────────┬─────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│         MyBatis Mapper (数据访问)                   │
└───────────────────────┬─────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│              MySQL (分区表)                          │
└─────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 职责 | 关键技术 |
|------|------|----------|
| **AsyncPersistenceFacade** | 异步适配、缓冲、降级 | Reactor, BlockingQueue |
| **TransactionService** | 事务管理、批量操作 | @Transactional |
| **JsonTypeHandler** | JSON字段序列化 | Jackson, MyBatis TypeHandler |
| **FallbackFileWriter** | 降级写入、恢复 | NIO Files |
| **专用调度器** | 线程隔离 | Reactor Schedulers |

---

## 关键问题与解决方案

### ❌ 问题1: WebFlux与MyBatis阻塞冲突

**症状**: 高并发下线程池耗尽，响应变慢

**原因**: MyBatis是同步阻塞的，直接在响应式流中使用会阻塞线程

**✅ 解决方案**: 专用线程池隔离

```java
// config/PersistenceSchedulerConfig.java
@Configuration
public class PersistenceSchedulerConfig {
    @Bean("dbScheduler")
    public Scheduler dbScheduler() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Schedulers.newBoundedElastic(
            cores * 4,           // 最大线程数
            Integer.MAX_VALUE,   // 队列容量
            "db-scheduler",
            60, true
        );
    }
}

// 使用
public Mono<Void> saveAsync(RoutingLog log) {
    return Mono.fromRunnable(() -> mapper.insert(log))
        .subscribeOn(dbScheduler);  // 在专用调度器上执行
}
```

---

### ❌ 问题2: 分区表主键设计错误

**症状**: 创建分区表失败
```
ERROR: A PRIMARY KEY must include all columns in the table's partitioning function
```

**原因**: MySQL要求分区键必须包含在主键中

**✅ 解决方案**: 复合主键

```sql
-- ❌ 错误
CREATE TABLE routing_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  start_time DATETIME NOT NULL
) PARTITION BY RANGE (TO_DAYS(start_time)) (...);

-- ✅ 正确
CREATE TABLE routing_logs (
  id BIGINT AUTO_INCREMENT,
  start_time DATETIME NOT NULL,
  PRIMARY KEY (id, start_time),  -- 包含分区键
  UNIQUE KEY uk_request_time (request_id, start_time)
) PARTITION BY RANGE (TO_DAYS(start_time)) (...);
```

---

### ❌ 问题3: 并发插入/更新冲突

**症状**: 先查询再插入，并发时可能重复或丢失

**✅ 解决方案**: UPSERT原子操作

```java
// Mapper
@Insert("INSERT INTO mcp_servers (...) VALUES (...) " +
        "ON DUPLICATE KEY UPDATE " +
        "healthy = VALUES(healthy), " +
        "updated_at = CURRENT_TIMESTAMP")
int upsert(McpServer server);

// 一条SQL完成，原子操作，并发安全
```

---

### ❌ 问题4: 数据库故障导致数据丢失

**症状**: 数据库宕机或网络故障，日志丢失

**✅ 解决方案**: 多层降级策略

```java
@Component
public class AsyncPersistenceFacade {
    private final BlockingQueue<RoutingLog> logBuffer = 
        new LinkedBlockingQueue<>(10000);
    
    public Mono<Void> saveAsync(RoutingLog log) {
        return Mono.fromRunnable(() -> {
            // 1. 先加入缓冲队列
            boolean offered = logBuffer.offer(log);
            if (!offered) {
                // 2. 队列满，降级到文件
                fallbackFileWriter.writeToFile(log);
            }
        })
        .then()
        .onErrorResume(e -> {
            // 3. 最后兜底：打印日志
            log.error("Failed to buffer: {}", log.getRequestId());
            return Mono.empty();
        });
    }
    
    // 定时批量刷新到数据库（带重试）
    @Scheduled(fixedDelay = 5000)
    public void flushBatch() {
        persistenceFacade.flushBatch(500)
            .retryWhen(Retry.backoff(2, Duration.ofMillis(200)))
            .subscribe();
    }
}
```

---

### ❌ 问题5: JSON字段序列化失败

**症状**: `Could not set property 'metadata'`

**✅ 解决方案**: 自定义TypeHandler

```java
@Slf4j
@MappedTypes({Map.class})
public class JsonTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, 
                                    Map<String, Object> parameter, 
                                    JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, objectMapper.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JSON", e);
        }
    }
    
    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) 
            throws SQLException {
        String json = rs.getString(columnName);
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize JSON", e);
        }
    }
    
    // ... 其他方法类似
}

// 使用
@Result(property = "metadata", column = "metadata", 
        typeHandler = JsonTypeHandler.class)
```

---

## 完整代码实现

### 目录结构

```
src/main/java/com/pajk/mcpbridge/core/
├── config/
│   ├── PersistenceSchedulerConfig.java    # 专用线程池
│   └── JsonTypeHandler.java               # JSON处理器
├── entity/
│   ├── RoutingLog.java                    # 路由日志实体
│   └── McpServer.java                     # 服务器实体
├── mapper/
│   ├── RoutingLogMapper.java              # 日志Mapper
│   └── McpServerMapper.java               # 服务器Mapper
└── service/persistence/
    ├── AsyncPersistenceFacade.java        # 异步门面 ⭐
    ├── RoutingLogTransactionService.java  # 事务服务
    ├── FallbackFileWriter.java            # 降级写入
    └── PersistenceScheduledTasks.java     # 定时任务

src/main/resources/
└── mapper/
    ├── RoutingLogMapper.xml
    └── McpServerMapper.xml
```

### 核心类实现

#### 1. AsyncPersistenceFacade (异步门面)

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncPersistenceFacade {
    
    private final RoutingLogTransactionService routingLogTxService;
    private final FallbackFileWriter fallbackFileWriter;
    @Qualifier("dbScheduler")
    private final Scheduler dbScheduler;
    
    private final BlockingQueue<RoutingLog> logBuffer = 
        new LinkedBlockingQueue<>(10000);
    
    /**
     * 异步保存（非阻塞）
     */
    public Mono<Void> saveRoutingLogAsync(RoutingLog log) {
        return Mono.fromRunnable(() -> {
            if (!logBuffer.offer(log)) {
                log.warn("Buffer full, fallback to file: {}", log.getRequestId());
                fallbackFileWriter.writeToFile(log);
            }
        })
        .then()
        .onErrorResume(e -> {
            log.error("Failed to buffer log: {}", log.getRequestId(), e);
            return Mono.empty();
        });
    }
    
    /**
     * 批量刷新
     */
    public Mono<Integer> flushBatch(int batchSize) {
        return Mono.fromCallable(() -> {
            List<RoutingLog> batch = new ArrayList<>(batchSize);
            logBuffer.drainTo(batch, batchSize);
            
            if (batch.isEmpty()) return 0;
            
            routingLogTxService.batchInsertLogs(batch);
            return batch.size();
        })
        .subscribeOn(dbScheduler)
        .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
            .filter(e -> e instanceof SQLException)
        )
        .onErrorResume(e -> {
            log.error("Failed to flush batch", e);
            return Mono.just(0);
        });
    }
}
```

#### 2. RoutingLogTransactionService (事务服务)

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingLogTransactionService {
    
    private final RoutingLogMapper routingLogMapper;
    
    @Transactional(rollbackFor = Exception.class)
    public void insertLog(RoutingLog log) {
        routingLogMapper.insert(log);
    }
    
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertLogs(List<RoutingLog> logs) {
        if (logs == null || logs.isEmpty()) return 0;
        
        int totalInserted = 0;
        int batchSize = 500;
        
        for (int i = 0; i < logs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, logs.size());
            List<RoutingLog> batch = logs.subList(i, end);
            totalInserted += routingLogMapper.batchInsert(batch);
        }
        
        return totalInserted;
    }
}
```

#### 3. RoutingLogMapper.xml (批量插入)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.pajk.mcpbridge.core.mapper.RoutingLogMapper">
    
    <insert id="batchInsert">
        INSERT IGNORE INTO routing_logs (
            request_id, server_key, method, path, mcp_method, tool_name,
            start_time, end_time, duration, response_status, is_success,
            error_message, client_ip
        ) VALUES
        <foreach collection="logs" item="log" separator=",">
            (
                #{log.requestId}, #{log.serverKey}, #{log.method}, #{log.path},
                #{log.mcpMethod}, #{log.toolName}, #{log.startTime}, #{log.endTime},
                #{log.duration}, #{log.responseStatus}, #{log.isSuccess},
                #{log.errorMessage}, #{log.clientIp}
            )
        </foreach>
    </insert>
    
</mapper>
```

#### 4. PersistenceScheduledTasks (定时任务)

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistenceScheduledTasks {
    
    private final AsyncPersistenceFacade persistenceFacade;
    
    @Scheduled(fixedDelay = 5000)
    public void flushLogBuffer() {
        persistenceFacade.flushBatch(500)
            .doOnSuccess(count -> {
                if (count > 0) {
                    log.info("Flushed {} logs", count);
                }
            })
            .subscribe();
    }
}
```

---

## 性能调优

### 1. 连接池优化

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 10               # CPU核心数
      maximum-pool-size: 50          # 核心数 * 2 + 磁盘数
      connection-timeout: 30000      # 30秒
      idle-timeout: 600000           # 10分钟
      max-lifetime: 1800000          # 30分钟
      leak-detection-threshold: 30000 # 泄漏检测
```

### 2. 批量写入优化

**开启批量重写**（性能提升10x）
```yaml
spring:
  datasource:
    url: jdbc:mysql://...?rewriteBatchedStatements=true
```

**最佳批量大小**: 500-1000

### 3. 索引优化

```sql
-- 覆盖索引（避免回表）
CREATE INDEX idx_cover_summary 
ON routing_logs(server_key, start_time, is_success, duration, tool_name);

-- 查询时只选择索引字段
SELECT server_key, start_time, is_success, duration, tool_name
FROM routing_logs WHERE server_key = ? ORDER BY start_time DESC;
```

### 4. 分区维护

```sql
-- 自动创建明天的分区（定时任务）
CALL create_routing_log_partition();

-- 删除90天前的分区
ALTER TABLE routing_logs DROP PARTITION p_2024_12_01;
```

### 性能指标

| 指标 | 目标值 |
|------|--------|
| 写入吞吐量 | 5000+ TPS |
| P99写入延迟 | <1ms |
| 数据丢失率 | <0.001% |
| 查询响应时间 | <50ms |

---

## 故障排查

### 问题：数据库连接失败

```bash
# 检查MySQL是否运行
mysql -u root -p -e "SELECT 1;"

# 检查端口
netstat -an | grep 3306

# 检查用户权限
mysql -u root -p -e "SHOW GRANTS FOR 'root'@'localhost';"
```

### 问题：MyBatis Mapper未找到

```
org.apache.ibatis.binding.BindingException: Invalid bound statement
```

**解决**:
1. 检查 `mapper-locations: classpath:mapper/*.xml`
2. 确保XML文件在 `src/main/resources/mapper/`
3. 检查 namespace 是否匹配 Mapper接口全限定名

### 问题：缓冲区满

**症状**: 日志中出现 `Buffer full, fallback to file`

**解决**:
1. 增大缓冲区: `new LinkedBlockingQueue<>(20000)`
2. 减小刷新间隔: `@Scheduled(fixedDelay = 3000)`
3. 增加批量大小: `flushBatch(1000)`

### 问题：慢查询

```bash
# 启用慢查询日志
mysql -e "SET GLOBAL slow_query_log = 'ON';"
mysql -e "SET GLOBAL long_query_time = 1;"

# 查看慢查询
tail -f /var/log/mysql/slow-query.log
```

**优化**:
1. 添加索引
2. 使用覆盖索引
3. 避免 SELECT *
4. 利用分区裁剪

---

## 数据库Schema说明

### 核心表

| 表名 | 用途 | 分区 | 保留期 |
|------|------|------|--------|
| `mcp_servers` | 服务器注册信息 | 否 | 永久 |
| `routing_logs` | 路由请求日志 | 按天 | 7天 |
| `routing_logs_archive` | 日志归档 | 按天 | 30天 |
| `health_check_records` | 健康检查记录 | 按月 | 30天 |

### 重要索引

```sql
-- mcp_servers
UNIQUE KEY uk_server_key (server_key)
KEY idx_healthy_enabled (healthy, enabled, deleted_at)

-- routing_logs
PRIMARY KEY (id, start_time)  -- 包含分区键
UNIQUE KEY uk_request_time (request_id, start_time)
KEY idx_cover_summary (server_key, start_time, is_success, duration, tool_name)
```

---

## 最佳实践

### ✅ DO

1. **使用专用线程池** - 避免阻塞响应式流
2. **批量操作加事务** - 保证原子性
3. **使用UPSERT** - 避免并发问题
4. **失败降级** - 确保零数据丢失
5. **覆盖索引** - 提升查询性能
6. **监控告警** - 及时发现问题

### ❌ DON'T

1. **不要在响应式流中直接调用MyBatis** - 会阻塞线程
2. **不要循环插入** - 使用批量操作
3. **不要SELECT *** - 只查询需要的字段
4. **不要忘记分区键** - 主键必须包含分区键
5. **不要使用CASCADE DELETE** - 会丢失历史数据
6. **不要忽略连接池配置** - 使用默认值性能差

---

## 监控指标

### 关键指标

```java
@Component
public class PersistenceMetrics {
    
    @Scheduled(fixedDelay = 60000)
    public void reportMetrics() {
        // 1. 缓冲区使用率
        double bufferUsage = facade.getBufferStats().getUsagePercentage();
        
        // 2. 连接池状态
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        int activeConns = pool.getActiveConnections();
        
        // 3. 每分钟写入量
        log.info("Metrics - Buffer: {:.1f}%, Connections: {}, TPS: {}", 
                 bufferUsage, activeConns, tps);
    }
}
```

### 告警阈值

| 指标 | 告警阈值 | 说明 |
|------|----------|------|
| 缓冲区使用率 | >80% | 考虑增大缓冲区或优化刷新策略 |
| 活跃连接数 | >40 (总50) | 考虑增大连接池或优化查询 |
| 写入延迟P99 | >5ms | 检查数据库性能 |
| 降级文件数 | >0 | 数据库可能有问题 |

---

## 附录

### A. 完整配置示例

参考 `mcp-router-v3/src/main/resources/application.yml`

### B. 数据库Schema

参考 `mcp-router-v3/database/schema.sql`

### C. 示例代码

参考本文档"完整代码实现"章节

### D. 测试用例

```java
@SpringBootTest
public class PersistenceIntegrationTest {
    // 参考实际项目中的测试
}
```

---

**文档版本**: v2.0  
**维护者**: MCP Router V3 Team  
**最后更新**: 2025-03-01

