# mcp-router-v2 API 使用方法

---

## 1. 路由与工具调用接口（McpRouterController）

### 1.1 路由消息到指定服务
- **POST** `/mcp/router/route/{serviceName}`
- **Body**: McpMessage JSON
- **示例**：
```bash
curl -X POST http://localhost:8052/mcp/router/route/mcp-server-v3 \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 1 }
    }
  }'
```
curl -X POST http://localhost:8052/mcp/router/smart-route \
  -H "Content-Type: application/json" \
  -d '{
    "id": "smart-test-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": {
        "id": 1
      }
    }
  }'

### 1.2 广播消息到所有服务实例
- **POST** `/mcp/router/broadcast/{serviceName}`
- **Body**: McpMessage JSON
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/router/broadcast/mcp-server-v2 \
  -H "Content-Type: application/json" \
  -d '{
    "id": "broadcast-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 2 }
    }
  }'
```

### 1.3 通过SSE发送消息
- **POST** `/mcp/router/sse/send/{sessionId}?eventType=tool_call`
- **Body**: McpMessage JSON
- **示例**：
```bash
curl -X POST "http://localhost:8050/mcp/router/sse/send/{sessionId}?eventType=tool_call" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sse-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 3 }
    }
  }'
```

### 1.4 通过SSE广播消息
- **POST** `/mcp/router/sse/broadcast?eventType=tool_broadcast`
- **Body**: McpMessage JSON
- **示例**：
```bash
curl -X POST "http://localhost:8050/mcp/router/sse/broadcast?eventType=tool_broadcast" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sse-broadcast-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 4 }
    }
  }'
```

### 1.5 路由SSE消息
- **POST** `/mcp/router/sse/route/{serviceName}/{sessionId}`
- **Body**: McpMessage JSON
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/router/sse/route/mcp-server-v2/54fea6fb-8d3f-4b18-9025-e25c57e7e2ff \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sse-route-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 5 }
    }
  }'
```

### 1.6 获取服务健康状态
- **GET** `/mcp/router/health/{serviceName}`
- **示例**：
```bash
curl http://localhost:8050/mcp/router/health/mcp-server-v2
```

### 1.7 获取路由统计信息
- **GET** `/mcp/router/stats`
- **示例**：
```bash
curl http://localhost:8050/mcp/router/stats
```

---

## 2. SSE 实时通信接口（McpSseController）

### 2.1 建立SSE连接
- **GET** `/sse/connect?clientId=xxx[&metadata=key1=val1,key2=val2]`
- **示例**：
```bash
curl -N -H "Accept: text/event-stream" "http://localhost:8050/sse/connect?clientId=test-client-001"
```

### 2.2 发送消息到指定会话
- **POST** `/sse/message/{sessionId}?eventType=custom_event`
- **Body**: 原始字符串数据
- **示例**：
```bash
curl -X POST "http://localhost:8050/sse/message/{sessionId}?eventType=custom_event" \
  -H "Content-Type: text/plain" \
  -d '{"msg":"hello session"}'

curl -X POST "http://localhost:8050/sse/message/54fea6fb-8d3f-4b18-9025-e25c57e7e2ff?eventType=custom_event" \
  -H "Content-Type: text/plain" \
  -d '{"msg":"hello session"}'
  
```

### 2.3 发送消息到指定客户端
- **POST** `/sse/message/client/{clientId}?eventType=custom_event`
- **Body**: 原始字符串数据
- **示例**：
```bash
curl -X POST "http://localhost:8050/sse/message/client/test-client-001?eventType=custom_event" \
  -H "Content-Type: text/plain" \
  -d '{"msg":"hello client"}'
```

### 2.4 广播消息到所有活跃会话
- **POST** `/sse/broadcast?eventType=system_notification`
- **Body**: 原始字符串数据
- **示例**：
```bash
curl -X POST "http://localhost:8050/sse/broadcast?eventType=system_notification" \
  -H "Content-Type: text/plain" \
  -d '{"msg":"broadcast to all"}'
```

### 2.5 获取会话信息
- **GET** `/sse/session/{sessionId}`
- **示例**：
```bash
curl http://localhost:8050/sse/session/{sessionId}

curl http://localhost:8050/sse/session/54fea6fb-8d3f-4b18-9025-e25c57e7e2ff
```

### 2.6 获取所有活跃会话
- **GET** `/sse/sessions`
- **示例**：
```bash
curl http://localhost:8050/sse/sessions
```

### 2.7 关闭指定会话
- **DELETE** `/sse/session/{sessionId}`
- **示例**：
```bash
curl -X DELETE http://localhost:8050/sse/session/{sessionId}
```

### 2.8 清理超时会话
- **POST** `/sse/cleanup`
- **示例**：
```bash
curl -X POST http://localhost:8050/sse/cleanup
```

---

## 3. 服务发现与注册接口（McpServerController）

### 3.1 获取所有健康的MCP服务器
- **GET** `/mcp/servers/healthy?serviceName=mcp-server-v2&serviceGroup=mcp-server`
- **示例**：
```bash
curl "http://localhost:8050/mcp/servers/healthy?serviceName=mcp-server-v2&serviceGroup=mcp-server"
```

### 3.2 获取指定服务的所有实例
- **GET** `/mcp/servers/instances?serviceName=mcp-server-v2&serviceGroup=mcp-server`
- **示例**：
```bash
curl "http://localhost:8050/mcp/servers/instances?serviceName=mcp-server-v2&serviceGroup=mcp-server"
```

### 3.3 选择一个健康的服务实例
- **GET** `/mcp/servers/select?serviceName=mcp-server-v2&serviceGroup=mcp-server`
- **示例**：
```bash
curl "http://localhost:8050/mcp/servers/select?serviceName=mcp-server-v2&serviceGroup=mcp-server"
```

### 3.4 手动注册MCP服务器
- **POST** `/mcp/servers/register`
- **Body**: McpServerInfo JSON
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mcp-server-v2",
    "version": "1.0.0",
    "ip": "127.0.0.1",
    "port": 8062,
    "sseEndpoint": "/sse",
    "status": "UP",
    "serviceGroup": "mcp-server",
    "ephemeral": true,
    "healthy": true,
    "weight": 1.0
  }'

curl -X POST http://localhost:8052/mcp/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "demo-server",
    "version": "v1",
    "toolsDescriptionRef": "xxx",
    "remoteServerConfig": { "host": "127.0.0.1", "port": 9000 },
    "namespaceId": "public",
    "versionDetail": { "desc": "test version" },
    "toolsMeta": { "gray": true }
  }'
```

### 3.5 注销MCP服务器
- **DELETE** `/mcp/servers/deregister?serviceName=mcp-server-v2&serviceGroup=mcp-server`
- **示例**：
```bash
curl -X DELETE "http://localhost:8050/mcp/servers/deregister?serviceName=mcp-server-v2&serviceGroup=mcp-server"
```

### 3.6 获取服务器配置信息 ??????
- **GET** `/mcp/servers/config/mcp-server-v2?version=1.0.0`
- **示例**：
```bash
curl "http://localhost:8050/mcp/servers/config/mcp-server-v2?version=1.0.1"
```

### 3.7 发布服务器配置
- **POST** `/mcp/servers/config/publish`
- **Body**: McpServerInfo JSON
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/servers/config/publish \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mcp-server-v2",
    "version": "1.0.0",
    "ip": "127.0.0.1",
    "port": 8062,
    "sseEndpoint": "/sse",
    "status": "UP",
    "serviceGroup": "mcp-server",
    "ephemeral": true,
    "healthy": true,
    "weight": 1.0
  }'
```

### 3.8 发布工具配置
- **POST** `/mcp/servers/config/tools/publish`
- **Body**: McpServerInfo JSON
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/servers/config/tools/publish \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mcp-server-v2",
    "version": "1.0.0",
    "ip": "127.0.0.1",
    "port": 8062,
    "sseEndpoint": "/sse",
    "status": "UP",
    "serviceGroup": "mcp-server",
    "ephemeral": true,
    "healthy": true,
    "weight": 1.0
  }'
```

### 3.9 发布版本配置
- **POST** `/mcp/servers/config/version/publish`
- **Body**: McpServerInfo JSON
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/servers/config/version/publish \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mcp-server-v2",
    "version": "1.0.0",
    "ip": "127.0.0.1",
    "port": 8062,
    "sseEndpoint": "/sse",
    "status": "UP",
    "serviceGroup": "mcp-server",
    "ephemeral": true,
    "healthy": true,
    "weight": 1.0
  }'
```

---

## 4. 健康检查与熔断器接口（HealthCheckController）

### 4.1 获取所有服务的健康状态
- **GET** `/mcp/health/status`
- **示例**：
```bash
curl http://localhost:8050/mcp/health/status
```

### 4.2 获取指定服务的健康状态
- **GET** `/mcp/health/status/mcp-server-v2`
- **示例**：
```bash
curl http://localhost:8050/mcp/health/status/mcp-server-v2
```

### 4.3 获取所有熔断器状态
- **GET** `/mcp/health/circuit-breakers`
- **示例**：
```bash
curl http://localhost:8050/mcp/health/circuit-breakers
```

### 4.4 获取指定服务的熔断器状态
- **GET** `/mcp/health/circuit-breakers/mcp-server-v2`
- **示例**：
```bash
curl http://localhost:8050/mcp/health/circuit-breakers/mcp-server-v2
```

### 4.5 重置熔断器
- **POST** `/mcp/health/circuit-breakers/mcp-server-v2/reset`
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/health/circuit-breakers/mcp-server-v2/reset
```

### 4.6 手动开启熔断器
- **POST** `/mcp/health/circuit-breakers/mcp-server-v2/open`
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/health/circuit-breakers/mcp-server-v2/open
```

### 4.7 手动关闭熔断器
- **POST** `/mcp/health/circuit-breakers/mcp-server-v2/close`
- **示例**：
```bash
curl -X POST http://localhost:8050/mcp/health/circuit-breakers/mcp-server-v2/close
```

### 4.8 获取健康检查统计信息
- **GET** `/mcp/health/stats`
- **示例**：
```bash
curl http://localhost:8050/mcp/health/stats
```

---

## 5. 基础健康检查接口（HealthController）

### 5.1 健康检查
- **GET** `/health`
- **示例**：
```bash
curl http://localhost:8050/health
```

### 5.2 就绪检查
- **GET** `/health/ready`
- **示例**：
```bash
curl http://localhost:8050/health/ready
``` 