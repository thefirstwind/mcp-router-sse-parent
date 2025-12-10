# 快速修复 DNS 解析慢问题（适用于所有客户端）

## 问题

使用域名 `mcp-bridge.local` 访问时，DNS 解析需要 5 秒，导致所有客户端（浏览器、MCP Inspector、VSCode 等）都很慢。

## 快速解决方案（5 分钟）

### 方案 A：自动配置 /etc/hosts（最简单）

```bash
cd mcp-router-v3
./scripts/setup-local-dns.sh
```

**优点**：
- ✅ 一键配置
- ✅ 所有客户端立即生效
- ✅ 无需安装额外软件

**适用场景**：个人开发、快速测试

---

### 方案 B：配置 dnsmasq（推荐，团队开发）

```bash
cd mcp-router-v3
./scripts/setup-dnsmasq.sh
```

然后按照提示配置系统 DNS。

**优点**：
- ✅ 支持所有 `.local` 域名
- ✅ 适合团队统一配置
- ✅ 一次配置，永久有效

**适用场景**：团队开发、多项目

---

## 验证

配置后，运行验证脚本：

```bash
./scripts/verify-all-clients.sh
```

应该看到：
- ✅ ping 成功，响应时间 < 0.1 秒
- ✅ curl 成功，响应时间 < 0.1 秒
- ✅ /etc/hosts 已配置

---

## 如果仍然慢

1. **刷新 DNS 缓存**：
   ```bash
   sudo dscacheutil -flushcache
   sudo killall -HUP mDNSResponder
   ```

2. **检查配置**：
   ```bash
   grep mcp-bridge.local /etc/hosts
   ```

3. **重新测试**：
   ```bash
   ./scripts/verify-all-clients.sh
   ```

---

## 详细文档

- `docs/UNIVERSAL_DNS_FIX.md` - 完整解决方案
- `docs/RESTFUL_DOMAIN_SLOW_DIAGNOSIS.md` - 问题分析
- `docs/RESTFUL_DOMAIN_SLOW_SOLUTION.md` - 解决方案












