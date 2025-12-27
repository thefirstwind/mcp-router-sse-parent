# RESTful 接口域名访问慢 - 解决方案

## 问题确认

通过排查脚本确认：
- **DNS 解析时间：5.003 秒**（占 99.6%）
- **总时间：5.024 秒**
- **IP 访问：0.001 秒**（快 5000 倍）

**根本原因**：macOS 上 `.local` 域名使用 mDNS，即使 `/etc/hosts` 中有条目，curl 可能仍会先尝试 mDNS 查询，导致 5 秒延迟。

## 立即解决方案

### 方案 1：使用 --resolve 选项（推荐用于测试）

```bash
curl --resolve mcp-bridge.local:80:127.0.0.1 \
  http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```

**优点**：
- 立即生效，无需系统配置
- 完全绕过 DNS 查询
- 适合自动化脚本

**缺点**：
- 每次都需要指定

### 方案 2：刷新 DNS 缓存

```bash
# 刷新 DNS 缓存
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder

# 然后重新测试
curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```

### 方案 3：使用 IP 地址（最简单）

```bash
curl http://127.0.0.1/mcp/router/tools/mcp-server-v6
```

## 长期解决方案

### 方案 A：配置 dnsmasq（本地开发）

1. 安装 dnsmasq：
```bash
brew install dnsmasq
```

2. 配置 dnsmasq：
```bash
echo 'address=/.local/127.0.0.1' >> /usr/local/etc/dnsmasq.conf
```

3. 启动 dnsmasq：
```bash
brew services start dnsmasq
```

4. 配置系统 DNS：
   - 系统偏好设置 > 网络 > 高级 > DNS
   - 添加 `127.0.0.1` 到 DNS 服务器列表的最前面

### 方案 B：使用真实域名（生产环境）

在生产环境使用真实域名（如 `mcp-bridge.example.com`），配置标准 DNS 记录。

## 验证脚本

创建测试脚本 `test-restful-fast.sh`：

```bash
#!/bin/bash

echo "=== 测试 RESTful 接口响应时间 ==="
echo ""

# 方法1：使用 --resolve（推荐）
echo "1. 使用 --resolve（绕过 DNS）："
time curl --resolve mcp-bridge.local:80:127.0.0.1 \
  -s -o /dev/null \
  http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
echo ""

# 方法2：直接使用 IP
echo "2. 直接使用 IP："
time curl -s -o /dev/null \
  http://127.0.0.1/mcp/router/tools/mcp-server-v6
echo ""

# 方法3：使用域名（如果 DNS 已修复）
echo "3. 使用域名（如果 DNS 已修复）："
time curl -s -o /dev/null \
  http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```

## 总结

**问题**：macOS mDNS 导致 `.local` 域名解析慢（5 秒）

**立即解决**：使用 `--resolve` 选项或 IP 地址

**长期解决**：配置 dnsmasq 或使用真实域名

**影响**：所有使用 `.local` 域名的请求（SSE 和 RESTful）



















