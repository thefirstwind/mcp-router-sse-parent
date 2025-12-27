# dnsmasq 配置指南 - 解决 DNS 解析慢问题

## 当前状态

✅ **dnsmasq 已安装**
✅ **配置文件已更新** (`/opt/homebrew/etc/dnsmasq.conf`)
✅ **/etc/hosts 已配置**

## 下一步操作

### 1. 启动 dnsmasq（需要 sudo 权限）

```bash
sudo brew services start dnsmasq
```

或者如果已经启动，重启：

```bash
sudo brew services restart dnsmasq
```

检查状态：

```bash
brew services list | grep dnsmasq
```

应该看到 `dnsmasq started`。

---

### 2. 配置系统 DNS（最重要！）

这是**最关键的一步**。必须让系统优先使用 dnsmasq (127.0.0.1)。

#### 方法 A：通过系统偏好设置（推荐）

1. 打开：**系统偏好设置 > 网络**
2. 选择当前网络（**Wi-Fi** 或 **Ethernet**）
3. 点击 **高级** > **DNS**
4. 点击左下角的 **+** 添加 DNS 服务器
5. 输入：`127.0.0.1`
6. **将 127.0.0.1 拖到列表最前面**（最重要！）
7. 点击 **好** 保存

#### 方法 B：通过命令行

```bash
# 配置 Wi-Fi DNS
sudo networksetup -setdnsservers Wi-Fi 127.0.0.1

# 配置 Ethernet DNS
sudo networksetup -setdnsservers Ethernet 127.0.0.1
```

---

### 3. 刷新 DNS 缓存

```bash
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder
```

---

### 4. 验证配置

#### 测试 dnsmasq 是否响应

```bash
dig @127.0.0.1 mcp-bridge.local +short
```

应该返回：`127.0.0.1`

#### 测试 DNS 解析速度

```bash
time dig +short mcp-bridge.local
```

应该 < 0.1 秒（而不是 5 秒）。

#### 测试 HTTP 请求

```bash
time curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```

应该 < 0.2 秒（而不是 5+ 秒）。

---

## 配置文件位置

- **dnsmasq 配置**：`/opt/homebrew/etc/dnsmasq.conf`
- **/etc/hosts**：`/etc/hosts`

已添加的配置：

```
# MCP Router: 自动解析所有 .local 域名到 127.0.0.1
address=/.local/127.0.0.1
address=/mcp-bridge.local/127.0.0.1
```

---

## 故障排查

### 问题 1：DNS 解析仍然慢

**检查系统 DNS 配置**：

```bash
networksetup -getdnsservers Wi-Fi
networksetup -getdnsservers Ethernet
```

应该显示 `127.0.0.1` 在最前面。

**检查 dnsmasq 状态**：

```bash
brew services list | grep dnsmasq
```

应该显示 `started`。

**检查 dnsmasq 日志**：

```bash
tail -f /opt/homebrew/var/log/dnsmasq.log
```

### 问题 2：dnsmasq 启动失败

**检查配置文件语法**：

```bash
sudo dnsmasq --test -C /opt/homebrew/etc/dnsmasq.conf
```

**检查端口占用**：

```bash
lsof -i :53
```

如果端口被占用，可能需要停止其他 DNS 服务。

### 问题 3：某些应用仍然慢

某些应用可能缓存了 DNS 结果。尝试：

1. 重启应用
2. 刷新 DNS 缓存（见步骤 3）
3. 重启系统（最后手段）

---

## 验证所有客户端

配置完成后，测试以下客户端：

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
   - 应该立即连接

4. **VSCode MCP 插件**：
   - 配置中使用 `http://mcp-bridge.local`
   - 应该立即连接

---

## 卸载（如果需要）

```bash
# 停止服务
sudo brew services stop dnsmasq

# 卸载
brew uninstall dnsmasq

# 恢复系统 DNS（移除 127.0.0.1）
sudo networksetup -setdnsservers Wi-Fi "Empty"
sudo networksetup -setdnsservers Ethernet "Empty"
```

---

## 总结

配置 dnsmasq 后：

- ✅ 所有 `.local` 域名立即解析（< 0.1 秒）
- ✅ 所有客户端都受益（浏览器、MCP Inspector、VSCode 等）
- ✅ 一次配置，永久有效

**关键点**：必须配置系统 DNS 为 `127.0.0.1`，否则系统仍然会优先使用 mDNS。


















