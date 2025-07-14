# MCP 与 Nacos 交互分析

本文档分析 `spring-ai-alibaba-mcp` 模块如何与 Nacos 进行交互，包括服务注册和配置监听的关键逻辑。

## 1. 核心交互模块

`spring-ai-alibaba-mcp` 的核心 Nacos 交互逻辑位于 `spring-ai-alibaba-mcp-nacos` 子模块中。

关键类是 `com.alibaba.cloud.ai.mcp.nacos.registry.NacosMcpRegister`。

## 2. 服务注册

服务注册是指 MCP 服务实例启动后，将其自身的信息发布到 Nacos 中，以便其他服务（如网关或其他 MCP 客户端）能够发现和调用它。

### 2.1 注册时机

- 注册过程由 Spring 的 `WebServerInitializedEvent` 事件触发。这意味着当内置的 Web 服务器（如 Tomcat 或 Netty）成功启动后，注册逻辑开始执行。

### 2.2 注册到 Nacos 的信息

`NacosMcpRegister` 类负责收集并注册以下两类核心信息到 Nacos：

#### a) MCP 服务基本信息 (`McpServerBasicInfo`)

这部分定义了服务实例的基础属性：

- **服务名 (Name)**: MCP 服务的唯一标识符。
- **版本 (Version)**: 服务的版本号，用于版本控制和灰度发布。
- **协议 (Protocol)**: 通信协议，通常是 `sse` (Server-Sent Events)，表示该服务通过 SSE 协议提供能力。
- **端点引用 (Endpoint Reference)**: 如果协议是 `sse`，则会注册一个 Nacos 服务实例。其他服务可以通过这个实例的 `serviceName` 和 `groupName` 来发现该 MCP 服务。
- **远程配置 (Remote Server Config)**: 包含 SSE 的具体暴露路径，例如 `/mcp/v1/sse`，客户端会访问这个路径来建立连接。

#### b) MCP 工具信息 (`McpToolSpecification`)

这部分是 MCP 的核心，定义了 AI Agent 可以调用的具体能力（Tools/Functions）：

- **工具列表 (Tools)**: 注册该服务提供的所有工具。
- **工具定义 (Tool Definition)**: 每个工具都包含：
  - **名称 (name)**: 工具的唯一名称。
  - **描述 (description)**: 对工具功能的详细描述，这个描述通常会展示给大语言模型（LLM），帮助模型理解何时以及如何使用该工具。
  - **输入 Schema (inputSchema)**: 使用 JSON Schema 格式定义了调用该工具需要传入的参数结构。

### 2.3 注册流程

1.  应用启动，Web 服务器初始化完成。
2.  `NacosMcpRegister` 监听到 `WebServerInitializedEvent` 事件。
3.  它从 `McpAsyncServer` 中获取服务的基本信息和工具列表。
4.  将这些信息组装成 `McpServerBasicInfo` 和 `McpToolSpecification` 对象。
5.  调用 `NacosMcpOperationService.createMcpServer()` 方法，通过 Nacos 的 API 将这些信息注册到 Nacos 服务器。

## 3. 配置监听 (Listener)

除了服务注册，`spring-ai-alibaba-mcp` 还利用 Nacos 的动态配置能力来监听并热更新服务的元数据。

### 3.1 监听的启动

- 在服务注册成功后，`NacosMcpRegister` 会立即调用 `nacosMcpOperationService.subscribeNacosMcpServer()` 方法。
- 这个方法会向 Nacos **订阅** 当前服务版本（由 `serverName` 和 `version` 组合的唯一标识）的配置变更事件。

### 3.2 监听的内容

- 监听的核心目标是 **`McpTool` 的元数据变更**。
- 特别是，它允许在 **Nacos 控制台**中直接修改一个已注册工具的 `description` 或其他元数据。

### 3.3 监听后的动作（热更新）

1.  当运维人员或开发者在 Nacos 中修改了某个 Tool 的描述并发布后，Nacos 会将变更通知推送给所有订阅了该配置的 MCP 服务实例。
2.  `NacosMcpRegister` 中设置的回调函数会被触发。
3.  回调函数会执行 `updateTools()` 方法。
4.  该方法会用从 Nacos 收到的最新工具定义来**更新服务内存中**的 `McpTool` 实例。

### 3.4 监听的价值

- **动态优化 Prompt**: Tool 的 `description` 对 AI 的决策至关重要。通过监听，我们可以在不重启服务的情况下，动态调整和优化这些描述，从而引导 AI 更准确地使用工具。
- **运行时管理**: 实现了对 AI能力的运行时管理，提高了灵活性和运维效率。

## 4. 总结

`spring-ai-alibaba-mcp` 模块深度整合了 Nacos，实现了双重目的：

1.  **服务注册与发现**: 基于 Nacos 的标准服务注册机制，使得 MCP 服务可以被其他微服务（如网关）发现。
2.  **AI 能力动态管理**: 创见性地利用 Nacos 的配置推送能力，实现了一套对 AI Tools 元数据的**动态、实时热更新**机制，这是其与普通微服务注册最大的不同和亮点。 