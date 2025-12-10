# 域名访问问题修复

## 问题描述

通过 IP 访问（`http://localhost:8051`）正常，但通过域名访问（`http://mcp-bridge.local`）时 SSE 连接超时。

## 问题原因

Nginx 配置文件 `/opt/homebrew/etc/nginx/servers/mcp-bridge.conf` 是旧版本，缺少以下关键配置：

1. **`X-Forwarded-Host` 头**：应用无法识别原始域名
2. **SSE 特定配置**：
   - `proxy_buffering off`：禁用缓冲，确保 SSE 实时传输
   - `proxy_read_timeout 300s`：SSE 长连接需要较长的超时
   - `proxy_set_header Connection ""`：保持连接
   - `proxy_set_header X-Accel-Buffering "no"`：禁用 Nginx 缓冲

## 解决方案

### 方法 1：使用自动更新脚本（推荐）

```bash
cd mcp-router-v3
./scripts/update-nginx-config.sh
```

脚本会自动：
1. 检测 Nginx 配置目录（macOS/Linux）
2. 复制最新的配置文件
3. 测试配置
4. 询问是否重载 Nginx

### 方法 2：手动更新

```bash
# 1. 复制配置文件
sudo cp mcp-router-v3/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# 2. 测试配置
sudo nginx -t

# 3. 重载 Nginx
sudo nginx -s reload
```

## 验证

更新配置后，验证域名访问：

```bash
# 测试域名访问
curl -N -m 5 http://mcp-bridge.local/sse/mcp-server-v6

# 应该能看到 endpoint 事件
# event:endpoint
# data:http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...
```

## 关键配置说明

更新后的 Nginx 配置包含：

```nginx
# 传递原始请求的 Host 和域名信息
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Host $host;  # 关键：让应用识别域名
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Port $server_port;

# SSE 特定配置
proxy_buffering off;              # 禁用缓冲
proxy_read_timeout 300s;         # 长连接超时
proxy_set_header Connection "";   # 保持连接
proxy_set_header X-Accel-Buffering "no";  # 禁用 Nginx 缓冲
```

## 技术细节

应用代码（`McpRouterServerConfig.buildBaseUrlFromRequest`）会按以下优先级识别域名：

1. **优先使用 `X-Forwarded-Host` 头**（如果存在）
2. 其次使用 `Host` 头
3. 最后回退到本地配置

因此，Nginx 必须正确传递 `X-Forwarded-Host` 头，应用才能生成正确的 endpoint URL。












