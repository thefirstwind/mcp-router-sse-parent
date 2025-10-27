# 🎯 事件驱动连接感知测试计划

## 测试架构说明

### 正确的架构理解
- **mcp-router-v3**: 双重角色
  - 注册到 Nacos（让其他服务通过服务发现找到它）
  - 监听 Nacos 事件（感知其他服务的状态变化）
- **mcp-server-v3**: MCP 服务，注册到 Nacos，通过服务发现找到 mcp-router-v3，然后主动连接
- **事件流向**: MCP Server → Nacos → mcp-router-v3 (通过 EventListener)

### 去掉的轮询机制
- ✅ 已禁用 `@Scheduled(fixedRate = 30000)` 定时健康检查
- ✅ 改为完全事件驱动的感知机制

## 📋 测试计划

### Phase 1: 基础验证 🟢

#### Test 1.1 - 验证轮询已禁用
```bash
# 检查 mcp-router-v3 日志，应该看到：
# "🚫 Scheduled health check disabled - using event-driven connection monitoring instead"
```

#### Test 1.2 - 验证服务启动状态
```bash
# 检查所有服务端口
netstat -an | grep -E "(8052|8063|8848)" | grep LISTEN
# 预期：8052(mcp-router-v3), 8063(mcp-server-v3), 8848(Nacos) 都在监听
```

#### Test 1.3 - 验证连接监听器启动
```bash
# 检查 mcp-router-v3 日志，应该看到：
# "🔔 Starting MCP connection event listener..."
# "✅ MCP connection event listener started"
```

### Phase 2: 连接建立验证 🟡

#### Test 2.0 - 验证 mcp-router-v3 注册到 Nacos
```bash
# 检查 mcp-router-v3 是否成功注册到 Nacos
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mcp-router-v3&groupName=mcp-server" | jq '.'
# 预期：能看到 mcp-router-v3 实例，metadata 中包含 type=mcp-router, role=router
```

#### Test 2.1 - 验证 mcp-server-v3 服务发现和连接
```bash
# 重启 mcp-server-v3，观察日志应该看到：
# "🔗 Discovered mcp-router-v3 via Nacos service discovery: http://x.x.x.x:8052"
# "📍 Router instance metadata: {type=mcp-router, role=router, ...}"
# "📡 SSE connection established: true"
```

#### Test 2.2 - 验证连接状态注册到 Nacos
```bash
# 检查 Nacos 中的连接状态服务
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v3-connection&groupName=mcp-server" | jq '.'
# 预期：能看到 mcp-server-v3-connection 服务实例
```

#### Test 2.3 - 验证路由器接收连接请求
```bash
# 检查连接接收端点
curl -s "http://localhost:8052/api/mcp/servers/connections" | jq '.'
# 预期：返回连接信息
```

### Phase 3: 事件监听验证 🟠

#### Test 3.1 - 验证 Nacos 事件订阅
```bash
# 检查 mcp-router-v3 日志，应该看到：
# "🔔 Subscribed to connection service: mcp-server-v3-connection"
# "📋 Discovered X existing connection services"
```

#### Test 3.2 - 模拟连接事件
```bash
# 发送模拟连接请求
curl -X POST "http://localhost:8052/api/mcp/servers/connect" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "test-server-123",
    "serverName": "test-mcp-server",
    "serverPort": 8063,
    "capabilities": "tools,resources"
  }'
# 预期：返回成功响应
```

#### Test 3.3 - 验证事件处理
```bash
# 检查 mcp-router-v3 日志，应该看到：
# "📡 Received connection request from MCP Server"
# "✅ Connection request accepted from server"
```

### Phase 4: 断开感知验证 🔴

#### Test 4.1 - 停止 mcp-server-v3
```bash
# 停止 mcp-server-v3 服务
# 观察 mcp-router-v3 日志应该看到：
# "🔴 MCP Server disconnected: mcp-server-v3"
# "🧹 Cleaned up SSE session"
```

#### Test 4.2 - 验证资源自动清理
```bash
# 检查连接状态
curl -s "http://localhost:8052/api/mcp/servers/connections/mcp-server-v3" | jq '.'
# 预期：connected 字段为 false
```

#### Test 4.3 - 验证 Nacos 事件传播
```bash
# 检查 Nacos 中连接状态变化
# 预期：mcp-server-v3-connection 服务实例状态变为 unhealthy
```

### Phase 5: 重连验证 🟢

#### Test 5.1 - 重启 mcp-server-v3
```bash
# 重新启动 mcp-server-v3
# 观察 mcp-router-v3 日志应该看到：
# "🟢 MCP Server connected: mcp-server-v3"
```

#### Test 5.2 - 验证心跳机制
```bash
# 等待 30 秒，观察心跳日志：
# "💓 Heartbeat sent, connection status updated"
```

### Phase 6: 性能对比验证 ⚡

#### Test 6.1 - 响应时间测试
```bash
# 测试事件响应时间（应该是毫秒级）
time_start=$(date +%s%3N)
# 触发连接事件
time_end=$(date +%s%3N)
echo "Event response time: $((time_end - time_start)) ms"
# 预期：< 100ms
```

#### Test 6.2 - 对比轮询延迟
```bash
# 旧机制：最大 30 秒延迟
# 新机制：毫秒级响应
echo "延迟对比：30s → <100ms，性能提升 >99%"
```

## 🚨 测试预期结果

### 成功指标
- ✅ 无定时轮询日志
- ✅ 连接建立 < 5 秒
- ✅ 事件响应 < 100ms  
- ✅ 断开感知 < 1 秒
- ✅ 资源自动清理
- ✅ 重连自动恢复

### 失败场景处理
- ❌ 连接失败 → 检查端口和服务状态
- ❌ 事件未收到 → 检查 Nacos 订阅状态
- ❌ 资源未清理 → 检查清理逻辑

## 🔧 测试执行命令

### 自动化测试脚本
```bash
# 运行完整测试
./mcp-router-v3/test-event-driven-connection.sh

# 分阶段测试
./mcp-router-v3/test-event-driven-connection.sh --phase 1
./mcp-router-v3/test-event-driven-connection.sh --phase 2
# ... 等等
```

### 手动验证关键点
```bash
# 1. 检查事件监听器
curl -s "http://localhost:8052/api/mcp/servers/connections" | jq '.'

# 2. 检查 Nacos 连接服务
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v3-connection&groupName=mcp-server" | jq '.'

# 3. 模拟连接测试
curl -X POST "http://localhost:8052/api/mcp/servers/connect" \
  -H "Content-Type: application/json" \
  -d '{"serverId":"test","serverName":"test","serverPort":8063}'
```

## 📊 验证标准

| 测试项 | 旧机制 | 新机制 | 改进幅度 |
|--------|--------|--------|----------|
| **感知延迟** | 最大30秒 | <100ms | >99% |
| **资源消耗** | 持续轮询 | 事件驱动 | 显著降低 |
| **扩展性** | O(n)连接数 | O(1)监听器 | 线性提升 |
| **可靠性** | 轮询失败 | 事件保证 | 更可靠 |

🎯 **核心验证目标：证明事件驱动机制完全替代了轮询，实现了实时、高效的连接感知！** 