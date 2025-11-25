# MCP Router V3 多实例快速开始

## 快速启动

### 1. 一键启动所有服务

```bash
cd mcp-router-v3
./scripts/quick-start.sh
```

### 2. 手动启动

#### 启动 Router 实例

```bash
# 启动所有实例
./scripts/start-instances.sh start

# 查看状态
./scripts/start-instances.sh status

# 查看日志
./scripts/start-instances.sh logs        # 所有实例
./scripts/start-instances.sh logs 8051   # 特定实例

# 停止所有实例
./scripts/start-instances.sh stop
```

#### 配置 Nginx

```bash
# macOS
sudo cp nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf
sudo nginx -t
sudo nginx -s reload

# Linux
sudo cp nginx/nginx.conf /etc/nginx/conf.d/mcp-bridge.conf
sudo nginx -t
sudo systemctl reload nginx
```

#### 配置本地域名（开发环境）

```bash
# 编辑 /etc/hosts
sudo vim /etc/hosts

# 添加：
127.0.0.1 mcp-bridge.local
```

### 3. 验证部署

```bash
# 运行测试脚本
./scripts/test-multi-instance.sh

# 或手动验证
./scripts/verify-session.sh
```

## 架构

```
客户端
  ↓
Nginx (端口 80, 虚拟域名: mcp-bridge.local)
  ↓ (负载均衡, ip_hash)
  ├─→ Router 实例 1 (端口 8051, instance-id: router-instance-1)
  ├─→ Router 实例 2 (端口 8052, instance-id: router-instance-2)
  └─→ Router 实例 3 (端口 8053, instance-id: router-instance-3)
        ↓
      Redis (共享会话存储)
        ↓
      ├─→ Server 实例 1 (端口 8071)
      └─→ Server 实例 2 (端口 8072)
```

## 关键配置

### 实例配置

每个实例通过以下方式配置：

1. **端口**: 通过 `--server.port` 参数或 `SERVER_PORT` 环境变量
2. **实例ID**: 通过 `--mcp.session.instance-id` 参数或 `MCP_SESSION_INSTANCE_ID` 环境变量
3. **Redis**: 所有实例共享相同的 Redis 配置

### Nginx 配置要点

- **ip_hash**: 实现会话粘性
- **proxy_buffering off**: SSE 实时传输
- **proxy_read_timeout 300s**: 支持长连接
- **X-Forwarded-Prefix**: 设置 context-path

## 常用命令

```bash
# 管理实例
./scripts/start-instances.sh start      # 启动
./scripts/start-instances.sh stop       # 停止
./scripts/start-instances.sh restart    # 重启
./scripts/start-instances.sh status     # 状态
./scripts/start-instances.sh logs       # 日志

# 测试
./scripts/test-multi-instance.sh        # 完整测试
./scripts/verify-session.sh            # 会话验证

# 健康检查
curl http://localhost:8051/actuator/health
curl http://localhost:8052/actuator/health
curl http://localhost:8053/actuator/health
curl http://mcp-bridge.local/actuator/health
```

## 故障排查

### 实例无法启动

```bash
# 检查端口占用
lsof -i :8051
lsof -i :8052
lsof -i :8053

# 查看日志
tail -f logs/router-8051.log
```

### Nginx 502 错误

```bash
# 检查后端实例
curl http://localhost:8051/actuator/health

# 检查 Nginx 日志
tail -f /var/log/nginx/mcp-bridge-error.log
```

### 会话丢失

```bash
# 检查 Redis
redis-cli KEYS "mcp:sessions:*"
redis-cli KEYS "mcp:instance:*"
```

## 更多信息

- [多实例部署指南](./docs/MULTI_INSTANCE_DEPLOYMENT.md)
- [Nginx 负载均衡总结](./docs/NGINX_LOAD_BALANCER_SUMMARY.md)
- [Redis 客户端配置](./docs/REDIS_CLIENT_CONFIGURATION.md)


