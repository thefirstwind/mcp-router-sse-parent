# Nginx 域名访问慢问题修复

## 问题描述

通过域名访问（`http://mcp-bridge.local`）时，连接时间需要 5 秒多，而直接通过 IP 访问（`http://localhost:8051`）只需要 0.03 秒。

## 问题分析

从测试结果发现：
1. **直接访问应用（IP）**：0.03s - 非常快
2. **通过域名访问（Nginx）**：5.1s - 很慢，连接时间 5 秒
3. **使用 IP 直接访问 Nginx（绕过 DNS）**：0.019s - 非常快
4. **DNS 解析测试**：`nslookup` 超时 15 秒

**根本原因**：Nginx 在建立到后端的连接时，可能尝试解析域名，导致 DNS 查询延迟。虽然 `/etc/hosts` 中有配置，但 Nginx 可能使用了不同的 DNS 解析机制。

## 修复方案

### 1. 添加 DNS 解析配置

在 Nginx 配置中添加 `resolver` 配置，明确指定 DNS 服务器和超时时间：

```nginx
# DNS 解析配置：使用系统 DNS，设置较短的超时避免延迟
resolver 127.0.0.1 valid=10s;
resolver_timeout 1s;
```

### 2. 添加连接超时设置

在 `location` 块中添加 `proxy_connect_timeout`，避免长时间等待：

```nginx
# 连接超时设置（关键：避免 5 秒延迟）
proxy_connect_timeout 1s;      # 连接超时 1 秒
proxy_send_timeout 60s;        # 发送超时 60 秒
proxy_read_timeout 300s;       # 读取超时 300 秒（SSE 长连接）
```

### 3. 确保 upstream 使用 IP 地址

`upstream` 块中的服务器地址已经使用 IP 地址（`127.0.0.1`），不需要修改。

## 修改文件

`mcp-router-v3/nginx/nginx.conf`

## 更新步骤

```bash
# 1. 复制配置文件
sudo cp mcp-router-v3/nginx/nginx.conf /opt/homebrew/etc/nginx/servers/mcp-bridge.conf

# 2. 测试配置
sudo nginx -t

# 3. 重载 Nginx
sudo nginx -s reload
```

## 预期效果

- 域名访问的连接时间从 5 秒降低到几毫秒
- 总响应时间从 5 秒降低到几十毫秒
- SSE 连接建立不再有延迟

## 验证

更新配置后，测试域名访问速度：

```bash
# 测试域名访问
time curl -s -o /dev/null -w "连接时间: %{time_connect}s\n总时间: %{time_total}s\n" http://mcp-bridge.local/actuator/health

# 应该看到：
# 连接时间: 0.00Xs
# 总时间: 0.0Xs
```

## 技术细节

### DNS 解析延迟的原因

1. **Nginx 的 DNS 解析机制**：Nginx 在启动时或首次连接时可能会解析域名
2. **系统 DNS 配置**：如果系统 DNS 配置有问题，可能导致解析延迟
3. **缺少 resolver 配置**：没有明确指定 DNS 服务器和超时，Nginx 可能使用默认值，导致延迟

### proxy_connect_timeout 的作用

- **默认值**：通常是 60 秒
- **问题**：如果后端连接有问题，会等待 60 秒才超时
- **修复**：设置为 1 秒，快速失败，避免长时间等待

## 注意事项

- `resolver 127.0.0.1` 使用本地 DNS（通常是 systemd-resolved 或 dnsmasq）
- 如果本地 DNS 不可用，可以改为 `8.8.8.8` 或其他公共 DNS
- `valid=10s` 表示 DNS 结果缓存 10 秒
- `resolver_timeout 1s` 表示 DNS 查询超时 1 秒





