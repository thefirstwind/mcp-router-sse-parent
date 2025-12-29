# MCP Router V3 多实例部署指南

## 概述

本文档描述如何使用 Nginx 作为负载均衡器，部署多个 `mcp-router-v3` 实例，并确保会话管理在多实例环境下正常工作。

## 架构

```
                    ┌─────────────┐
                    │   Nginx     │
                    │ (Port 80)   │
                    └──────┬──────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
    ┌───────▼──────┐ ┌────▼──────┐ ┌────▼──────┐
    │ Router-8051  │ │Router-8052│ │Router-8053│
    │ Instance-1   │ │Instance-2  │ │Instance-3  │
    └───────┬──────┘ └────┬───────┘ └────┬───────┘
            │             │              │
            └─────────────┼──────────────┘
                          │
                  ┌───────▼────────┐
                  │  Redis (共享)   │
                  │  会话状态存储    │
                  └─────────────────┘
                          │
            ┌─────────────┼─────────────┐
            │             │             │
    ┌───────▼──────┐ ┌────▼──────┐
    │ Server-8071  │ │Server-8072│
    │ mcp-server-v6│ │mcp-server-v6│
    └──────────────┘ └────────────┘
```

## 部署步骤

### 1. 配置 Nginx

#### 1.1 安装 Nginx

```bash
# macOS
brew install nginx

# Ubuntu/Debian
sudo apt-get install nginx

# CentOS/RHEL
sudo yum install nginx
```

#### 1.2 配置 Nginx

将 `nginx/nginx.conf` 复制到 Nginx 配置目录：

```bash
# macOS
sudo cp mcp-router-v3/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# Linux
sudo cp mcp-router-v3/nginx/nginx.conf /etc/nginx/conf.d/mcp-bridge.conf
```

#### 1.3 修改配置

编辑 Nginx 配置文件，更新以下内容：

- `server_name`: 将 `mcp-bridge.local` 替换为实际域名（如 `mcp-bridge.example.com`）
- `upstream` 中的服务器地址：如果应用不在本机，更新为实际 IP 地址

#### 1.4 配置本地域名（开发环境）

在 `/etc/hosts` 中添加：

```
127.0.0.1 mcp-bridge.local
```

#### 1.5 测试并启动 Nginx

```bash
# 测试配置
sudo nginx -t

# 启动/重启 Nginx
sudo nginx
# 或
sudo systemctl restart nginx
```

### 2. 启动 MCP Router 实例

#### 2.1 使用启动脚本（推荐）

```bash
cd mcp-router-v3
chmod +x scripts/start-instances.sh

# 启动所有实例
./scripts/start-instances.sh start

# 查看状态
./scripts/start-instances.sh status

# 停止所有实例
./scripts/start-instances.sh stop

# 重启所有实例
./scripts/start-instances.sh restart
```

#### 2.2 手动启动（用于调试）

```bash
# 实例 1
export SERVER_PORT=8051
export MCP_SESSION_INSTANCE_ID=router-instance-1
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8051 --mcp.session.instance-id=router-instance-1"

# 实例 2（新终端）
export SERVER_PORT=8052
export MCP_SESSION_INSTANCE_ID=router-instance-2
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8052 --mcp.session.instance-id=router-instance-2"

# 实例 3（新终端）
export SERVER_PORT=8053
export MCP_SESSION_INSTANCE_ID=router-instance-3
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8053 --mcp.session.instance-id=router-instance-3"
```

### 3. 启动 MCP Server 实例

```bash
# 启动 mcp-server-v6 实例 1 (端口 8071)
cd mcp-server-v6
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8071"

# 启动 mcp-server-v6 实例 2 (端口 8072) - 新终端
cd mcp-server-v6
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8072"
```

### 4. 验证部署

#### 4.1 检查实例状态

```bash
# 检查端口监听
netstat -an | grep -E "8051|8052|8053|8071|8072"

# 检查健康状态
curl http://localhost:8051/actuator/health
curl http://localhost:8052/actuator/health
curl http://localhost:8053/actuator/health
```

#### 4.2 测试 Nginx 负载均衡

```bash
# 测试 SSE 连接
curl -N http://mcp-bridge.local/sse/mcp-server-v6

# 测试 RESTful API
curl -X POST http://mcp-bridge.local/mcp/router/route/mcp-server-v6 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

#### 4.3 验证会话管理

使用 `scripts/verify-session.sh` 脚本验证会话在多实例间的共享：

```bash
./scripts/verify-session.sh
```

## 会话管理

### Redis 会话存储

所有 `mcp-router-v3` 实例共享同一个 Redis 实例，会话状态存储在 Redis 中：

- **键格式**: `mcp:sessions:{sessionId}`
- **实例键**: `mcp:instance:{instanceId}`
- **TTL**: 30 分钟（可配置）

### 会话粘性

Nginx 使用 `ip_hash` 策略实现会话粘性：

- 同一客户端的请求会路由到同一个后端实例
- 这对于 SSE 长连接特别重要
- 如果实例宕机，Nginx 会自动切换到其他实例

### 会话共享

即使使用会话粘性，会话状态仍然存储在 Redis 中，这意味着：

- 如果客户端切换到不同的实例（例如，IP 变化或实例重启），会话仍然可用
- 所有实例可以访问相同的会话数据
- 支持会话迁移和故障恢复

## 配置说明

### Nginx 配置要点

1. **ip_hash**: 确保同一客户端的请求路由到同一实例
2. **proxy_buffering off**: 禁用缓冲，确保 SSE 实时传输
3. **proxy_read_timeout**: 设置为 300s，支持长连接
4. **X-Forwarded-Prefix**: 设置 context-path，确保应用能正确生成 URL

### 应用配置要点

1. **instance-id**: 每个实例必须有唯一的实例 ID
2. **Redis 配置**: 所有实例共享相同的 Redis 配置
3. **端口配置**: 通过环境变量或启动参数指定

## 故障排查

### 问题 1: 实例无法启动

**检查**:
- 端口是否被占用: `lsof -i :8051`
- 日志文件: `logs/router-8051.log`
- Redis 连接是否正常

**解决**:
- 停止占用端口的进程
- 检查 Redis 服务状态
- 查看应用日志

### 问题 2: Nginx 502 Bad Gateway

**检查**:
- 后端实例是否运行: `curl http://localhost:8051/actuator/health`
- Nginx 错误日志: `/var/log/nginx/mcp-bridge-error.log`

**解决**:
- 启动后端实例
- 检查 Nginx 配置中的 upstream 地址

### 问题 3: 会话丢失

**检查**:
- Redis 连接是否正常
- 会话键是否存在: `redis-cli KEYS "mcp:sessions:*"`
- 实例 ID 是否正确配置

**解决**:
- 检查 Redis 配置
- 验证实例 ID 唯一性
- 查看 Redis 日志

### 问题 4: SSE 连接断开

**检查**:
- Nginx 超时配置
- 后端实例日志
- 网络连接

**解决**:
- 增加 `proxy_read_timeout`
- 检查后端实例健康状态
- 验证会话粘性配置

## 生产环境建议

1. **HTTPS**: 配置 SSL/TLS 证书
2. **健康检查**: 使用 Nginx Plus 或第三方健康检查模块
3. **日志轮转**: 配置日志轮转，避免日志文件过大
4. **监控**: 集成监控系统（Prometheus、Grafana）
5. **高可用**: 配置 Redis 主从或集群
6. **防火墙**: 配置防火墙规则，只允许必要的端口

## 性能优化

1. **连接池**: 调整 Redis 连接池大小
2. **缓存**: 考虑添加应用层缓存
3. **压缩**: 启用响应压缩
4. **CDN**: 静态资源使用 CDN

## 相关文档

- [Redis 客户端配置](./REDIS_CLIENT_CONFIGURATION.md)
- [会话管理修复](./SESSION_MANAGEMENT_FIX.md)




















