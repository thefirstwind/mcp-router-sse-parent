# 虚拟项目健康检查修复

## 问题描述
`mcp-router-v3` 中的 `McpConnectionEventListener` 对虚拟项目（`virtual-*`）进行了健康检查，记录不健康实例的日志，并执行数据库操作。用户要求对于虚拟节点不要进行健康检查判断。

## 问题原因
1. `handleServiceChangeEvent` 方法对所有服务（包括虚拟项目）都进行健康检查并记录日志
2. `syncNacosStateToDatabase` 方法对所有服务都进行健康检查统计
3. 虚拟项目由 `zkInfo` 管理，`mcp-router-v3` 不应该对其进行健康检查

## 修复内容

### 1. handleServiceChangeEvent 方法
- **文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`
- **修改**:
  - 对于虚拟项目，跳过健康检查日志记录（`❌ Unhealthy instance`、`✅ Healthy instance`）
  - 对于虚拟项目，只统计健康实例数量，不记录详细的健康检查日志
  - 使用简化的日志格式：`📊 [Virtual Project]` 而不是 `📊 [Service Statistics]`

### 2. syncNacosStateToDatabase 方法
- **文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`
- **修改**:
  - 对于虚拟项目，跳过健康检查判断
  - 对于虚拟项目，假设所有实例都是健康的（由 zkInfo 管理）
  - 对于虚拟项目，跳过详细的实例日志记录（`log.debug`）

## 修复效果

### 修复前
- 虚拟项目实例不健康时，会记录 `❌ Unhealthy instance` 日志
- 虚拟项目的健康状态会被统计和记录
- 可能导致虚拟项目被误标记为不健康

### 修复后
- 虚拟项目实例不健康时，不记录健康检查日志
- 虚拟项目的健康状态不被判断，假设所有实例都是健康的
- 虚拟项目由 `zkInfo` 完全管理，`mcp-router-v3` 只负责路由

## 虚拟项目识别规则
- 服务名以 `virtual-` 开头的服务被视为虚拟项目
- 例如：`virtual-data-analysis`、`virtual-test-project` 等

## 注意事项
1. 虚拟项目由 `zkInfo` 管理，`mcp-router-v3` 不应该对其进行健康检查
2. 虚拟项目的生命周期和健康状态由 `zkInfo` 控制
3. `mcp-router-v3` 只负责路由请求到虚拟项目，不判断其健康状态

## 相关文件
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`

