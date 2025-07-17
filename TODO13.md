根据TODO10.md、nacos-api-code-anlysis.md和nacos-api-code.md的分析，我来制定mcp-router的改造方案。主要问题是需要将HTTP通信改为SSE通信，并严格遵循MCP协议规范。

让我们分步骤进行改造：

### 第一步：添加MCP协议依赖和配置

1. 修改 mcp-router/pom.xml：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-core</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-transport-sse</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

2. 添加MCP配置类：
```java
@Configuration
public class McpRouterConfig {
    @Bean
    public WebFluxSseServerTransportProvider sseServerTransportProvider() {
        return new WebFluxSseServerTransportProvider();
    }
    
    @Bean
    public McpAsyncClient mcpAsyncClient(WebClient.Builder builder) {
        return new McpAsyncClient(builder);
    }
}
```

验证步骤：
```bash
mvn -f mcp-router/pom.xml clean install -nsu
# 启动服务，检查是否正常启动
```

### 第二步：实现SSE服务端

1. 添加SSE控制器：
```java
@RestController
@RequestMapping("/sse")
public class McpSseController {
    private final WebFluxSseServerTransportProvider sseProvider;
    
    @GetMapping
    public Mono<Void> handleSse(ServerHttpRequest request, ServerHttpResponse response) {
        return sseProvider.handle(request, response);
    }
}
```

2. 修改application.yml配置：
```yaml
spring:
  ai:
    mcp:
      sse:
        endpoint: /sse
      session:
        timeout: 60s
```

验证步骤：
```bash
mvn -f mcp-router/pom.xml clean install -nsu
# 启动服务，测试SSE端点
curl -N http://localhost:8050/sse
```

### 第三步：改造服务注册机制

1. 修改McpServerRegistryConfig：
```java
@Configuration
public class McpServerRegistryConfig {
    @Bean
    public NacosRegistryProperties nacosRegistryProperties() {
        NacosRegistryProperties properties = new NacosRegistryProperties();
        properties.setServiceGroup("mcp-server");
        properties.setServiceEphemeral(true);
        return properties;
    }
    
    @Bean
    public McpServerRegistry mcpServerRegistry(NamingService namingService) {
        return new McpServerRegistry(namingService, nacosRegistryProperties());
    }
}
```

2. 实现服务发现监听：
```java
@Component
public class McpServerDiscoveryListener implements ApplicationListener<WebServerInitializedEvent> {
    private final McpServerRegistry serverRegistry;
    
    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        serverRegistry.startDiscovery();
    }
}
```

验证步骤：
```bash
mvn -f mcp-router/pom.xml clean install -nsu
# 启动服务，检查Nacos中的服务注册情况
curl -X GET "http://localhost:8848/nacos/v2/ns/instance/list?serviceName=mcp-server-v2&groupName=mcp-server"
```

### 第四步：实现MCP路由转发

1. 添加路由服务：
```java
@Service
public class McpRouterService {
    private final McpAsyncClient mcpClient;
    private final McpServerRegistry serverRegistry;
    
    public Mono<McpResponse> routeRequest(McpRequest request) {
        return serverRegistry.getAvailableServer()
            .flatMap(server -> mcpClient.sendRequest(server, request));
    }
}
```

2. 修改控制器：
```java
@RestController
@RequestMapping("/api/mcp")
public class McpRouterController {
    private final McpRouterService routerService;
    
    @PostMapping("/route")
    public Mono<McpResponse> route(@RequestBody McpRequest request) {
        return routerService.routeRequest(request);
    }
}
```

验证步骤：
```bash
mvn -f mcp-router/pom.xml clean install -nsu
# 启动服务，测试路由功能
curl -X POST http://localhost:8050/api/mcp/route -H "Content-Type: application/json" -d '{"toolName": "getAllPersons"}'
```

### 第五步：实现会话管理

1. 添加会话管理：
```java
@Component
public class McpSessionManager {
    private final Map<String, SseSession> sessions = new ConcurrentHashMap<>();
    
    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> 
            now - entry.getValue().getLastAccessTime() > 60000);
    }
}
```

2. 配置会话超时：
```yaml
spring:
  ai:
    mcp:
      session:
        timeout: 60s
        cleanup-interval: 30s
```

验证步骤：
```bash
mvn -f mcp-router/pom.xml clean install -nsu
# 启动服务，测试长连接会话
```

每个步骤完成后，都需要进行如下验证：
1. 编译并启动服务
2. 检查服务注册状态
3. 测试SSE连接
4. 验证路由功能
5. 测试会话管理

完整验证流程：
```bash
# 1. 启动所有服务
sh ./run-demo.sh

# 2. 验证服务注册
curl -X GET "http://localhost:8848/nacos/v2/ns/instance/list?serviceName=mcp-server-v2&groupName=mcp-server"

# 3. 测试客户端调用
curl "http://localhost:8070/mcp-client/api/v1/tools/list"

# 4. 测试数据库操作
curl -X POST http://localhost:8070/mcp-client/api/v1/tools/call \
-H "Content-Type: application/json" \
-d '{"toolName": "getAllPersons", "arguments": {}}'
```

需要我详细解释任何步骤吗？