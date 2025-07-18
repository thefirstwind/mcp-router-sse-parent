

### **⚠️ 需要改进的方面**

#### 1. **mcp-server 通过 mcp client 连接 mcp-router 不是必要的**

**当前问题：**
- mcp-server 既要注册到 Nacos，又要主动连接 mcp-router
- 这造成了**双重心跳负担**：Nacos 心跳 + MCP 连接心跳
- 增加了系统复杂度和故障点

**更优雅的方案：**
```java
// 建议方案：仅依赖 Nacos 服务发现和健康检查
@Component
public class OptimizedMcpServerRegistration {
    
    @EventListener(ApplicationReadyEvent.class)
    public void registerToNacos() {
        // 1. 只注册到 Nacos，包含健康检查端点
        // 2. mcp-router 通过 Nacos 服务发现找到服务
        // 3. 路由时再建立 MCP 连接（按需连接）
    }
}
```

#### 2. **健康检查策略可以进一步优化**

```java
// 当前：基于连接状态的健康检查
private Mono<Boolean> performMcpHealthCheck(McpServerInfo serverInfo) {
    return mcpClientManager.getOrCreateMcpClient(serverInfo)
            .flatMap(client -> checkMcpServerInfo(client, serverInfo));
}

// 建议：结合 Nacos 健康检查 + MCP 能力检查
public class HybridHealthChecker {
    
    public Mono<Boolean> checkHealth(McpServerInfo serverInfo) {
        return checkNacosHealth(serverInfo)  // 快速检查
                .flatMap(healthy -> {
                    if (!healthy) return Mono.just(false);
                    // 只有 Nacos 健康才进行 MCP 能力检查
                    return checkMcpCapabilities(serverInfo);
                });
    }
}
```

## 🎯 优化建议

### **短期优化（1-2周）**

1. **简化连接机制**
   - mcp-server 只注册到 Nacos，不主动连接 mcp-router
   - mcp-router 在需要时通过 Nacos 发现服务并建立连接

2. **健康检查分层**
   ```
   Level 1: Nacos 心跳检查（基础存活）
   Level 2: MCP 协议检查（功能可用）
   ```

### **中期优化（1个月）**

3. **连接池管理**
   - mcp-router 维护到 mcp-server 的连接池
   - 按需建立连接，空闲时回收

4. **智能路由**
   - 基于服务负载、响应时间进行智能路由
   - 结合健康检查结果进行权重调整

### **长期优化（2-3个月）**

5. **统一监控面板**
   - 整合 Nacos 健康状态 + MCP 连接状态
   - 提供可视化的服务拓扑和健康状态

## 🔥 核心结论

**当前架构 70% 合理，但有改进空间：**

✅ **合理的设计：**
- 事件驱动替代轮询
- 分层职责设计
- 故障恢复机制

❌ **不必要的复杂度：**
- mcp-server 主动连接 mcp-router **不是必要的**
- 双重心跳增加了维护负担
- 可以通过 Nacos + 按需连接 实现更简洁的架构

**建议：** 改为 **Nacos 服务发现 + 按需 MCP 连接** 的模式，既保证了路由功能，又简化了架构复杂度。