# SSE 域名访问验证报告

## 验证时间
2025-11-23 23:47

## 验证结果
✅ **成功** - 通过域名访问 SSE 连接已正常工作

## 关键发现

### 1. 请求正确到达应用
```
SSE connection request: serviceName=mcp-server-v6, path=/sse/mcp-server-v6, 
Host=mcp-bridge.local, X-Forwarded-Host=mcp-bridge.local, X-Forwarded-Proto=http
```

### 2. X-Forwarded 头正确传递
- ✅ `X-Forwarded-Host: mcp-bridge.local`
- ✅ `X-Forwarded-Proto: http`
- ✅ `X-Forwarded-Port: 80`
- ✅ `X-Forwarded-For: 127.0.0.1`

### 3. Base URL 生成正确
```
Building base URL - forwardedProto: http, forwardedHost: mcp-bridge.local, 
forwardedPort: 80, contextPath: null, Host: mcp-bridge.local
```

### 4. Endpoint 生成正确
```
Generated endpoint for SSE connection: 
baseUrl=http://mcp-bridge.local, 
messageEndpoint=http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...
```

### 5. SSE 连接成功建立
```
✅ SSE connection subscribed: sessionId=..., serviceName=mcp-server-v6, baseUrl=http://mcp-bridge.local
```

### 6. Endpoint 事件正确返回
```
event:endpoint
data:http://mcp-bridge.local/mcp/mcp-server-v6/message?sessionId=...
```

## Java 代码修复内容

### 1. 流处理优化
- 将 `publish().autoConnect(1)` 改为 `share()`，避免流过早完成
- 添加 `onBackpressureBuffer()` 防止背压导致连接关闭

### 2. 响应头增强
- 添加 `Transfer-Encoding: chunked` 明确使用分块传输

### 3. 请求头读取增强
- 同时尝试大小写两种形式读取 `X-Forwarded-Host` 和 `X-Forwarded-Proto`
- 添加详细的调试日志记录所有请求头

### 4. Base URL 构建逻辑
- 优先使用 `X-Forwarded-Host` 和 `X-Forwarded-Proto`
- 正确处理端口（标准端口 80/443 不添加）
- 支持 context-path 提取

## 验证步骤

1. **启动所有实例**：
   ```bash
   ./scripts/start-instances.sh restart
   ```

2. **测试通过域名访问**：
   ```bash
   curl -N http://mcp-bridge.local/sse/mcp-server-v6
   ```

3. **检查应用日志**：
   ```bash
   tail -100 logs/router-8051.log logs/router-8052.log logs/router-8053.log | grep "SSE connection request"
   ```

4. **验证 endpoint**：
   - 应该返回 `event:endpoint`
   - `data:` 中的 URL 应该使用 `mcp-bridge.local` 而不是 `localhost:8051`

## 注意事项

1. **连接取消是正常的**：
   - `SSE connection cancelled` 日志出现是因为 curl 超时或手动取消
   - 这不影响功能，连接在超时前是正常工作的

2. **负载均衡**：
   - Nginx 使用 `ip_hash` 实现会话粘性
   - 同一个 IP 的请求会路由到同一个后端实例

3. **日志检查**：
   - 通过域名访问时，需要检查所有三个实例的日志（8051、8052、8053）
   - 因为负载均衡可能将请求路由到任意一个实例

## 相关文件

- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`
- `mcp-router-v3/nginx/nginx.conf`
- `mcp-router-v3/src/main/resources/application.yml`


