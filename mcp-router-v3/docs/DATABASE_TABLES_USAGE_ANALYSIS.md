# MCP Router V3 数据库表使用情况分析报告

## 概述

本报告分析了 `mcp-router-v3` 项目中所有数据库表的使用情况。数据库 schema 定义在 `database/schema.sql` 中，共包含 **12 张表**。

## 表使用情况汇总

| 表名 | 状态 | 使用情况 | 说明 |
|------|------|----------|------|
| `mcp_servers` | ✅ **使用中** | 42个文件引用 | 核心业务表，存储MCP服务器实例信息 |
| `health_check_records` | ✅ **使用中** | 7个文件引用 | 健康检查记录表，存储健康检查历史 |
| `routing_logs` | ✅ **使用中** | 10个文件引用 | 路由请求日志表，存储所有路由请求日志 |
| `routing_logs_archive` | ⚠️ **部分使用** | 1个文件引用 | 路由日志归档表，仅用于归档操作 |
| `load_balancer_metrics` | ❌ **未使用** | 0个文件引用 | 负载均衡指标表，代码中未实现 |
| `circuit_breaker_history` | ❌ **未使用** | 0个文件引用 | 熔断器状态历史表，代码中未实现 |
| `connection_pool_stats` | ❌ **未使用** | 0个文件引用 | 连接池统计表，代码中未实现 |
| `sse_session_records` | ❌ **未使用** | 0个文件引用 | SSE会话记录表，代码中未实现 |
| `mcp_tools` | ❌ **未使用** | 0个文件引用 | MCP工具配置表，代码中未实现 |
| `system_config` | ❌ **未使用** | 0个文件引用 | 系统配置表，代码中未实现 |
| `server_daily_stats` | ❌ **未使用** | 0个文件引用 | 服务器日统计表，代码中未实现 |
| `tool_call_stats` | ❌ **未使用** | 0个文件引用 | 工具调用统计表，代码中未实现 |

## 详细分析

### ✅ 正在使用的表（3张）

#### 1. `mcp_servers` - MCP服务器实例表
- **使用频率**: 高（42个文件）
- **实体类**: `com.pajk.mcpbridge.persistence.entity.McpServer`
- **Mapper**: `com.pajk.mcpbridge.persistence.mapper.McpServerMapper`
- **Service**: `com.pajk.mcpbridge.persistence.service.McpServerPersistenceService`
- **主要用途**:
  - 存储MCP服务器注册信息
  - 服务发现和健康检查
  - 负载均衡选择
  - 服务器状态管理
- **操作类型**: INSERT, UPDATE, SELECT, DELETE（软删除）

#### 2. `health_check_records` - 健康检查记录表
- **使用频率**: 中（7个文件）
- **实体类**: `com.pajk.mcpbridge.persistence.entity.HealthCheckRecord`
- **Mapper**: `com.pajk.mcpbridge.persistence.mapper.HealthCheckRecordMapper`
- **Service**: `com.pajk.mcpbridge.persistence.service.HealthCheckRecordBatchWriter`
- **主要用途**:
  - 记录健康检查历史
  - 健康状态追踪
  - 性能指标统计
- **操作类型**: INSERT（批量插入）
- **分区**: 按月分区

#### 3. `routing_logs` - 路由请求日志表
- **使用频率**: 中（10个文件）
- **实体类**: `com.pajk.mcpbridge.persistence.entity.RoutingLog`
- **Mapper**: `com.pajk.mcpbridge.persistence.mapper.RoutingLogMapper`
- **Service**: `com.pajk.mcpbridge.persistence.service.RoutingLogBatchWriter`
- **主要用途**:
  - 记录所有路由请求日志
  - 请求追踪和调试
  - 性能分析
  - 错误分析
- **操作类型**: INSERT（批量插入）, SELECT, DELETE
- **分区**: 按天分区

### ⚠️ 部分使用的表（1张）

#### 4. `routing_logs_archive` - 路由日志归档表
- **使用频率**: 低（1个文件）
- **实体类**: 无（使用与 `routing_logs` 相同的结构）
- **Mapper**: `RoutingLogMapper.archiveToHistory`
- **主要用途**:
  - 归档历史路由日志
  - 数据清理和归档
- **操作类型**: INSERT（从 `routing_logs` 归档）
- **说明**: 仅在 `RoutingLogMapper.xml` 中有归档操作，但没有独立的实体类或Service

### ❌ 未使用的表（8张）

#### 5. `load_balancer_metrics` - 负载均衡指标表
- **状态**: 未使用
- **设计用途**: 存储负载均衡性能指标
- **未实现原因**: 负载均衡统计目前仅在内存中维护，未持久化到数据库
- **建议**: 
  - 如果不需要历史指标分析，可以删除此表
  - 如果需要历史指标，需要实现相应的Service和Mapper

#### 6. `circuit_breaker_history` - 熔断器状态历史表
- **状态**: 未使用
- **设计用途**: 记录熔断器状态变更历史
- **未实现原因**: 代码中未实现熔断器功能
- **建议**: 
  - 如果不需要熔断器功能，可以删除此表
  - 如果需要熔断器功能，需要实现相应的Service和Mapper

#### 7. `connection_pool_stats` - 连接池统计表
- **状态**: 未使用
- **设计用途**: 存储连接池统计信息
- **未实现原因**: 连接池统计目前仅在内存中维护（`McpClientManager.getPoolStats()`），未持久化到数据库
- **建议**: 
  - 如果不需要历史统计，可以删除此表
  - 如果需要历史统计，需要实现相应的Service和Mapper

#### 8. `sse_session_records` - SSE会话记录表
- **状态**: 未使用
- **设计用途**: 记录SSE会话信息
- **未实现原因**: SSE会话管理目前仅在内存中维护（`McpSessionBridgeService`），未持久化到数据库
- **建议**: 
  - 如果不需要会话历史记录，可以删除此表
  - 如果需要会话历史记录，需要实现相应的Service和Mapper

#### 9. `mcp_tools` - MCP工具配置表
- **状态**: 未使用
- **设计用途**: 存储MCP工具配置信息
- **未实现原因**: 工具配置目前存储在Nacos配置中心（`McpConfigService`），未使用数据库存储
- **说明**: 代码中有 `McpToolsConfig` 模型类，但这是用于Nacos配置的，不是数据库实体
- **建议**: 
  - 如果不需要数据库存储工具配置，可以删除此表
  - 如果需要数据库存储，需要实现相应的Service和Mapper

#### 10. `system_config` - 系统配置表
- **状态**: 未使用
- **设计用途**: 存储系统配置信息
- **未实现原因**: 系统配置目前使用Spring Boot的配置文件（`application.yml`），未使用数据库存储
- **说明**: Schema中有初始化数据，但代码中未使用
- **建议**: 
  - 如果不需要数据库存储系统配置，可以删除此表
  - 如果需要动态配置管理，需要实现相应的Service和Mapper

#### 11. `server_daily_stats` - 服务器日统计表
- **状态**: 未使用
- **设计用途**: 存储服务器每日统计信息
- **未实现原因**: 未实现每日统计聚合功能
- **建议**: 
  - 如果不需要日统计，可以删除此表
  - 如果需要日统计，需要实现定时任务和聚合逻辑

#### 12. `tool_call_stats` - 工具调用统计表
- **状态**: 未使用
- **设计用途**: 存储工具调用统计信息
- **未实现原因**: 未实现工具调用统计聚合功能
- **建议**: 
  - 如果不需要工具调用统计，可以删除此表
  - 如果需要工具调用统计，需要实现定时任务和聚合逻辑

## 视图和存储过程

### 视图（2个）
1. `v_server_overview` - 服务器概览视图
   - **状态**: 未使用（代码中未查询此视图）
   - **建议**: 如果不需要，可以删除

2. `v_recent_24h_stats` - 最近24小时请求统计视图
   - **状态**: 未使用（代码中未查询此视图）
   - **建议**: 如果不需要，可以删除

### 存储过程（1个）
1. `create_routing_log_partition` - 自动创建路由日志分区
   - **状态**: 未使用（代码中未调用此存储过程）
   - **建议**: 如果需要自动分区管理，需要实现定时任务调用此存储过程

## 总结

### 使用情况统计
- **正在使用**: 3张表（25%）
- **部分使用**: 1张表（8%）
- **未使用**: 8张表（67%）

### 建议

#### 短期建议（清理未使用的表）
1. **可以删除的表**（如果确认不需要）:
   - `load_balancer_metrics`
   - `circuit_breaker_history`
   - `connection_pool_stats`
   - `sse_session_records`
   - `mcp_tools`
   - `system_config`
   - `server_daily_stats`
   - `tool_call_stats`

2. **可以删除的视图**:
   - `v_server_overview`
   - `v_recent_24h_stats`

#### 长期建议（实现未使用的功能）
如果需要以下功能，可以逐步实现：
1. **负载均衡指标持久化**: 实现 `LoadBalancerMetricsService` 和 `LoadBalancerMetricsMapper`
2. **熔断器功能**: 实现熔断器逻辑和 `CircuitBreakerHistoryService`
3. **连接池统计持久化**: 在 `McpClientManager` 中添加统计持久化逻辑
4. **SSE会话记录**: 在 `McpSessionBridgeService` 中添加会话记录持久化
5. **工具配置数据库存储**: 实现 `McpToolsService` 和 `McpToolsMapper`
6. **系统配置数据库存储**: 实现 `SystemConfigService` 和 `SystemConfigMapper`
7. **统计聚合**: 实现定时任务进行日统计和工具调用统计聚合

## 代码引用统计

### 核心业务表
- `mcp_servers`: 42个文件引用
- `health_check_records`: 7个文件引用
- `routing_logs`: 10个文件引用

### 扩展功能表
- 所有扩展功能表（`load_balancer_metrics`, `circuit_breaker_history`, `connection_pool_stats`, `sse_session_records`）: 0个文件引用

### 配置和统计表
- `mcp_tools`: 0个文件引用（注意：代码中有 `McpToolsConfig` 模型类，但用于Nacos配置，不是数据库实体）
- `system_config`: 0个文件引用
- `server_daily_stats`: 0个文件引用
- `tool_call_stats`: 0个文件引用

---

**生成时间**: 2025-11-12
**分析范围**: mcp-router-v3 模块
**数据库**: mcp_bridge













