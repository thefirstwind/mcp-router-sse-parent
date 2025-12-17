# DNS 解析慢问题 - 最终解决方案

## 当前状态

✅ **dnsmasq 实际上在运行**（虽然 brew services 状态显示不对）
- 进程正在运行
- 功能正常：`dig @127.0.0.1 mcp-bridge.local` 返回正确

✅ **已配置 .test 域名作为备选方案**
- `/etc/hosts` 已添加 `mcp-bridge.test`
- Nginx 配置已更新为 `server_name mcp-bridge.test`

## 两个解决方案

### 方案 1：使用 dnsmasq + .local 域名

**前提**：dnsmasq 已经在工作（已验证）

**步骤**：

1. **配置系统 DNS 为 127.0.0.1**（最重要）：
   ```bash
   sudo networksetup -setdnsservers Wi-Fi 127.0.0.1
   ```

2. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

3. **验证**：
   ```bash
   time dig +short mcp-bridge.local
   # 应该 < 0.1 秒
   
   time curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
   # 应该 < 0.2 秒
   ```

**优点**：
- ✅ 使用原有的 .local 域名
- ✅ 不需要修改客户端配置

**缺点**：
- ⚠️ 需要配置系统 DNS
- ⚠️ brew services 状态显示异常（但不影响使用）

---

### 方案 2：使用 .test 域名（推荐，最简单）

**前提**：已配置完成（已验证）

**步骤**：

1. **重新加载 Nginx**（如果还没做）：
   ```bash
   sudo nginx -s reload -c $(pwd)/nginx/nginx.conf
   ```

2. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

3. **使用新域名**：
   ```bash
   curl http://mcp-bridge.test/mcp/router/tools/mcp-server-v6
   ```

**优点**：
- ✅ 不需要 dnsmasq
- ✅ 不需要配置系统 DNS
- ✅ 立即生效
- ✅ 所有客户端都支持

**缺点**：
- ⚠️ 需要更新客户端配置（从 .local 改为 .test）

---

## 推荐

**如果不想修改客户端配置**：
- 使用方案 1（dnsmasq + .local），只需配置系统 DNS

**如果想要最简单的方案**：
- 使用方案 2（.test 域名），不需要 dnsmasq

---

## 验证脚本

### 验证 dnsmasq 方案

```bash
./scripts/verify-dnsmasq.sh
```

### 验证 .test 域名方案

```bash
# 测试 DNS 解析
time dig +short mcp-bridge.test

# 测试 HTTP 请求
time curl http://mcp-bridge.test/mcp/router/tools/mcp-server-v6
```

---

## 故障排查

### dnsmasq 方案仍然慢

1. **检查系统 DNS 配置**：
   ```bash
   networksetup -getdnsservers Wi-Fi
   ```
   应该显示 `127.0.0.1`

2. **检查 dnsmasq 是否工作**：
   ```bash
   dig @127.0.0.1 mcp-bridge.local +short
   ```
   应该返回 `127.0.0.1`

3. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

### .test 域名方案仍然慢

1. **检查 /etc/hosts**：
   ```bash
   grep mcp-bridge.test /etc/hosts
   ```

2. **检查 Nginx 配置**：
   ```bash
   grep server_name nginx/nginx.conf
   ```

3. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

---

## 总结

两个方案都已配置完成，选择其中一个使用即可：

- **方案 1**：配置系统 DNS → 使用 `mcp-bridge.local`
- **方案 2**：直接使用 `mcp-bridge.test`（推荐）














