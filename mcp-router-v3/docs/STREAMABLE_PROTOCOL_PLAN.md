# Streamable 协议支持规划

## 背景

当前 `mcp-router-v3` 对外仅支持 SSE (`/sse`) 协议。为兼容更多客户端和传输方案，需要引入 streamable 协议，并同时保持 SSE 可用。

## 目标

- 客户端可通过请求头 `X-MCP-Transport` 或 `?transport=` 指定传输协议。
- SSE 客户端继续使用 `/sse/{service}`。
- streamable 客户端将使用 `/mcp/{service}`（逐步上线）。
- Router → MCP Server 的传输层同样支持双协议，根据 Nacos 元数据动态选择。

## 分阶段计划

### 阶段 1：协议协商与骨架（进行中）
- [x] 引入 `TransportType` 枚举与 `TransportPreferenceResolver`。
- [x] SSE 路由根据请求偏好判断；若请求 streamable，则返回 501（提示准备中）。
- [x] `/mcp/**` 路由先行暴露占位符，便于 Ingress/客户端配置验证。

### 阶段 2：Server-Side Streamable Transport
- [ ] 实现 `WebFluxStreamableServerTransportProvider`，输出 chunked/流式响应。
- [ ] 完成会话管理、心跳、清理在 streamable 模式下的适配。

### 阶段 3：Client-Side Streamable Transport
- [ ] `McpClientManager` 新增 streamable client，支持 Router → MCP Server 的双协议。
- [ ] Nacos metadata 增加 `streamableEndpoint`、`protocol` 信息。

### 阶段 4：联合验证与文档
- [ ] 端到端联调（Router ↔ Server ↔ Client）。
- [ ] 更新 `MCP_SESSION_FLOW_INGRESS.md`、部署指南、诊断脚本。

## 当前状态

- `/sse/**` 仍保持原有行为。
- `/mcp/**` 已暴露 NDJSON 流式接口（Alpha 状态），与 SSE 共享同一 Session 与消息流，仅在输出格式上不同。
  - 响应示例：`{"event":"endpoint","data":"http://.../mcp/mcp-server/message?sessionId=xxx"}\n`
  - 心跳：`{"event":"heartbeat","data":"{\"type\":\"heartbeat\",\"timestamp\":...}"}\n`
  - `Content-Type: application/x-ndjson`
- Streamable 响应统一透传 `Mcp-Session-Id` / `Mcp-Transport` 头，消息端点也支持通过同名请求头携带 session，方便 MCP Inspector 等官方客户端接入。
- 所有请求会根据 header/query 自动解析传输偏好，若客户端在 `/sse` 路径声明 `streamable`，Router 会自动切换到 `/mcp` 行为。

## 下一步

- 完成 Transport Provider 抽象，编写 streamable server 模板。
- 为 MCP Server v6 定义/同步 streamable 端点和协议细节。
- 按阶段逐步替换占位实现，确保每一步都可独立验证回退。

