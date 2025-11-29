# SSE 连接自动断开问题分析

## 问题现象

SSE 连接 `http://mcp-bridge.test/sse/mcp-server-v6` 在建立后有一定几率自动断开。

从日志观察：
- 连接成功建立
- 约 30 秒后被取消：`❌ SSE connection cancelled`
- 心跳间隔是 30 秒，但连接在第一次心跳前就断开

## 可能原因

### 1. Nginx 超时配置（最可能）

**当前配置**：
- `proxy_read_timeout: 300s` (5分钟)
- `proxy_send_timeout: 60s`

**问题**：
- SSE 长连接需要更长的超时时间
- 如果 5 分钟内没有数据传输，Nginx 可能关闭连接
- 心跳是注释形式（`comment`），可能不被 Nginx 识别为有效数据

**解决方案**：
- 增加 `proxy_read_timeout` 到至少 600s (10分钟) 或更长
- 确保心跳消息被正确识别

### 2. 负载均衡问题

**当前配置**：
- 使用 `ip_hash` 实现会话粘性
- 后端服务器：8051, 8052, 8053

**问题**：
- `ip_hash` 基于客户端 IP，如果客户端 IP 变化，连接可能被路由到不同实例
- 如果 Nginx 重启或后端实例变化，`ip_hash` 映射可能改变
- SSE 连接状态存储在应用内存中，如果请求被路由到不同实例，无法找到连接

**解决方案**：
- 确保 `ip_hash` 稳定
- 或者使用 Redis 共享 SSE 连接状态（如果多实例）

### 3. 心跳机制问题

**当前实现**：
- 心跳间隔：30 秒
- 心跳格式：`comment("heartbeat")`

**问题**：
- SSE 注释（`comment`）可能不被所有客户端识别为有效数据
- 如果客户端不识别注释，可能认为连接已断开

**解决方案**：
- 改为发送实际的数据事件，而不是注释
- 或者缩短心跳间隔

### 4. 客户端主动断开

**可能原因**：
- 客户端超时设置
- 客户端重连逻辑
- 网络问题

## 诊断步骤

### 1. 检查 Nginx 日志

```bash
# 查看 Nginx 错误日志
tail -f /var/log/nginx/error.log

# 查看 Nginx 访问日志
tail -f /var/log/nginx/access.log
```

### 2. 检查应用日志

```bash
# 查看 SSE 连接相关日志
grep "SSE connection" logs/*.log | tail -20

# 查看连接取消日志
grep "connection cancelled" logs/*.log | tail -20
```

### 3. 测试 SSE 连接

```bash
# 长时间测试（10分钟）
./scripts/diagnose-sse-disconnect.sh http://mcp-bridge.test/sse/mcp-server-v6 600

# 检查是否在特定时间断开
```

## 解决方案

### 方案 1：增加 Nginx 超时时间（推荐）

修改 `nginx/nginx.conf`：

```nginx
location / {
    # ... 其他配置 ...
    
    # SSE 长连接需要更长的超时时间
    proxy_read_timeout 600s;      # 增加到 10 分钟
    proxy_send_timeout 600s;      # 增加到 10 分钟
    proxy_connect_timeout 10s;    # 连接超时保持较短
    
    # ... 其他配置 ...
}
```

然后重新加载 Nginx：
```bash
sudo nginx -s reload -c $(pwd)/nginx/nginx.conf
```

### 方案 2：改进心跳机制

修改心跳为实际数据事件，而不是注释：

```java
// 当前实现（注释）
ServerSentEvent.<String>builder()
    .comment("heartbeat")
    .build()

// 改进实现（数据事件）
ServerSentEvent.<String>builder()
    .event("heartbeat")
    .data("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}")
    .build()
```

### 方案 3：缩短心跳间隔

将心跳间隔从 30 秒缩短到 15 秒或 10 秒：

```java
Flux.interval(Duration.ofSeconds(15))  // 从 30 秒改为 15 秒
```

### 方案 4：检查负载均衡稳定性

确保 `ip_hash` 稳定：

```nginx
upstream mcp_router_backend {
    ip_hash;
    
    # 确保后端服务器稳定
    server 127.0.0.1:8051;
    server 127.0.0.1:8052;
    server 127.0.0.1:8053;
    
    # 如果某个实例不可用，标记为 down
    # server 127.0.0.1:8051 down;
}
```

## 推荐修复顺序

1. **立即修复**：增加 Nginx `proxy_read_timeout` 到 600s
2. **改进心跳**：将心跳从注释改为数据事件
3. **缩短心跳间隔**：从 30 秒改为 15 秒
4. **验证负载均衡**：确保 `ip_hash` 稳定

## 验证

修复后，运行长时间测试：

```bash
# 测试 10 分钟
timeout 600 curl -N -H "Accept: text/event-stream" \
    http://mcp-bridge.test/sse/mcp-server-v6

# 应该持续 10 分钟不断开
```




