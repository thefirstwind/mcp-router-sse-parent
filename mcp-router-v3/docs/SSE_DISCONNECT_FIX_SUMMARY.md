# SSE 连接自动断开问题 - 修复总结

## 已完成的修复

### 1. Nginx 超时配置 ✅

**文件**: `nginx/nginx.conf`

**修改内容**:
```nginx
# 之前
proxy_connect_timeout 1s;
proxy_send_timeout 60s;
proxy_read_timeout 300s;  # 5分钟

# 现在
proxy_connect_timeout 10s;
proxy_send_timeout 600s;   # 10分钟
proxy_read_timeout 600s;   # 10分钟（SSE 长连接）
```

**原因**: SSE 长连接需要更长的超时时间，5分钟可能不够。

---

### 2. 心跳机制改进 ✅

**文件**: `src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

**修改内容**:

**之前**:
```java
Flux.interval(Duration.ofSeconds(30))
    .map(tick -> ServerSentEvent.<String>builder()
            .comment("heartbeat")  // 注释形式，可能不被客户端识别
            .build())
```

**现在**:
```java
Flux.interval(Duration.ofSeconds(15))  // 缩短到15秒
    .map(tick -> ServerSentEvent.<String>builder()
            .event("heartbeat")  // 改为事件形式
            .data("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}")  // 发送实际数据
            .build())
```

**改进点**:
1. ✅ 心跳间隔从 30 秒缩短到 15 秒（更频繁，保持连接活跃）
2. ✅ 从注释（`comment`）改为数据事件（`event` + `data`），确保所有客户端都能识别
3. ✅ 包含时间戳，便于调试和监控

---

## 需要手动执行的操作

### 1. 重新加载 Nginx 配置

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3
sudo nginx -t -c "$(pwd)/nginx/nginx.conf"  # 测试配置
sudo nginx -s reload -c "$(pwd)/nginx/nginx.conf"  # 重新加载
```

### 2. 重新编译和重启应用

```bash
cd /Users/shine/projects.mcp-router-sse-parent/mcp-router-v3

# 重新编译
mvn clean package

# 重启应用实例（根据你的启动方式）
# 如果使用 start-instances.sh:
./scripts/start-instances.sh restart

# 或者手动重启各个实例
```

---

## 验证修复

### 1. 测试 SSE 连接稳定性

```bash
# 长时间测试（10分钟）
./scripts/diagnose-sse-disconnect.sh http://mcp-bridge.test/sse/mcp-server-v6 600
```

### 2. 检查心跳消息

使用 curl 观察心跳消息：

```bash
curl -N -H "Accept: text/event-stream" \
     http://mcp-bridge.test/sse/mcp-server-v6 | grep heartbeat
```

应该每 15 秒看到一次心跳事件：
```
event: heartbeat
data: {"type":"heartbeat","timestamp":1234567890}
```

### 3. 检查应用日志

```bash
# 查看心跳日志
grep "SSE heartbeat" logs/*.log | tail -20

# 查看连接取消日志（应该减少或消失）
grep "connection cancelled" logs/*.log | tail -20
```

---

## 预期效果

修复后：
- ✅ SSE 连接应该能够保持至少 10 分钟不断开
- ✅ 心跳消息每 15 秒发送一次，保持连接活跃
- ✅ 心跳消息以数据事件形式发送，所有客户端都能识别
- ✅ Nginx 超时时间足够长，不会主动关闭连接

---

## 如果仍然断开

如果修复后仍然有问题，检查：

1. **客户端超时设置**
   - 检查客户端代码是否有超时或重连逻辑
   - 某些客户端可能有默认的超时设置

2. **网络问题**
   - 检查网络是否稳定
   - 检查防火墙或代理设置

3. **负载均衡问题**
   - 确保 `ip_hash` 稳定
   - 检查后端实例是否正常

4. **应用日志**
   - 查看详细的错误日志
   - 检查是否有异常或错误导致连接关闭

---

## 相关文档

- `docs/SSE_DISCONNECT_ANALYSIS.md` - 问题分析
- `scripts/diagnose-sse-disconnect.sh` - 诊断脚本
- `scripts/fix-sse-disconnect.sh` - 修复脚本


















