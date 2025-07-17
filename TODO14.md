# MCP Router V2 改造方案

## 核心问题

1. 通信协议不规范
   - 当前使用HTTP协议进行通信
   - 需要改为SSE协议，符合MCP标准

2. 服务注册机制不完整
   - 缺少完整的元数据管理
   - 服务健康检查机制简单

3. 路由转发机制待优化
   - 缺少负载均衡
   - 没有实现服务降级

## 改造步骤

### 第一步：项目初始化与基础配置

1. 创建项目结构
```bash
mcp-router-v2/
├── src/main/java/com/nacos/mcp/router/
│   ├── config/         # 配置类
│   ├── controller/     # API控制器
│   ├── model/         # 数据模型
│   ├── service/       # 业务逻辑
│   └── McpRouterV2Application.java
└── pom.xml
```

2. 配置pom.xml，参考mcp-server-v2和 mcp-router原有pom.xml文件，版本号不能修改


3. 基础配置类
```java
@Configuration
public class McpRouterConfig {
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
```

验证方式：
```bash
mvn clean install
mvn spring-boot:run
```

### 第二步：实现MCP服务注册

1. 注册配置类
```java
@Configuration
@EnableConfigurationProperties(McpRouterProperties.class)
public class McpServerRegistryConfig {
    @Bean
    public NamingService namingService() {
        return NacosFactory.createNamingService(properties);
    }
}
```

2. 服务注册实现
```java
@Component
public class McpServerRegistry {
    public void registerServer(McpServerInfo serverInfo) {
        Instance instance = new Instance();
        instance.setIp(serverInfo.getIp());
        instance.setPort(serverInfo.getPort());
        instance.setMetadata(buildMetadata(serverInfo));
        namingService.registerInstance(serverInfo.getName(), instance);
    }
}
```

验证方式：
```bash
curl -X GET \"http://localhost:8848/nacos/v2/ns/instance/list?serviceName=mcp-server-v2\"
```

### 第三步：实现SSE通信

1. SSE传输提供者
```java
@Component
public class McpSseTransportProvider {
    private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();
    
    public Flux<ServerSentEvent<String>> connect(String clientId) {
        return Flux.create(sink -> {
            SseEmitter emitter = new SseEmitter();
            sessions.put(clientId, emitter);
            // 处理连接关闭
            sink.onCancel(() -> sessions.remove(clientId));
        });
    }
}
```

2. SSE控制器
```java
@RestController
@RequestMapping(\"/sse\")
public class McpSseController {
    @GetMapping(\"/connect\")
    public Flux<ServerSentEvent<String>> connect(@RequestParam String clientId) {
        return mcpSseTransportProvider.connect(clientId);
    }
}
```

验证方式：
```bash
curl -N \"http://localhost:8050/sse/connect?clientId=test\"
```

### 第四步：实现MCP路由转发

1. 路由服务实现
```java
@Service
public class McpRouterService {
    public Mono<String> routeRequest(String serverName, McpMessage message) {
        Instance instance = selectServer(serverName);
        return mcpSseTransportProvider
            .sendMessage(instance, message)
            .timeout(Duration.ofSeconds(30));
    }
}
```

2. 负载均衡实现
```java
@Component
public class LoadBalancer {
    public Instance selectServer(List<Instance> instances) {
        // 实现轮询策略
        return instances.get(roundRobinIndex.getAndIncrement() % instances.size());
    }
}
```

验证方式：
```bash
# 通过mcp-client调用
curl -X POST \"http://localhost:8070/mcp-client/api/v1/tools/call\" \\
-H \"Content-Type: application/json\" \\
-d '{\"toolName\": \"getAllPersons\", \"arguments\": {}}'
```

### 第五步：实现服务健康检查

1. 健康检查服务
```java
@Component
public class HealthCheckService {
    @Scheduled(fixedRate = 30000)
    public void checkHealth() {
        List<Instance> instances = serverRegistry.getAllInstances();
        instances.forEach(this::checkInstanceHealth);
    }
}
```

2. 服务降级实现
```java
@Component
public class CircuitBreaker {
    public boolean isOpen(String serverName) {
        HealthStatus status = healthCheckService.getStatus(serverName);
        return status.getFailureCount() > threshold;
    }
}
```

验证方式：
```bash
# 停止一个mcp-server实例，观察健康检查日志
tail -f mcp-router-v2/logs/router.log
```

## 编译和部署步骤

1. 编译项目
```bash
cd mcp-router-v2
mvn clean install
```

2. 启动服务
```bash
mvn spring-boot:run
```

3. 验证服务
```bash
# 检查服务注册
curl -X GET \"http://localhost:8848/nacos/v2/ns/instance/list?serviceName=mcp-server-v2\"

# 验证SSE连接
curl -N \"http://localhost:8050/sse/connect?clientId=test\"

# 验证工具调用
curl -X POST \"http://localhost:8070/mcp-client/api/v1/tools/call\" \\
-H \"Content-Type: application/json\" \\
-d '{\"toolName\": \"getAllPersons\", \"arguments\": {}}'
```

## 注意事项

1. 配置检查
- 确保nacos服务正常运行
- 检查端口占用情况
- 验证配置文件正确性

2. 依赖管理
- 使用响应式依赖
- 避免使用阻塞式API
- 确保版本兼容性

3. 测试验证
- 每步完成后进行测试
- 确保向后兼容性
- 验证错误处理机制"}}

这个改造方案分为五个主要步骤：

1. 项目初始化与基础配置
   - 创建基础项目结构
   - 配置必要的依赖
   - 实现基础配置类

2. 实现MCP服务注册
   - 完整的服务注册机制
   - 元数据管理
   - 注册事件处理

3. 实现SSE通信
   - SSE连接管理
   - 会话超时处理
   - 消息转发机制

4. 实现MCP路由转发
   - 路由策略实现
   - 负载均衡
   - 超时处理

5. 实现服务健康检查
   - 定时健康检查
   - 服务降级机制
   - 熔断器实现

每个步骤都可以独立编译和验证，并且包含了具体的验证方法。您想从哪个步骤开始实施？




收工注册服务

curl -X POST "http://localhost:8050/mcp/servers/register" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-mcp-server",
    "version": "1.0.0",
    "ip": "127.0.0.1",
    "port": 8061,
    "sseEndpoint": "/sse",
    "status": "UP",
    "serviceGroup": "mcp-server",
    "healthy": true,
    "ephemeral": true,
    "weight": 1.0,
    "metadata": {
      "type": "test",
      "description": "Test MCP server for validation"
    }
  }'