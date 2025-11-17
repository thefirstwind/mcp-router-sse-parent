# MCP Router V3 改造说明 - 按照 mcp-server-v6 的方式

## 📋 改造概述

将 **mcp-router-v3** 改造成使用与 **mcp-server-v6** 相同的标准 MCP SSE 协议，提供统一的接口体验。

---

## 🎯 改造目标

1. ✅ 使用标准的 MCP SSE 端点：`GET /sse` 和 `POST /mcp/message?sessionId=xxx`
2. ✅ 支持标准 JSON-RPC 2.0 协议
3. ✅ 保持现有的路由功能（服务发现、负载均衡等）
4. ✅ 兼容 mcp-server-v6 的客户端

---

## 🔧 改造内容

### 1. 新增配置类

**文件**: `src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

**功能**:
- 使用 `WebFluxSseServerTransportProvider` 提供标准的 SSE 端点
- 拦截 `/mcp/message` 请求并路由到后端服务器
- 将路由结果转换为标准 JSON-RPC 2.0 格式

**关键代码**:
```java
@Bean
public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
    WebFluxSseServerTransportProvider provider = new WebFluxSseServerTransportProvider(
            objectMapper,
            baseUrl,
            "/mcp/message",  // 与 mcp-server-v6 相同
            "/sse"          // 与 mcp-server-v6 相同
    );
    return provider;
}
```

### 2. 更新配置文件

**文件**: `src/main/resources/application.yml`

**变更**:
```yaml
spring:
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.0.1
        type: ASYNC
        instructions: "MCP Router provides intelligent routing to backend MCP servers"
        sse-message-endpoint: /mcp/message  # 与 mcp-server-v6 相同
        sse-endpoint: /sse                  # 与 mcp-server-v6 相同
        capabilities:
          tool: true
          resource: true
          prompt: true
          completion: true
```

---

## 📡 接口对比

### 改造前（mcp-router-v3 旧接口）

```
GET  /sse/connect?clientId=xxx
POST /sse/message/{sessionId}
```

### 改造后（与 mcp-server-v6 相同，但需要声明服务名称）

```
GET  /sse?serviceName=mcp-server-v6&sessionId=xxx    # 建立 SSE 连接（声明目标服务）
POST /mcp/message?sessionId=xxx                       # 发送 MCP 消息
```

---

## 🔄 消息处理流程

### 1. 客户端建立 SSE 连接（**必须声明服务名称**）

```http
GET /sse?serviceName=mcp-server-v6&sessionId=my-session HTTP/1.1
Host: localhost:8052
Accept: text/event-stream
```

**查询参数说明**:
- `serviceName` (必需): 要连接的后端 MCP 服务器名称，如 `mcp-server-v6`
- `sessionId` (可选): 会话ID，如果不提供则自动生成

**响应**:
```
data: {"type":"connection","status":"connected","sessionId":"my-session","serviceName":"mcp-server-v6","baseUrl":"http://localhost:8052","timestamp":1761877499188}
```

**注意**: 
- ⚠️ **必须在建立连接时声明 `serviceName`**，否则后续消息将使用智能路由（自动发现服务）
- 如果不指定 `serviceName`，系统会记录警告日志，但仍允许连接

### 2. 客户端发送 MCP 消息

```http
POST /mcp/message?sessionId=xxx HTTP/1.1
Host: localhost:8052
Content-Type: application/json
```

**请求体**:
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

### 3. 路由处理

1. **消息拦截**: `McpRouterServerConfig.handleMcpMessage()` 拦截请求
2. **服务名称获取**（优先级顺序）:
   - **优先**: 从会话中获取（在建立连接时声明的 `serviceName`）
   - **其次**: 从消息中提取（`metadata.targetService` 或 `targetService` 字段）
   - **最后**: 使用智能路由自动发现服务
3. **负载均衡**: 选择最优后端服务器
4. **路由转发**: 调用 `McpRouterService.routeRequest()` 或 `smartRoute()`
5. **响应转换**: 将路由结果转换为标准 JSON-RPC 2.0 格式

### 4. 响应格式

**成功响应**:
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"id\":5,\"firstName\":\"Pierre\",\"lastName\":\"Dubois\",...}"
      }
    ],
    "isError": false
  }
}
```

**错误响应**:
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "error": {
    "code": -32603,
    "message": "Internal server error"
  }
}
```

---

## 🎨 服务路由支持

### 方式1: 在建立连接时声明服务（**推荐**）

在建立 SSE 连接时通过查询参数声明目标服务：

```bash
GET /sse?serviceName=mcp-server-v6&sessionId=my-session
```

**优点**:
- ✅ 一次声明，整个会话有效
- ✅ 不需要在每个消息中重复指定
- ✅ 性能更好，减少路由查找开销

### 方式2: 在消息中指定服务名称

在 MCP 消息的 `metadata` 或 `targetService` 字段中指定服务名称：

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": "req-001",
  "params": {
    "name": "getPersonById",
    "arguments": {"id": 5}
  },
  "targetService": "mcp-server-v6"
}
```

**注意**: 如果连接时已声明服务名称，消息中的服务名称将被忽略（连接时的声明优先级更高）

### 方式3: 自动服务发现（智能路由）

如果既没有在连接时声明，也没有在消息中指定，将使用智能路由自动发现支持该工具的后端服务器：

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": "req-001",
  "params": {
    "name": "getPersonById",
    "arguments": {"id": 5}
  }
}
```

**优先级总结**:
1. 🥇 **连接时声明的服务名称**（最高优先级）
2. 🥈 **消息中的服务名称**（`targetService` 或 `metadata.targetService`）
3. 🥉 **智能路由**（自动发现）

---

## 🔍 关键实现细节

### 1. SSE 连接拦截

`McpRouterServerConfig` 创建了一个自定义的 SSE 连接处理，拦截 `/sse` 请求并提取服务名称：

```java
RouterFunction<ServerResponse> sseRouter = route()
    .GET("/sse", this::handleSseConnection)
    .build();
```

**功能**:
- 从查询参数 `serviceName` 中提取目标服务名称
- 生成或使用提供的 `sessionId`
- 将 `sessionId` 与服务名称关联存储到 `McpSessionService`
- 返回连接确认消息和心跳

### 2. 消息拦截

`McpRouterServerConfig` 创建了一个自定义的路由函数，拦截 `/mcp/message` 请求：

```java
RouterFunction<ServerResponse> messageRouter = route()
    .POST("/mcp/message", this::handleMcpMessage)
    .build();
```

**功能**:
- 优先从会话中获取服务名称（连接时声明）
- 如果会话中没有，从消息中提取
- 如果都没有，使用智能路由

### 3. 会话服务管理

`McpSessionService` 负责管理 `sessionId` 与服务名称的关联：

```java
@Service
public class McpSessionService {
    private final Map<String, String> sessionServiceMap = new ConcurrentHashMap<>();
    
    public void registerSessionService(String sessionId, String serviceName);
    public String getServiceName(String sessionId);
    public void removeSession(String sessionId);
}
```

**功能**:
- 在建立连接时注册服务名称
- 在消息处理时查询服务名称
- 在连接断开时清理会话

### 4. 响应格式转换

将 `McpMessage` 转换为标准 JSON-RPC 2.0 格式，特别是将 `result` 对象包装成 MCP 标准的 `content` 数组格式：

```java
Map<String, Object> mcpResult = new HashMap<>();
List<Map<String, Object>> content = new ArrayList<>();
Map<String, Object> contentItem = new HashMap<>();
contentItem.put("type", "text");
contentItem.put("text", objectMapper.writeValueAsString(result));
content.add(contentItem);
mcpResult.put("content", content);
mcpResult.put("isError", false);
```

### 5. 服务名称提取（优先级顺序）

1. **会话中的服务名称**（最高优先级）- 从 `McpSessionService` 获取
2. **消息中的服务名称** - 从 `metadata.targetService` 或 `targetService` 字段提取
3. **智能路由** - 如果都没有，使用自动服务发现

---

## 🧪 测试方法

### 1. 启动服务

```bash
cd mcp-router-v3
mvn spring-boot:run
```

### 2. 建立 SSE 连接（**必须声明服务名称**）

```bash
# 方式1: 指定服务名称和会话ID
curl -N "http://localhost:8052/sse?serviceName=mcp-server-v6&sessionId=test-session"

# 方式2: 只指定服务名称（自动生成会话ID）
curl -N "http://localhost:8052/sse?serviceName=mcp-server-v6"
```

**预期响应**:
```
data: {"type":"connection","status":"connected","sessionId":"test-session","serviceName":"mcp-server-v6","baseUrl":"http://localhost:8052","timestamp":1761877499188}
data: {"type":"heartbeat","timestamp":1761877529188}
data: {"type":"heartbeat","timestamp":1761877559188}
...
```

### 3. 发送工具调用请求

```bash
curl -X POST "http://localhost:8052/mcp/message?sessionId=test-session" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "id": "req-001",
    "params": {
      "name": "getPersonById",
      "arguments": {
        "id": 5
      }
    }
  }'
```

**注意**: 
- 使用步骤2中建立的 `sessionId`
- 消息会自动路由到连接时声明的服务（`mcp-server-v6`）
- 不需要在消息中再次指定服务名称

**预期响应**:
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"id\":5,\"firstName\":\"Pierre\",\"lastName\":\"Dubois\",...}"
      }
    ],
    "isError": false
  }
}
```

---

## 📊 兼容性说明

### 与 mcp-server-v6 的兼容性

✅ **完全兼容**: 
- 相同的 SSE 端点：`GET /sse`
- 相同的消息端点：`POST /mcp/message?sessionId=xxx`
- 相同的 JSON-RPC 2.0 协议格式
- 相同的响应格式（`result.content` 数组）

### 与旧接口的兼容性

⚠️ **不兼容**: 
- 旧的 `/sse/connect` 端点已移除
- 旧的 `/sse/message/{sessionId}` 端点已移除

**迁移建议**:
- 更新客户端代码，使用新的标准端点
- 或保留旧的 `McpSseController` 作为兼容层（可选）

---

## 🔄 保留的功能

✅ **完全保留**:
- 服务发现（Nacos）
- 智能负载均衡
- 健康检查
- 路由日志
- 性能监控
- 故障转移

✅ **增强功能**:
- 标准 MCP 协议支持
- 与 mcp-server-v6 完全兼容
- 更好的客户端兼容性

---

## 📝 后续优化建议

1. **工具列表聚合**: 实现 `tools/list` 方法，聚合所有后端服务器的工具列表
2. **资源支持**: 实现 `resources/list` 和 `resources/read` 方法
3. **提示支持**: 实现 `prompts/list` 和 `prompts/get` 方法
4. **兼容层**: 可选保留旧的 SSE 接口作为兼容层

---

## 🎯 总结

通过这次改造，**mcp-router-v3** 现在：

1. ✅ 使用与 **mcp-server-v6** 相同的标准 MCP SSE 协议
2. ✅ 提供统一的接口体验
3. ✅ 完全兼容 MCP 客户端
4. ✅ 保留所有路由功能（服务发现、负载均衡等）
5. ✅ 支持智能路由和指定服务路由两种模式

**客户端现在可以使用相同的代码连接 mcp-router-v3 和 mcp-server-v6！** 🎉

---

**文档版本**: v1.0.0  
**更新日期**: 2025-11-09  
**作者**: MCP Team
