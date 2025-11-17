# MCP Router V3 - Path-Based API 文档

## 📋 概述

MCP Router V3 支持使用 **Path 方式**建立 SSE 连接和发送 MCP 消息，服务名称包含在 URL 路径中，sessionId 通过查询参数传递（符合官方 MCP 协议规范）。

---

## 🔌 API 端点

### 1. SSE 连接端点

```
GET /sse/{serviceName}
```

**说明:**
- `serviceName` 在路径中指定
- `sessionId` 由服务器自动生成，并在响应中返回

**示例:**
```bash
curl -N -H "Accept: text/event-stream" \
  http://localhost:8050/sse/mcp-server-v6
```

**响应格式:**
```
data: {"type":"connection","status":"connected","sessionId":"550e8400-e29b-41d4-a716-446655440000","serviceName":"mcp-server-v6","baseUrl":"http://127.0.0.1:8050","messageUrl":"http://127.0.0.1:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000","timestamp":1754386992538}
```

**响应字段说明:**
| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 固定值 `"connection"` |
| `status` | string | 连接状态，固定值 `"connected"` |
| `sessionId` | string | 会话ID |
| `serviceName` | string | 服务名称 |
| `baseUrl` | string | 服务器基础URL |
| `messageUrl` | string | 消息端点URL（包含服务名称和会话ID） |
| `timestamp` | number | 时间戳 |

---

### 2. MCP 消息端点

```
POST /mcp/{serviceName}/message?sessionId={sessionId}
```

**说明:**
- `serviceName` 在路径中指定
- `sessionId` 通过查询参数传递（从 SSE 连接响应中获取）

**示例:**
```bash
curl -X POST "http://localhost:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "req-001",
    "params": {}
  }'
```

**注意:**
- 如果查询参数中没有提供 `sessionId`，服务器会自动生成一个新的 sessionId

**请求格式:**
- **Content-Type**: `application/json`
- **请求体**: JSON-RPC 2.0 格式

**响应格式:**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "..."
      }
    ],
    "isError": false
  }
}
```

---

## 🔄 完整使用流程

### 步骤 1: 建立 SSE 连接

```bash
# 建立 SSE 连接，指定服务名称（sessionId 自动生成）
curl -N -H "Accept: text/event-stream" \
  http://localhost:8050/sse/mcp-server-v6
```

**响应:**
```
data: {"type":"connection","status":"connected","sessionId":"550e8400-e29b-41d4-a716-446655440000","serviceName":"mcp-server-v6","baseUrl":"http://127.0.0.1:8050","messageUrl":"http://127.0.0.1:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000","timestamp":1754386992538}
```

**重要:** 从响应中提取 `sessionId` 和 `messageUrl`，用于后续的消息发送。

### 步骤 2: 发送 MCP 消息

使用响应中的 `messageUrl` 发送消息（或手动构建 URL）：

```bash
# 方式 1: 使用响应中的 messageUrl
curl -X POST "http://127.0.0.1:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "req-001",
    "params": {}
  }'
```

**响应:**
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"tools\":[...]}"
      }
    ],
    "isError": false
  }
}
```

### 步骤 3: 调用工具

```bash
# 调用工具（使用相同的 sessionId）
curl -X POST "http://localhost:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "id": "req-002",
    "params": {
      "name": "getPersonById",
      "arguments": {
        "id": 1
      }
    }
  }'
```

---

## 📝 参数说明

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| `serviceName` | Path | ✅ | 服务名称，例如 `mcp-server-v6` |
| `sessionId` | Query Parameter | ⚠️ | 会话ID，从 SSE 连接响应中获取。如果不提供则自动生成 |

---

## 🔍 服务名称解析优先级

当发送 MCP 消息时，服务名称的解析优先级如下：

1. **Path 变量** (最高优先级)
   - 从 URL 路径中提取：`/mcp/{serviceName}/message`

2. **会话注册**
   - 从建立 SSE 连接时注册的会话中获取（通过 sessionId 查询参数）

3. **消息内容**
   - 从 MCP 消息的 `metadata.targetService` 或 `targetService` 字段中提取

4. **智能路由** (最低优先级)
   - 如果以上方式都无法确定服务名称，则使用智能路由自动发现服务

---

## 🆚 Path 方式 vs Query 参数方式

### Path 方式（推荐，符合官方 MCP 协议）
```
GET /sse/{serviceName}
POST /mcp/{serviceName}/message?sessionId={sessionId}
```

**特点:**
- ✅ 服务名称在路径中，符合 RESTful 风格
- ✅ sessionId 通过查询参数传递，符合官方 MCP 协议规范
- ✅ SSE 连接时自动生成 sessionId，简化客户端逻辑
- ✅ 响应中直接返回完整的 messageUrl，便于使用

### Query 参数方式（兼容）
```
GET /sse?serviceName={serviceName}&sessionId={sessionId}
POST /mcp/message?sessionId={sessionId}
```

**说明:**
- 为了向后兼容，仍然支持查询参数方式
- 但推荐使用 Path 方式（符合官方 MCP 协议）

---

## 🚀 快速开始

### 1. 启动 MCP Router

```bash
cd mcp-router-v3
mvn spring-boot:run
```

### 2. 测试 SSE 连接

```bash
# 建立 SSE 连接（sessionId 自动生成）
curl -N -H "Accept: text/event-stream" \
  http://localhost:8050/sse/mcp-server-v6
```

**从响应中提取 sessionId，例如：**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "messageUrl": "http://127.0.0.1:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000"
}
```

### 3. 测试消息发送

```bash
# 发送 tools/list 请求（使用从 SSE 响应中获取的 sessionId）
curl -X POST "http://localhost:8050/mcp/mcp-server-v6/message?sessionId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/list",
    "id": "test-001",
    "params": {}
  }'
```

---

## 📚 相关文档

- [MCP Router V3 主文档](../readme.md)
- [MCP Server V6 对比文档](../../mcp-server-v6/MCP_SERVER_V6_VS_ROUTER_V3_COMPARISON.md)


















