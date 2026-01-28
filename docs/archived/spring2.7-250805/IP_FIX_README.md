# MCP Server V5 IP地址修复说明

## 问题描述

MCP Server V5在注册到Nacos时，URL中的IP地址显示为null：
```json
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "null:8065/sse"
    }
  }
}
```

这导致外部系统通过mcp-router-v2调用时无法找到对应的MCP Server服务。

## 问题原因

1. `NacosMcpProperties`的`init()`方法中，`NetUtils.localIp()`在某些环境下可能返回null
2. 系统属性没有正确设置IP地址
3. 缺少自定义的IP地址获取逻辑

## 修复方案

### 1. 创建自定义IP地址获取类

**文件**: `CustomNacosMcpRegister.java`
- 在应用启动完成后设置系统属性
- 实现智能IP地址获取逻辑，优先获取非回环地址
- 设置所有相关的Nacos和Spring Cloud系统属性

### 2. 创建自定义Properties配置类

**文件**: `CustomNacosMcpProperties.java`
- 继承`NacosMcpProperties`并重写`init()`方法
- 确保IP地址在初始化时就被正确设置
- 使用`@Primary`注解确保优先使用自定义配置

### 3. 更新配置文件

**文件**: `application.yml`
- 显式设置`spring.ai.alibaba.mcp.nacos.ip`属性
- 确保配置的完整性

### 4. 提供启动脚本

**文件**: `start-with-ip-fixed.sh`
- 通过JVM参数设置IP地址
- 包含所有必要的系统属性设置

## 修复内容

### 核心修复逻辑

1. **智能IP地址获取**:
   ```java
   private String getLocalIpAddress() throws Exception {
       // 首先尝试获取非回环地址
       String nonLoopbackIp = getNonLoopbackIpAddress();
       if (nonLoopbackIp != null && !nonLoopbackIp.isEmpty()) {
           return nonLoopbackIp;
       }
       
       // 如果获取不到非回环地址，则使用localhost
       String localhostIp = InetAddress.getLocalHost().getHostAddress();
       if (localhostIp != null && !localhostIp.isEmpty()) {
           return localhostIp;
       }
       
       // 最后兜底使用127.0.0.1
       return "127.0.0.1";
   }
   ```

2. **系统属性设置**:
   ```java
   System.setProperty("spring.cloud.client.ip-address", localIp);
   System.setProperty("spring.ai.alibaba.mcp.nacos.ip", localIp);
   System.setProperty("nacos.client.ip", localIp);
   // ... 更多属性设置
   ```

3. **自定义Properties初始化**:
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

## 使用方法

### 方法1: 使用修复后的启动脚本
```bash
cd mcp-server-v5
./start-with-ip-fixed.sh
```

### 方法2: 直接使用Maven启动
```bash
cd mcp-server-v5
mvn spring-boot:run
```

### 方法3: 使用JVM参数启动
```bash
cd mcp-server-v5
mvn spring-boot:run \
  -Dspring.ai.alibaba.mcp.nacos.ip=127.0.0.1 \
  -Dspring.cloud.client.ip-address=127.0.0.1
```

## 验证修复效果

1. **检查Nacos注册信息**:
   ```bash
   curl "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=mcp-server-v5&namespaceId=public&groupName=mcp-server"
   ```

2. **检查MCP Server健康状态**:
   ```bash
   curl "http://127.0.0.1:8065/actuator/health"
   ```

3. **测试MCP Router连接**:
   ```bash
   curl "http://localhost:8052/mcp/router/tools/mcp-server-v5"
   ```

## 预期结果

修复后，Nacos中的MCP Server配置应该显示正确的IP地址：
```json
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "127.0.0.1:8065/sse"
    }
  }
}
```

## 注意事项

1. 确保Nacos服务正在运行（默认端口8848）
2. 确保mcp-router-v2服务正在运行（默认端口8052）
3. 如果使用Docker或容器环境，可能需要额外的网络配置
4. 修复后的代码兼容Spring Boot 2.7.18和Java 17

## 技术细节

- **Spring Boot版本**: 2.7.18
- **Java版本**: 17
- **Spring AI Alibaba版本**: 1.0.0.3.250728
- **Nacos版本**: 3.0.1
- **修复类型**: 配置和系统属性设置
- **兼容性**: 向后兼容，不影响现有功能 