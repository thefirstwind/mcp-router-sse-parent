# dnsmasq 启动问题 - 替代方案

## 问题

dnsmasq 无法通过 `brew services` 正常启动，可能的原因：
- macOS 系统服务占用端口 53
- 权限问题
- 配置文件问题

## 解决方案 A：使用 .test 域名（推荐，最简单）

**优点**：
- ✅ 不需要 dnsmasq
- ✅ 不需要修改系统 DNS
- ✅ 立即生效
- ✅ 所有客户端都支持

### 步骤

1. **添加 .test 域名到 /etc/hosts**：
   ```bash
   sudo sh -c "echo '127.0.0.1 mcp-bridge.test' >> /etc/hosts"
   ```

2. **修改 Nginx 配置**：
   ```bash
   # 编辑 nginx/nginx.conf
   # 将 server_name mcp-bridge.local; 改为：
   server_name mcp-bridge.test;
   ```

3. **重新加载 Nginx**：
   ```bash
   sudo nginx -s reload -c $(pwd)/nginx/nginx.conf
   ```

4. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

5. **使用新域名**：
   ```bash
   curl http://mcp-bridge.test/mcp/router/tools/mcp-server-v6
   ```

---

## 解决方案 B：修复 dnsmasq（如果必须使用 .local）

### 检查 dnsmasq 是否实际运行

```bash
ps aux | grep dnsmasq | grep -v grep
```

如果看到进程，说明 dnsmasq 在运行，只是 brew services 状态不对。

### 测试 dnsmasq 是否工作

```bash
dig @127.0.0.1 mcp-bridge.local +short
```

如果返回 `127.0.0.1`，说明 dnsmasq 正常工作。

### 如果 dnsmasq 未运行，尝试手动启动

```bash
# 停止可能存在的进程
sudo pkill dnsmasq

# 手动启动（前台模式，查看错误）
sudo /opt/homebrew/opt/dnsmasq/sbin/dnsmasq --keep-in-foreground -C /opt/homebrew/etc/dnsmasq.conf
```

查看错误信息，常见问题：

1. **端口 53 被占用**：
   - 修改配置文件，使用非标准端口：
     ```
     port=5353
     ```
   - 但这样需要修改系统 DNS 配置为 `127.0.0.1:5353`，macOS 不支持

2. **权限问题**：
   - 确保使用 `sudo` 启动

3. **配置文件错误**：
   - 检查配置文件语法：
     ```bash
     sudo /opt/homebrew/opt/dnsmasq/sbin/dnsmasq --test -C /opt/homebrew/etc/dnsmasq.conf
     ```

---

## 推荐方案对比

| 方案 | 复杂度 | 可靠性 | 推荐度 |
|------|--------|--------|--------|
| 改用 .test 域名 | ⭐ 简单 | ⭐⭐⭐ 高 | ✅ 推荐 |
| 修复 dnsmasq | ⭐⭐⭐ 复杂 | ⭐⭐ 中等 | ⚠️ 不推荐 |

---

## 快速切换脚本

创建 `scripts/switch-to-test-domain.sh`：

```bash
#!/bin/bash

DOMAIN="mcp-bridge.test"
NGINX_CONF="nginx/nginx.conf"

# 1. 添加 /etc/hosts
if ! grep -q "${DOMAIN}" /etc/hosts 2>/dev/null; then
    sudo sh -c "echo '127.0.0.1 ${DOMAIN}' >> /etc/hosts"
    echo "✅ 已添加 ${DOMAIN} 到 /etc/hosts"
fi

# 2. 修改 Nginx 配置
if grep -q "server_name mcp-bridge.local" "${NGINX_CONF}"; then
    sed -i '' 's/server_name mcp-bridge.local/server_name mcp-bridge.test/g' "${NGINX_CONF}"
    echo "✅ 已更新 Nginx 配置"
fi

# 3. 重新加载 Nginx
sudo nginx -s reload -c "$(pwd)/${NGINX_CONF}"
echo "✅ Nginx 已重新加载"

# 4. 刷新 DNS 缓存
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder
echo "✅ DNS 缓存已刷新"

echo ""
echo "=== 完成 ==="
echo "现在使用: http://${DOMAIN}/mcp/router/tools/mcp-server-v6"
```

---

## 总结

**如果 dnsmasq 无法启动，强烈推荐改用 .test 域名**：
- 不需要 dnsmasq
- 不需要修改系统 DNS
- 立即生效
- 所有客户端都支持

