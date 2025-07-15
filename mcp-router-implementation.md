# MCP Router 改造实现方案

## 1. 架构说明

当前系统架构：
- mcp-client: 客户端，调用 mcp-router
- mcp-router: 路由服务，负责转发请求到合适的 mcp-server-v2
- mcp-server-v2: 服务端，提供具体的 tool 实现

## 2. 改造需求

### 2.1 需求列表
1. 监听并保存 mcp-server-v2 注册信息
2. 监控 mcp-server-v2 健康状态
3. 提供 mcp server 列表查询接口

## 3. 具体实现方案

### 3.1 注册信息监听与存储

```java
@Configuration
public class McpServerRegistryConfig {
    
    @Bean
    public NacosServiceRegistry nacosServiceRegistry() {
        return new NacosServiceRegistry();
    }
    
    @Bean
    public McpServerRegistry mcpServerRegistry(NacosServiceRegistry registry) {
        return new McpServerRegistry(registry);
    }
}

@Service
@Slf4j
public class McpServerRegistry {
    private final ConcurrentHashMap<String, McpServerInfo> serverInfoMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instance> instanceMap = new ConcurrentHashMap<>();
    private final NacosServiceRegistry registry;
    
    public void onServerRegister(Event event) {
        // 1. 解析注册信息
        McpServerInfo serverInfo = parseServerInfo(event);
        Instance instance = parseInstance(event);
        
        // 2. 保存到本地缓存
        serverInfoMap.put(serverInfo.getName(), serverInfo);
        instanceMap.put(instance.getInstanceId(), instance);
        
        log.info("MCP Server registered: {}", serverInfo.getName());
    }
}
```

### 3.2 健康状态监控

```java
@Configuration
public class NacosMcpOperationConfig {
    
    @Bean
    public NacosWatch nacosWatch(NamingService namingService) {
        return new NacosWatch(namingService);
    }
}

@Component
@Slf4j
public class McpServerHealthChecker {
    private final McpServerRegistry serverRegistry;
    private final NacosWatch nacosWatch;
    
    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void checkHealth() {
        serverRegistry.getAllInstances().forEach(instance -> {
            boolean healthy = nacosWatch.isHealthy(instance);
            if (!healthy) {
                serverRegistry.removeInstance(instance.getInstanceId());
                log.warn("Unhealthy MCP Server removed: {}", instance.getInstanceId());
            }
        });
    }
}
```

### 3.3 服务列表查询接口

```java
@RestController
@RequestMapping("/api/mcp/servers")
public class McpServerController {
    
    private final McpServerRegistry serverRegistry;
    
    @GetMapping("/list")
    public ResponseEntity<List<McpServerDTO>> listServers(
            @RequestParam(required = false) Boolean onlyHealthy) {
        List<McpServerDTO> servers = serverRegistry.getServers(onlyHealthy);
        return ResponseEntity.ok(servers);
    }
    
    @GetMapping("/{serverName}/tools")
    public ResponseEntity<List<McpToolInfo>> getServerTools(
            @PathVariable String serverName) {
        McpServerInfo serverInfo = serverRegistry.getServerInfo(serverName);
        return ResponseEntity.ok(serverInfo.getTools());
    }
}

@Data
public class McpServerDTO {
    private String name;
    private String version;
    private String description;
    private boolean healthy;
    private String endpoint;
    private Map<String, String> metadata;
}
```

## 4. 数据结构

### 4.1 本地缓存结构
```java
// 服务信息缓存
Map<String, McpServerInfo> {
    "server-name": {
        "name": "mcp-server-v2",
        "version": "1.0.0",
        "description": "MCP Server V2",
        "tools": [...],
        "metadata": {...}
    }
}

// 实例信息缓存
Map<String, Instance> {
    "instance-id": {
        "ip": "127.0.0.1",
        "port": 8080,
        "healthy": true,
        "metadata": {...}
    }
}
```

## 5. 关键流程

1. **服务注册监听流程**
   - 监听 Nacos 服务注册事件
   - 解析并保存服务信息
   - 解析并保存实例信息
   - 触发注册成功事件

2. **健康检查流程**
   - 定时获取所有实例
   - 检查每个实例健康状态
   - 移除不健康实例
   - 更新本地缓存

3. **服务发现流程**
   - 接收查询请求
   - 过滤健康实例
   - 转换为 DTO
   - 返回结果

## 6. 注意事项

1. 需要处理并发访问情况
2. 需要实现优雅关闭
3. 需要处理注册信息变更
4. 需要实现数据一致性检查
5. 需要添加适当的日志记录
6. 需要实现监控指标
7. 需要考虑性能优化

## 7. 后续优化建议

1. 添加缓存过期机制
2. 实现服务信息定时同步
3. 添加服务统计功能
4. 实现负载均衡策略
5. 添加服务降级机制 