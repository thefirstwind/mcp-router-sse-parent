# MCP 标准协议测试用例

## 概述

本文档描述了为 mcp-server-v3 和 mcp-server-v6 创建的标准 MCP 协议测试用例，专门测试 `PersonManagementTool` 的 `getAllPersons` 和 `getPersonById` 方法。

## 测试背景

### 关键发现
- **mcp-server-v3** 和 **mcp-server-v6** 使用标准 MCP 协议，而不是简单的 RPC 协议
- 通过 `WebFluxSseServerTransportProvider` 实现 SSE (Server-Sent Events) 传输
- 使用 `io.modelcontextprotocol` 库进行标准 MCP 通信

### 协议架构
```
MCP Client (测试) <--SSE/HTTP--> MCP Server (v3/v6)
                                      ↓
                              PersonManagementTool
                                  (@Tool methods)
```

## 测试文件

### 1. mcp-server-v6 测试
**文件**: `mcp-server-v6/src/test/java/com/nacos/mcp/server/v6/integration/McpStandardProtocolTest.java`

### 2. mcp-server-v3 测试  
**文件**: `mcp-server-v3/src/test/java/com/nacos/mcp/server/v3/integration/McpStandardProtocolTest.java`

## 测试用例详细说明

### 核心测试方法

#### 1. `testMcpInitialize()`
- **目的**: 验证 MCP 连接和初始化
- **验证点**: 
  - MCP 客户端成功创建
  - 能够列出服务器工具
  - 确认包含 `getAllPersons` 和 `getPersonById` 工具

#### 2. `testGetAllPersons_StandardMcpProtocol()`
- **目的**: 测试获取所有人员列表
- **MCP 调用**: 
  ```java
  McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
      "getAllPersons", 
      Map.of() // 无参数
  );
  ```
- **验证点**:
  - 调用成功，无错误
  - 返回文本内容
  - 包含测试数据中的人员姓名 (John, Jane)

#### 3. `testGetPersonById_ValidId_StandardMcpProtocol()`
- **目的**: 测试通过有效 ID 获取人员信息
- **MCP 调用**:
  ```java
  McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
      "getPersonById",
      Map.of("id", 1L)
  );
  ```
- **验证点**:
  - 成功获取 ID=1 的人员 (John Doe)
  - 返回内容包含姓名和找到标志

#### 4. `testGetPersonById_InvalidId_StandardMcpProtocol()`
- **目的**: 测试不存在的 ID
- **MCP 调用**: ID=999 (不存在)
- **验证点**:
  - 调用成功但返回"未找到"消息
  - 包含 "not found" 或 "false" 标志

#### 5. `testGetPersonById_MultipleValidIds_StandardMcpProtocol()`
- **目的**: 批量测试多个有效 ID
- **测试数据**: 
  - ID 1 → John Doe
  - ID 2 → Jane Smith  
  - ID 3 → Hans Mueller
- **验证点**: 每个 ID 返回对应的人员信息

#### 6. `testListTools_ContainsPersonManagementTools()`
- **目的**: 验证服务器暴露的工具列表
- **期望工具**: 
  - `getAllPersons`
  - `getPersonById` 
  - `addPerson`
  - `get_system_info`
  - `list_servers`

#### 7. `testAddPerson_StandardMcpProtocol()`
- **目的**: 测试添加新人员
- **MCP 调用**:
  ```java
  Map.of(
      "firstName", "Test",
      "lastName", "User", 
      "age", 25,
      "nationality", "TestLand",
      "gender", "MALE"
  )
  ```

#### 8. `testInvalidToolCall_StandardMcpProtocol()`
- **目的**: 测试调用不存在的工具
- **期望**: 抛出异常或返回错误

### 额外测试 (仅 mcp-server-v3)

#### 9. `testGetSystemInfo_StandardMcpProtocol()`
- **目的**: 测试系统信息工具
- **验证**: 返回系统相关信息

#### 10. `testGetPersonById_EmptyParameters_StandardMcpProtocol()`
- **目的**: 测试缺少必需参数的情况
- **验证**: 适当的错误处理

## 测试数据

### PersonManagementTool 模拟数据
```java
Person 1: John Doe, 30, American, MALE
Person 2: Jane Smith, 25, British, FEMALE  
Person 3: Hans Mueller, 35, German, MALE
Person 4: Maria Schmidt, 28, German, FEMALE
Person 5: Pierre Dubois, 40, French, MALE
// ... 更多测试数据
```

## 技术实现细节

### MCP 客户端设置
```java
@BeforeEach
void setUp() throws Exception {
    String serverUrl = "http://localhost:" + port;
    transport = HttpClientSseClientTransport.builder(serverUrl).build();
    mcpClient = McpClient.sync(transport).build();
    mcpClient.initialize();
    Thread.sleep(1000); // 等待连接稳定
}
```

### 标准 MCP 协议流程
1. **建立 SSE 连接**: 客户端连接到 `/sse` 端点
2. **初始化握手**: 发送 `initialize` 消息
3. **工具发现**: 调用 `tools/list` 获取可用工具
4. **工具调用**: 发送 `tools/call` 消息执行具体工具
5. **响应处理**: 接收和解析 MCP 标准响应格式

### 响应格式
```java
McpSchema.CallToolResult result = mcpClient.callTool(request);
// result.isError() - 是否有错误
// result.content() - 返回内容列表
// result.content().get(0) - 第一个内容项
// ((McpSchema.TextContent)content).text() - 文本内容
```

## 运行要求

### 前置条件
1. **Nacos 服务器**: 运行在 `127.0.0.1:8848`
2. **依赖库**: `io.modelcontextprotocol.client`
3. **Spring Boot**: 测试环境自动启动服务器

### 配置属性
```properties
spring.ai.alibaba.mcp.nacos.server-addr=127.0.0.1:8848
spring.ai.alibaba.mcp.nacos.namespace=public
spring.ai.alibaba.mcp.nacos.username=nacos
spring.ai.alibaba.mcp.nacos.password=nacos
spring.ai.alibaba.mcp.nacos.registry.enabled=true
```

## 验证重点

### 成功验证
- ✅ MCP 连接建立成功
- ✅ 工具列表正确暴露
- ✅ `getAllPersons` 返回完整人员列表
- ✅ `getPersonById` 正确处理有效和无效 ID
- ✅ 标准 MCP 协议格式正确

### 错误处理验证
- ✅ 无效工具调用的错误处理
- ✅ 缺少参数的错误处理  
- ✅ 不存在 ID 的友好错误消息

## 注意事项

1. **协议理解**: 这些测试基于对标准 MCP 协议的正确理解
2. **SSE 传输**: 使用 Server-Sent Events 而不是简单的 HTTP POST
3. **异步特性**: 虽然使用同步客户端，但底层仍是异步通信
4. **连接管理**: 测试后正确关闭 SSE 连接
5. **稳定性**: 包含连接等待时间确保测试稳定性

## 与之前测试的区别

### 错误的方法 (之前)
- ❌ 直接 HTTP POST 到 `/mcp/message`
- ❌ 假设简单的 RPC 协议
- ❌ 忽略 MCP 握手过程

### 正确的方法 (现在) 
- ✅ 使用标准 MCP 客户端库
- ✅ 正确的 SSE 连接建立
- ✅ 遵循 MCP 协议规范
- ✅ 适当的初始化和清理

这些测试用例正确地验证了 mcp-server-v3 和 mcp-server-v6 的标准 MCP 协议实现，确保 PersonManagementTool 的功能能够通过标准 MCP 客户端正确访问。 