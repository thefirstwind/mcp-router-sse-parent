# MCP Router v3 调试指南

本指南详细介绍了 MCP Router v3 项目中完善的调试日志系统，帮助开发者快速定位和解决问题。

## 🚀 快速开始

### 1. 启动调试模式

使用调试启动脚本：

```bash
# 基本调试模式
./debug-start.sh

# 性能监控模式
./debug-start.sh -m perf

# 请求跟踪模式
./debug-start.sh -m trace -l TRACE

# MCP协议调试模式
./debug-start.sh -m mcp

# 健康检查调试模式
./debug-start.sh -m health

# 后台运行
./debug-start.sh -b

# 清理日志后启动
./debug-start.sh -c
```

### 2. 查看日志

使用日志分析工具：

```bash
# 实时查看所有日志
./debug-log-analyzer.sh tail

# 查看错误日志
./debug-log-analyzer.sh errors

# 查看性能日志
./debug-log-analyzer.sh performance

# 查看连接日志
./debug-log-analyzer.sh connections

# 查看健康检查日志
./debug-log-analyzer.sh health

# 搜索特定内容
./debug-log-analyzer.sh search "connection"

# 显示日志统计
./debug-log-analyzer.sh stats
```

## 📁 日志文件结构

调试模式下会生成以下日志文件：

```
logs/
├── mcp-router-v3.log              # 主日志文件
├── mcp-router-v3-debug.log        # 调试日志（DEBUG及以上级别）
├── mcp-router-v3-trace.log        # 跟踪日志（TRACE级别，仅开发环境）
├── mcp-router-v3-error.log        # 错误日志（ERROR级别）
├── mcp-router-v3-mcp.log          # MCP协议专用日志
├── mcp-router-v3-performance.log  # 性能监控日志
└── startup.log                    # 启动日志（后台运行时）
```

## 🔧 配置说明

### 1. 日志级别配置

在 `application-debug.yml` 中配置：

```yaml
logging:
  level:
    # MCP Router 核心组件 - 详细调试
    com.pajk.mcpbridge.core: TRACE
    com.pajk.mcpbridge.core.service.McpClientManager: TRACE
    com.pajk.mcpbridge.core.service.HealthCheckService: TRACE
    
    # 性能监控
    performance: TRACE
    
    # MCP 协议相关
    io.modelcontextprotocol: DEBUG
```

### 2. 调试功能配置

```yaml
debug:
  enabled: true
  
  # 请求跟踪配置
  request-tracking:
    enabled: true
    max-contexts: 1000
    cleanup-interval: 300s
    
  # 性能监控配置
  performance:
    enabled: true
    slow-request-threshold: 1000ms
    log-all-requests: true
    
  # 连接池监控
  connection-pool:
    log-stats-interval: 60s
    log-detailed-stats: true
```

## 📊 日志格式说明

### 1. 标准日志格式

```
2024-01-15 10:30:45.123 DEBUG --- [reactor-http-nio-2] c.p.m.c.service.McpClientManager [getOrCreateMcpClient:89] : 🔍 [getOrCreateMcpClient] Processing request for server: mcp-server-v2 -> key: mcp-server-v2:192.168.0.103:8062
```

格式说明：
- `2024-01-15 10:30:45.123` - 时间戳
- `DEBUG` - 日志级别
- `[reactor-http-nio-2]` - 线程名
- `c.p.m.c.service.McpClientManager` - 类名（缩写）
- `[getOrCreateMcpClient:89]` - 方法名和行号
- `🔍` - 表情符号（便于快速识别）
- `[getOrCreateMcpClient]` - 操作标识
- 后面是具体的日志消息

### 2. 性能日志格式

```
2024-01-15 10:30:45.123 INFO  --- [scheduling-1] performance : CONNECTION_CREATED server=mcp-server-v2 request_id=12345 pool_size=3 total_created=15
```

性能日志采用结构化格式，便于后续分析和监控。

## 🎯 调试场景指南

### 1. 连接问题调试

**症状**: MCP服务器连接失败

**调试步骤**:
```bash
# 1. 查看连接日志
./debug-log-analyzer.sh connections -n 50

# 2. 搜索连接错误
./debug-log-analyzer.sh search "CONNECTION.*FAILED"

# 3. 查看详细的连接创建过程
grep "createNewConnection" logs/mcp-router-v3-trace.log
```

**关键日志标识**:
- `🔗 [CONNECTION]` - 连接事件
- `🔧 [createNewConnection]` - 连接创建过程
- `❌ [createNewConnection] Failed` - 连接创建失败

### 2. 健康检查问题调试

**症状**: 健康检查失败或不准确

**调试步骤**:
```bash
# 1. 查看健康检查日志
./debug-log-analyzer.sh health -s mcp-server-v2

# 2. 查看健康检查统计
./debug-log-analyzer.sh stats

# 3. 分析健康检查模式
grep "checkServerHealthLayered" logs/mcp-router-v3-debug.log | tail -20
```

**关键日志标识**:
- `🔍 [checkServerHealthLayered]` - 健康检查开始
- `✅ Level 1 (Nacos)` - Nacos心跳检查通过
- `✅ Level 2 (MCP)` - MCP协议检查通过
- `❌ Level 1/2` - 相应级别检查失败

### 3. 路由问题调试

**症状**: 消息路由失败或响应慢

**调试步骤**:
```bash
# 1. 查看路由日志
./debug-log-analyzer.sh routing -n 30

# 2. 查看性能日志
./debug-log-analyzer.sh performance -t 5

# 3. 搜索特定消息ID
./debug-log-analyzer.sh search "message_id=12345"
```

**关键日志标识**:
- `📨 [routeMessage]` - 路由请求接收
- `✅ [routeMessage] Successfully` - 路由成功
- `❌ [routeMessage] Failed` - 路由失败

### 4. 性能问题调试

**症状**: 响应慢或资源使用高

**调试步骤**:
```bash
# 1. 启动性能监控模式
./debug-start.sh -m perf

# 2. 查看性能统计
./debug-log-analyzer.sh performance

# 3. 分析慢请求
grep "duration.*[5-9][0-9][0-9][0-9]ms" logs/mcp-router-v3-performance.log
```

**关键性能指标**:
- `CONNECTION_CREATED` - 连接创建时间
- `HEALTH_CHECK_SUCCESS` - 健康检查耗时
- `ROUTE_SUCCESS` - 路由处理耗时
- `MEMORY_STATS` - 内存使用情况

## 🛠️ 高级调试技巧

### 1. 请求跟踪

使用 `DebugLogger` 进行请求跟踪：

```java
// 开始请求跟踪
DebugLogger.RequestContext context = DebugLogger.startRequest("routeMessage");
context.setAttribute("serviceName", serviceName);
context.setAttribute("messageId", message.getId());

try {
    // 业务逻辑
    return processRequest();
} finally {
    // 结束请求跟踪
    DebugLogger.endRequest(context);
}
```

### 2. 自定义性能指标

```java
// 记录自定义性能指标
DebugLogger.logPerformance("CUSTOM_OPERATION", 
    "operation", "data_processing",
    "duration", "150ms",
    "records", "1000");
```

### 3. 结构化日志搜索

使用结构化格式搜索日志：

```bash
# 搜索特定服务器的所有事件
grep "server=mcp-server-v2" logs/mcp-router-v3-performance.log

# 搜索耗时超过1秒的操作
grep "duration=[1-9][0-9][0-9][0-9]ms" logs/mcp-router-v3-performance.log

# 搜索错误率高的时间段
grep "error_rate=[5-9][0-9]" logs/mcp-router-v3-performance.log
```

## 📈 监控和告警

### 1. 关键指标监控

建议监控以下指标：

- **连接池使用率**: `pool_size` / `MAX_POOL_SIZE`
- **健康检查成功率**: `HEALTH_CHECK_SUCCESS` / `HEALTH_CHECK_TOTAL`
- **路由成功率**: `ROUTE_SUCCESS` / `ROUTE_TOTAL`
- **平均响应时间**: `duration` 字段的平均值
- **内存使用率**: `MEMORY_STATS` 中的 `usage` 字段

### 2. 告警阈值建议

- 连接池使用率 > 80%
- 健康检查成功率 < 95%
- 路由成功率 < 99%
- 平均响应时间 > 2000ms
- 内存使用率 > 85%

## 🔍 故障排查清单

### 连接问题
- [ ] 检查目标服务器是否启动
- [ ] 检查网络连通性
- [ ] 检查端口是否正确
- [ ] 检查连接池是否已满
- [ ] 检查SSL/TLS配置（如适用）

### 健康检查问题
- [ ] 检查Nacos注册状态
- [ ] 检查MCP协议握手
- [ ] 检查健康检查超时配置
- [ ] 检查熔断器状态

### 性能问题
- [ ] 检查JVM内存使用
- [ ] 检查连接池配置
- [ ] 检查数据库连接（如适用）
- [ ] 检查网络延迟
- [ ] 检查并发请求数量

## 📝 最佳实践

1. **日志级别管理**
   - 开发环境使用 DEBUG/TRACE
   - 测试环境使用 INFO/DEBUG
   - 生产环境使用 INFO/WARN

2. **日志文件管理**
   - 定期清理旧日志文件
   - 设置合理的日志文件大小限制
   - 使用日志轮转避免磁盘空间不足

3. **性能监控**
   - 定期检查性能指标
   - 设置合理的告警阈值
   - 建立性能基线用于对比

4. **问题定位**
   - 使用结构化日志便于搜索
   - 记录关键业务指标
   - 保留足够的上下文信息

## 🤝 贡献指南

如果您发现调试功能的问题或有改进建议，请：

1. 查看现有的日志配置
2. 测试您的改进
3. 更新相关文档
4. 提交 Pull Request

---

通过这套完善的调试日志系统，您可以快速定位和解决 MCP Router v3 中的各种问题。如有疑问，请参考日志文件或联系开发团队。

