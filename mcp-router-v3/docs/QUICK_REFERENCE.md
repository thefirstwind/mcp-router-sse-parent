# MCP Router V3 多实例部署快速参考

## 🚀 快速启动

```bash
# 1. 部署前检查
./scripts/deploy-checklist.sh

# 2. 启动所有实例
./scripts/start-instances.sh start

# 3. 查看状态
./scripts/start-instances.sh status

# 4. 运行完整测试
./scripts/full-deployment-test.sh
```

## 📋 常用命令

### 实例管理
```bash
# 启动
./scripts/start-instances.sh start

# 停止
./scripts/start-instances.sh stop

# 重启
./scripts/start-instances.sh restart

# 状态
./scripts/start-instances.sh status

# 日志
./scripts/start-instances.sh logs           # 所有实例
./scripts/start-instances.sh logs 8051      # 特定实例
```

### 测试验证
```bash
# 完整测试
./scripts/full-deployment-test.sh

# 多实例测试
./scripts/test-multi-instance.sh

# 会话验证
./scripts/verify-session.sh
```

### 健康检查
```bash
curl http://localhost:8051/actuator/health
curl http://localhost:8052/actuator/health
curl http://localhost:8053/actuator/health
```

## 🔧 配置要点

### 实例配置
- **端口**: 8051, 8052, 8053
- **实例ID**: router-instance-1, router-instance-2, router-instance-3
- **配置文件**: `application-multi-instance.yml`

### Redis 配置
```yaml
mcp:
  session:
    redis:
      type: local
      host: localhost
      port: 6379
```

### Nginx 配置
- **虚拟域名**: mcp-bridge.local
- **负载均衡**: ip_hash（会话粘性）
- **配置文件**: `nginx/nginx.conf`

## 📊 架构

```
客户端
  ↓
Nginx (端口 80)
  ↓ (ip_hash)
  ├─→ 实例 1 (8051)
  ├─→ 实例 2 (8052)
  └─→ 实例 3 (8053)
        ↓
      Redis (会话共享)
```

## 🐛 故障排查

### 实例无法启动
```bash
# 检查端口
lsof -i :8051

# 查看日志
tail -f logs/router-8051.log

# 检查状态
./scripts/start-instances.sh status
```

### 健康检查失败
```bash
# 测试端点
curl http://localhost:8051/actuator/health

# 检查数据库连接
# 检查 Redis 连接
```

### Nginx 502
```bash
# 检查后端
curl http://localhost:8051/actuator/health

# 检查 Nginx 日志
tail -f /var/log/nginx/mcp-bridge-error.log
```

## 📁 重要文件

- `scripts/start-instances.sh` - 实例管理
- `scripts/deploy-checklist.sh` - 部署检查
- `scripts/full-deployment-test.sh` - 完整测试
- `nginx/nginx.conf` - Nginx 配置
- `src/main/resources/application-multi-instance.yml` - 多实例配置

## 🔗 相关文档

- [多实例部署指南](./MULTI_INSTANCE_DEPLOYMENT.md)
- [部署检查清单](./DEPLOYMENT_CHECKLIST.md)
- [验证结果](./VERIFICATION_RESULTS.md)
- [最终验证总结](./FINAL_VERIFICATION_SUMMARY.md)
