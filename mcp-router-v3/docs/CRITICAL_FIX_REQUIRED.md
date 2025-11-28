# 关键问题修复说明

## 问题确认

从日志分析发现：

1. **Nginx 配置存在但未完全生效**：
   - 配置文件 `/opt/homebrew/etc/nginx/servers/mcp-bridge.conf` 包含正确的配置
   - 但应用日志显示有时能收到 `forwardedHost: mcp-bridge.local`，有时是 `null`
   - 说明 Nginx 可能有多个 worker 进程，部分加载了新配置，部分还是旧配置

2. **SSE 连接超时**：
   - 即使能收到 `forwardedHost`，SSE 连接仍然超时
   - 连接建立后 2 秒左右就被取消

## 必须执行的修复步骤

### 步骤 1: 强制重载 Nginx（必须）

```bash
# 测试配置
sudo nginx -t

# 如果测试通过，重载配置
sudo nginx -s reload

# 如果重载失败，尝试重启
sudo nginx -s stop
sudo nginx
```

### 步骤 2: 验证配置生效

```bash
# 运行调试脚本
cd mcp-router-v3
./scripts/debug-nginx-headers.sh

# 应该看到 X-Forwarded 头被传递
```

### 步骤 3: 检查 Nginx worker 进程

```bash
# 检查所有 worker 进程
ps aux | grep nginx

# 如果看到多个 worker 进程，可能需要重启 Nginx 而不是重载
```

## 如果重载后仍然有问题

### 检查 1: Nginx 错误日志

```bash
tail -f /opt/homebrew/var/log/nginx/error.log
```

### 检查 2: 应用日志

```bash
tail -f mcp-router-v3/logs/router-8051.log | grep -E "(forwardedHost|SSE connection)"
```

### 检查 3: 直接测试后端

```bash
# 测试后端是否正常
curl -N -m 3 -H "X-Forwarded-Host: mcp-bridge.local" http://localhost:8051/sse/mcp-server-v6

# 如果这个能工作，说明问题在 Nginx
```

## 可能的根本原因

1. **Nginx 配置未完全加载**：需要重启而不是重载
2. **Nginx worker 进程不一致**：部分进程使用旧配置
3. **SSE 配置不完整**：虽然配置文件中有，但可能没有正确应用

## 最终解决方案

如果重载/重启 Nginx 后仍然不行，可能需要：

1. **检查 Nginx 主配置文件**：
   ```bash
   cat /opt/homebrew/etc/nginx/nginx.conf | grep "include servers"
   ```
   确保包含 `include servers/*;`

2. **完全重启 Nginx**：
   ```bash
   sudo nginx -s stop
   sudo nginx
   ```

3. **检查端口占用**：
   ```bash
   lsof -i :80
   ```

## 验证修复

修复后，运行完整测试：

```bash
cd mcp-router-v3
./scripts/quick-verify.sh
```

应该看到：
- ✅ 测试 1 成功（应用代码正常）
- ✅ 测试 2 成功（Nginx 配置已生效）




