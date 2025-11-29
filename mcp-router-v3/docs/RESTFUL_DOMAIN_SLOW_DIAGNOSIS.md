# RESTful 接口域名访问慢问题排查

## 问题现象

使用域名访问 RESTful 接口非常慢：
```bash
curl http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```

**总时间：约 5 秒**

## 排查结果

### 1. 时间分解

| 阶段 | 时间 | 占比 |
|------|------|------|
| DNS 解析 | 5.003 秒 | 99.6% |
| 连接建立 | 5.004 秒 | 99.6% |
| 开始传输 | 5.024 秒 | 99.9% |
| **总时间** | **5.024 秒** | **100%** |

### 2. 对比测试

| 访问方式 | 总时间 | 说明 |
|---------|--------|------|
| 域名访问 | 5.024 秒 | 慢 |
| IP 访问 | 0.001 秒 | 快 |
| 直接访问后端 | 0.005-0.110 秒 | 正常 |

**结论：域名访问比 IP 访问慢 5.019 秒，几乎全部是 DNS 解析时间**

### 3. DNS 解析问题

测试结果：
- `dig mcp-bridge.local`: NXDOMAIN（DNS 服务器无法解析）
- `nslookup mcp-bridge.local`: NXDOMAIN
- `ping mcp-bridge.local`: 成功（使用 mDNS）

**原因分析：**
- `.local` 域名在 macOS 上使用 **mDNS (Multicast DNS)**
- mDNS 需要广播查询，通常需要 5 秒超时
- 这是 macOS 系统的默认行为，不是 Nginx 或应用的问题

## 解决方案

### 方案 1：使用 /etc/hosts（推荐，最简单）

在 `/etc/hosts` 文件中添加：
```
127.0.0.1 mcp-bridge.local
```

**优点：**
- 立即生效
- 无需修改代码或配置
- 完全绕过 DNS 查询

**缺点：**
- 需要每台机器配置
- 不适合生产环境（除非使用真实域名）

### 方案 2：使用 --resolve 选项（临时测试）

```bash
curl --resolve mcp-bridge.local:80:127.0.0.1 \
  http://mcp-bridge.local/mcp/router/tools/mcp-server-v6
```

**优点：**
- 无需修改系统配置
- 适合临时测试

**缺点：**
- 每次都需要指定
- 不适合自动化脚本

### 方案 3：配置 dnsmasq（本地开发环境）

安装并配置 dnsmasq：
```bash
# 安装
brew install dnsmasq

# 配置
echo 'address=/.local/127.0.0.1' >> /usr/local/etc/dnsmasq.conf

# 启动
brew services start dnsmasq

# 配置系统 DNS
# 在 系统偏好设置 > 网络 > 高级 > DNS 中添加 127.0.0.1
```

**优点：**
- 自动解析所有 `.local` 域名
- 适合本地开发环境

**缺点：**
- 需要安装和配置
- 可能影响其他 DNS 查询

### 方案 4：使用真实域名（生产环境）

在生产环境使用真实的域名（如 `mcp-bridge.example.com`），配置 DNS 记录指向服务器 IP。

**优点：**
- 标准 DNS 解析，速度快
- 适合生产环境

**缺点：**
- 需要 DNS 服务器配置
- 不适合本地开发

### 方案 5：使用 IP 地址（最简单）

直接使用 IP 地址访问：
```bash
curl http://127.0.0.1/mcp/router/tools/mcp-server-v6
```

**优点：**
- 最快，无 DNS 延迟
- 适合本地开发

**缺点：**
- 不便于记忆
- 不适合生产环境

## 推荐方案

### 本地开发环境
**使用方案 1（/etc/hosts）**：
```bash
sudo sh -c 'echo "127.0.0.1 mcp-bridge.local" >> /etc/hosts'
```

### 生产环境
**使用方案 4（真实域名）**：
- 配置 DNS A 记录：`mcp-bridge.example.com` → 服务器 IP
- 修改 Nginx 配置中的 `server_name`

## 验证

配置后，重新运行排查脚本：
```bash
./scripts/debug-restful-domain.sh
```

预期结果：
- DNS 解析时间：< 0.01 秒
- 总时间：< 0.1 秒

## 其他发现

1. **Nginx 未运行**：当前测试直接访问后端服务，如果启用 Nginx，需要确保 Nginx 正在运行
2. **后端服务正常**：三个实例（8051, 8052, 8053）都健康，响应时间正常（0.005-0.110 秒）

## 总结

**根本原因**：macOS 上 `.local` 域名使用 mDNS，需要 5 秒超时才能解析。

**解决方案**：使用 `/etc/hosts` 或真实域名，完全绕过 mDNS 查询。

**影响范围**：所有使用 `.local` 域名的请求（SSE 和 RESTful 接口）。





