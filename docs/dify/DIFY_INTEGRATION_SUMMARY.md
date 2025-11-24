# MCP Router V3 接入 Dify - 总结文档

## 📊 接口分析总结

### 接口1: tools/list（工具列表）

**用途：** 获取 MCP Server 上所有可用工具的列表

**请求：**
```bash
GET http://localhost:8052/mcp/router/tools/mcp-server-v6
```

**响应：**
```json
{
  "tools": [
    {
      "name": "getPersonById",
      "description": "Get a person by their ID",
      "inputSchema": {
        "type": "object",
        "properties": {
          "id": {"type": "integer", "description": "Person's ID"}
        },
        "required": ["id"]
      }
    },
    {
      "name": "getAllPersons",
      "description": "Get all persons from the database",
      "inputSchema": {"type": "object", "properties": {}}
    },
    {
      "name": "addPerson",
      "description": "Add a new person to the database",
      "inputSchema": {
        "type": "object",
        "properties": {
          "firstName": {"type": "string"},
          "lastName": {"type": "string"},
          "age": {"type": "integer"},
          "nationality": {"type": "string"},
          "gender": {"type": "string"}
        },
        "required": ["firstName", "lastName", "age", "nationality", "gender"]
      }
    }
  ]
}
```

**关键字段：**
- `tools`: 工具数组
- `name`: 工具名称（用于调用）
- `description`: 工具描述（帮助 LLM 理解）
- `inputSchema`: 参数定义（JSON Schema 格式）

---

### 接口2: tools/call（工具调用）

**用途：** 调用 MCP Server 上的具体工具

**请求：**
```bash
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

**响应：**
```json
{
  "id": "req-12345",
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "getPersonById",
    "arguments": { "id": 5 }
  },
  "result": {
    "id": 5,
    "firstName": "Pierre",
    "lastName": "Dubois",
    "age": 40,
    "nationality": "French",
    "gender": "MALE",
    "found": true
  },
  "error": null,
  "metadata": {
    "targetServer": "mcp-server-v6",
    "responseTime": 35,
    "routingStrategy": "intelligent",
    "toolName": "getPersonById"
  }
}
```

**关键字段：**
- **请求部分：**
  - `id`: 请求ID（建议用UUID）
  - `method`: 固定值 `tools/call`
  - `params.name`: 工具名称
  - `params.arguments`: 工具参数（根据 inputSchema）

- **响应部分：**
  - `result`: 工具执行结果（成功时）
  - `error`: 错误信息（失败时）
  - `metadata`: 路由元数据（性能、策略等）

---

## 🎯 Dify 接入方案

### 方案对比

| 特性 | 方案A: HTTP工作流 | 方案B: OpenAPI工具 |
|------|------------------|-------------------|
| **难度** | ⭐⭐ 简单 | ⭐⭐⭐ 中等 |
| **灵活性** | ⭐⭐⭐ 高 | ⭐⭐⭐⭐ 很高 |
| **维护成本** | ⭐⭐ 低 | ⭐ 很低 |
| **工具发现** | ❌ 手动配置 | ✅ 自动发现 |
| **多服务器** | ⭐⭐ 需修改 | ⭐⭐⭐⭐ 易扩展 |
| **推荐场景** | 单服务器、快速验证 | 多服务器、生产环境 |

### 推荐：方案B（OpenAPI 工具集成）

**原因：**
1. ✅ 自动工具发现，无需手动配置
2. ✅ 标准化接口，易于维护
3. ✅ 支持多 MCP Server，易扩展
4. ✅ Dify 原生支持，用户体验好
5. ✅ 工具参数自动校验

---

## 🔄 完整调用流程

```
┌─────────────────────────────────────────────────────────────┐
│  Step 1: 用户输入                                             │
│  "查询用户 id=5"                                              │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  v
┌─────────────────────────────────────────────────────────────┐
│  Step 2: Dify LLM 理解意图                                   │
│  - 识别：查询用户信息                                         │
│  - 工具：getPersonById                                        │
│  - 参数：{id: 5}                                              │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  v
┌─────────────────────────────────────────────────────────────┐
│  Step 3: 构造 MCP 请求                                       │
│  {                                                            │
│    "id": "uuid",                                              │
│    "method": "tools/call",                                    │
│    "params": {                                                │
│      "name": "getPersonById",                                 │
│      "arguments": {"id": 5}                                   │
│    }                                                          │
│  }                                                            │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  v
┌─────────────────────────────────────────────────────────────┐
│  Step 4: 调用 MCP Router                                     │
│  POST /mcp/router/route/mcp-server-v6                        │
│                                                               │
│  → MCP Router 智能路由                                       │
│  → 负载均衡到最优服务器                                       │
│  → 执行工具逻辑                                               │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  v
┌─────────────────────────────────────────────────────────────┐
│  Step 5: 返回结果                                            │
│  {                                                            │
│    "result": {                                                │
│      "id": 5,                                                 │
│      "firstName": "Pierre",                                   │
│      "lastName": "Dubois",                                    │
│      "age": 40,                                               │
│      "nationality": "French",                                 │
│      "gender": "MALE",                                        │
│      "found": true                                            │
│    },                                                         │
│    "metadata": {                                              │
│      "responseTime": 35                                       │
│    }                                                          │
│  }                                                            │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  v
┌─────────────────────────────────────────────────────────────┐
│  Step 6: Dify LLM 格式化回复                                 │
│  "找到了用户信息！                                            │
│                                                               │
│   用户ID: 5                                                   │
│   姓名: Pierre Dubois                                         │
│   年龄: 40岁                                                  │
│   国籍: 法国                                                  │
│   性别: 男性"                                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 💡 核心设计要点

### 1. 系统提示词设计

**关键要素：**
```
✅ 角色定义：你是一个用户信息查询助手
✅ 能力说明：可以查询、添加、管理用户信息
✅ 工具列表：列出所有可用工具及参数
✅ 调用示例：提供具体的调用示例
✅ 错误处理：说明如何处理各种错误
✅ 回复格式：要求友好、简洁的回复
```

**示例提示词：**
```
你是一个用户信息管理助手。你可以：
1. 根据ID查询用户（工具: getPersonById）
2. 查看所有用户（工具: getAllPersons）
3. 添加新用户（工具: addPerson）

当用户询问"查询用户 id=5"时，你应该：
1. 调用 callMcpTool 工具
2. 参数设置为：
   {
     "serverKey": "mcp-server-v6",
     "body": {
       "id": "uuid",
       "method": "tools/call",
       "params": {
         "name": "getPersonById",
         "arguments": {"id": 5}
       }
     }
   }
3. 根据返回的 result 字段，友好地回复用户

请始终用友好、简洁的语言与用户交流。
```

### 2. 参数提取与校验

**LLM 提取：**
```javascript
// LLM 从自然语言中提取结构化参数
输入: "查询用户 id=5"
输出: {
  "toolName": "getPersonById",
  "arguments": {"id": 5}
}

输入: "添加用户张三，25岁，中国人，男性"
输出: {
  "toolName": "addPerson",
  "arguments": {
    "firstName": "张",
    "lastName": "三",
    "age": 25,
    "nationality": "Chinese",
    "gender": "MALE"
  }
}
```

**代码校验：**
```python
def validate_arguments(tool_name: str, arguments: dict) -> bool:
    """校验参数是否符合工具要求"""
    schemas = {
        "getPersonById": {"id": "integer"},
        "addPerson": {
            "firstName": "string",
            "lastName": "string",
            "age": "integer",
            "nationality": "string",
            "gender": "string"
        }
    }
    
    schema = schemas.get(tool_name)
    if not schema:
        return False
    
    # 检查必填字段
    for field, type_name in schema.items():
        if field not in arguments:
            return False
        # 类型检查...
    
    return True
```

### 3. 错误处理策略

**错误分类：**
```
1. 参数错误（400）
   → 提示用户修正参数格式
   
2. 工具未找到（404）
   → 提示工具不存在，列出可用工具
   
3. 服务不可用（500/503）
   → 友好提示服务暂时不可用，建议稍后重试
   
4. 超时错误
   → 提示请求超时，建议简化查询条件
   
5. 业务错误（found: false）
   → 提示数据不存在，建议检查参数
```

**错误处理示例：**
```python
def handle_mcp_response(response: dict) -> str:
    """处理 MCP 响应，生成友好提示"""
    
    # 检查 HTTP 错误
    if response.status_code != 200:
        return f"服务暂时不可用（错误码: {response.status_code}），请稍后重试。"
    
    data = response.json()
    
    # 检查 MCP 错误
    if data.get('error'):
        error = data['error']
        return f"操作失败: {error.get('message', '未知错误')}"
    
    # 检查业务结果
    result = data.get('result', {})
    if not result.get('found', True):
        return "未找到相关数据，请检查查询参数。"
    
    # 成功
    return format_success_message(result)
```

### 4. 性能优化

**缓存策略：**
```yaml
# 适合缓存的场景
getAllPersons:
  cache: true
  ttl: 300  # 5分钟

# 不适合缓存的场景  
getPersonById:
  cache: false  # 实时数据

addPerson:
  cache: false  # 写操作
```

**并发控制：**
```python
# 批量查询
async def query_multiple_users(ids: list) -> list:
    tasks = [query_user(id) for id in ids]
    results = await asyncio.gather(*tasks)
    return results

# 限制并发数
semaphore = asyncio.Semaphore(5)  # 最多5个并发
```

---

## 📈 性能指标

### 测试结果

**单次查询性能：**
```
工具: getPersonById
参数: {id: 5}
响应时间: 35ms ✅
成功率: 100% ✅
```

**批量查询性能：**
```
工具: getAllPersons
结果数量: 13
响应时间: 42ms ✅
成功率: 100% ✅
```

**错误场景处理：**
```
场景: 查询不存在的用户 (id=99999)
响应时间: 38ms ✅
返回: {"found": false, "message": "..."}
成功率: 100% ✅
```

### 性能优化建议

1. **启用 HTTP/2**
   - 减少连接开销
   - 支持多路复用

2. **使用连接池**
   - 复用 TCP 连接
   - 减少握手时间

3. **启用响应压缩**
   - gzip 压缩响应体
   - 减少网络传输

4. **数据库优化**
   - 添加索引（id, firstName, lastName）
   - 使用查询缓存

---

## 🔒 安全建议

### 1. 认证授权

```yaml
# MCP Router 配置
security:
  enabled: true
  auth-type: API_KEY
  
# Dify HTTP 请求配置
headers:
  X-API-Key: ${env.MCP_API_KEY}
```

### 2. 速率限制

```yaml
rate-limit:
  enabled: true
  requests-per-second: 10
  burst: 20
  scope: per-client  # 按客户端限流
```

### 3. 数据脱敏

```python
def mask_sensitive_data(person: dict) -> dict:
    """脱敏敏感信息"""
    if 'ssn' in person:
        person['ssn'] = '***-**-' + person['ssn'][-4:]
    if 'phone' in person:
        person['phone'] = person['phone'][:3] + '****' + person['phone'][-4:]
    return person
```

### 4. 输入校验

```python
def validate_user_input(text: str) -> bool:
    """校验用户输入，防止注入攻击"""
    # 检查 SQL 注入
    dangerous_patterns = ['DROP', 'DELETE', 'UPDATE', '--', ';']
    for pattern in dangerous_patterns:
        if pattern in text.upper():
            return False
    
    # 检查长度
    if len(text) > 1000:
        return False
    
    return True
```

---

## 📦 交付物清单

### ✅ 已完成

1. **设计文档**
   - [x] `DIFY_INTEGRATION_DESIGN.md` - 完整设计方案（36页）
   - [x] `DIFY_QUICK_START.md` - 快速开始指南
   - [x] `DIFY_INTEGRATION_SUMMARY.md` - 总结文档

2. **配置文件**
   - [x] `mcp-router-openapi.yaml` - OpenAPI 3.0 规范
   - [x] `dify-workflow-example.json` - Dify 工作流配置

3. **测试脚本**
   - [x] `test-dify-integration.sh` - 自动化测试脚本
   - [x] 6个测试用例，全部通过 ✅

4. **接口验证**
   - [x] tools/list 接口 - 正常工作 ✅
   - [x] tools/call 接口 - 正常工作 ✅
   - [x] 错误场景处理 - 正常工作 ✅

### 📋 使用步骤

```bash
# 1. 运行测试验证
./docs/test-dify-integration.sh

# 2. 查看快速开始指南
cat docs/DIFY_QUICK_START.md

# 3. 导入 OpenAPI 到 Dify
# 文件: docs/mcp-router-openapi.yaml

# 4. 创建 Dify Agent/Workflow
# 参考: docs/dify-workflow-example.json

# 5. 测试对话
# 输入: "查询用户 id=5"
```

---

## 🎉 示例对话

### 场景1：查询用户

```
👤 用户: 查询用户 id=5

🤖 AI: 正在查询...

[调用 MCP Router]
POST /mcp/router/route/mcp-server-v6
{
  "id": "uuid",
  "method": "tools/call",
  "params": {
    "name": "getPersonById",
    "arguments": {"id": 5}
  }
}

🤖 AI: 找到了用户信息！

**用户ID:** 5  
**姓名:** Pierre Dubois  
**年龄:** 40岁  
**国籍:** 法国 🇫🇷  
**性别:** 男性

查询耗时：35ms
```

### 场景2：查看所有用户

```
👤 用户: 查看所有用户

🤖 AI: 正在查询所有用户...

[调用 MCP Router]
...

🤖 AI: 共找到 13 个用户：

| ID | 姓名 | 年龄 | 国籍 | 性别 |
|:--:|:----:|:----:|:----:|:----:|
| 1 | John Smith | 28 | 美国 | 男 |
| 2 | Jane Doe | 32 | 英国 | 女 |
| 3 | Hans Mueller | 45 | 德国 | 男 |
| 4 | Maria Garcia | 29 | 西班牙 | 女 |
| 5 | Pierre Dubois | 40 | 法国 | 男 |
| ... | ... | ... | ... | ... |

查询耗时：42ms
```

### 场景3：添加用户

```
👤 用户: 添加新用户，名字叫张三，25岁，中国人，男性

🤖 AI: 正在添加用户...

[调用 MCP Router]
POST /mcp/router/route/mcp-server-v6
{
  "method": "tools/call",
  "params": {
    "name": "addPerson",
    "arguments": {
      "firstName": "张",
      "lastName": "三",
      "age": 25,
      "nationality": "Chinese",
      "gender": "MALE"
    }
  }
}

🤖 AI: ✅ 成功添加用户！

**用户ID:** 14 (新)  
**姓名:** 张三  
**年龄:** 25岁  
**国籍:** 中国 🇨🇳  
**性别:** 男性

用户已保存到数据库。
```

### 场景4：错误处理

```
👤 用户: 查询用户 id=99999

🤖 AI: 正在查询...

[调用 MCP Router]
...

🤖 AI: ❌ 未找到该用户。

原因：数据库中不存在 ID 为 99999 的用户。

💡 建议：
- 检查用户ID是否正确
- 使用"查看所有用户"查看可用的用户列表
```

---

## 📊 架构图

```
┌──────────────────────────────────────────────────────────────┐
│                         Dify AI Platform                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Agent / Workflow                                     │   │
│  │  - 理解用户意图                                       │   │
│  │  - 提取参数                                           │   │
│  │  - 调用 MCP 工具                                      │   │
│  │  - 格式化响应                                         │   │
│  └─────────────────────┬────────────────────────────────┘   │
└────────────────────────┼─────────────────────────────────────┘
                         │ HTTP POST
                         │ JSON-RPC 2.0
                         v
┌──────────────────────────────────────────────────────────────┐
│                      MCP Router V3                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  核心功能                                             │   │
│  │  - 智能路由                                           │   │
│  │  - 负载均衡                                           │   │
│  │  - 健康检查                                           │   │
│  │  - 熔断降级                                           │   │
│  │  - 日志记录                                           │   │
│  └─────────────────────┬────────────────────────────────┘   │
└────────────────────────┼─────────────────────────────────────┘
                         │ MCP SSE
                         │ Protocol
                         v
┌──────────────────────────────────────────────────────────────┐
│                      MCP Server V6                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  工具实现                                             │   │
│  │  - getPersonById                                      │   │
│  │  - getAllPersons                                      │   │
│  │  - addPerson                                          │   │
│  │  - ...                                                │   │
│  └─────────────────────┬────────────────────────────────┘   │
└────────────────────────┼─────────────────────────────────────┘
                         │ JDBC
                         v
┌──────────────────────────────────────────────────────────────┐
│                      MySQL Database                           │
│  - persons 表                                                 │
│  - 13 条测试数据                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 🔗 相关链接

- **设计文档：** [DIFY_INTEGRATION_DESIGN.md](./DIFY_INTEGRATION_DESIGN.md)
- **快速开始：** [DIFY_QUICK_START.md](./DIFY_QUICK_START.md)
- **OpenAPI 规范：** [mcp-router-openapi.yaml](./mcp-router-openapi.yaml)
- **工作流配置：** [dify-workflow-example.json](./dify-workflow-example.json)
- **测试脚本：** [test-dify-integration.sh](./test-dify-integration.sh)

---

## 📞 技术支持

如有问题，请：
1. 查阅完整设计文档
2. 运行测试脚本验证环境
3. 查看 MCP Router 日志
4. 联系技术团队

---

**文档版本：** v1.0.0  
**更新日期：** 2025-10-31  
**作者：** MCP Router Team

