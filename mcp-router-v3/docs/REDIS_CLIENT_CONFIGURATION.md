# Redis 客户端配置说明

## 概述

`mcp-router-v3` 支持两种 Redis 客户端实现：
1. **本地环境**：使用 Jedis（适合本地开发和测试）
2. **生产环境**：使用 PajkJedisClient（生产环境封装的 Redis 客户端）

## 配置方式

### 本地环境配置（默认）

在 `application.yml` 中配置：

```yaml
mcp:
  session:
    redis:
      type: local  # 或省略此配置（默认为 local）
      host: localhost
      port: 6379
      password: 
      database: 0
      timeout: 2000
      pool:
        max-total: 20
        max-idle: 10
        min-idle: 5
```

### 生产环境配置

在 `application.yml` 中配置：

```yaml
mcp:
  session:
    redis:
      type: production
      production:
        cluster-name: mcpbridge
        category: MCP
        app-name: mcp-bridge
```

## 架构设计

### 接口抽象

- `RedisClient` 接口：定义统一的 Redis 操作方法
- `LocalJedisRedisClient`：本地环境实现，使用 JedisPool
- `ProductionPajkRedisClient`：生产环境实现，使用 PajkJedisClient

### 条件加载

使用 Spring 的 `@ConditionalOnProperty` 注解实现条件加载：

- `JedisConfig`：仅在 `mcp.session.redis.type=local` 时加载（默认）
- `LocalJedisRedisClient`：仅在 `mcp.session.redis.type=local` 时加载（默认）
- `PajkJedisClient`：仅在 `mcp.session.redis.type=production` 时加载
- `ProductionPajkRedisClient`：仅在 `mcp.session.redis.type=production` 时加载

### SessionRedisRepository

`SessionRedisRepository` 通过依赖注入 `RedisClient` 接口，自动根据配置选择对应的实现。

## PajkJedisClient 说明

### 依赖要求

`PajkJedisClient` 依赖生产环境的 Redis 客户端封装库，以下类需要在生产环境的 classpath 中存在：

- `com.pajk.redis.client.RedisStoredClient`
- `com.pajk.redis.client.StorageFactory`
- `com.pajk.redis.client.Storage`
- `com.pajk.redis.client.stored()` 静态方法

### 当前实现状态

由于生产环境的 Redis 客户端库不在本地开发环境中，`PajkJedisClient` 当前使用占位实现。在生产环境部署时，需要：

1. 确保生产环境的 Redis 客户端库在 classpath 中
2. 根据实际 API 调整 `PajkJedisClient` 的实现
3. 根据实际 API 调整 `ProductionPajkRedisClient` 的适配逻辑

### 配置参数

- `cluster-name`：集群名称，默认 `mcpbridge`
- `category`：分类，默认 `MCP`
- `app-name`：应用名称，默认 `mcp-bridge`

## 切换环境

### 从本地切换到生产

1. 修改 `application.yml`：
   ```yaml
   mcp:
     session:
       redis:
         type: production
         production:
           cluster-name: mcpbridge
           category: MCP
           app-name: mcp-bridge
   ```

2. 确保生产环境的 Redis 客户端库在 classpath 中

3. 重启应用

### 从生产切换到本地

1. 修改 `application.yml`：
   ```yaml
   mcp:
     session:
       redis:
         type: local
         host: localhost
         port: 6379
         # ... 其他配置
   ```

2. 重启应用

## 注意事项

1. **本地开发**：默认使用 Jedis，无需额外配置
2. **生产部署**：需要确保生产环境的 Redis 客户端库可用
3. **API 差异**：`PajkJedisClient` 的 API 可能与标准 Redis 操作不完全一致，`ProductionPajkRedisClient` 需要根据实际 API 进行适配
4. **Hash 操作**：如果 `PajkJedisClient` 不支持 Hash 操作，`ProductionPajkRedisClient` 可能需要使用组合键的方式模拟

## 验证

启动应用后，查看日志确认使用的 Redis 客户端：

- 本地环境：`✅ LocalJedisRedisClient initialized (using JedisPool)`
- 生产环境：`✅ ProductionPajkRedisClient initialized (using PajkJedisClient)`





