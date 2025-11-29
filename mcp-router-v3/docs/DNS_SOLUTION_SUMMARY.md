# DNS 解析慢问题 - 解决方案总结

## 问题确认

✅ **根本原因已确认**：macOS 对 `.local` 域名优先使用 mDNS，导致 5 秒延迟

- 即使 `/etc/hosts` 已配置，系统仍然先尝试 mDNS
- mDNS 查询失败（5秒超时）后才回退到 `/etc/hosts`
- 这导致所有客户端（浏览器、MCP Inspector、VSCode 等）都受影响

## 立即行动

### 方案 A：使用 dnsmasq（推荐，5分钟配置）

```bash
cd mcp-router-v3
./scripts/setup-dnsmasq.sh
```

然后按照提示配置系统 DNS（将 `127.0.0.1` 添加到 DNS 服务器列表最前面）。

**效果**：所有客户端立即受益，DNS 解析时间从 5 秒降到 < 0.1 秒。

---

### 方案 B：改用 `.test` 域名（简单，2分钟配置）

```bash
# 1. 添加 .test 域名到 /etc/hosts
sudo sh -c "echo '127.0.0.1 mcp-bridge.test' >> /etc/hosts"

# 2. 修改 Nginx 配置
# 编辑 nginx/nginx.conf，将 server_name 改为 mcp-bridge.test

# 3. 重新加载 Nginx
sudo nginx -s reload -c $(pwd)/nginx/nginx.conf

# 4. 使用新域名
# http://mcp-bridge.test/mcp/router/tools/mcp-server-v6
```

**效果**：完全避免 mDNS 查询，DNS 解析时间 < 0.1 秒。

---

## 验证

配置后，运行：

```bash
./scripts/verify-all-clients.sh
```

或手动测试：

```bash
# 测试 DNS 解析
time dig +short mcp-bridge.local  # 应该 < 0.1 秒

# 测试 HTTP 请求
time curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
# 应该 < 0.2 秒
```

---

## 相关文档

- `docs/DNS_SLOW_ROOT_CAUSE.md` - 详细技术分析
- `docs/UNIVERSAL_DNS_FIX.md` - 完整解决方案对比
- `docs/QUICK_DNS_FIX.md` - 快速修复指南

---

## 脚本列表

- `scripts/setup-dnsmasq.sh` - 自动配置 dnsmasq
- `scripts/setup-local-dns.sh` - 配置 /etc/hosts（临时方案）
- `scripts/quick-fix-dns.sh` - 交互式快速修复
- `scripts/verify-all-clients.sh` - 验证所有客户端
- `scripts/diagnose-domain-slow.sh` - 详细诊断




