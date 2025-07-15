# Nacos API 与代码对应关系分析

本文档分析 spring-ai-alibaba-mcp 项目中 Nacos API 的使用情况及其对应的代码实现。

## 1. 服务注册 API

### API 调用
```bash
curl -X POST "127.0.0.1:8848/nacos/v3/client/ns/instance" \
  -d "serviceName=mcp-server-v2&ip=127.0.0.1&port=3306"
```

### 代码实现
主要实现类：`NacosMcpRegister`
注册触发点：`onApplicationEvent(WebServerInitializedEvent event)`

```java
// NacosMcpRegister.java
Instance instance = new Instance();
instance.setIp(nacosMcpProperties.getIp());
instance.setPort(port);
instance.setEphemeral(nacosMcpRegistryProperties.isServiceEphemeral());
instance.setMetadata(metadata);
namingService.registerInstance(
    this.serverInfo.name() + McpNacosConstant.SERVER_NAME_SUFFIX,
    nacosMcpRegistryProperties.getServiceGroup(), 
    instance
);
```

## 2. 配置查询 API

### API 调用
```bash
curl -X GET "http://localhost:8848/nacos/v2/cs/history/configs?pageNo=1&pageSize=100&namespaceId=public&appName=mcp-server-v2"
```

### 代码实现
主要实现类：`NacosMcpRegister`

```java
// NacosMcpRegister.java
String content = configService.getConfig(
    this.serverInfo.name() + McpNacosConstant.SERVER_CONFIG_SUFFIX,
    McpNacosConstant.SERVER_GROUP, 
    TIME_OUT_MS
);
```

## 3. 版本信息配置 API

### API 调用
```bash
curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-mcp-versions.json&groupName=mcp-server-versions'
```

### 代码实现
```java
// NacosMcpRegister.java
ServerVersionDetail serverVersionDetail = new ServerVersionDetail();
serverVersionDetail.setVersion(this.serverInfo.version());
McpServerBasicInfo serverBasicInfo = new McpServerBasicInfo();
serverBasicInfo.setName(this.serverInfo.name());
serverBasicInfo.setVersionDetail(serverVersionDetail);
```

## 4. 服务器配置 API

### API 调用
```bash
curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-server.json&groupName=mcp-server'
```

### 代码实现
```java
// NacosMcpRegister.java
McpServerRemoteServiceConfig remoteServerConfigInfo = new McpServerRemoteServiceConfig();
String contextPath = this.nacosMcpRegistryProperties.getSseExportContextPath();
if (StringUtils.isBlank(contextPath)) {
    contextPath = "";
}
remoteServerConfigInfo.setExportPath(contextPath + this.mcpServerProperties.getSseEndpoint());
serverBasicInfo.setRemoteServerConfig(remoteServerConfigInfo);
```

## 5. 工具配置 API

### API 调用
```bash
curl -X GET '127.0.0.1:8848/nacos/v3/client/cs/config?dataId=7dfbf3c6-fd63-4c49-9c11-cd6e6ed735a5-1.0.1-mcp-tools.json&groupName=mcp-tools'
```

### 代码实现
```java
// NacosMcpRegister.java
if (this.serverCapabilities.tools() != null) {
    List<McpSchema.Tool> toolsNeedtoRegister = this.tools.stream()
        .map(McpServerFeatures.AsyncToolSpecification::tool)
        .toList();
    mcpToolSpec.setTools(toolsToNacosList);
}
```

## 6. 实例列表查询 API

### API 调用
```bash
curl -X GET "http://localhost:8848/nacos/v2/ns/instance/list?serviceName=mcp-server-v2&groupName=mcp-server"
```

### 代码实现
主要实现类：`McpServerRegistry`

```java
// McpServerRegistry.java
List<Instance> instances = namingService.selectInstances(SERVICE_NAME, GROUP_NAME, true);
instances.forEach(mcpServerRegistry::registerInstance);
```

## 7. 配置属性定义

```java
@ConfigurationProperties(prefix = NacosMcpRegistryProperties.CONFIG_PREFIX)
public class NacosMcpRegistryProperties {
    public static final String CONFIG_PREFIX = "spring.ai.alibaba.mcp.nacos.registry";
    String serviceGroup = "DEFAULT_GROUP";
    String serviceName;
    String sseExportContextPath;
    boolean serviceRegister = true;
    boolean serviceEphemeral = true;
}
```

## 8. 自动配置类

```java
@EnableConfigurationProperties({ 
    NacosMcpRegistryProperties.class, 
    NacosMcpProperties.class,
    McpServerProperties.class 
})
@AutoConfiguration(after = McpServerAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = McpServerProperties.CONFIG_PREFIX, 
    name = "enabled", 
    havingValue = "true",
    matchIfMissing = true
)
public class NacosMcpRegistryAutoConfiguration {
    // 配置类实现
}
```

这些 API 和代码的对应关系显示了项目如何使用 Nacos 进行服务注册、配置管理和服务发现。每个 API 都有其对应的代码实现，并且通过配置属性来控制其行为。 