# 虚拟项目保护修复

## 问题描述
`mcp-router-v3` 项目中的 `McpConnectionEventListener` 在检测到虚拟项目服务（`virtual-*`）没有实例时，会执行数据库操作（标记临时节点为不健康），这可能导致虚拟项目被标记为 offline。

## 问题原因
1. `handleServiceChangeEvent` 方法在检测到实例列表为空时，会调用 `handleAllInstancesOffline`
2. `syncNacosStateToDatabase` 方法在同步 Nacos 状态时，会对所有服务执行数据库操作
3. 这些操作不应该应用于虚拟项目，因为虚拟项目由 `zkInfo` 管理

## 修复内容

### 1. handleServiceChangeEvent 方法
- **文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`
- **修改**:
  - 添加虚拟项目检测：`boolean isVirtualProject = serviceName != null && serviceName.startsWith("virtual-");`
  - 当实例列表为空时，如果是虚拟项目，只记录日志，不调用 `handleAllInstancesOffline`
  - 当有实例时，如果是虚拟项目，跳过 `persistInstanceToDatabase` 和 `markOfflineEphemeralInstances` 操作

### 2. syncNacosStateToDatabase 方法
- **文件**: `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`
- **修改**:
  - 添加虚拟项目检测
  - 如果是虚拟项目，跳过所有数据库操作（`markEphemeralInstancesUnhealthy`、`persistInstanceSyncToDatabase`、`markOfflineEphemeralInstances`）

## 修复效果

### 修复前
- 虚拟项目服务没有实例时，会被标记为不健康
- 虚拟项目的状态会被同步到数据库
- 可能导致虚拟项目被误标记为 offline

### 修复后
- 虚拟项目服务没有实例时，只记录日志，不进行数据库操作
- 虚拟项目的状态不会被同步到数据库
- 虚拟项目由 `zkInfo` 完全管理，`mcp-router-v3` 只负责路由

## 虚拟项目识别规则
- 服务名以 `virtual-` 开头的服务被视为虚拟项目
- 例如：`virtual-data-analysis`、`virtual-test-project` 等

## 注意事项
1. 虚拟项目由 `zkInfo` 管理，`mcp-router-v3` 不应该对其进行持久化操作
2. 虚拟项目的生命周期由 `zkInfo` 控制，`mcp-router-v3` 只负责路由请求
3. 如果虚拟项目服务暂时没有实例，这是正常的（可能是重新注册过程中），不应该标记为 offline

## 相关文件
- `mcp-router-v3/src/main/java/com/pajk/mcpbridge/core/listener/McpConnectionEventListener.java`

