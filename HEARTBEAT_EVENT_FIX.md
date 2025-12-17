# 心跳事件类型修复

## 问题描述

1. **`mcp-router-v3` 错误**：`Received unrecognized SSE event type: heartbeat`
   - MCP 客户端（`WebFluxSseClientTransport`）不识别 `heartbeat` 事件类型
   - 导致错误：`io.modelcontextprotocol.spec.McpError: Received unrecognized SSE event type: heartbeat`

2. **`zkInfo` 错误**：`Connection reset by peer` 被记录为 ERROR 级别
   - 心跳发送时客户端断开连接，错误被记录为 ERROR
   - 应该降级为 DEBUG 级别

## 问题原因

1. **心跳事件类型不兼容**：
   - `zkInfo` 和 `mcp-router-v3` 都使用了 `event("heartbeat")` 发送心跳事件
   - MCP 客户端只识别标准的事件类型（如 `message`），不识别自定义的 `heartbeat` 类型
   - 导致客户端抛出错误

2. **错误日志级别过高**：
   - `Connection reset by peer` 是正常的客户端断开情况
   - 应该降级为 DEBUG 级别，而不是 ERROR

## 修复方案

### 1. zkInfo - 移除心跳事件发送

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/SseController.java`

**修改**:
- 移除心跳事件的发送，只更新会话活跃时间（touch）
- 心跳的目的是保持连接活跃，通过 touch 更新会话时间即可
- 避免发送客户端不识别的事件类型

**代码示例**:
```java
// 修复前
emitter.send(SseEmitter.event()
        .name("heartbeat")
        .data(heartbeatData));

// 修复后
// 不发送心跳事件，只更新会话活跃时间（touch session）
// 原因：MCP 客户端不识别 "heartbeat" 事件类型，会报错
sessionManager.touch(sessionId);
```

**效果**:
- 不再发送客户端不识别的事件类型
- 避免客户端报错
- 心跳功能仍然有效（通过 touch 更新会话时间）

### 2. mcp-router-v3 - 使用 SSE comment

**文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

**修改**:
- 将心跳事件改为使用 SSE `comment`（注释）
- SSE 注释会被客户端忽略，不会报错
- 保持心跳功能（通过 touch 更新会话时间）

**代码示例**:
```java
// 修复前
ServerSentEvent.<String>builder()
    .event("heartbeat")
    .data("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}")
    .build()

// 修复后
ServerSentEvent.<String>builder()
    .comment("heartbeat " + System.currentTimeMillis()) // 使用 comment，客户端会忽略，不会报错
    .build()
```

**效果**:
- 使用 SSE 注释格式，客户端会忽略
- 避免客户端报错
- 心跳功能仍然有效

### 3. zkInfo - 改进错误处理

**文件**: `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/SseController.java`

**修改**:
- 在 `onError` 回调中添加对 `Connection reset by peer` 的处理
- 将 `Connection reset` 和 `Broken pipe` 都降级为 DEBUG 级别

**代码示例**:
```java
// 修复前
if (ex instanceof IOException && ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
    log.debug("ℹ️ Client disconnected (broken pipe) for session: {}", sessionId);
}

// 修复后
if (ex instanceof IOException && errorMsg != null && 
    (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
    log.debug("ℹ️ Client disconnected ({}) for session: {}", errorMsg, sessionId);
}
```

**效果**:
- `Connection reset by peer` 不再记录为 ERROR
- 减少错误日志噪音
- 提高日志可读性

## 修复效果

### 修复前
- `mcp-router-v3` 收到 `heartbeat` 事件时报错：`Received unrecognized SSE event type: heartbeat`
- `zkInfo` 心跳发送时 `Connection reset by peer` 被记录为 ERROR
- 客户端连接不稳定

### 修复后
- `zkInfo` 不再发送心跳事件，只更新 touch，避免客户端报错
- `mcp-router-v3` 使用 SSE comment，客户端会忽略，不会报错
- `Connection reset by peer` 降级为 DEBUG，不再记录为错误
- 客户端连接稳定

## SSE 事件类型说明

### 标准事件类型
- `message`: MCP 消息事件（标准）
- `endpoint`: 端点信息事件（标准）

### 非标准事件类型
- `heartbeat`: 心跳事件（非标准，客户端不识别）

### SSE 注释
- `comment`: SSE 注释，客户端会忽略，不会报错
- 格式：`:comment text`

## 注意事项

1. **心跳功能仍然有效**：
   - 虽然不发送心跳事件，但通过 `touch` 更新会话时间
   - 心跳的目的是保持连接活跃，touch 已经满足需求

2. **SSE 注释格式**：
   - SSE 注释以 `:` 开头
   - 客户端会忽略注释行，不会报错
   - 适合用于心跳等非关键信息

3. **错误处理**：
   - `Broken pipe` 和 `Connection reset` 都是正常的客户端断开情况
   - 应该降级为 DEBUG 级别，而不是 ERROR

## 相关文件
- `zk-mcp-parent/zkInfo/src/main/java/com/zkinfo/controller/SseController.java`
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/config/McpRouterServerConfig.java`

