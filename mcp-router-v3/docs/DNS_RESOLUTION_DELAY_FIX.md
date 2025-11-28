# DNS 解析延迟问题修复

## 问题确认

从详细时间统计发现：
- **通过域名访问**：`time_namelookup: 5.002218` - DNS 解析用了 5 秒！
- **直接访问应用**：`time_namelookup: 0.000009` - DNS 解析几乎为 0

**根本原因**：这是**客户端 DNS 解析问题**，不是 Nginx 或应用的问题。当客户端（curl、浏览器等）尝试解析 `mcp-bridge.local` 时，DNS 查询用了 5 秒。

## 问题分析

虽然 `/etc/hosts` 中有配置 `127.0.0.1 mcp-bridge.local`，但 macOS 的 DNS 解析机制可能：
1. **DNS 缓存问题**：系统 DNS 缓存可能有问题
2. **解析顺序问题**：可能先尝试 DNS 服务器，再查 `/etc/hosts`
3. **DNS 服务器超时**：如果 DNS 服务器（192.168.31.1）响应慢，会等待超时

## 修复方案

### 1. 刷新 DNS 缓存

```bash
# 刷新 DNS 缓存
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder
```

### 2. 确保 /etc/hosts 配置正确

```bash
# 检查 /etc/hosts
cat /etc/hosts | grep mcp-bridge

# 应该看到：
# 127.0.0.1 mcp-bridge.local
```

### 3. 检查系统 DNS 配置

```bash
# 检查 DNS 配置
scutil --dns | grep nameserver
```

### 4. 临时解决方案：使用 --resolve

对于测试，可以使用 curl 的 `--resolve` 选项强制解析：

```bash
curl --resolve mcp-bridge.local:80:127.0.0.1 http://mcp-bridge.local/actuator/health
```

## 验证

刷新 DNS 缓存后，测试域名访问速度：

```bash
# 测试域名访问
time curl -s -o /dev/null -w "DNS解析时间: %{time_namelookup}s\n连接时间: %{time_connect}s\n总时间: %{time_total}s\n" http://mcp-bridge.local/actuator/health

# 应该看到：
# DNS解析时间: 0.00Xs
# 连接时间: 0.00Xs
# 总时间: 0.0Xs
```

## 长期解决方案

### 方案 1：使用本地 DNS 服务器（推荐）

配置系统使用本地 DNS 服务器（如 dnsmasq），优先查询 `/etc/hosts`：

```bash
# 安装 dnsmasq
brew install dnsmasq

# 配置 dnsmasq 优先使用 /etc/hosts
echo "addn-hosts=/etc/hosts" >> /opt/homebrew/etc/dnsmasq.conf

# 启动 dnsmasq
brew services start dnsmasq

# 配置系统使用本地 DNS
# 在系统设置 -> 网络 -> DNS 中添加 127.0.0.1
```

### 方案 2：修改系统 DNS 解析顺序

确保系统优先使用 `/etc/hosts`，而不是 DNS 服务器。这通常需要修改系统配置。

### 方案 3：使用 IP 地址访问（临时）

如果 DNS 解析问题无法解决，可以临时使用 IP 地址访问：

```bash
# 使用 IP 访问，但设置 Host 头
curl -H "Host: mcp-bridge.local" http://127.0.0.1/actuator/health
```

## 注意事项

- DNS 解析延迟是**客户端问题**，不是服务器问题
- Nginx 和应用代码都是正常的
- 刷新 DNS 缓存后，第一次访问可能仍然慢（需要重新解析）
- 后续访问应该会快（DNS 结果被缓存）

## 技术细节

### macOS DNS 解析机制

macOS 使用 `mDNSResponder` 进行 DNS 解析，解析顺序通常是：
1. 检查本地缓存
2. 查询 `/etc/hosts`
3. 查询配置的 DNS 服务器

如果 DNS 服务器响应慢或超时，会导致解析延迟。

### time_namelookup 的含义

- `time_namelookup`：DNS 解析时间
- `time_connect`：TCP 连接建立时间
- `time_total`：总时间

如果 `time_namelookup` 很大，说明问题在 DNS 解析阶段。




