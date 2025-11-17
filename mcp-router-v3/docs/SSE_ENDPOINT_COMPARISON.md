# SSE 端点请求和响应格式对比文档

## 📋 概述

本文档详细对比 **mcp-router-v3** (`http://localhost:8052/sse/mcp-server-v6`) 和 **mcp-server-v6** (`http://localhost:8071/sse`) 两个 SSE 端点的请求格式、响应格式，以及所有 MCP 标准协议接口的支持情况。

---

## 🔌 SSE 连接建立对比

### mcp-server-v6: `GET /sse`

#### 请求格式
```http
GET /sse HTTP/1.1
Host: localhost:8071
Accept: text/event-stream
```

**查询参数：** 无查询参数

#### 响应格式（SSE Stream）

**标准 Spring AI WebFluxSseServerTransportProvider 格式：**

```
event:endpoint
data:http://localhost:8071/mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000

:heartbeat
:heartbeat
:heartbeat
...
```

**响应说明：**
- `event:endpoint` - 事件类型，标识这是端点信息（Spring AI 标准格式）
- `data:` - 包含消息端点的完整 URL，包含自动生成的 `sessionId`
- `:heartbeat` - 心跳注释，每 30 秒发送一次，保持连接活跃

**注意：** 实际的 WebFluxSseServerTransportProvider 实现可能返回不同的格式。如果返回的是 JSON 格式的连接信息，格式可能为：
```
data: {"type":"connection","status":"connected","baseUrl":"http://localhost:8071","timestamp":1754386992538}
```
但标准实现应该返回 `event:endpoint` 格式。

**HTTP 响应头：**
```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

---

### mcp-router-v3: `GET /sse/{serviceName}`

#### 请求格式
```http
GET /sse/mcp-server-v6 HTTP/1.1
Host: localhost:8052
Accept: text/event-stream
```

**路径参数：**
- `{serviceName}` - 必需，目标 MCP 服务名称，例如：`mcp-server-v6`

**查询参数：** 无

#### 响应格式（SSE Stream）

**自定义格式（兼容 Spring AI 标准）：**

```
event:endpoint
data:http://localhost:8052/mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000

:heartbeat
:heartbeat
:heartbeat
...
```

**响应说明：**
- `event:endpoint` - 事件类型，标识这是端点信息（与 mcp-server-v6 相同）
- `data:` - 包含消息端点的完整 URL，包含自动生成的 `sessionId`
- `:heartbeat` - 心跳注释，每 30 秒发送一次，保持连接活跃

**HTTP 响应头：**
```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

**关键差异：**
- ✅ **路径参数方式**：通过 URL 路径指定服务名称（`/sse/{serviceName}`）
- ✅ **会话关联**：自动将 `sessionId` 与 `serviceName` 关联，存储在会话服务中
- ✅ **格式兼容**：响应格式与 mcp-server-v6 完全兼容

---

## 📨 MCP 消息端点对比

### mcp-server-v6: `POST /mcp/message?sessionId=xxx`

#### 请求格式
```http
POST /mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Host: localhost:8071
Content-Type: application/json
```

**查询参数：**
- `sessionId` - 必需，从 SSE 连接响应中获取的会话 ID

**请求体（JSON-RPC 2.0）：**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": "req-001",
  "params": {}
}
```

#### 响应格式
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "tools": [
      {
        "name": "getPersonById",
        "description": "Get a person by their ID",
        "inputSchema": {
          "type": "object",
          "properties": {
            "id": {
              "type": "integer",
              "format": "int64",
              "description": "Person's ID"
            }
          },
          "required": ["id"],
          "additionalProperties": false
        }
      }
    ],
    "toolsMeta": {}
  }
}
```

---

### mcp-router-v3: `POST /mcp/message?sessionId=xxx`

#### 请求格式
```http
POST /mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Host: localhost:8052
Content-Type: application/json
```

**查询参数：**
- `sessionId` - 必需，从 SSE 连接响应中获取的会话 ID

**请求体（JSON-RPC 2.0）：**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": "req-001",
  "params": {}
}
```

#### 响应格式
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "tools": [
      {
        "name": "getPersonById",
        "description": "Get a person by their ID",
        "inputSchema": {
          "type": "object",
          "properties": {
            "id": {
              "type": "integer",
              "format": "int64",
              "description": "Person's ID"
            }
          },
          "required": ["id"],
          "additionalProperties": false
        }
      }
    ],
    "toolsMeta": {}
  }
}
```

**关键差异：**
- ✅ **路由功能**：根据 `sessionId` 查找关联的 `serviceName`，自动路由到后端服务
- ✅ **格式兼容**：请求和响应格式与 mcp-server-v6 完全兼容
- ✅ **智能路由**：如果会话中没有 `serviceName`，支持从消息中提取或智能路由

---

## 🔧 MCP 标准协议接口支持对比

### 1. initialize（初始化）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "initialize",
  "id": "init-001",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "client",
      "version": "1.0.0"
    }
  }
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "init-001",
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {},
      "resources": {},
      "prompts": {}
    },
    "serverInfo": {
      "name": "mcp-server-v6",
      "version": "1.0.1"
    }
  }
}
```

#### mcp-router-v3
✅ **支持** - 透传到后端服务，返回后端服务的初始化响应

---

### 2. tools/list（工具列表）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": "req-001",
  "params": {}
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "tools": [
      {
        "name": "getPersonById",
        "description": "Get a person by their ID",
        "inputSchema": { ... }
      }
    ],
    "toolsMeta": {}
  }
}
```

#### mcp-router-v3
✅ **支持** - 路由到后端服务，返回工具列表

---

### 3. tools/call（工具调用）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": "req-001",
  "params": {
    "name": "getPersonById",
    "arguments": {
      "id": 5
    }
  }
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"id\":5,\"firstName\":\"Pierre\",\"lastName\":\"Dubois\",\"age\":40,\"nationality\":\"French\",\"gender\":\"MALE\",\"found\":true}"
      }
    ],
    "isError": false
  }
}
```

#### mcp-router-v3
✅ **支持** - 路由到后端服务，返回工具调用结果

**响应格式处理：**
- 如果后端返回的是标准 MCP 格式（包含 `content` 数组），直接返回
- 如果后端返回的是其他格式，自动转换为标准 MCP 格式

---

### 4. resources/list（资源列表）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "resources/list",
  "id": "req-001",
  "params": {}
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "resources": [
      {
        "uri": "file:///path/to/resource",
        "name": "Resource Name",
        "description": "Resource description",
        "mimeType": "text/plain"
      }
    ]
  }
}
```

#### mcp-router-v3
✅ **支持** - 路由到后端服务，返回资源列表

---

### 5. resources/read（读取资源）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "resources/read",
  "id": "req-001",
  "params": {
    "uri": "file:///path/to/resource"
  }
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "contents": [
      {
        "uri": "file:///path/to/resource",
        "mimeType": "text/plain",
        "text": "Resource content"
      }
    ]
  }
}
```

#### mcp-router-v3
✅ **支持** - 路由到后端服务，返回资源内容

---

### 6. prompts/list（提示词列表）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "prompts/list",
  "id": "req-001",
  "params": {}
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "prompts": [
      {
        "name": "prompt-name",
        "description": "Prompt description",
        "arguments": [
          {
            "name": "arg1",
            "description": "Argument description",
            "required": true
          }
        ]
      }
    ]
  }
}
```

#### mcp-router-v3
✅ **支持** - 路由到后端服务，返回提示词列表

---

### 7. prompts/get（获取提示词）

#### mcp-server-v6
```json
{
  "jsonrpc": "2.0",
  "method": "prompts/get",
  "id": "req-001",
  "params": {
    "name": "prompt-name",
    "arguments": {
      "arg1": "value1"
    }
  }
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "description": "Prompt description",
    "messages": [
      {
        "role": "user",
        "content": {
          "type": "text",
          "text": "Prompt content"
        }
      }
    ]
  }
}
```

#### mcp-router-v3
✅ **支持** - 路由到后端服务，返回提示词内容

---

## 📊 详细对比表

### SSE 连接建立

| 维度 | mcp-server-v6 | mcp-router-v3 |
|------|---------------|---------------|
| **端点** | `GET /sse` | `GET /sse/{serviceName}` |
| **服务名称指定** | 无（单服务器） | 路径参数（必需） |
| **响应格式** | `event:endpoint\ndata:http://.../mcp/message?sessionId=xxx` | `event:endpoint\ndata:http://.../mcp/message?sessionId=xxx` |
| **心跳** | `:heartbeat` (每 30 秒) | `:heartbeat` (每 30 秒) |
| **会话管理** | 自动生成 sessionId | 自动生成 sessionId + 关联 serviceName |
| **格式兼容性** | ✅ 标准 Spring AI 格式 | ✅ 兼容标准格式 |

### MCP 消息端点

| 维度 | mcp-server-v6 | mcp-router-v3 |
|------|---------------|---------------|
| **端点** | `POST /mcp/message?sessionId=xxx` | `POST /mcp/message?sessionId=xxx` |
| **请求格式** | JSON-RPC 2.0 | JSON-RPC 2.0 |
| **响应格式** | JSON-RPC 2.0 | JSON-RPC 2.0 |
| **路由功能** | 无（直接处理） | 根据 sessionId 路由到后端服务 |
| **服务发现** | 无 | 支持（通过 Nacos） |
| **负载均衡** | 无 | 支持 |

### MCP 协议接口支持

| 接口 | mcp-server-v6 | mcp-router-v3 | 说明 |
|------|---------------|---------------|------|
| `initialize` | ✅ | ✅ | 透传到后端服务 |
| `tools/list` | ✅ | ✅ | 路由到后端服务 |
| `tools/call` | ✅ | ✅ | 路由到后端服务 |
| `resources/list` | ✅ | ✅ | 路由到后端服务 |
| `resources/read` | ✅ | ✅ | 路由到后端服务 |
| `prompts/list` | ✅ | ✅ | 路由到后端服务 |
| `prompts/get` | ✅ | ✅ | 路由到后端服务 |

---

## 🔍 关键差异总结

### 1. SSE 连接建立

**mcp-server-v6:**
- 端点：`GET /sse`
- 无服务名称参数（单服务器）
- 直接返回消息端点 URL

**mcp-router-v3:**
- 端点：`GET /sse/{serviceName}`
- 通过路径参数指定服务名称
- 自动关联 `sessionId` 和 `serviceName`
- 响应格式完全兼容

### 2. MCP 消息处理

**mcp-server-v6:**
- 直接处理请求
- 无路由功能

**mcp-router-v3:**
- 根据 `sessionId` 查找关联的 `serviceName`
- 自动路由到后端服务
- 支持智能路由（如果会话中没有 serviceName）
- 支持负载均衡

### 3. 协议支持

**两者都完全支持所有 MCP 标准协议接口：**
- ✅ `initialize`
- ✅ `tools/list`
- ✅ `tools/call`
- ✅ `resources/list`
- ✅ `resources/read`
- ✅ `prompts/list`
- ✅ `prompts/get`

**差异：**
- mcp-server-v6：直接处理
- mcp-router-v3：透传路由到后端服务

---

## 🧪 测试示例

### 测试 mcp-server-v6

```bash
# 1. 建立 SSE 连接
curl -N http://localhost:8071/sse

# 响应：
# event:endpoint
# data:http://localhost:8071/mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000
# :heartbeat
# ...

# 2. 发送 tools/list 请求
curl -X POST "http://localhost:8071/mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "req-001",
    "params": {}
  }'
```

### 测试 mcp-router-v3

```bash
# 1. 建立 SSE 连接（指定服务名称）
curl -N http://localhost:8052/sse/mcp-server-v6

# 响应：
# event:endpoint
# data:http://localhost:8052/mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000
# :heartbeat
# ...

# 2. 发送 tools/list 请求（自动路由到 mcp-server-v6）
curl -X POST "http://localhost:8052/mcp/message?sessionId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "req-001",
    "params": {}
  }'
```

---

## ✅ 兼容性说明

### 完全兼容
- ✅ SSE 响应格式（`event:endpoint` + `data:`）
- ✅ MCP 消息请求格式（JSON-RPC 2.0）
- ✅ MCP 消息响应格式（JSON-RPC 2.0）
- ✅ 所有 MCP 标准协议接口

### 扩展功能
- ✅ mcp-router-v3 支持路径参数方式指定服务名称
- ✅ mcp-router-v3 支持智能路由和负载均衡
- ✅ mcp-router-v3 支持多服务聚合

---

## 📝 总结

1. **SSE 连接格式**：两者完全兼容，都使用 Spring AI 标准格式（`event:endpoint` + `data:`）
2. **MCP 消息格式**：两者完全兼容，都使用 JSON-RPC 2.0 标准
3. **协议支持**：两者都完全支持所有 MCP 标准协议接口
4. **主要差异**：mcp-router-v3 通过路径参数指定服务名称，并支持路由功能

**结论**：mcp-router-v3 在保持与 mcp-server-v6 完全兼容的基础上，增加了路由和负载均衡功能，可以作为 mcp-server-v6 的透明代理使用。

---

## 🔍 实际验证方法

### 验证 SSE 响应格式

```bash
# 验证 mcp-server-v6 的 SSE 响应
curl -N http://localhost:8071/sse | head -5

# 验证 mcp-router-v3 的 SSE 响应
curl -N http://localhost:8052/sse/mcp-server-v6 | head -5
```

**预期输出（两者应该相同）：**
```
event:endpoint
data:http://localhost:XXXX/mcp/message?sessionId=...

:heartbeat
```

### 验证 MCP 消息响应格式

```bash
# 1. 建立 SSE 连接并获取 sessionId
SESSION_ID=$(curl -N http://localhost:8052/sse/mcp-server-v6 2>/dev/null | grep -oP 'sessionId=\K[^"]+' | head -1)

# 2. 发送 tools/list 请求
curl -X POST "http://localhost:8052/mcp/message?sessionId=$SESSION_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "req-001",
    "params": {}
  }' | jq .
```

**预期输出：**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "tools": [...],
    "toolsMeta": {}
  }
}
```

### 验证所有 MCP 协议接口

```bash
# 测试所有 MCP 标准协议接口
METHODS=("initialize" "tools/list" "tools/call" "resources/list" "resources/read" "prompts/list" "prompts/get")

for method in "${METHODS[@]}"; do
  echo "Testing $method..."
  curl -X POST "http://localhost:8052/mcp/message?sessionId=$SESSION_ID" \
    -H "Content-Type: application/json" \
    -d "{
      \"jsonrpc\": \"2.0\",
      \"method\": \"$method\",
      \"id\": \"req-$(date +%s)\",
      \"params\": {}
    }" | jq .
  echo ""
done
```

---

## 📚 相关文档

- [MCP 协议规范](https://modelcontextprotocol.io/)
- [Spring AI MCP Server 文档](https://docs.spring.io/spring-ai/reference/api/mcp-server.html)
- [mcp-router-v3 路由功能文档](./PATH_BASED_API.md)


















