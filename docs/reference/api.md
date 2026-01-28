# API 参考文档

> MCP Router 项目完整 API 参考

## 📚 目录

1. [MCP Client API](#mcp-client-api)
2. [MCP Router API](#mcp-router-api)
3. [MCP Server API](#mcp-server-api)

---

## MCP Client API

**Base URL**: `http://localhost:8080`

### 1. 人员管理

#### GET /persons/all

获取所有人员列表

**请求示例**:
```bash
curl http://localhost:8080/persons/all
```

**响应示例**:
```json
[
  {
    "id": 1,
    "name": "Albert Einstein",
    "nationality": "German",
    "birthYear": 1879
  }
]
```

**状态码**:
- `200 OK` - 成功
- `500 Internal Server Error` - 服务错误

---

#### GET /persons/nationality/{nationality}

按国籍查找人员

**路径参数**:
- `nationality` (string, required) - 国籍

**请求示例**:
```bash
curl http://localhost:8080/persons/nationality/German
```

**响应示例**:
```json
[
  {
    "id": 1,
    "name": "Albert Einstein",
    "nationality": "German"
  }
]
```

---

#### GET /persons/count-by-nationality/{nationality}

统计指定国籍的人员数量

**路径参数**:
- `nationality` (string, required) - 国籍

**请求示例**:
```bash
curl http://localhost:8080/persons/count-by-nationality/French
```

**响应示例**:
```json
{
  "nationality": "French",
  "count": 3
}
```

---

#### POST /persons/query

AI 自然语言查询

**请求体**:
```json
{
  "query": "Who is the oldest person?",
  "options": {
    "model": "deepseek-chat",
    "temperature": 0.7
  }
}
```

**请求示例**:
```bash
curl -X POST http://localhost:8080/persons/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "告诉我最年长的人是谁？"
  }'
```

**响应示例**:
```json
{
  "result": "数据库中最年长的人是...",
  "toolsUsed": ["getAllPersons", "getPersonById"],
  "model": "deepseek-chat",
  "tokensUsed": 150
}
```

---

### 2. 健康检查

#### GET /actuator/health

服务健康状态

**请求示例**:
```bash
curl http://localhost:8080/actuator/health
```

**响应示例**:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

## MCP Router API

**Base URL**: `http://localhost:8000`

### 1. 服务管理

#### GET /mcp/servers

列出所有已注册的 MCP Server

**请求示例**:
```bash
curl http://localhost:8000/mcp/servers
```

**响应示例**:
```json
[
  {
    "serviceId": "mcp-server-v6",
    "host": "localhost",
    "port": 8060,
    "healthy": true,
    "tools": [
      {
        "name": "getPersonById",
        "description": "Find person by ID"
      }
    ]
  }
]
```

---

#### GET /mcp/servers/{serviceId}

获取特定服务详情

**路径参数**:
- `serviceId` (string, required) - 服务ID

**请求示例**:
```bash
curl http://localhost:8000/mcp/servers/mcp-server-v6
```

**响应示例**:
```json
{
  "serviceId": "mcp-server-v6",
  "instances": [
    {
      "instanceId": "192.168.1.100:8060",
      "host": "192.168.1.100",
      "port": 8060,
      "healthy": true,
      "metadata": {
        "version": "1.0.0",
        "tools": "getPersonById,getAllPersons"
      }
    }
  ],
  "totalInstances": 1,
  "healthyInstances": 1
}
```

---

#### POST /mcp/servers/{serviceId}/tools

列出服务提供的所有工具

**路径参数**:
- `serviceId` (string, required) - 服务ID

**请求示例**:
```bash
curl http://localhost:8000/mcp/servers/mcp-server-v6/tools
```

**响应示例**:
```json
{
  "serviceId": "mcp-server-v6",
  "tools": [
    {
      "name": "getPersonById",
      "description": "Find person by ID",
      "inputSchema": {
        "type": "object",
        "properties": {
          "id": {
            "type": "integer",
            "description": "Person ID"
          }
        },
        "required": ["id"]
      }
    }
  ]
}
```

---

### 2. 搜索功能

#### GET /mcp/search?q={query}

搜索工具

**查询参数**:
- `q` (string, required) - 搜索关键词

**请求示例**:
```bash
curl "http://localhost:8000/mcp/search?q=person"
```

**响应示例**:
```json
{
  "query": "person",
  "results": [
    {
      "serviceId": "mcp-server-v6",
      "tool": "getPersonById",
      "score": 0.95
    }
  ]
}
```

---

## MCP Server API

**Base URL**: `http://localhost:8060`

### SSE Endpoint

#### GET /mcp

MCP 协议 SSE 连接

**请求示例**:
```bash
curl -N http://localhost:8060/mcp
```

**响应**: Server-Sent Events 流

**事件类型**:
- `initialize` - 初始化
- `tools/list` - 工具列表
- `tools/call` - 工具调用
- `resources/list` - 资源列表

---

## 🔐 认证

所有 API 支持以下认证方式：

### API Key (推荐)

```bash
curl -H "X-API-Key: your-api-key" \
  http://localhost:8080/persons/all
```

### Bearer Token

```bash
curl -H "Authorization: Bearer your-jwt-token" \
  http://localhost:8080/persons/all
```

---

## ⚠️ 错误码

| 状态码 | 说明 | 示例 |
|--------|------|------|
| 200 | 成功 | - |
| 400 | 请求参数错误 | `{"error": "Invalid request"}` |
| 401 | 未授权 | `{"error": "Unauthorized"}` |
| 404 | 资源不存在 | `{"error": "Not found"}` |
| 429 | 限流 | `{"error": "Rate limit exceeded"}` |
| 500 | 服务器错误 | `{"error": "Internal error"}` |

**通用错误响应格式**:
```json
{
  "error": "Error description",
  "code": "ERROR_CODE",
  "timestamp": "2026-01-28T12:00:00Z",
  "path": "/api/endpoint"
}
```

---

## 📊 限流

API 限流策略：

- **默认限制**: 100 请求/秒
- **Burst**: 200 请求
- **响应头**:
  ```
  X-RateLimit-Limit: 100
  X-RateLimit-Remaining: 95
  X-RateLimit-Reset: 1706428800
  ```

---

## 🔄 版本控制

API 版本通过 URL 路径或 Header 指定：

### URL 版本 (推荐)

```bash
curl http://localhost:8080/v1/persons/all
curl http://localhost:8080/v2/persons/all
```

### Header 版本

```bash
curl -H "API-Version: 1" \
  http://localhost:8080/persons/all
```

---

## 📚 相关文档

- [快速开始](../quick-start/getting-started.md)
- [架构设计](../explanations/architecture.md)
- [故障排除](../how-to-guides/troubleshooting.md)

---

**需要更新或补充？** [创建 Issue](https://github.com/thefirstwind/mcp-router-sse-parent/issues)
