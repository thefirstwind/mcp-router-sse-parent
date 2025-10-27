# MCP Bridge v3 API 接口文档

## 📋 概述

本文档详细描述了 MCP Bridge v3 提供的所有 REST API 接口，包括工具调用、服务管理、监控查询等功能。

---

## 🌐 基础信息

### 服务地址
- **开发环境**: `http://localhost:8080`
- **生产环境**: `https://mcp-bridge.your-domain.com`

### 认证方式
- **开发环境**: 无需认证
- **生产环境**: Bearer Token 或 API Key

### 响应格式
- **Content-Type**: `application/json`
- **字符编码**: `UTF-8`

---

## 🚀 核心 API

### 1. 工具调用接口

#### 1.1 智能工具调用
**自动服务发现，根据工具名称智能路由到最佳服务实例**

```http
POST /mcp/smart/call
Content-Type: application/json

{
  "toolName": "getPersonById",
  "arguments": {
    "id": 1
  },
  "timeout": 30000,
  "metadata": {
    "requestId": "req-001",
    "source": "web-app"
  }
}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "result": "Person found: John Doe, age 30",
    "executionTime": 156,
    "serviceInstance": "mcp-server-v6:8066"
  },
  "metadata": {
    "requestId": "req-001",
    "timestamp": "2025-01-12T10:30:00Z",
    "version": "v3.0.0"
  }
}
```

#### 1.2 指定服务调用
**直接调用指定服务的工具**

```http
POST /mcp/bridge/route/{serviceName}
Content-Type: application/json

{
  "id": "req-002",
  "method": "tools/call",
  "params": {
    "name": "getAllPersons",
    "arguments": {}
  }
}
```

**路径参数**:
- `serviceName`: 目标服务名称，如 `mcp-server-v6`

**响应示例**:
```json
{
  "jsonrpc": "2.0",
  "id": "req-002",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Found 5 persons: John Doe, Jane Smith, Hans Mueller, Maria Schmidt, Pierre Dubois"
      }
    ]
  }
}
```

#### 1.3 批量工具调用
**一次请求调用多个工具**

```http
POST /mcp/smart/batch
Content-Type: application/json

{
  "calls": [
    {
      "toolName": "getPersonById",
      "arguments": {"id": 1}
    },
    {
      "toolName": "getPersonById", 
      "arguments": {"id": 2}
    }
  ],
  "parallel": true,
  "timeout": 45000
}
```

---

### 2. 服务管理接口

#### 2.1 查询注册服务列表
```http
GET /mcp/bridge/services
```

**响应示例**:
```json
{
  "services": [
    {
      "serviceName": "mcp-server-v6",
      "version": "1.0.1",
      "instances": [
        {
          "instanceId": "mcp-server-v6-001",
          "ip": "192.168.1.100",
          "port": 8066,
          "healthy": true,
          "weight": 1.0,
          "metadata": {
            "zone": "zone-a",
            "cluster": "default"
          }
        }
      ],
      "tools": [
        "getAllPersons",
        "getPersonById",
        "addPerson"
      ],
      "capabilities": {
        "tools": true,
        "resources": true,
        "prompts": false
      }
    }
  ],
  "total": 1
}
```

#### 2.2 查询特定服务详情
```http
GET /mcp/bridge/services/{serviceName}
```

#### 2.3 查询工具清单
```http
GET /mcp/bridge/tools
```

**查询参数**:
- `serviceName`: 可选，过滤特定服务的工具
- `category`: 可选，按类别过滤工具

**响应示例**:
```json
{
  "tools": [
    {
      "name": "getPersonById",
      "description": "Retrieve a specific person by their unique identifier",
      "serviceName": "mcp-server-v6",
      "category": "data-access",
      "inputSchema": {
        "type": "object",
        "properties": {
          "id": {
            "type": "integer",
            "description": "Unique person identifier"
          }
        },
        "required": ["id"]
      }
    }
  ]
}
```

---

### 3. 健康检查接口

#### 3.1 系统健康状态
```http
GET /actuator/health
```

**响应示例**:
```json
{
  "status": "UP",
  "components": {
    "nacos": {
      "status": "UP",
      "details": {
        "serverAddr": "127.0.0.1:8848",
        "namespace": "public"
      }
    },
    "mcpBridge": {
      "status": "UP", 
      "details": {
        "activeConnections": 3,
        "registeredServices": 2,
        "healthyServices": 2
      }
    }
  }
}
```

#### 3.2 服务实例健康检查
```http
GET /mcp/bridge/health/{serviceName}
```

#### 3.3 连接池状态
```http
GET /mcp/bridge/connections/status
```

---

### 4. 监控统计接口

#### 4.1 系统统计信息
```http
GET /mcp/bridge/stats
```

**响应示例**:
```json
{
  "system": {
    "status": "RUNNING",
    "version": "v3.0.0",
    "uptime": "2d 5h 30m",
    "startTime": "2025-01-10T08:00:00Z"
  },
  "requests": {
    "total": 15642,
    "success": 15234,
    "failed": 408,
    "successRate": 97.39
  },
  "performance": {
    "avgResponseTime": 245,
    "p95ResponseTime": 456,
    "p99ResponseTime": 892,
    "activeRequests": 12
  },
  "services": {
    "registered": 3,
    "healthy": 3,
    "unhealthy": 0
  },
  "connections": {
    "active": 15,
    "idle": 5,
    "total": 20,
    "maxPoolSize": 50
  }
}
```

#### 4.2 工具调用统计
```http
GET /mcp/bridge/metrics/tools
```

**查询参数**:
- `timeRange`: 时间范围 (1h, 6h, 24h, 7d)
- `toolName`: 可选，特定工具统计

#### 4.3 负载均衡统计
```http
GET /mcp/bridge/metrics/loadbalancing
```

---

### 5. 配置管理接口

#### 5.1 查看当前配置
```http
GET /mcp/bridge/config
```

#### 5.2 动态更新配置
```http
PUT /mcp/bridge/config
Content-Type: application/json

{
  "loadBalancer": {
    "algorithm": "WEIGHTED_ROUND_ROBIN",
    "healthCheckInterval": 30
  },
  "connectionPool": {
    "maxConnections": 50,
    "connectionTimeout": 30000
  }
}
```

---

## 🔧 管理接口

### 1. 服务实例管理

#### 1.1 手动下线服务实例
```http
POST /mcp/bridge/admin/services/{serviceName}/instances/{instanceId}/offline
```

#### 1.2 手动上线服务实例
```http
POST /mcp/bridge/admin/services/{serviceName}/instances/{instanceId}/online
```

#### 1.3 调整实例权重
```http
PUT /mcp/bridge/admin/services/{serviceName}/instances/{instanceId}/weight
Content-Type: application/json

{
  "weight": 2.0
}
```

### 2. 连接管理

#### 2.1 重置连接池
```http
POST /mcp/bridge/admin/connections/reset
```

#### 2.2 清理失效连接
```http
POST /mcp/bridge/admin/connections/cleanup
```

---

## ⚠️ 错误处理

### 标准错误响应格式
```json
{
  "success": false,
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "No healthy instances available for service: mcp-server-v6",
    "details": {
      "serviceName": "mcp-server-v6",
      "totalInstances": 2,
      "healthyInstances": 0
    }
  },
  "metadata": {
    "requestId": "req-003",
    "timestamp": "2025-01-12T10:35:00Z"
  }
}
```

### 错误代码说明

| 错误代码 | HTTP状态 | 说明 |
|---------|----------|------|
| `INVALID_REQUEST` | 400 | 请求参数错误 |
| `UNAUTHORIZED` | 401 | 认证失败 |
| `FORBIDDEN` | 403 | 权限不足 |
| `TOOL_NOT_FOUND` | 404 | 工具不存在 |
| `SERVICE_NOT_FOUND` | 404 | 服务不存在 |
| `REQUEST_TIMEOUT` | 408 | 请求超时 |
| `SERVICE_UNAVAILABLE` | 503 | 服务不可用 |
| `INTERNAL_ERROR` | 500 | 内部错误 |

---

## 🔒 安全认证

### API Key 认证
```http
GET /mcp/bridge/services
Authorization: Bearer your-api-key-here
```

### 权限范围
- **READ**: 查询接口权限
- **WRITE**: 调用工具权限  
- **ADMIN**: 管理接口权限

---

## 📊 速率限制

### 限制规则
- **智能调用**: 100 请求/分钟
- **直接调用**: 200 请求/分钟
- **批量调用**: 10 请求/分钟
- **查询接口**: 1000 请求/分钟

### 响应头
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1641968400
```

---

## 🧪 测试示例

### cURL 示例
```bash
# 智能工具调用
curl -X POST http://localhost:8080/mcp/smart/call \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "getPersonById",
    "arguments": {"id": 1}
  }'

# 查询服务列表
curl http://localhost:8080/mcp/bridge/services

# 查看系统统计
curl http://localhost:8080/mcp/bridge/stats
```

### Postman Collection
```json
{
  "info": {
    "name": "MCP Bridge v3 API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Smart Tool Call",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"toolName\": \"getPersonById\",\n  \"arguments\": {\n    \"id\": 1\n  }\n}"
        },
        "url": {
          "raw": "{{baseUrl}}/mcp/smart/call",
          "host": ["{{baseUrl}}"],
          "path": ["mcp", "smart", "call"]
        }
      }
    }
  ]
}
```

---

## 📚 SDK 支持

### Java SDK 示例
```java
McpBridgeClient client = McpBridgeClient.builder()
    .baseUrl("http://localhost:8080")
    .apiKey("your-api-key")
    .timeout(Duration.ofSeconds(30))
    .build();

// 智能工具调用
ToolCallResponse response = client.callTool("getPersonById", 
    Map.of("id", 1));

// 查询服务列表
List<ServiceInfo> services = client.getServices();
```

### Python SDK 示例
```python
from mcp_bridge_client import McpBridgeClient

client = McpBridgeClient(
    base_url="http://localhost:8080",
    api_key="your-api-key"
)

# 智能工具调用
response = client.call_tool("getPersonById", {"id": 1})

# 查询服务列表
services = client.get_services()
```

---

## 📝 更新日志

### v3.0.0 (2025-01-12)
- ✅ 新增智能工具调用接口
- ✅ 支持批量工具调用
- ✅ 增强服务管理功能
- ✅ 完善监控统计接口

### v2.1.0 (2024-12-15)
- ✅ 添加连接池管理
- ✅ 支持动态配置更新
- ✅ 增强错误处理机制

---

> 💡 **提示**: 此 API 文档会随着系统版本更新而持续完善，建议定期查看最新版本。
