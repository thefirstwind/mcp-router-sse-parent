# MCP Router 和 MCP Server 框架结构分析

## 1. 整体架构概览

这是一个基于 **Spring Boot + Spring AI + Nacos** 的 MCP (Model Context Protocol) 微服务架构，包含以下核心组件：

- **MCP Router V3**: 路由网关，负责服务发现、负载均衡和消息路由
- **MCP Server V5**: 具体的 MCP 服务实现，提供工具和功能
- **Nacos**: 服务注册与发现中心
- **Spring AI Alibaba**: 提供 MCP 协议的自动配置支持

## 2. MCP Router V3 架构分析

### 2.1 技术栈

```xml
<!-- 核心依赖 -->
- Spring Boot 3.2.5 (WebFlux)
- Spring AI 1.0.0
- Spring AI Alibaba 1.0.0.3.250728
- Nacos Client 3.0.1
- Java 17
```

### 2.2 核心组件结构

```
mcp-router-v3/
├── controller/           # REST API 控制器
│   ├── McpRouterController.java      # 主要路由控制器
│   ├── HealthCheckController.java    # 健康检查
│   ├── McpSseController.java         # SSE 连接管理
│   └── SmartToolController.java      # 智能工具管理
├── service/             # 业务逻辑服务
│   ├── McpRouterService.java         # 核心路由服务
│   ├── McpClientManager.java         # MCP 客户端管理
│   ├── LoadBalancer.java             # 负载均衡器
│   ├── HealthCheckService.java       # 健康检查服务
│   └── CircuitBreakerService.java    # 熔断器服务
├── config/              # 配置类
│   ├── NacosMcpRegistryConfig.java   # Nacos 注册配置
│   └── McpRouterNacosRegistration.java # 路由注册
└── registry/            # 服务注册
    └── McpServerRegistry.java        # 服务注册管理
```

### 2.3 核心功能

1. **智能路由**: 自动发现 MCP 服务并路由请求
2. **负载均衡**: 支持多种负载均衡策略
3. **健康检查**: 实时监控服务健康状态
4. **服务发现**: 通过 Nacos 发现 MCP 服务
5. **熔断保护**: 防止服务雪崩

## 3. MCP Server V5 架构分析

### 3.1 技术栈

```xml
<!-- 核心依赖 -->
- Spring Boot 2.7.18 (继承自父项目)
- Spring AI Alibaba MCP Server
- Spring AI MCP Server WebFlux
- Nacos Spring Context 2.1.1
- Java 17
```

### 3.2 核心组件结构

```
mcp-server-v5/
├── config/              # 配置类
│   ├── McpServerConfig.java          # MCP 服务器配置
│   ├── NacosRegistrationConfig.java  # Nacos 注册配置
│   ├── CustomNacosMcpProperties.java # 自定义 Nacos 属性
│   └── CustomNacosMcpRegister.java   # 自定义注册器
├── tools/               # MCP 工具实现
│   └── PersonManagementTool.java     # 人员管理工具
└── model/               # 数据模型
```

### 3.3 核心功能

1. **MCP 协议实现**: 提供标准的 MCP 服务器功能
2. **工具注册**: 注册和管理 MCP 工具
3. **SSE 传输**: 支持 Server-Sent Events 传输
4. **Nacos 注册**: 自动注册到 Nacos 服务发现

## 4. 服务注册与发现机制

### 4.1 注册流程

```java
// MCP Server 注册到 Nacos
1. 应用启动 → NacosMcpRegistryAutoConfiguration
2. 创建 NacosMcpRegister Bean
3. 获取本地 IP 地址 (NetUtils.localIp())
4. 注册服务实例到 Nacos
5. 上传 MCP 配置信息
```

### 4.2 发现流程

```java
// MCP Router 发现服务
1. 通过 NacosMcpRegistryConfig 配置
2. 使用 McpServerRegistry 查询服务
3. 获取健康实例列表
4. 负载均衡选择最优实例
5. 建立连接并路由请求
```

## 5. IP 地址获取机制

### 5.1 待解决问题，更新到spring-boot2.7之后

当前项目中 MCP Server V5 存在 IP 地址为 null 的问题：

```json
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "null:8065/sse"  // IP 地址为 null
    }
  }
}
```

### 5.2 解决方案

项目已经实现了自定义的 IP 地址获取机制：

```java
// CustomNacosMcpProperties.java
@PostConstruct
@Override
public void init() throws Exception {
    if (getIp() == null || getIp().isEmpty()) {
        String localIp = getLocalIpAddress();
        setIp(localIp);
    }
    super.init();
}

// 智能 IP 地址获取策略
private String getLocalIpAddress() throws Exception {
    // 1. 优先获取非回环地址
    String nonLoopbackIp = getNonLoopbackIpAddress();
    if (nonLoopbackIp != null && !nonLoopbackIp.isEmpty()) {
        return nonLoopbackIp;
    }
    
    // 2. 使用 localhost 地址
    String localhostIp = InetAddress.getLocalHost().getHostAddress();
    if (localhostIp != null && !localhostIp.isEmpty()) {
        return localhostIp;
    }
    
    // 3. 兜底使用 127.0.0.1
    return "127.0.0.1";
}
```

## 6. 版本兼容性问题

### 6.1 当前版本配置

- **父项目**: Spring Boot 3.2.5
- **MCP Server V5**: Spring Boot 2.7.18 (需要降级)
- **Java**: 17
- **Spring AI Alibaba**: 1.0.0.3.250728


### 2. SSE连接问题 - 已修复 ✅
**问题**: `Received unrecognized SSE event type: null`
**修复结果**: SSE端点正常响应
```bash
$ curl -N -H "Accept: text/event-stream" "http://127.0.0.1:8065/sse"
data: {"type":"connection","status":"connected","baseUrl":"http://127.0.0.1:8065","timestamp":1754386992538}
```

### 3. MCP Server启动问题 - 已修复 ✅
**问题**: `java.lang.NoClassDefFoundError: org/springframework/http/HttpStatusCode`
**修复结果**: 服务正常启动
```json
{
  "status": "UP"
}
```

### 4. Nacos注册问题 - 已修复 ✅
**问题**: 服务无法正确注册到Nacos
**修复结果**: 服务已正确注册
```json
{
  "name": "mcp-server@@mcp-server-v5",
  "groupName": "mcp-server",
  "hosts": [
    {
      "ip": "127.0.0.1",
      "port": 8065,
      "healthy": true
    }
  ]
}
```

## 🔧 技术解决方案

### 1. Spring Boot 2.7.18兼容性修复
- 创建了`SpringBoot27CompatibleSseTransportProvider`
- 避免了使用Spring Framework 6.0+的`HttpStatusCode`类
- 实现了完整的MCP协议兼容的SSE传输

### 2. IP地址获取优化
- 实现了智能IP地址获取逻辑
- 优先获取非回环地址
- 提供了多层兜底机制

### 3. 系统属性配置
- 设置了所有相关的Spring Cloud和Nacos系统属性
- 确保IP地址在整个系统中一致

## 📊 功能验证

| 功能 | 状态 | 测试结果 |
|------|------|----------|
| MCP Server启动 | ✅ 成功 | 健康检查返回UP |
| Nacos注册 | ✅ 成功 | IP地址正确显示 |
| SSE端点 | ✅ 成功 | 返回标准SSE数据 |
| MCP Message端点 | ✅ 成功 | 返回JSON响应 |
| 工具注册 | ✅ 成功 | PersonManagementTool已注册 |


### 测试端点
```bash
# 健康检查
curl "http://127.0.0.1:8065/actuator/health"

# SSE连接
curl -N -H "Accept: text/event-stream" "http://127.0.0.1:8065/sse"

# MCP消息端点
curl "http://127.0.0.1:8065/mcp/message"

# Nacos注册信息
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v5&namespaceId=public&groupName=mcp-server"
```


修复方法，参考  spring-ai-alibaba中的mcp nacos autoconfig中的实现方法，自定义实现 sseTransportProvider，不要直接实现jsonRpc，跨度太大了


# nacos配置要求
## 1 配置管理列表中：需要创建3条记录， 

|  dataId  |  Group  |  格式  |  归属应用  |
|---------|---------|---------|---------|
| {md5}-version-mcp-tools.json|mcp-tools|JSON|mcp-server-v5|
| {md5}-mcp-versions.json|mcp-server-versions|JSON|mcp-server-v5|
| {md5}-{version}-mcp-server.json|mcp-server|JSON|mcp-server-V5|


## 2 服务列表中需要注册
服务名：mcp-server-v5
分组名称：mcp-server


## 3 MCP管理

| MCP Server | 支持能力 | 类型 | 版本 | 
|--------|--------|--------|--------|
|mcp-server-v5| TOOL| mcp-sse | {version} |

MCP Server详情
命名空间：public
名称：mcp-server-v5
类型：sse
描述：{description}
Server Config:
```
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "127.0.0.1:8065/sse"
    }
  }
}
```