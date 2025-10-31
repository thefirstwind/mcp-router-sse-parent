# MCP Router v3 MySQL 集成总结

## 📋 完成的工作

已为 mcp-router-v3 项目成功集成 MySQL 数据库持久化功能，使用 MyBatis 作为 ORM 框架。

### ✅ 已完成的任务

1. **设计数据库表结构** ✓
2. **添加MySQL依赖和配置** ✓  
3. **创建实体类** ✓
4. **创建Mapper接口** ✓
5. **修改服务类支持持久化** ✓
6. **创建数据库初始化脚本** ✓

## 🗄️ 数据库设计

### 数据库信息
- **数据库名**: `mcp-bridge`
- **用户名**: `root`
- **密码**: `hot365fm`
- **字符集**: `utf8mb4`

### 核心表结构

#### 1. mcp_servers (MCP服务器信息表)
存储所有 MCP 服务器的基本信息，包括连接参数、状态、元数据等。

**主要字段**:
- `id` - 主键ID
- `server_name` - 服务器名称
- `server_key` - 服务器唯一标识
- `host`, `port` - 连接信息
- `service_group` - 服务组
- `healthy`, `enabled` - 状态标识
- `metadata` - JSON元数据
- `last_heartbeat` - 最后心跳时间

#### 2. health_check_records (健康检查记录表)
记录所有健康检查的历史数据，支持分析和监控。

**主要字段**:
- `server_id` - 关联服务器
- `check_type` - 检查类型 (NACOS/MCP/COMBINED)
- `check_result` - 检查结果 (SUCCESS/FAILURE/TIMEOUT/ERROR)
- `response_time` - 响应时间
- `check_details` - JSON检查详情

#### 3. routing_logs (路由请求日志表)
记录所有路由请求的详细信息，用于性能分析和问题排查。

**主要字段**:
- `request_id` - 请求唯一标识
- `server_id` - 目标服务器
- `service_name`, `tool_name` - 服务和工具信息
- `request_params`, `response_data` - JSON请求响应数据
- `duration` - 请求耗时

#### 4. 其他支持表
- `mcp_tools` - MCP工具配置
- `connection_pool_stats` - 连接池统计
- `circuit_breaker_states` - 熔断器状态
- `performance_metrics` - 性能指标
- `system_configs` - 系统配置

### 视图和存储过程
- `v_server_health_summary` - 服务器健康状态汇总视图
- `v_performance_summary` - 性能指标汇总视图
- `sp_cleanup_expired_data` - 数据清理存储过程

## 🔧 技术实现

### 依赖配置

#### Maven 依赖
```xml
<!-- MyBatis Spring Boot Starter -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- MySQL Connector -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- HikariCP Connection Pool -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

#### 数据源配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mcp-bridge?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: hot365fm
    driver-class-name: com.mysql.cj.jdbc.Driver
    
    # HikariCP 连接池配置
    hikari:
      pool-name: McpBridgeHikariCP
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
```

#### MyBatis 配置
```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.pajk.mcpbridge.core.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

### 核心组件

#### 1. 实体类 (Entity)
- `BaseEntity` - 基础实体类，包含通用字段
- `McpServer` - MCP服务器实体
- `HealthCheckRecord` - 健康检查记录实体
- `RoutingLog` - 路由日志实体

#### 2. Mapper 接口
- `McpServerMapper` - MCP服务器数据访问接口
- `HealthCheckRecordMapper` - 健康检查记录数据访问接口
- `RoutingLogMapper` - 路由日志数据访问接口

#### 3. 服务类
- `McpServerPersistenceService` - MCP服务器持久化服务
- `HealthCheckPersistenceService` - 健康检查持久化服务
- `MyBatisConfig` - MyBatis配置类

#### 4. 类型处理器
- `JsonTypeHandler` - JSON字段类型处理器

## 🚀 使用方式

### 1. 数据库初始化

```bash
# 执行数据库设置脚本
cd mcp-router-v3/database
./setup.sh
```

脚本会自动：
- 检查 MySQL 连接
- 创建数据库
- 执行表结构脚本
- 初始化示例数据
- 验证安装

### 2. 应用配置

确保 `application.yml` 中的数据库配置正确：
```yaml
spring:
  profiles:
    active: dev  # 或 debug
  datasource:
    url: jdbc:mysql://localhost:3306/mcp-bridge?...
    username: root
    password: hot365fm
```

### 3. 服务使用示例

#### 保存服务器信息
```java
@Autowired
private McpServerPersistenceService persistenceService;

// 保存或更新服务器
McpServer server = persistenceService.saveOrUpdateServer(serverInfo);

// 更新健康状态
persistenceService.updateServerHealth(serverKey, true);
```

#### 记录健康检查
```java
@Autowired
private HealthCheckPersistenceService healthService;

// 记录成功检查
healthService.recordSuccessfulCheck(serverId, serverName, 
    CheckType.COMBINED, responseTime, details);

// 获取健康统计
Map<String, Object> stats = healthService.getServerHealthStats(serverId, 24);
```

## 📊 功能特性

### 1. 数据持久化
- **服务器信息持久化**: 自动保存和更新 MCP 服务器信息
- **健康检查历史**: 完整记录所有健康检查结果
- **路由日志记录**: 详细记录每个路由请求的执行情况
- **性能指标存储**: 保存各种性能监控数据

### 2. 查询和统计
- **健康状态汇总**: 实时查看服务器健康状态
- **性能分析**: 基于历史数据的性能趋势分析
- **故障排查**: 详细的错误日志和失败记录
- **统计报表**: 各种维度的统计信息

### 3. 数据管理
- **自动清理**: 定期清理过期数据
- **批量操作**: 支持批量插入和更新
- **事务管理**: 确保数据一致性
- **连接池管理**: 高效的数据库连接管理

### 4. 监控和告警
- **实时监控**: 基于数据库数据的实时监控
- **阈值告警**: 可配置的告警规则
- **趋势分析**: 长期数据趋势分析
- **报表生成**: 自动生成各种报表

## 🔍 调试和监控

### 1. SQL 日志
在调试环境中启用 SQL 日志：
```yaml
logging:
  level:
    com.pajk.mcpbridge.core.mapper: DEBUG
```

### 2. 连接池监控
HikariCP 提供详细的连接池监控信息，可通过 JMX 或日志查看。

### 3. 性能监控
- 查询执行时间监控
- 连接获取时间监控
- 数据库连接数监控

## 📈 性能优化

### 1. 索引优化
已为常用查询字段创建了合适的索引：
- 服务器查询索引
- 时间范围查询索引
- 状态过滤索引

### 2. 连接池优化
- 合理的连接池大小配置
- 连接泄漏检测
- 连接有效性验证

### 3. 查询优化
- 使用 MyBatis 缓存
- 批量操作支持
- 分页查询支持

## 🛠️ 扩展建议

### 1. 读写分离
可以配置主从数据库实现读写分离：
```yaml
spring:
  datasource:
    master:
      url: jdbc:mysql://master:3306/mcp-bridge
    slave:
      url: jdbc:mysql://slave:3306/mcp-bridge
```

### 2. 分库分表
对于大量数据，可以考虑：
- 按时间分表（如按月分表）
- 按服务器分库
- 使用 ShardingSphere

### 3. 缓存集成
- Redis 缓存热点数据
- 本地缓存提升查询性能
- 缓存更新策略

### 4. 数据分析
- 集成 Elasticsearch 用于日志分析
- 使用 ClickHouse 进行大数据分析
- 集成 Grafana 进行数据可视化

## 🔒 安全考虑

### 1. 数据库安全
- 使用专用数据库用户
- 限制数据库访问权限
- 启用 SSL 连接

### 2. 数据保护
- 敏感数据加密存储
- 定期数据备份
- 访问日志记录

### 3. SQL 注入防护
- 使用参数化查询
- 输入验证和过滤
- 最小权限原则

## 📝 维护指南

### 1. 定期维护
- 执行数据清理存储过程
- 检查索引使用情况
- 监控数据库性能

### 2. 备份策略
- 定期全量备份
- 增量备份
- 备份恢复测试

### 3. 升级计划
- 数据库版本升级
- 表结构变更管理
- 数据迁移策略

## 🎉 总结

通过本次 MySQL 集成，mcp-router-v3 项目现在具备了：

- **完整的数据持久化能力** - 所有关键信息都能可靠存储
- **强大的查询和分析功能** - 支持复杂的数据查询和统计分析
- **高性能的数据访问** - 优化的连接池和查询性能
- **灵活的扩展能力** - 易于扩展新的数据表和功能
- **完善的监控和调试** - 详细的日志和监控信息

这为后续的功能开发和系统监控提供了坚实的数据基础。




