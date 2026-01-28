# 如何添加 MCP Server

> 完整指南：创建并注册新的 MCP Server

## 📋 前置条件

- [ ] Java 17+
- [ ] Maven 3.6+
- [ ] 了解 Spring Boot 基础
- [ ] Nacos Server 运行中

---

## 🎯 目标

本指南将帮助您:
1. 创建新的 MCP Server 模块
2. 定义工具(Tools)
3. 注册到 Nacos
4. 测试验证

**预计耗时**: 20-30 分钟

---

## 步骤 1: 创建新模块

### 1.1 创建 Maven 模块

```bash
cd mcp-router-sse-parent
mkdir my-mcp-server
cd my-mcp-server
```

### 1.2 创建 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>my-mcp-server</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0</spring-ai.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <!-- Spring AI -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>
        
        <!-- Nacos Discovery -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
            <version>2022.0.0.0</version>
        </dependency>
        
        <!-- Lombok (optional) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

---

## 步骤 2: 定义工具(Tools)

### 2.1 创建 Tool Service

创建 `src/main/java/com/example/tools/MyToolService.java`:

```java
package com.example.tools;

import org.springframework.ai.tool.Tool;
import org.springframework.stereotype.Service;

@Service
public class MyToolService {
    
    /**
     * 获取当前时间
     */
    @Tool(description = "Get current timestamp in milliseconds")
    public long getCurrentTime() {
        return System.currentTimeMillis();
    }
    
    /**
     * 计算两个数的和
     */
    @Tool(description = "Calculate sum of two numbers")
    public int add(
        @Tool.Param(description = "First number") int a,
        @Tool.Param(description = "Second number") int b
    ) {
        return a + b;
    }
    
    /**
     * 字符串反转
     */
    @Tool(description = "Reverse a string")
    public String reverse(
        @Tool.Param(description = "Input string") String input
    ) {
        return new StringBuilder(input).reverse().toString();
    }
}
```

### 2.2 复杂 Tool 示例

```java
@Service
public class UserToolService {
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 查找用户
     */
    @Tool(description = "Find user by ID")
    public User findUserById(
        @Tool.Param(description = "User ID") Long userId
    ) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }
    
    /**
     * 搜索用户
     */
    @Tool(description = "Search users by name")
    public List<User> searchUsers(
        @Tool.Param(description = "Search keyword") String keyword
    ) {
        return userRepository.findByNameContaining(keyword);
    }
    
    /**
     * 创建用户
     */
    @Tool(description = "Create a new user")
    public User createUser(
        @Tool.Param(description = "User data") UserCreateRequest request
    ) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        return userRepository.save(user);
    }
}
```

---

## 步骤 3: 配置应用

### 3.1 创建 application.yml

```yaml
server:
  port: 8070  # 选择未使用的端口

spring:
  application:
    name: my-mcp-server  # 服务名称
  
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
        metadata:
          # 元数据，帮助路由器识别
          version: 1.0.0
          tools: getCurrentTime,add,reverse  # 工具列表
          description: My Custom MCP Server

# Spring AI MCP Server 配置
mcp:
  server:
    enabled: true
    path: /mcp  # SSE endpoint

# 日志配置
logging:
  level:
    com.example: DEBUG
    org.springframework.ai: DEBUG
```

### 3.2 创建主类

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient  // 启用服务发现
public class MyMcpServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MyMcpServerApplication.class, args);
    }
}
```

---

## 步骤 4: 启动和注册

### 4.1 启动服务

```bash
# 构建
mvn clean install

# 运行
mvn spring-boot:run
```

### 4.2 验证注册

```bash
# 检查健康状态
curl http://localhost:8070/actuator/health

# 检查 Nacos 注册
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=my-mcp-server

# 应该看到:
# {
#   "hosts": [{
#     "ip": "192.168.1.100",
#     "port": 8070,
#     "healthy": true
#   }]
# }
```

### 4.3 测试 MCP 连接

```bash
# 连接到 SSE endpoint
curl -N http://localhost:8070/mcp
```

**预期输出**:
```
event: initialize
data: {"version":"1.0.0"}

event: tools/list
data: {"tools":[{"name":"getCurrentTime","description":"..."}]}
```

---

## 步骤 5: 集成到项目

### 5.1 添加到父 POM

编辑根目录的 `pom.xml`:

```xml
<modules>
    <module>mcp-router-v3</module>
    <module>mcp-server-v6</module>
    <module>my-mcp-server</module>  <!-- 新添加 -->
</modules>
```

### 5.2 从 MCP Client 调用

```java
// 在 MCP Client 中使用
@RestController
public class MyController {
    
    @Autowired
    private ChatClient chatClient;
    
    @PostMapping("/query")
    public String query(@RequestBody String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }
}
```

测试：
```bash
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -d '{"query": "What is the current time?"}'
```

---

## 💡 最佳实践

### 1. 工具命名

```java
// ✅ 好的命名
@Tool(description = "Find user by email address")
public User findUserByEmail(String email)

// ❌ 不好的命名
@Tool(description = "get")
public User get(String e)
```

### 2. 参数描述

```java
// ✅ 清晰的描述
@Tool.Param(description = "User email address in format: user@example.com")
String email

// ❌ 模糊的描述  
@Tool.Param(description = "email")
String email
```

### 3. 错误处理

```java
@Tool(description = "Find user by ID")
public User findUserById(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> 
            new ToolExecutionException("User not found: " + userId)
        );
}
```

### 4. 输入验证

```java
@Tool(description = "Create user")
public User createUser(UserCreateRequest request) {
    // 验证输入
    if (request.getEmail() == null || !request.getEmail().contains("@")) {
        throw new IllegalArgumentException("Invalid email address");
    }
    
    // 处理逻辑
    return userRepository.save(toEntity(request));
}
```

---

## 🔍 调试技巧

### 1. 启用详细日志

```yaml
logging:
  level:
    org.springframework.ai.mcp: TRACE
    com.example: DEBUG
```

### 2. 查看工具注册

```bash
# 访问 actuator endpoints
curl http://localhost:8070/actuator/beans | jq '.[] | select(.type | contains("Tool"))'
```

### 3. 监控 Tool 调用

```java
@Aspect
@Component
public class ToolCallAspect {
    
    @Around("@annotation(org.springframework.ai.tool.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("Tool {} executed in {}ms", toolName, duration);
            return result;
        } catch (Exception e) {
            log.error("Tool {} failed", toolName, e);
            throw e;
        }
    }
}
```

---

## ✅ 验证清单

完成后确认：

- [ ] 服务成功启动在指定端口
- [ ] 在 Nacos 中可以看到服务注册
- [ ] SSE endpoint 可以连接
- [ ] Tools 列表正确返回
- [ ] 从 MCP Client 可以调用工具
- [ ] 日志正常无报错
- [ ] 健康检查通过

---

## 📚 相关文档

- [Spring AI 文档](https://docs.spring.io/spring-ai/reference/)
- [Nacos 文档](https://nacos.io/docs/)
- [MCP 协议规范](https://spec.modelcontextprotocol.io/)
- [API 参考](../reference/api.md)

---

## 🆘 遇到问题？

- [查看故障排除](troubleshooting.md)
- [创建 Issue](https://github.com/thefirstwind/mcp-router-sse-parent/issues)

**恭喜！您已成功创建了第一个 MCP Server！** 🎉
