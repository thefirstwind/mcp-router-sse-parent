# Nginx 配置更新说明

## 当前状态

配置文件已准备好，但需要手动更新 Nginx 配置（需要 sudo 权限）。

## 更新步骤

### 方法 1: 使用脚本（推荐）

```bash
cd mcp-router-v3
./scripts/update-nginx-config.sh
```

脚本会提示输入密码，然后自动完成：
1. 复制配置文件
2. 测试配置
3. 重载 Nginx

### 方法 2: 手动更新

```bash
# 1. 复制配置文件
sudo cp mcp-router-v3/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# 2. 测试配置
sudo nginx -t

# 3. 重载 Nginx
sudo nginx -s reload
```

## 验证配置

更新后，检查配置是否生效：

```bash
# 检查关键配置项
grep -E "X-Forwarded-Host|proxy_buffering|proxy_read_timeout" /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# 应该看到：
# proxy_set_header X-Forwarded-Host $host;
# proxy_buffering off;
# proxy_read_timeout 300s;
```

## 测试连接

```bash
# 测试 SSE 连接（应该能正常连接，不超时）
curl -N -m 5 http://mcp-bridge.local/sse/mcp-server-v6

# 应该能看到：
# event:endpoint
# data:http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...
```

## 如果仍然有问题

1. **检查 Nginx 日志**：
   ```bash
   tail -f /opt/homebrew/var/log/nginx/error.log
   ```

2. **检查应用日志**：
   ```bash
   tail -f mcp-router-v3/logs/router-8051.log | grep -E "(endpoint|baseUrl|forwardedHost)"
   ```

3. **确认域名解析**：
   ```bash
   ping mcp-bridge.local
   # 或
   curl -v http://mcp-bridge.local/actuator/health
   ```

## 关键配置说明

更新后的配置包含：

1. **X-Forwarded-Host**: 让应用识别原始域名
2. **proxy_buffering off**: 禁用缓冲，确保 SSE 实时传输
3. **proxy_read_timeout 300s**: SSE 长连接需要较长的超时
4. **X-Accel-Buffering no**: 禁用 Nginx 缓冲

这些配置对于 SSE 连接正常工作至关重要。




















