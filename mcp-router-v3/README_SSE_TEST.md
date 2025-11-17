# SSE连接测试说明

## 问题描述

MCP Inspector 和 mcp-router-v3 通过 `http://127.0.0.1:8052/sse/mcp-server-v6` 建立 SSE 连接后立即断开。

## 修复内容

### 1. 修复控制器返回类型
- **问题**: 控制器返回 `Flux<String>`，无法正确序列化为SSE格式
- **修复**: 改为返回 `Flux<ServerSentEvent<String>>`，确保Spring正确序列化SSE事件

### 2. 修复心跳机制
- **问题**: 心跳订阅没有正确管理，可能被垃圾回收导致连接断开
- **修复**: 
  - 保存心跳订阅到 `Disposable`，在连接关闭时正确清理
  - 改进心跳发送逻辑，检查会话状态和存在性
  - 心跳间隔：30秒
  - 超时时间：10分钟（600秒）

### 3. 修复连接管理
- **问题**: `Flux.create` 的实现可能导致连接立即完成
- **修复**:
  - 正确管理 sink 订阅和心跳订阅
  - 在连接关闭时清理所有订阅
  - 改进错误处理，防止错误导致连接断开

## 配置参数

- **会话超时时间**: `DEFAULT_TIMEOUT_MS = 600_000` (10分钟)
- **心跳间隔**: `HEARTBEAT_INTERVAL_MS = 30_000` (30秒)

## 测试方法

### 方法1: Python测试脚本（推荐）

```bash
# 完整测试（10分钟）
python3 test-sse-connection.py

# 快速测试（60秒）
python3 test-sse-connection.py --quick

# 自定义测试时间（5分钟）
python3 test-sse-connection.py --duration 300

# 指定服务器URL
python3 test-sse-connection.py --url http://127.0.0.1:8052
```

### 方法2: Shell脚本测试

```bash
# 完整测试（10分钟）
./test-sse-connection.sh

# 自定义测试时间（5分钟）
./test-sse-connection.sh http://127.0.0.1:8052 300
```

### 方法3: 使用curl手动测试

```bash
# 建立SSE连接
curl -N -H "Accept: text/event-stream" \
     -H "Cache-Control: no-cache" \
     "http://127.0.0.1:8052/sse/mcp-server-v6"
```

### 方法4: 使用MCP Inspector

1. 启动 mcp-router-v3 服务
2. 打开 MCP Inspector
3. 连接到 `http://127.0.0.1:8052/sse/mcp-server-v6`
4. 观察连接是否保持10分钟以上

## 预期结果

1. ✅ 连接成功建立，收到 `connected` 事件
2. ✅ 每30秒收到一次 `heartbeat` 事件
3. ✅ 连接保持至少10分钟不断开
4. ✅ 10分钟内没有主动断开连接

## 验证要点

- [ ] 连接建立后立即收到 `connected` 事件
- [ ] 每30秒收到一次心跳事件
- [ ] 连接保持10分钟以上
- [ ] 没有出现连接错误或异常断开
- [ ] 日志中显示心跳正常发送

## 故障排查

### 如果连接仍然立即断开

1. **检查服务器日志**
   ```bash
   # 查看SSE相关日志
   tail -f logs/application.log | grep -i sse
   ```

2. **检查网络代理/网关超时**
   - Nginx: 检查 `proxy_read_timeout` 配置
   - API Gateway: 检查响应超时设置
   - 负载均衡器: 检查空闲连接超时

3. **检查Spring WebFlux配置**
   - 确认没有设置全局响应超时
   - 确认CORS配置正确

4. **检查客户端**
   - 确认客户端没有设置连接超时
   - 确认客户端正确处理SSE事件流

## 相关文件

- `McpSseController.java`: SSE控制器
- `McpSseTransportProvider.java`: SSE传输提供者
- `SseSession.java`: SSE会话模型
- `test-sse-connection.py`: Python测试脚本
- `test-sse-connection.sh`: Shell测试脚本

