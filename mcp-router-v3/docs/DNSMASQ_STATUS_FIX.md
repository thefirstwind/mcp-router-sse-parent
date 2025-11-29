# dnsmasq 状态问题 - 解决方案

## 问题现象

`brew services list` 显示 dnsmasq 状态为 `none`，但实际进程在运行。

## 原因

dnsmasq 可能是手动启动的，而不是通过 `brew services` 启动的，导致状态显示不一致。

## 验证 dnsmasq 是否工作

```bash
# 检查进程
ps aux | grep dnsmasq | grep -v grep

# 测试功能
dig @127.0.0.1 mcp-bridge.local +short
# 应该返回: 127.0.0.1
```

如果返回正确，说明 **dnsmasq 实际上在工作**，只是状态显示有问题。

## 解决方案

### 方案 1：继续使用 dnsmasq（如果它已经在工作）

如果 `dig @127.0.0.1 mcp-bridge.local` 返回正确，只需要：

1. **配置系统 DNS 为 127.0.0.1**（最重要）：
   ```bash
   sudo networksetup -setdnsservers Wi-Fi 127.0.0.1
   ```

2. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

3. **测试**：
   ```bash
   time dig +short mcp-bridge.local
   # 应该 < 0.1 秒
   ```

### 方案 2：改用 .test 域名（推荐，最简单）

如果 dnsmasq 有问题，或者想要更简单的方案：

```bash
./scripts/switch-to-test-domain.sh
```

**优点**：
- ✅ 不需要 dnsmasq
- ✅ 不需要修改系统 DNS
- ✅ 立即生效
- ✅ 所有客户端都支持

### 方案 3：修复 brew services 状态

如果想修复 brew services 状态：

```bash
# 停止所有 dnsmasq 进程
sudo pkill dnsmasq

# 通过 brew services 启动
sudo brew services start dnsmasq

# 检查状态
brew services list | grep dnsmasq
```

## 推荐

**如果 dnsmasq 已经在工作**（`dig @127.0.0.1` 返回正确）：
- 继续使用，只需配置系统 DNS

**如果 dnsmasq 无法启动或有问题**：
- 改用 `.test` 域名（最简单）

## 验证

运行验证脚本：

```bash
./scripts/fix-dnsmasq-status.sh
```

或手动测试：

```bash
# 测试 dnsmasq
dig @127.0.0.1 mcp-bridge.local +short

# 测试系统 DNS（如果配置了 127.0.0.1）
time dig +short mcp-bridge.local

# 测试 HTTP
time curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```




