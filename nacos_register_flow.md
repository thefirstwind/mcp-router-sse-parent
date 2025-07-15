# MCP (Model Context Protocol) 注册与调用流程分析

## 1. MCP Server 注册到 Nacos

### 1.1 配置类和属性定义

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: mcp-server
        version: 0.0.1
        description: MCP Server Instance
      nacos:
        enabled: true
        server-addr: 127.0.0.1:8848
        username: nacos
        password: nacos
        registry:
          service-namespace: public
          service-group: mcp-server
```

### 1.2 注册流程

1. **自动配置类初始化**
- `NacosMcpRegistryAutoConfiguration`: 负责创建 MCP Server 注册相关的 Bean
- `NacosMcpRegistryProperties`: Nacos 注册相关的配置属性类
- `McpServerProperties`: MCP Server 的基本配置属性

2. **注册实现类 `NacosMcpRegister`**
```java
// 1. 服务信息注册
McpServerInfo mcpServerInfo = new McpServerInfo();
mcpServerInfo.setName(this.serverInfo.name());
mcpServerInfo.setVersion(this.serverInfo.version());
mcpServerInfo.setDescription(serverDescription);
mcpServerInfo.setEnabled(true);

// 2. 工具信息注册
McpToolsInfo mcpToolsInfo = new McpToolsInfo();
mcpToolsInfo.setTools(toolsNeedtoRegister);
mcpToolsInfo.setToolsMeta(this.toolsMeta);

// 3. 服务实例注册
Instance instance = new Instance();
instance.setIp(ip);
instance.setPort(port);
instance.setHealthy(true);
instance.setEnabled(true);
instance.setMetadata(metadata);
```

## 2. MCP Client 注册到 Nacos

### 2.1 配置类和属性定义

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        name: mcp-client-webflux
        version: 0.0.1
        initialized: true
        request-timeout: 600s
        nacos-enabled: true
    alibaba:
      mcp:
        nacos:
          enabled: true
          server-addr: 127.0.0.1:8848
          username: nacos
          password: nacos
          registry:
            service-namespace: xxx
            service-group: mcp-server
```

### 2.2 注册流程

1. **自动配置类初始化**
- `NacosMcpClientAutoConfiguration`: 负责创建 MCP Client 的负载均衡客户端
- `NacosMcpSseClientAutoConfiguration`: 负责创建基于 SSE 的 MCP Client

2. **客户端实现**
```java
// 同步客户端
LoadbalancedMcpSyncClient loadbalancedMcpSyncClient = LoadbalancedMcpSyncClient.builder()
    .serviceName(serviceName)
    .namingService(namingService)
    .nacosConfigService(nacosConfigService)
    .serviceGroup(nacosMcpRegistryProperties.getServiceGroup())
    .build();

// 初始化和订阅
loadbalancedMcpSyncClient.init();
loadbalancedMcpSyncClient.subscribe();
```

## 3. Client 调用 Server 流程

### 3.1 建立连接

1. **获取服务实例**
```java
List<Instance> instances = namingService.selectInstances(
    serviceName + "-mcp-service", 
    serviceGroup, 
    true
);
```

2. **创建 SSE 传输层**
```java
String baseUrl = instance.getMetadata().getOrDefault("scheme", "http") + 
                "://" + instance.getIp() + ":" + instance.getPort();

WebFluxSseServerTransportProvider transportProvider = new WebFluxSseServerTransportProvider(
    WebClient.builder()
        .baseUrl(baseUrl)
        .build()
);
```

### 3.2 调用流程

1. **初始化连接**
- 客户端通过 SSE 建立长连接
- 服务端保持连接并等待客户端请求

2. **请求处理**
- 客户端发送请求到服务端
- 服务端处理请求并返回结果
- 支持异步流式响应

## 4. 注册到 Nacos 的数据格式

### 4.1 服务实例数据

```json
{
  "name": "mcp-server",
  "groupName": "mcp-server",
  "clusters": [],
  "metadata": {
    "preserved.register.source": "SPRING_CLOUD",
    "scheme": "http",
    "version": "0.0.1"
  },
  "ip": "127.0.0.1",
  "port": 8080,
  "healthy": true,
  "enabled": true
}
```

### 4.2 配置数据

```json
{
  "mcpServerInfo": {
    "name": "mcp-server",
    "version": "0.0.1",
    "description": "MCP Server Instance",
    "enabled": true
  },
  "mcpToolsInfo": {
    "tools": [...],
    "toolsMeta": {...}
  }
}
```

## 5. 关键类说明

1. **Server 端**
- `NacosMcpRegister`: 负责服务注册
- `McpServerInfo`: 服务信息实体类
- `McpToolsInfo`: 工具信息实体类

2. **Client 端**
- `LoadbalancedMcpSyncClient`: 负载均衡客户端
- `WebFluxSseServerTransportProvider`: SSE 传输层实现
- `McpClientConfig`: 客户端配置类

## 6. 注意事项

1. Server 和 Client 都需要正确配置 Nacos 的连接信息
2. 确保 Nacos 服务可用且网络连通
3. SSE 连接需要处理断线重连机制
4. 服务注册需要考虑健康检查
5. 配置信息变更需要及时同步 