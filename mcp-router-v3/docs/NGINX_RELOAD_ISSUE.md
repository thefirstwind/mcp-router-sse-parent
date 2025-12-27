# Nginx 重新加载问题 - 解决方案

## 问题

执行 `sudo nginx -s reload -c $(pwd)/nginx/nginx.conf` 后，Nginx 依然没有使用新配置。

## 原因

`nginx -s reload` 命令会向**正在运行的 Nginx 进程**发送重新加载信号，但：
1. 如果 Nginx 使用的是**不同的配置文件**启动的，reload 不会切换到新配置文件
2. reload 只会重新读取**启动时使用的配置文件**
3. 如果 Nginx 进程不存在，reload 会失败

## 解决方案

### 方案 1：完全重启（推荐）

停止现有 Nginx，然后用新配置启动：

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3

# 1. 停止所有 Nginx 进程
sudo pkill nginx

# 2. 等待进程完全停止
sleep 2

# 3. 确认没有 Nginx 进程
ps aux | grep nginx | grep -v grep

# 4. 使用项目配置启动
sudo nginx -c $(pwd)/nginx/nginx.conf

# 5. 验证
ps aux | grep nginx | grep -v grep
curl http://mcp-bridge.test/actuator/health
```

### 方案 2：使用重启脚本

使用提供的脚本自动重启：

```bash
./scripts/restart-nginx.sh
```

### 方案 3：检查现有 Nginx 使用的配置

如果 Nginx 已经在运行，检查它使用的配置文件：

```bash
# 查找 Nginx master 进程
ps aux | grep "nginx.*master"

# 查看进程命令（包含配置文件路径）
ps -p <PID> -o command=
```

如果使用的不是项目配置文件，需要停止并重新启动。

## 验证

### 1. 检查 Nginx 进程

```bash
ps aux | grep nginx | grep -v grep
```

应该看到：
- `nginx: master process nginx -c /path/to/nginx.conf`
- `nginx: worker process`

### 2. 检查端口 80

```bash
lsof -i :80 | grep nginx
```

应该看到 Nginx 监听端口 80。

### 3. 测试配置

```bash
curl http://mcp-bridge.test/actuator/health
```

应该返回应用的健康状态。

## 常见问题

### Q: reload 后配置没有生效？

A: 检查 Nginx 启动时使用的配置文件：
```bash
ps aux | grep "nginx.*master" | grep -o "\-c [^ ]*"
```

如果路径不对，需要停止并重新启动。

### Q: 端口 80 被占用？

A: 检查占用端口的进程：
```bash
lsof -i :80
```

如果是其他 Nginx 实例，停止它：
```bash
sudo pkill nginx
```

### Q: 配置文件语法错误？

A: 测试配置文件：
```bash
nginx -t -c $(pwd)/nginx/nginx.conf
```

修复错误后重新启动。

## 相关脚本

- `scripts/diagnose-nginx.sh` - 诊断 Nginx 状态
- `scripts/restart-nginx.sh` - 重启 Nginx（使用项目配置）


















