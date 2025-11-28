# 通用 DNS 解析慢问题解决方案

## 问题

`curl --resolve` 只能解决 curl 的问题，但其他客户端（浏览器、MCP Inspector、VSCode MCP 插件等）无法使用这个选项。

## 通用解决方案

### 方案 1：配置 dnsmasq（推荐，所有应用受益）

这是最通用的方案，可以让**所有应用**都受益，包括：
- 浏览器
- MCP Inspector
- VSCode MCP 插件
- curl
- 任何使用域名的客户端

#### 安装和配置

```bash
# 1. 安装 dnsmasq
brew install dnsmasq

# 2. 配置 dnsmasq（自动解析所有 .local 域名）
echo 'address=/.local/127.0.0.1' >> /usr/local/etc/dnsmasq.conf

# 3. 启动 dnsmasq
brew services start dnsmasq

# 4. 配置系统 DNS（让系统使用 dnsmasq）
# 方法 A：通过系统偏好设置
# - 系统偏好设置 > 网络 > 高级 > DNS
# - 添加 127.0.0.1 到 DNS 服务器列表的最前面
# - 点击"好"保存

# 方法 B：通过命令行（需要管理员权限）
# sudo networksetup -setdnsservers Wi-Fi 127.0.0.1
# sudo networksetup -setdnsservers Ethernet 127.0.0.1
```

#### 验证

```bash
# 刷新 DNS 缓存
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder

# 测试
ping -c 1 mcp-bridge.local
# 应该立即返回，而不是等待 5 秒
```

#### 优点
- ✅ 所有应用都受益
- 一次配置，永久有效
- 支持所有 `.local` 域名

#### 缺点
- 需要安装和配置
- 需要修改系统 DNS 设置

---

### 方案 2：使用真实域名（生产环境推荐）

在生产环境使用真实域名，配置标准 DNS 记录。

#### 配置步骤

1. **配置 DNS 记录**（在 DNS 服务器上）：
   ```
   A 记录：mcp-bridge.example.com → 服务器 IP
   ```

2. **修改 Nginx 配置**：
   ```nginx
   server_name mcp-bridge.example.com;
   ```

3. **更新客户端配置**：
   - 使用 `http://mcp-bridge.example.com` 而不是 `http://mcp-bridge.local`

#### 优点
- ✅ 标准 DNS 解析，速度快
- ✅ 所有客户端自动支持
- ✅ 适合生产环境

#### 缺点
- 需要 DNS 服务器配置
- 不适合本地开发

---

### 方案 3：自动配置 /etc/hosts（简单但需每台机器配置）

创建一个脚本，自动在 `/etc/hosts` 中添加条目。

#### 创建配置脚本

```bash
#!/bin/bash
# scripts/setup-local-dns.sh

DOMAIN="mcp-bridge.local"
IP="127.0.0.1"
HOSTS_FILE="/etc/hosts"

if grep -q "${DOMAIN}" "${HOSTS_FILE}" 2>/dev/null; then
    echo "✅ ${DOMAIN} 已在 ${HOSTS_FILE} 中"
else
    echo "添加 ${DOMAIN} 到 ${HOSTS_FILE}..."
    sudo sh -c "echo '${IP} ${DOMAIN}' >> ${HOSTS_FILE}"
    echo "✅ 已添加"
    
    # 刷新 DNS 缓存
    sudo dscacheutil -flushcache
    sudo killall -HUP mDNSResponder
    echo "✅ DNS 缓存已刷新"
fi
```

#### 使用

```bash
chmod +x scripts/setup-local-dns.sh
./scripts/setup-local-dns.sh
```

#### 优点
- ✅ 简单，无需安装额外软件
- ✅ 所有应用都受益

#### 缺点
- 需要每台机器配置
- 需要管理员权限

---

### 方案 4：修改 Nginx 配置（仅适用于 Nginx 作为代理）

如果 Nginx 是代理，可以在 Nginx 层面处理，但客户端仍然需要解析域名。

这个方案**不能解决客户端 DNS 解析慢的问题**，因为 DNS 解析发生在客户端，在请求到达 Nginx 之前。

---

## 推荐方案对比

| 方案 | 适用场景 | 所有客户端支持 | 配置复杂度 |
|------|---------|---------------|-----------|
| dnsmasq | 本地开发 | ✅ | 中等 |
| 真实域名 | 生产环境 | ✅ | 低 |
| /etc/hosts | 本地开发 | ✅ | 低 |
| --resolve | 临时测试 | ❌ | 低 |

## 最佳实践

### 本地开发环境
**推荐：方案 1（dnsmasq）或方案 3（/etc/hosts）**

- 如果团队多人开发：使用 dnsmasq（统一配置）
- 如果个人开发：使用 /etc/hosts（简单快速）

### 生产环境
**推荐：方案 2（真实域名）**

- 使用标准 DNS 记录
- 所有客户端自动支持
- 性能最佳

## 验证所有客户端

配置后，测试以下客户端：

1. **浏览器**：
   ```bash
   open http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
   ```

2. **curl**：
   ```bash
   curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
   ```

3. **MCP Inspector**：
   - 连接：`http://mcp-bridge.local/sse/mcp-server-v6`
   - 应该立即连接，而不是等待 5 秒

4. **VSCode MCP 插件**：
   - 配置中使用 `http://mcp-bridge.local`
   - 应该立即连接

## 故障排查

如果配置后仍然慢：

1. **检查 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

2. **检查 /etc/hosts**：
   ```bash
   grep mcp-bridge.local /etc/hosts
   ```

3. **检查 dnsmasq 状态**：
   ```bash
   brew services list | grep dnsmasq
   ```

4. **测试 DNS 解析**：
   ```bash
   dig mcp-bridge.local
   # 或
   nslookup mcp-bridge.local
   ```




