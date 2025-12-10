# SSE 连接断开问题 - 诊断和解决方案

## 已完成的修复

### 1. Nginx 超时配置 ✅
- `proxy_read_timeout`: 300s → 600s (10分钟)
- `proxy_send_timeout`: 60s → 600s (10分钟)
- `proxy_connect_timeout`: 1s → 10s

### 2. 心跳机制改进 ✅
- **间隔**: 30秒 → 15秒（更频繁）
- **格式**: `comment("heartbeat")` → `event("heartbeat")` + `data(...)`
- **内容**: 添加时间戳 JSON 数据

## 测试结果

从测试看：
- ✅ 心跳机制已生效（每 15 秒收到心跳）
- ✅ 连接保持稳定（60 秒测试期间未断开）
- ✅ Nginx 超时配置已更新

## 可能的原因

如果仍然有连接断开问题，可能的原因：

### 1. 客户端主动断开

**症状**：
- 连接在特定时间后断开
- 日志显示 `SSE connection cancelled`
- 没有错误信息

**可能原因**：
- 客户端超时设置
- 客户端重连逻辑
- 客户端资源清理

**解决方案**：
- 检查客户端代码的超时设置
- 检查客户端是否有自动重连逻辑
- 确保客户端正确处理 SSE 连接

### 2. 负载均衡问题

**症状**：
- 连接建立后立即断开
- 请求被路由到不同实例

**可能原因**：
- `ip_hash` 不稳定
- 客户端 IP 变化
- 后端实例重启

**解决方案**：
- 确保 `ip_hash` 配置正确
- 检查后端实例稳定性
- 考虑使用 Redis 共享会话状态

### 3. 网络问题

**症状**：
- 连接随机断开
- 网络不稳定

**解决方案**：
- 检查网络连接
- 检查防火墙设置
- 检查代理设置

### 4. 应用错误

**症状**：
- 日志中有错误信息
- 连接因错误而断开

**解决方案**：
- 检查应用日志
- 修复代码错误
- 改进错误处理

## 诊断步骤

### 1. 检查日志

```bash
# 查看连接取消记录
grep "SSE connection cancelled" logs/*.log | tail -20

# 查看错误记录
grep "SSE.*error" logs/*.log | tail -20

# 查看心跳记录
grep "SSE heartbeat" logs/*.log | tail -20
```

### 2. 测试连接稳定性

```bash
# 长时间测试（10分钟）
./scripts/test-sse-stability.sh http://mcp-bridge.test/sse/mcp-server-v6 600
```

### 3. 分析断开模式

```bash
# 分析断开模式
./scripts/analyze-sse-disconnects.sh
```

## 进一步优化

### 1. 增加连接保活机制

如果客户端支持，可以：
- 缩短心跳间隔（从 15 秒到 10 秒）
- 添加连接状态检查
- 实现自动重连

### 2. 改进错误处理

- 捕获并记录所有错误
- 提供详细的错误信息
- 实现错误恢复机制

### 3. 监控和告警

- 监控连接数
- 监控断开率
- 设置告警阈值

## 验证修复

运行以下命令验证修复是否生效：

```bash
# 1. 测试连接稳定性
./scripts/test-sse-stability.sh http://mcp-bridge.test/sse/mcp-server-v6 300

# 2. 分析断开情况
./scripts/analyze-sse-disconnects.sh

# 3. 检查配置
grep "proxy_read_timeout\|heartbeat" nginx/nginx.conf
grep "Duration.ofSeconds(15)" src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java
```

## 相关文档

- `docs/SSE_DISCONNECT_FIX_SUMMARY.md` - 修复总结
- `docs/SSE_DISCONNECT_ANALYSIS.md` - 问题分析
- `scripts/test-sse-stability.sh` - 稳定性测试脚本
- `scripts/analyze-sse-disconnects.sh` - 断开分析脚本











