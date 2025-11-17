# SSE 会话复用测试

## 概述

此测试用于验证 SSE 连接创建后，使用同一个 `sessionId` 发送多个请求的功能。

## 测试流程

1. **创建 SSE 连接**
   - 连接到 `/sse/{serviceName}` 端点
   - 从响应中提取 `sessionId`

2. **使用同一个 sessionId 发送多个请求**
   - 通过 `/mcp/message?sessionId={sessionId}` 端点发送请求
   - 验证系统能够根据 `sessionId` 自动查找对应的 `serviceName` 并路由请求

3. **测试用例**
   - `tools/list` - 获取工具列表
   - `resources/list` - 获取资源列表
   - `prompts/list` - 获取提示词列表
   - `resources/templates/list` - 获取模板列表
   - 再次发送 `tools/list` - 验证会话复用

## 使用方法

### Python 脚本（推荐）

```bash
# 方式1: 使用虚拟环境（推荐）
python3 -m venv test-env
source test-env/bin/activate  # Windows: test-env\Scripts\activate
pip install -r requirements-test.txt
python3 test_session_reuse.py
deactivate

# 方式2: 直接安装（如果系统允许）
pip install -r requirements-test.txt
# 或者直接安装
pip install requests

# 运行测试
python3 test_session_reuse.py
```

### Bash 脚本

```bash
# 运行测试
./test-session-reuse.sh
```

## 配置

在脚本中修改以下配置：

```python
BASE_URL = "http://localhost:8052"  # MCP Router 服务地址
SERVICE_NAME = "mcp-server-v6"       # 要测试的服务名称
```

## 预期结果

所有测试应该通过，并且：

1. ✅ SSE 连接成功创建
2. ✅ 能够从 SSE 响应中提取 `sessionId`
3. ✅ 所有请求都能成功发送
4. ✅ 所有请求都路由到同一个后端服务（通过 `sessionId` 查找）

## 工作原理

1. **SSE 连接建立时**：
   - 系统生成一个唯一的 `sessionId`
   - 将 `sessionId` 和 `serviceName` 的关联关系注册到 `McpSessionService`
   - 返回包含 `sessionId` 的 endpoint URL

2. **发送消息请求时**：
   - 从查询参数中提取 `sessionId`
   - 如果查询参数中没有 `serviceName`，系统会从会话注册表中查找
   - 使用找到的 `serviceName` 路由请求到对应的后端服务

## 验证点

- ✅ `sessionId` 能够正确提取
- ✅ 会话注册关系正确建立
- ✅ 后续请求能够通过 `sessionId` 找到对应的 `serviceName`
- ✅ 所有请求都路由到同一个后端服务
- ✅ 多次请求使用同一个会话，保持一致性
