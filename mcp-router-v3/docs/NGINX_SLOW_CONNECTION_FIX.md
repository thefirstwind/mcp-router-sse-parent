# Nginx 域名访问慢问题修复指南

## 问题确认

从测试结果确认：
- **直接访问应用（IP）**：0.03s ✅
- **通过域名访问（Nginx）**：5.1s ❌（连接时间 5 秒）
- **使用 IP 直接访问 Nginx**：0.019s ✅

**问题根源**：Nginx 配置缺少 `proxy_connect_timeout` 设置，导致连接建立时使用默认超时（可能是 60 秒），在某些情况下会等待 5 秒左右。

## 修复步骤

### 1. 更新 Nginx 配置文件

配置文件已更新：`mcp-router-v3/nginx/nginx.conf`

关键修改：
- 添加 `resolver` 配置（DNS 解析优化）
- 添加 `proxy_connect_timeout 1s`（连接超时 1 秒）
- 添加 `proxy_send_timeout 60s`（发送超时）
- 保持 `proxy_read_timeout 300s`（SSE 长连接需要）

### 2. 应用配置

```bash
# 方法 1：使用脚本（推荐）
cd mcp-router-v3
./scripts/update-nginx-config.sh

# 方法 2：手动更新
sudo cp mcp-router-v3/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf
sudo nginx -t
sudo nginx -s reload
```

### 3. 验证

```bash
# 测试域名访问速度
time curl -s -o /dev/null -w "连接时间: %{time_connect}s\n总时间: %{time_total}s\n" http://mcp-bridge.local/actuator/health

# 应该看到：
# 连接时间: 0.00Xs
# 总时间: 0.0Xs
```

## 关键配置说明

```nginx
# DNS 解析配置
resolver 127.0.0.1 valid=10s;
resolver_timeout 1s;

# 连接超时设置（关键）
proxy_connect_timeout 1s;      # 连接超时 1 秒（避免 5 秒延迟）
proxy_send_timeout 60s;        # 发送超时 60 秒
proxy_read_timeout 300s;       # 读取超时 300 秒（SSE 长连接）
```

## 为什么需要这些配置？

1. **`proxy_connect_timeout 1s`**：
   - 默认值通常是 60 秒
   - 如果后端连接有问题，会等待很长时间
   - 设置为 1 秒可以快速失败，避免延迟

2. **`resolver` 配置**：
   - 明确指定 DNS 服务器和超时
   - 避免 DNS 解析延迟
   - `valid=10s` 缓存 DNS 结果 10 秒

3. **`resolver_timeout 1s`**：
   - DNS 查询超时 1 秒
   - 避免长时间等待 DNS 响应

## 如果仍然慢

1. **检查 Nginx 是否重载成功**：
   ```bash
   sudo nginx -t
   sudo nginx -s reload
   ```

2. **检查配置是否生效**：
   ```bash
   grep -E "proxy_connect_timeout|resolver" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf
   ```

3. **检查 Nginx 错误日志**：
   ```bash
   tail -f /opt/homebrew/var/log/nginx/error.log
   ```

4. **尝试重启 Nginx**（而不是重载）：
   ```bash
   sudo nginx -s stop
   sudo nginx
   ```















