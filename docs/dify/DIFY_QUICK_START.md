# Dify 快速接入指南

## 🚀 5分钟快速开始

本指南帮助您快速将 MCP Router V3 接入 Dify AI 平台，实现用户信息查询功能。

---

## 📋 前置条件

- ✅ MCP Router V3 已启动（端口 8052）
- ✅ MCP Server V6 已注册并运行
- ✅ Dify 平台已部署（本地或云端）
- ✅ 测试脚本已验证通过

```bash
# 验证服务运行状态
./docs/test-dify-integration.sh
```

---

## 🎯 方案选择

### 方案A：简单 HTTP 工作流（推荐新手）

**优点：** 简单直接，无需额外配置
**缺点：** 需要手动编写提示词解析逻辑

### 方案B：OpenAPI 工具集成（推荐生产）

**优点：** 自动工具发现，支持多服务器
**缺点：** 初始配置稍复杂

---

## 📝 方案A：HTTP 工作流配置

### 步骤1：创建 Dify Workflow

1. 登录 Dify 控制台
2. 点击"创建应用" → 选择"工作流"
3. 命名为"MCP用户查询助手"

### 步骤2：配置节点

#### 节点1：开始节点
```yaml
变量名: user_input
类型: 文本
必填: 是
描述: 用户的查询请求
示例: "查询用户 id=5"
```

#### 节点2：LLM 参数提取器
```yaml
节点类型: LLM
模型: gpt-4o-mini（或其他模型）
温度: 0.1

系统提示词:
---
你是一个参数提取助手。从用户输入中提取工具调用信息。

可用工具：
1. getPersonById - 根据ID查询用户，参数: {"id": 整数}
2. getAllPersons - 获取所有用户，参数: {}
3. addPerson - 添加新用户，参数: {"firstName":"","lastName":"","age":0,"nationality":"","gender":""}

请以JSON格式返回（MUST是有效的JSON）：
{
  "toolName": "工具名称",
  "arguments": {}
}

示例：
输入: "查询用户 id=5"
输出: {"toolName":"getPersonById","arguments":{"id":5}}

输入: "查看所有用户"
输出: {"toolName":"getAllPersons","arguments":{}}

如果无法识别，返回: {"toolName":"unknown","arguments":{},"error":"无法理解"}
---

用户提示词:
---
{{start.user_input}}
---
```

#### 节点3：代码节点（构造请求）
```python
import json
import uuid

def main(extraction_result: str) -> dict:
    """构造 MCP 调用请求"""
    try:
        extracted = json.loads(extraction_result)
        
        if extracted.get('toolName') == 'unknown':
            return {
                'success': False,
                'error_message': extracted.get('error', '无法理解用户意图')
            }
        
        mcp_request = {
            'id': str(uuid.uuid4()),
            'method': 'tools/call',
            'params': {
                'name': extracted['toolName'],
                'arguments': extracted.get('arguments', {})
            }
        }
        
        return {
            'success': True,
            'request_body': json.dumps(mcp_request),
            'tool_name': extracted['toolName']
        }
    except Exception as e:
        return {
            'success': False,
            'error_message': f'解析失败: {str(e)}'
        }
```

**输入变量：**
- `extraction_result`: 来自 LLM 节点的输出

#### 节点4：条件判断
```yaml
条件: {{code.success}} == True
True分支: HTTP请求节点
False分支: 错误提示节点
```

#### 节点5：HTTP 请求节点
```yaml
请求方法: POST
URL: http://localhost:8052/mcp/router/route/mcp-server-v6
请求头:
  Content-Type: application/json
请求体: {{code.request_body}}
超时: 30秒
```

**注意：** 生产环境请替换为实际的 MCP Router 地址。

#### 节点6：结果格式化（LLM）
```yaml
节点类型: LLM
模型: gpt-4o-mini
温度: 0.7

系统提示词:
---
你是一个友好的助手。请根据查询结果，用自然语言回复用户。

工具名称: {{code.tool_name}}
原始结果: {{http.body}}

要求：
1. 用简洁、友好的方式展示
2. 突出重要信息
3. 如果是列表，用表格或项目符号展示
4. 如果查询失败，友好提示用户

请生成回复。
---
```

#### 节点7：结束节点
```yaml
输出: {{llm_format.output}}
```

### 步骤3：测试工作流

测试输入：
```
查询用户 id=5
```

预期输出：
```
找到了用户信息！

用户ID: 5
姓名: Pierre Dubois
年龄: 40岁
国籍: 法国
性别: 男性
```

---

## 🔧 方案B：OpenAPI 工具集成

### 步骤1：导入 OpenAPI 规范

1. 打开 Dify 控制台
2. 导航到"工具" → "自定义工具" → "导入 OpenAPI"
3. 上传文件：`docs/mcp-router-openapi.yaml`
4. 工具名称：`MCP Router V3`
5. 点击"导入"

### 步骤2：创建 Agent 应用

1. 创建新应用 → 选择"Agent"
2. 命名为"MCP用户助手"
3. 选择模型：gpt-4o 或 gpt-4o-mini

### 步骤3：配置 Agent

**系统提示词：**
```
你是一个用户信息管理助手。你可以帮助用户查询、添加和管理用户信息。

你拥有以下能力：
1. 根据ID查询用户信息
2. 查看所有用户列表
3. 添加新用户

当用户询问时，请使用可用的工具来完成任务。

示例对话：
用户："查询用户 id=5"
你：[调用 callMcpTool 工具]
你：找到了用户 Pierre Dubois，40岁，法国人。

用户："查看所有用户"
你：[调用 callMcpTool 工具]
你：共找到13个用户，这是前5个：...

用户："添加新用户，叫张三，25岁，中国人，男性"
你：[调用 callMcpTool 工具]
你：成功添加用户张三！

请根据用户意图，正确选择工具和参数。工具调用格式：
- 工具名: callMcpTool
- serverKey: mcp-server-v6
- body: JSON-RPC 2.0 格式请求
```

**绑定工具：**
- 勾选 `MCP Router V3` 工具
- 配置默认参数：
  - `serverKey`: `mcp-server-v6`

### 步骤4：测试 Agent

测试对话：

**测试1：查询用户**
```
用户: 查询用户 id=5
AI: [自动调用工具] 找到了用户 Pierre Dubois...
```

**测试2：查看所有用户**
```
用户: 查看所有用户
AI: [自动调用工具] 共找到13个用户...
```

**测试3：添加用户**
```
用户: 添加新用户，名字叫李四，30岁，中国人，男性
AI: [自动调用工具] 成功添加用户李四！
```

---

## 🎨 进阶配置

### 1. 多轮对话支持

在 Agent 配置中启用"对话记忆"：
```yaml
记忆类型: 完整对话
最大轮数: 10
```

示例：
```
用户: 查询用户 id=5
AI: 找到了 Pierre Dubois，40岁。

用户: 他多大了？
AI: Pierre Dubois 今年40岁。

用户: 再查一下 id=3
AI: [查询 id=3]
```

### 2. 错误处理优化

在系统提示词中添加：
```
错误处理规则：
1. 如果工具返回 error 字段，友好地说明原因
2. 如果用户不存在，提示"未找到该用户（ID: X）"
3. 如果参数错误，提示正确的格式
4. 如果服务不可用，提示"服务暂时不可用，请稍后重试"
```

### 3. 批量查询

```
用户: 查询用户 id=1,2,3,4,5
AI: [并发调用5次] 
    共查询5个用户：
    1. John Smith, 28岁, 美国人
    2. Jane Doe, 32岁, 英国人
    ...
```

实现方式：在代码节点中循环调用 HTTP 请求。

### 4. 数据可视化

使用 Markdown 表格格式化输出：

```python
def format_users_table(users: list) -> str:
    table = "| ID | 姓名 | 年龄 | 国籍 | 性别 |\n"
    table += "|:--:|:----:|:----:|:----:|:----:|\n"
    for user in users:
        name = f"{user['firstName']} {user['lastName']}"
        table += f"| {user['id']} | {name} | {user['age']} | {user['nationality']} | {user['gender']} |\n"
    return table
```

---

## 🔐 生产环境部署

### 1. 环境变量配置

在 Dify 中配置环境变量：
```bash
MCP_ROUTER_URL=https://mcp-router.your-domain.com
MCP_SERVER_KEY=mcp-server-v6
API_KEY=your-api-key-here
```

### 2. 添加认证

在 HTTP 请求节点中添加：
```yaml
请求头:
  Content-Type: application/json
  X-API-Key: {{env.API_KEY}}
```

### 3. 速率限制

在 MCP Router 中配置限流：
```yaml
rate-limit:
  enabled: true
  requests-per-second: 10
  burst: 20
```

### 4. 监控告警

- 配置 Prometheus 监控
- 设置告警规则（响应时间 >2秒）
- 收集错误日志

---

## 📊 效果展示

### 查询用户信息
```
👤 用户: 查询用户 id=5

🤖 助手: 找到了用户信息！

**用户ID:** 5  
**姓名:** Pierre Dubois  
**年龄:** 40岁  
**国籍:** 法国 🇫🇷  
**性别:** 男性
```

### 查看所有用户
```
👤 用户: 查看所有用户

🤖 助手: 共找到 13 个用户，以下是详细列表：

| ID | 姓名 | 年龄 | 国籍 | 性别 |
|:--:|:----:|:----:|:----:|:----:|
| 1 | John Smith | 28 | 美国 | 男 |
| 2 | Jane Doe | 32 | 英国 | 女 |
| 3 | Hans Mueller | 45 | 德国 | 男 |
| ... | ... | ... | ... | ... |
```

---

## ❓ 常见问题

### Q1: 工具调用失败怎么办？

**检查清单：**
1. MCP Router 是否运行？`curl http://localhost:8052/actuator/health`
2. MCP Server 是否注册？查看日志
3. 网络是否可达？检查防火墙
4. 参数格式是否正确？查看日志

### Q2: 如何支持多个 MCP Server？

修改系统提示词，添加服务器选择逻辑：
```
可用服务器：
- mcp-server-v6: 用户管理
- mcp-server-v7: 订单管理

根据用户意图，选择正确的 serverKey。
```

### Q3: 响应太慢怎么办？

优化方案：
1. 启用 Redis 缓存（getAllPersons 结果）
2. 使用 MCP Router 的智能路由
3. 优化数据库查询
4. 启用 HTTP/2

### Q4: 如何处理大量数据？

实现分页：
```json
{
  "name": "getAllPersons",
  "arguments": {
    "page": 1,
    "pageSize": 10
  }
}
```

---

## 🚀 下一步

1. ✅ 完成基础配置
2. ✅ 测试基本功能
3. ⬜ 添加更多工具（订单、产品等）
4. ⬜ 实现多模态交互（语音、图片）
5. ⬜ 集成到企业微信/钉钉

---

## 📚 相关文档

- [完整设计方案](./DIFY_INTEGRATION_DESIGN.md)
- [OpenAPI 规范](./mcp-router-openapi.yaml)
- [工作流示例](./dify-workflow-example.json)
- [测试脚本](./test-dify-integration.sh)
- [MCP 协议规范](https://spec.modelcontextprotocol.io/)

---

## 💡 技巧提示

### 提示词优化

**好的提示词：**
```
查询用户 id=5
查看所有用户
添加用户张三，25岁，中国人，男性
```

**不推荐的提示词：**
```
帮我查一下那个用户（太模糊）
给我数据（不明确）
执行操作（不具体）
```

### 工具调用最佳实践

1. **参数校验**：在代码节点中验证参数
2. **错误重试**：配置重试机制（最多3次）
3. **超时设置**：30秒超时，避免长时间等待
4. **日志记录**：记录所有工具调用，便于调试

---

**祝您使用愉快！🎉**

如有问题，请查阅完整文档或联系技术支持。

