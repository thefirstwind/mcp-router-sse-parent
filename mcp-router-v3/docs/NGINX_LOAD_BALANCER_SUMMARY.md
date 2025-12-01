# Nginx 负载均衡与多实例部署总结

## 完成的工作

### 1. Nginx 配置 ✅

**文件**: `nginx/nginx.conf`

- ✅ 配置了 `upstream` 负载均衡，包含 3 个 mcp-router-v3 实例（8051, 8052, 8053）
- ✅ 使用 `ip_hash` 策略实现会话粘性（确保 SSE 长连接路由到同一实例）
- ✅ 配置了 SSE 端点（`/sse`），禁用缓冲确保实时传输
- ✅ 配置了 MCP 消息端点（`/mcp/message`）
- ✅ 配置了 RESTful 路由端点（`/mcp/router/route`, `/mcp/router/tools`）
- ✅ 配置了 Admin 管理界面（`/admin`）
- ✅ 设置了正确的代理头（`X-Forwarded-*`），包括 `X-Forwarded-Prefix` 用于 context-path
- ✅ 配置了超时设置（SSE 需要长连接，`proxy_read_timeout=300s`）

### 2. 多实例启动脚本 ✅

**文件**: `scripts/start-instances.sh`

- ✅ 支持启动/停止/重启/状态查询
- ✅ 自动检查端口占用
- ✅ 自动生成实例 ID（router-instance-1, router-instance-2, router-instance-3）
- ✅ 后台运行，日志输出到 `logs/router-{port}.log`
- ✅ PID 文件管理，支持优雅停止

**使用方法**:
```bash
./scripts/start-instances.sh start    # 启动所有实例
./scripts/start-instances.sh stop     # 停止所有实例
./scripts/start-instances.sh restart  # 重启所有实例
./scripts/start-instances.sh status   # 查看状态
```

### 3. 应用配置支持多实例 ✅

**文件**: `src/main/resources/application-multi-instance.yml`

- ✅ 支持通过环境变量 `SERVER_PORT` 指定端口
- ✅ 支持通过环境变量 `MCP_SESSION_INSTANCE_ID` 指定实例 ID
- ✅ 使用 `multi-instance` profile 激活

**配置方式**:
```bash
export SERVER_PORT=8051
export MCP_SESSION_INSTANCE_ID=router-instance-1
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8051 --mcp.session.instance-id=router-instance-1"
```

### 4. Redis 会话管理 ✅

**已实现的功能**:
- ✅ 所有实例共享同一个 Redis 实例
- ✅ 会话状态存储在 Redis 中（键格式: `mcp:sessions:{sessionId}`）
- ✅ 实例信息存储在 Redis 中（键格式: `mcp:instance:{instanceId}`）
- ✅ 支持会话迁移（即使客户端切换到不同实例，会话仍然可用）
- ✅ 自动 TTL 管理（默认 30 分钟）

**会话管理流程**:
1. 客户端建立 SSE 连接，生成 `sessionId`
2. 实例将 `sessionId` 和 `instanceId` 存储到 Redis
3. 后续请求通过 `sessionId` 查找会话，可以从任何实例访问
4. 如果实例宕机，Nginx 自动切换到其他实例，会话数据仍然可用

### 5. 验证脚本 ✅

**文件**: `scripts/verify-session.sh`

- ✅ 检查 Redis 连接
- ✅ 检查所有实例健康状态
- ✅ 测试 SSE 连接
- ✅ 测试 RESTful API
- ✅ 检查 Redis 中的会话数据

**使用方法**:
```bash
./scripts/verify-session.sh [base-url] [redis-host] [redis-port]
```

### 6. 快速启动脚本 ✅

**文件**: `scripts/quick-start.sh`

- ✅ 一键启动所有服务
- ✅ 自动检查依赖和端口
- ✅ 可选启动 MCP Server 实例
- ✅ 可选配置 Nginx
- ✅ 自动验证部署

**使用方法**:
```bash
./scripts/quick-start.sh
```

### 7. 部署文档 ✅

**文件**: `docs/MULTI_INSTANCE_DEPLOYMENT.md`

- ✅ 详细的架构说明
- ✅ 完整的部署步骤
- ✅ 配置说明
- ✅ 故障排查指南
- ✅ 生产环境建议

## 架构设计

### 负载均衡策略

**ip_hash**: 基于客户端 IP 的哈希，确保同一客户端的请求路由到同一实例。

**优点**:
- 适合 SSE 长连接（连接必须保持在同一实例）
- 简单可靠，无需额外的会话管理

**缺点**:
- 如果客户端 IP 变化，可能路由到不同实例（但会话数据在 Redis 中，仍然可用）

### 会话管理策略

**Redis 共享存储**: 所有实例共享同一个 Redis，会话状态存储在 Redis 中。

**优点**:
- 支持会话迁移（客户端可以切换到不同实例）
- 支持故障恢复（实例重启后会话仍然可用）
- 支持跨实例访问会话数据

**缺点**:
- 依赖 Redis 的可用性（需要 Redis 高可用）

### 高可用设计

1. **实例级高可用**: 3 个实例，任何一个宕机不影响服务
2. **会话级高可用**: 会话数据存储在 Redis，实例重启不影响会话
3. **负载均衡**: Nginx 自动检测实例健康状态，自动切换

## 配置要点

### Nginx 配置

1. **ip_hash**: 实现会话粘性
2. **proxy_buffering off**: 禁用缓冲，确保 SSE 实时传输
3. **proxy_read_timeout 300s**: 支持长连接
4. **X-Forwarded-Prefix**: 设置 context-path

### 应用配置

1. **instance-id**: 每个实例必须有唯一的实例 ID
2. **Redis 配置**: 所有实例共享相同的 Redis 配置
3. **端口配置**: 通过环境变量或启动参数指定

## 使用场景

### 开发环境

```bash
# 1. 启动 Redis
redis-server

# 2. 启动所有实例
cd mcp-router-v3
./scripts/start-instances.sh start

# 3. 配置 Nginx（可选）
sudo cp nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf
sudo nginx -s reload

# 4. 访问
curl http://mcp-bridge.local/sse/mcp-server-v6
```

### 生产环境

1. 使用 systemd 或 supervisor 管理实例
2. 配置 Nginx 为系统服务
3. 配置 Redis 主从或集群
4. 配置 SSL/TLS 证书
5. 配置监控和告警

## 验证清单

- [ ] 所有实例正常启动
- [ ] Nginx 配置正确加载
- [ ] Redis 连接正常
- [ ] SSE 连接可以建立
- [ ] RESTful API 可以访问
- [ ] 会话数据存储在 Redis 中
- [ ] 实例间可以共享会话数据
- [ ] 实例重启后会话仍然可用

## 下一步

1. **监控**: 集成 Prometheus/Grafana
2. **日志**: 集中式日志收集（ELK Stack）
3. **健康检查**: 使用 Nginx Plus 或第三方健康检查模块
4. **自动扩缩容**: 基于负载自动调整实例数量
5. **蓝绿部署**: 支持零停机部署

## 相关文档

- [多实例部署指南](./MULTI_INSTANCE_DEPLOYMENT.md)
- [Redis 客户端配置](./REDIS_CLIENT_CONFIGURATION.md)
- [会话管理修复](./SESSION_MANAGEMENT_FIX.md)








