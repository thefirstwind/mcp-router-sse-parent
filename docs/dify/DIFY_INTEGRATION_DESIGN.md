# MCP Router V3 接入 Dify 设计方案

## 1. 接口分析

### 1.1 tools/list 接口（工具列表）

**接口地址：**
```
GET http://localhost:8052/mcp/router/tools/{serverKey}
```

**示例请求：**
```bash
curl --location 'http://localhost:8052/mcp/router/tools/mcp-server-v6'
```

**响应数据结构：**
```json
{
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
    },
    {
      "name": "getAllPersons",
      "description": "Get all persons from the database",
      "inputSchema": {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": false
      }
    },
    {
      "name": "addPerson",
      "description": "Add a new person to the database",
      "inputSchema": {
        "type": "object",
        "properties": {
          "firstName": {"type": "string", "description": "Person's first name"},
          "lastName": {"type": "string", "description": "Person's last name"},
          "age": {"type": "integer", "format": "int32", "description": "Person's age"},
          "nationality": {"type": "string", "description": "Person's nationality"},
          "gender": {"type": "string", "description": "Person's gender (MALE, FEMALE, OTHER)"}
        },
        "required": ["firstName", "lastName", "age", "nationality", "gender"],
        "additionalProperties": false
      }
    }
  ]
}
```

**返回字段说明：**
- `tools`: 工具列表数组
  - `name`: 工具名称（必填）
  - `description`: 工具描述（必填）
  - `inputSchema`: 输入参数的JSON Schema（必填）
    - `type`: 参数类型
    - `properties`: 参数属性定义
    - `required`: 必填参数列表
    - `additionalProperties`: 是否允许额外属性

---

### 1.2 tools/call 接口（工具调用）

**接口地址：**
```
POST http://localhost:8052/mcp/router/route/{serverKey}
```

**示例请求：**
```bash
curl --location 'http://localhost:8052/mcp/router/route/mcp-server-v6' \
--header 'Content-Type: application/json' \
--data '{
    "id": "1005",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": { "id": 5 }
    }
  }'
```

**请求体结构：**
```json
{
  "id": "string",           // 请求ID（可选，建议使用UUID）
  "method": "tools/call",   // 固定值
  "params": {
    "name": "string",       // 工具名称（必填）
    "arguments": {}         // 工具参数（必填，根据工具的inputSchema定义）
  }
}
```

**响应数据结构：**
```json
{
  "metadata": {
    "targetServer": "mcp-server-v6",
    "targetHost": "192.168.0.100:8073",
    "serverVersion": "1.0.1",
    "clientId": "mcp-router-v3-client",
    "responseTime": 35,
    "routingStrategy": "intelligent",
    "serverMetadata": {
      "sseEndpoint": "/sse",
      "serverName": "mcp-server-v6",
      "protocol": "mcp-sse",
      "sseMessageEndpoint": "/mcp/message",
      "version": "1.0.1"
    },
    "serverGroup": null,
    "routedAt": 1761877499188,
    "clientVersion": "2.0.0",
    "toolName": "getPersonById",
    "routerVersion": "v3"
  },
  "targetService": "mcp-server-v6",
  "clientId": "mcp-router-v3-client",
  "sessionId": null,
  "timestamp": 1761877499188,
  "id": "1005",
  "method": "tools/call",
  "params": {
    "name": "getPersonById",
    "arguments": {
      "id": 5
    }
  },
  "result": {
    "firstName": "Pierre",
    "lastName": "Dubois",
    "found": true,
    "nationality": "French",
    "gender": "MALE",
    "id": 5,
    "age": 40
  },
  "error": null,
  "jsonrpc": "2.0"
}
```

**响应字段说明：**
- `result`: 工具执行结果（成功时）
- `error`: 错误信息（失败时）
- `metadata`: 路由元数据（包含性能、路由策略等信息）
- `id`: 请求ID（与请求一致）
- `method`: 方法名
- `params`: 请求参数（原样返回）

---

## 2. Dify 接入方案

### 2.1 架构设计

```
┌─────────────┐
│   Dify AI   │
│   Platform  │
└──────┬──────┘
       │
       │ 1. 用户输入："查询用户 id=5"
       │
       v
┌─────────────────────────────┐
│  Dify Workflow / Agent      │
│  ┌──────────────────────┐   │
│  │  系统提示词(Prompt)  │   │
│  │  - 理解用户意图      │   │
│  │  - 提取参数          │   │
│  │  - 调用MCP工具       │   │
│  └──────────────────────┘   │
└──────────┬──────────────────┘
           │
           │ 2. 调用 MCP Router 工具
           │
           v
┌─────────────────────────────┐
│  MCP Router V3              │
│  ┌────────────────────┐     │
│  │  tools/list        │     │
│  │  (获取可用工具)    │     │
│  └────────────────────┘     │
│  ┌────────────────────┐     │
│  │  tools/call        │     │
│  │  (调用具体工具)    │     │
│  └────────────────────┘     │
└──────────┬──────────────────┘
           │
           │ 3. 路由到后端服务
           │
           v
┌─────────────────────────────┐
│  MCP Server V6              │
│  (Person Management)        │
└─────────────────────────────┘
```

---

### 2.2 Dify 工作流配置

#### 方案A：使用 Dify 的 HTTP Request 节点

**步骤1：创建工作流**
1. 在 Dify 中创建新的 Workflow
2. 添加"开始"节点，接收用户输入

**步骤2：添加 LLM 节点（参数提取）**
```yaml
节点名称: 参数提取器
节点类型: LLM
模型: gpt-4 或其他大模型

系统提示词:
你是一个参数提取助手。用户会询问关于用户信息的问题，你需要：
1. 理解用户意图
2. 提取出需要调用的工具名称
3. 提取出工具所需的参数

可用工具：
- getPersonById: 根据ID查询用户信息，参数: id (整数)
- getAllPersons: 获取所有用户信息，无参数
- addPerson: 添加新用户，参数: firstName, lastName, age, nationality, gender

请以JSON格式返回：
{
  "toolName": "工具名称",
  "arguments": { "参数名": "参数值" }
}

用户输入: {{start.user_input}}

输出变量: extraction_result
```

**步骤3：添加代码节点（构造请求）**
```python
# 节点名称: 构造MCP请求
# 节点类型: Code

import json
import uuid

def main(extraction_result: str) -> dict:
    # 解析LLM提取的结果
    extracted = json.loads(extraction_result)
    
    # 构造MCP调用请求
    mcp_request = {
        "id": str(uuid.uuid4()),
        "method": "tools/call",
        "params": {
            "name": extracted["toolName"],
            "arguments": extracted["arguments"]
        }
    }
    
    return {
        "request_body": json.dumps(mcp_request),
        "tool_name": extracted["toolName"]
    }
```

**步骤4：添加 HTTP Request 节点（调用MCP Router）**
```yaml
节点名称: 调用MCP工具
节点类型: HTTP Request

配置:
  方法: POST
  URL: http://localhost:8052/mcp/router/route/mcp-server-v6
  请求头:
    Content-Type: application/json
  请求体: {{code.request_body}}
  
输出变量: mcp_response
```

**步骤5：添加 LLM 节点（结果格式化）**
```yaml
节点名称: 结果展示
节点类型: LLM
模型: gpt-4

系统提示词:
你是一个友好的助手。请根据以下查询结果，用自然语言回复用户。

查询工具: {{code.tool_name}}
查询结果: {{http.mcp_response.result}}

请用友好、简洁的方式告诉用户查询结果。

输出变量: final_answer
```

**步骤6：添加"结束"节点**
```yaml
节点名称: 结束
节点类型: End
输出内容: {{llm_result.final_answer}}
```

---

#### 方案B：使用 Dify 的自定义工具（推荐）

Dify 支持通过 OpenAPI Specification 自动生成工具。

**步骤1：创建 OpenAPI 规范文件**

将 MCP Router 的接口封装成 OpenAPI 格式：

```yaml
# mcp-router-openapi.yaml
openapi: 3.0.0
info:
  title: MCP Router V3 API
  version: 1.0.0
  description: MCP Router V3 工具调用接口

servers:
  - url: http://localhost:8052/mcp/router

paths:
  /tools/{serverKey}:
    get:
      operationId: listTools
      summary: 获取可用工具列表
      description: 获取指定服务器上所有可用的MCP工具
      parameters:
        - name: serverKey
          in: path
          required: true
          schema:
            type: string
          description: 服务器标识
      responses:
        '200':
          description: 成功返回工具列表
          content:
            application/json:
              schema:
                type: object
                properties:
                  tools:
                    type: array
                    items:
                      type: object
                      properties:
                        name:
                          type: string
                        description:
                          type: string
                        inputSchema:
                          type: object

  /route/{serverKey}:
    post:
      operationId: callTool
      summary: 调用MCP工具
      description: 调用指定服务器上的MCP工具
      parameters:
        - name: serverKey
          in: path
          required: true
          schema:
            type: string
          description: 服务器标识
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - method
                - params
              properties:
                id:
                  type: string
                  description: 请求ID
                method:
                  type: string
                  enum: [tools/call]
                  description: 方法名称
                params:
                  type: object
                  required:
                    - name
                    - arguments
                  properties:
                    name:
                      type: string
                      description: 工具名称
                    arguments:
                      type: object
                      description: 工具参数
      responses:
        '200':
          description: 成功返回工具执行结果
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
                  result:
                    type: object
                  error:
                    type: object
                  metadata:
                    type: object
```

**步骤2：在 Dify 中导入工具**
1. 进入 Dify 控制台
2. 导航到"工具" → "自定义工具"
3. 点击"导入 OpenAPI"
4. 上传 `mcp-router-openapi.yaml` 文件
5. Dify 会自动解析并生成工具

**步骤3：创建 Agent 应用**
```yaml
应用类型: Agent
模型: gpt-4

系统提示词:
你是一个用户信息查询助手。你可以帮助用户查询、添加和管理用户信息。

你有以下工具可用：
1. callTool - 调用MCP工具来执行具体操作

当用户询问用户信息时：
- 如果用户指定了ID，使用 getPersonById 工具
- 如果用户要查看所有用户，使用 getAllPersons 工具
- 如果用户要添加新用户，使用 addPerson 工具

工具调用示例：
用户: "查询用户 id=5"
你应该调用: callTool
参数:
  serverKey: "mcp-server-v6"
  body:
    id: "unique-request-id"
    method: "tools/call"
    params:
      name: "getPersonById"
      arguments:
        id: 5

请根据工具返回的结果，用友好的方式回复用户。

可用工具: 
- callTool (MCP Router V3 API)
```

**步骤4：配置工具参数**

在 Agent 中配置 `callTool` 的默认参数：
- `serverKey`: 默认值 `mcp-server-v6`
- 其他参数由 LLM 根据用户输入自动填充

---

### 2.3 示例对话流程

**用户输入：** "查询用户 id=5"

**处理流程：**

1. **LLM 理解意图**
   - 识别用户想查询用户信息
   - 提取参数：id = 5
   - 选择工具：getPersonById

2. **调用 MCP Router**
   ```http
   POST http://localhost:8052/mcp/router/route/mcp-server-v6
   Content-Type: application/json

   {
     "id": "req-12345",
     "method": "tools/call",
     "params": {
       "name": "getPersonById",
       "arguments": { "id": 5 }
     }
   }
   ```

3. **接收响应**
   ```json
   {
     "result": {
       "id": 5,
       "firstName": "Pierre",
       "lastName": "Dubois",
       "age": 40,
       "nationality": "French",
       "gender": "MALE",
       "found": true
     }
   }
   ```

4. **LLM 格式化回复**
   ```
   找到了用户信息！
   
   用户ID: 5
   姓名: Pierre Dubois
   年龄: 40岁
   国籍: 法国
   性别: 男性
   ```

---

## 3. 进阶功能

### 3.1 多轮对话支持

在 Dify Agent 中配置对话记忆，支持上下文理解：

**示例对话：**
```
用户: 查询用户 id=5
AI: 找到了用户 Pierre Dubois，40岁，法国人。

用户: 他的年龄是多少？
AI: Pierre Dubois 今年40岁。

用户: 再查询一下 id=3 的用户
AI: [调用工具查询 id=3]
```

### 3.2 错误处理

在系统提示词中添加错误处理逻辑：

```
如果工具调用失败：
1. 检查 error 字段
2. 向用户友好地说明错误原因
3. 建议用户如何修正

常见错误：
- 用户不存在：提示"未找到该用户，请检查ID是否正确"
- 参数错误：提示"参数格式不正确，请提供有效的用户ID"
- 服务不可用：提示"服务暂时不可用，请稍后重试"
```

### 3.3 批量操作

支持批量查询场景：

```
用户: 查询用户 id=1, 2, 3, 4, 5 的信息
AI: [并发调用5次 getPersonById 工具]
    [汇总结果并展示]
```

### 3.4 数据可视化

在 Dify 中添加图表展示节点：

```python
def format_users_table(users: list) -> str:
    """将用户列表格式化为 Markdown 表格"""
    table = "| ID | 姓名 | 年龄 | 国籍 | 性别 |\n"
    table += "|---|---|---|---|---|\n"
    for user in users:
        table += f"| {user['id']} | {user['firstName']} {user['lastName']} | {user['age']} | {user['nationality']} | {user['gender']} |\n"
    return table
```

---

## 4. 部署建议

### 4.1 生产环境配置

```yaml
# Dify 环境变量配置
MCP_ROUTER_BASE_URL: https://mcp-router.example.com
MCP_SERVER_KEY: mcp-server-v6
REQUEST_TIMEOUT: 30000  # 30秒超时
RETRY_COUNT: 3          # 重试3次
```

### 4.2 安全考虑

1. **认证授权**
   - 为 MCP Router 添加 API Key 认证
   - 在 Dify HTTP 请求中配置 Authorization Header

2. **速率限制**
   - 在 Dify 中配置请求频率限制
   - 防止恶意用户滥用

3. **数据脱敏**
   - 敏感字段（如身份证号）不返回或脱敏处理

### 4.3 监控告警

1. **日志记录**
   - 记录所有 MCP 工具调用
   - 使用 MCP Router 的路由日志功能

2. **性能监控**
   - 监控响应时间（metadata.responseTime）
   - 设置告警阈值（如 >2秒）

3. **错误追踪**
   - 收集错误日志
   - 分析常见错误原因

---

## 5. 测试用例

### 5.1 功能测试

**测试用例1：查询存在的用户**
```
输入: 查询用户 id=5
期望: 返回 Pierre Dubois 的完整信息
```

**测试用例2：查询不存在的用户**
```
输入: 查询用户 id=999
期望: 提示用户不存在
```

**测试用例3：查询所有用户**
```
输入: 查询所有用户
期望: 返回用户列表
```

**测试用例4：添加新用户**
```
输入: 添加新用户，名字叫张三，25岁，中国人，男性
期望: 成功添加并返回新用户ID
```

### 5.2 异常测试

**测试用例5：无效参数**
```
输入: 查询用户 id=abc
期望: 提示参数格式错误
```

**测试用例6：服务不可用**
```
场景: MCP Server 关闭
期望: 友好提示服务不可用
```

**测试用例7：超时处理**
```
场景: 请求超过30秒
期望: 返回超时错误
```

---

## 6. 快速开始指南

### 6.1 前置条件
- ✅ MCP Router V3 已启动（端口 8052）
- ✅ MCP Server V6 已注册并运行
- ✅ Dify 平台已部署

### 6.2 配置步骤（5分钟）

1. **导入 OpenAPI 规范**
   - 复制上面的 OpenAPI YAML
   - 在 Dify 中导入

2. **创建 Agent 应用**
   - 使用上面的系统提示词
   - 绑定 callTool 工具

3. **测试验证**
   ```
   输入: 查询用户 id=5
   ```

4. **发布应用**
   - 生成 API 或 Web 访问链接
   - 分享给用户使用

---

## 7. 常见问题 FAQ

**Q1: 如何支持更多 MCP Server？**
A: 修改系统提示词，添加服务器选择逻辑，根据用户意图动态选择 serverKey。

**Q2: 如何提高响应速度？**
A: 
- 使用缓存（getAllPersons 结果）
- 启用 MCP Router 的智能路由
- 优化数据库查询

**Q3: 如何处理大量数据返回？**
A:
- 实现分页（修改 MCP 工具）
- 返回摘要信息
- 提供下载链接

**Q4: 如何支持文件上传？**
A: 
- 使用 Dify 的文件上传节点
- 转换为 Base64
- 通过 MCP 工具传递

---

## 8. 下一步计划

- [ ] 实现 OpenAPI 规范自动生成（从 MCP tools/list）
- [ ] 支持流式响应（SSE）
- [ ] 添加语音交互
- [ ] 实现多模态支持（图片、视频）
- [ ] 集成到企业微信/钉钉

---

## 附录

### A. 完整示例代码

参考项目：
- [Dify 工作流配置文件](./dify-workflow-example.json)
- [OpenAPI 规范文件](./mcp-router-openapi.yaml)
- [测试脚本](./test-dify-integration.sh)

### B. 相关文档
- [MCP 协议规范](https://spec.modelcontextprotocol.io/)
- [Dify 官方文档](https://docs.dify.ai/)
- [OpenAPI 规范](https://swagger.io/specification/)

