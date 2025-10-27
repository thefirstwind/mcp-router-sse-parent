# MCP Server V5 IP地址修复总结

## 修复概述

成功修复了MCP Server V5的两个关键问题：
1. **IP地址为null**: 在注册到Nacos时IP地址显示为null的问题
2. **SSE连接错误**: `Received unrecognized SSE event type: null`错误

确保外部系统能够正确调用MCP Server服务。

## 修复的文件列表

### 1. 新增文件
- `CustomNacosMcpRegister.java` - 自定义IP地址注册配置
- `CustomNacosMcpProperties.java` - 自定义Nacos MCP Properties配置
- `start-with-ip-fixed.sh` - 修复后的启动脚本
- `test-ip-fix.sh` - 测试脚本
- `test-sse-connection.sh` - SSE连接测试脚本
- `IP_FIX_README.md` - 详细修复说明文档
- `FIX_SUMMARY.md` - 修复总结文档

### 2. 修改文件
- `application.yml` - 添加显式IP地址配置，修复SSE端点路径
- `McpServerConfig.java` - 使用标准WebFluxSseServerTransportProvider

### 3. 删除文件
- `CustomMcpServerTransportProvider.java` - 删除自定义Transport Provider

## 核心修复内容

### 1. 智能IP地址获取逻辑
```java
private String getLocalIpAddress() throws Exception {
    // 优先获取非回环地址
    String nonLoopbackIp = getNonLoopbackIpAddress();
    if (nonLoopbackIp != null && !nonLoopbackIp.isEmpty()) {
        return nonLoopbackIp;
    }
    
    // 使用localhost地址
    String localhostIp = InetAddress.getLocalHost().getHostAddress();
    if (localhostIp != null && !localhostIp.isEmpty()) {
        return localhostIp;
    }
    
    // 兜底使用127.0.0.1
    return "127.0.0.1";
}
```

### 2. 系统属性设置
```java
System.setProperty("spring.cloud.client.ip-address", localIp);
System.setProperty("spring.ai.alibaba.mcp.nacos.ip", localIp);
System.setProperty("nacos.client.ip", localIp);
System.setProperty("server.address", localIp);
// ... 更多属性设置
```

### 3. 自定义Properties初始化
```java
@PostConstruct
@Override
public void init() throws Exception {
    if (getIp() == null || getIp().isEmpty()) {
        String localIp = getLocalIpAddress();
        setIp(localIp);
    }
    super.init();
}
```

### 4. SSE连接修复
```java
// 使用标准的WebFluxSseServerTransportProvider
WebFluxSseServerTransportProvider provider = new WebFluxSseServerTransportProvider(
    objectMapper,
    baseUrl,
    "/mcp/message",  // 消息端点
    "/sse"          // SSE端点
);
```

## 修复效果

### 修复前
```json
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "null:8065/sse"
    }
  }
}
```

### 修复后
```json
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "127.0.0.1:8065/sse"
    }
  }
}
```

## 技术实现细节

### 1. 多层次IP地址获取策略
- **第一层**: 获取非回环网络接口的IP地址
- **第二层**: 使用`InetAddress.getLocalHost()`获取本地地址
- **第三层**: 兜底使用`127.0.0.1`

### 2. 系统属性覆盖
- 覆盖Spring Cloud相关属性
- 覆盖Nacos客户端相关属性
- 覆盖Spring AI Alibaba MCP相关属性

### 3. 配置优先级
- 使用`@Primary`注解确保自定义配置优先
- 在`@PostConstruct`中确保初始化顺序
- 通过`@EventListener(ApplicationReadyEvent.class)`确保启动时机

## 兼容性保证

### 1. 版本兼容性
- Spring Boot 2.7.18 ✅
- Java 17 ✅
- Spring AI Alibaba 1.0.0.3.250728 ✅
- Nacos 3.0.1 ✅

### 2. 功能兼容性
- 不影响现有MCP Server功能 ✅
- 不影响工具注册和调用 ✅
- 不影响SSE连接 ✅
- 向后兼容现有配置 ✅

## 使用方法

### 启动方式1: 使用修复脚本
```bash
cd mcp-server-v5
./start-with-ip-fixed.sh
```

### 启动方式2: 直接Maven启动
```bash
cd mcp-server-v5
mvn spring-boot:run
```

### 启动方式3: JVM参数启动
```bash
cd mcp-server-v5
mvn spring-boot:run -Dspring.ai.alibaba.mcp.nacos.ip=127.0.0.1
```

## 验证方法

### 1. 检查Nacos注册
```bash
curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v5&namespaceId=public&groupName=mcp-server"
```

### 2. 检查MCP Server健康状态
```bash
curl "http://127.0.0.1:8065/actuator/health"
```

### 3. 测试MCP Router连接
```bash
curl "http://localhost:8052/mcp/router/tools/mcp-server-v5"
```

### 4. 使用测试脚本
```bash
cd mcp-server-v5
./test-ip-fix.sh
```

## 问题解决

### ✅ 已解决的问题
1. **IP地址为null**: 通过智能IP地址获取逻辑解决
2. **Nacos注册失败**: 通过系统属性设置解决
3. **MCP Router无法连接**: 通过正确的URL格式解决
4. **配置不生效**: 通过自定义Properties类解决
5. **SSE连接错误**: 通过使用标准WebFluxSseServerTransportProvider解决
6. **SSE事件类型错误**: 通过正确的MCP协议实现解决

### 🔧 技术改进
1. **IP地址获取**: 从简单获取改为智能获取
2. **系统属性**: 从部分设置改为全面设置
3. **配置管理**: 从依赖默认配置改为主动配置
4. **错误处理**: 从无兜底改为多层兜底
5. **SSE实现**: 从自定义实现改为标准MCP协议实现
6. **端点配置**: 统一SSE端点路径配置

## 总结

通过创建自定义的IP地址获取和配置类，成功解决了MCP Server V5在Nacos注册时IP地址为null的问题。修复方案具有以下特点：

1. **全面性**: 覆盖了所有相关的系统属性和配置
2. **智能性**: 实现了多层次的IP地址获取策略
3. **兼容性**: 保持了与现有系统的完全兼容
4. **可靠性**: 提供了多层兜底机制确保稳定性
5. **可维护性**: 提供了详细的文档和测试脚本

修复后的系统能够正确注册到Nacos，并且外部系统可以通过mcp-router-v2正常调用MCP Server服务。 