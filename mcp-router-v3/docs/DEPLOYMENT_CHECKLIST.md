# 多实例部署检查清单

## 部署前检查

使用脚本自动检查：
```bash
./scripts/deploy-checklist.sh
```

### 手动检查项

#### 1. 环境准备
- [ ] Java 17+ 已安装
- [ ] Maven 3.6+ 已安装
- [ ] MySQL 数据库已配置并运行
- [ ] Redis 已安装并运行（用于会话共享）
- [ ] Nginx 已安装（用于负载均衡，可选）

#### 2. 配置文件
- [ ] `application.yml` 数据库配置正确
- [ ] `application-multi-instance.yml` 存在
- [ ] Redis 配置正确（`mcp.session.redis.*`）
- [ ] Nginx 配置已准备（`nginx/nginx.conf`）

#### 3. 端口检查
- [ ] 端口 8051 可用
- [ ] 端口 8052 可用
- [ ] 端口 8053 可用
- [ ] 端口 80 可用（Nginx，如果使用）

#### 4. 脚本权限
- [ ] `scripts/start-instances.sh` 可执行
- [ ] `scripts/test-multi-instance.sh` 可执行
- [ ] `scripts/verify-session.sh` 可执行
- [ ] `scripts/deploy-checklist.sh` 可执行

#### 5. 目录结构
- [ ] `logs/` 目录存在或可创建
- [ ] `pids/` 目录存在或可创建

## 部署步骤

### 1. 运行部署前检查
```bash
./scripts/deploy-checklist.sh
```

### 2. 编译项目（如果需要）
```bash
mvn clean compile -DskipTests
```

### 3. 启动所有实例
```bash
./scripts/start-instances.sh start
```

### 4. 验证部署
```bash
# 查看状态
./scripts/start-instances.sh status

# 运行完整测试
./scripts/full-deployment-test.sh

# 或运行快速测试
./scripts/test-multi-instance.sh
```

### 5. 配置 Nginx（可选）
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

### 6. 配置本地域名（开发环境）
```bash
# 编辑 /etc/hosts
sudo vim /etc/hosts
# 添加: 127.0.0.1 mcp-bridge.local
```

## 部署后验证

### 基本验证
- [ ] 所有实例健康检查通过
- [ ] Admin API 可访问
- [ ] RESTful API 正常工作
- [ ] SSE 连接可以建立

### 负载均衡验证（如果配置了 Nginx）
- [ ] Nginx 配置正确加载
- [ ] 请求可以正确路由到后端实例
- [ ] 会话粘性正常工作

### 会话管理验证（如果配置了 Redis）
- [ ] Redis 连接正常
- [ ] 会话数据存储在 Redis 中
- [ ] 跨实例可以访问会话数据

## 故障排查

### 实例无法启动
1. 检查端口是否被占用：`lsof -i :8051`
2. 查看日志：`tail -f logs/router-8051.log`
3. 检查配置：`cat src/main/resources/application-multi-instance.yml`

### 健康检查失败
1. 检查实例是否真正启动：`./scripts/start-instances.sh status`
2. 检查数据库连接
3. 检查 Redis 连接（如果使用）

### Nginx 502 错误
1. 检查后端实例：`curl http://localhost:8051/actuator/health`
2. 检查 Nginx 日志：`tail -f /var/log/nginx/mcp-bridge-error.log`
3. 检查 Nginx 配置：`sudo nginx -t`

## 生产环境检查清单

### 安全
- [ ] 配置 HTTPS（SSL/TLS 证书）
- [ ] 配置防火墙规则
- [ ] 配置访问控制

### 监控
- [ ] 配置日志收集
- [ ] 配置监控告警
- [ ] 配置健康检查

### 高可用
- [ ] Redis 主从或集群配置
- [ ] 数据库主从配置
- [ ] 备份策略

### 性能
- [ ] 连接池配置优化
- [ ] JVM 参数优化
- [ ] Nginx 性能调优





