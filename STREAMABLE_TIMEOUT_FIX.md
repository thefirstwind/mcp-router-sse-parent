# Streamable / Tools Call Timeout Fix

## Issue Description
Users reported an intermittent error during `tools/call` in a production environment with Load Balancers and multiple servers:
`MCP error -1: Connection or request failed: Did not observe any item or terminal signal within 450ms in 'flatMap'`

## Root Cause Analysis
1.  **Aggressive Timeouts in McpClientManager**: The `McpClientManager` contained "aggressive optimization" logic with extremely short timeouts:
    *   `createNewConnection`: 300ms
    *   `initialize`: 200ms
    *   `listResources` / `listPrompts`: 500ms
    In a distributed environment (Client -> LB -> Router -> Server), network latency and handshake overhead often exceed these values, causing `TimeoutException`.
    
2.  **Specific "450ms" Error**: The 450ms error likely resulted from a combination of a 500ms timeout (intended for fast list operations) interacting with the Router's safety margin (0.9 factor), or simply the aggressive 300ms+200ms sequence failing.

3.  **Smart Route Clamping**: `McpRouterService.smartRoute` clamped the timeout to a maximum of 5 seconds (`Math.min(5, ...)`), which is insufficient for long-running tools.

## Applied Fixes

### 1. Relaxed McpClientManager Timeouts
Modified `src/main/java/com/pajk/mcpbridge/core/service/McpClientManager.java`:
*   **Connection Creation**: Increased from **300ms** to **3000ms (3s)**.
*   **Client Initialization**: Increased from **200ms** to **2000ms (2s)**.
*   **List Tools (Aggressive)**: Increased from **200ms** to **2000ms (2s)**.
*   **List Resources/Prompts**: Increased from **500ms** to **5000ms (5s)**.

These values are much safe for production environments while still failing fast enough
## 6. 额外发现与修复 (2025-01-29)

在进行全面测试时，发现并修复了以下两个关键 Bug：

### 6.1 Header 解析不完整
- **问题**: Router 无法从请求头中解析 `Session-Id`、`X-Session-Id` 等变体，仅支持 `Mcp-Session-Id`。导致部分客户端无法正确传递 Session。
- **修复**: 在 `McpRouterServerConfig.java` 的 `SESSION_ID_HEADER_CANDIDATES` 列表中添加了缺失的 Header Key。
  ```java
  private static final java.util.List<String> SESSION_ID_HEADER_CANDIDATES = java.util.List.of(
          "Mcp-Session-Id", "mcp-session-id",
          "X-Mcp-Session-Id", "x-mcp-session-id",
          "Session-Id", "session-id",
          "X-Session-Id", "x-session-id"
  );
  ```

### 6.2 路由冲突导致服务名解析错误
- **问题**: 请求 `/mcp/message` 被通配符路由 `/mcp/{serviceName}` 错误匹配，导致 Router 将 "message" 误识别为服务名 (`serviceName="message"`)。配合 Redis 连接不可用时，导致报错 `No healthy services found for: message`。
- **修复**: 在 `McpRouterServerConfig.java` 中使用 Regex 排除保留关键字 `message`，防止路由冲突。
  ```java
  .POST(STREAMABLE_BASE_PATH + "/{serviceName:^(?!message$).*}", this::handleMcpMessageWithPath)
  ```
- **验证**: 修复后，所有 Header 测试及端到端流程测试均通过。
### 2. Improved McpRouterService Logic
Modified `src/main/java/com/pajk/mcpbridge/core/service/McpRouterService.java`:
*   **Removed 5s Clamp**: `smartRoute` for `tools/call` no longer limits timeout to 5s. It now respects the requested timeout (default 30s-60s).
*   **Enhanced Logging**: Added timeout value logging to `routeRequest` to aid in future debugging.

## Verification
*   **Code Review**: Verified that all "aggressive" timeout values have been updated to production-friendly values.
*   **Configuration**: Confirmed `sse-message-endpoint` is set to `/mcp/message` in both Client Manager and Server Config (aligned with user's recent update).

## Next Steps
1.  **Deploy**: Rebuild and deploy `mcp-router-v3`.
2.  **Verify**: Test `tools/call` via MCP Inspector in the production environment. The error should be resolved.
