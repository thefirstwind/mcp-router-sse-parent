# Nacos MCP Router Spring

一个基于 Spring Boot 和 Nacos 的 Model Context Protocol (MCP) 路由器，使用官方 Spring AI MCP SDK 和 MCP Java SDK 重构。

## 项目概述

这个项目提供了一个完整的 MCP 路由器解决方案，使用 Nacos 作为服务发现和注册中心，支持多种 MCP 服务器的管理和路由。

## 🚀 最新更新 - 官方SDK迁移

### 依赖改写

项目已从自定义 MCP 实现迁移到官方 SDK：

#### 新增依赖

```xml
<!-- Spring AI MCP 支持 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>

<!-- 核心 MCP SDK -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>mcp</artifactId>
</dependency>

<!-- MCP WebFlux SSE Transport -->
<dependency>
    <groupId>org.springframework.experimental</groupId>
    <artifactId>mcp-webflux-sse-transport</artifactId>
</dependency>
```

#### 配置更新

应用配置已更新为 Spring AI MCP 格式：

```yaml
spring:
  ai:
    mcp:
      server:
        name: nacos-mcp-router
        version: 1.0.0
        transport:
          type: sse
          endpoint: /mcp/sse
        capabilities:
          tools:
            list-changed: true
          resources:
            subscribe: true
            list-changed: true
          prompts:
            list-changed: true
          logging:
            level: INFO
```

### 主要优势

1. **官方支持**: 使用 Spring AI 官方的 MCP 实现
2. **标准协议**: 完全兼容 MCP 2024-11-05 规范
3. **多传输**: 支持 STDIO、SSE、WebSocket 等传输方式
4. **类型安全**: 使用 Java 记录类型确保类型安全
5. **响应式**: 基于 Project Reactor 的响应式编程

## 核心功能

### 1. MCP 服务器管理
- **注册**: 支持多种传输类型的 MCP 服务器注册
- **发现**: 基于 Nacos 的服务发现机制
- **健康检查**: 实时监控 MCP 服务器状态
- **负载均衡**: 支持多实例负载均衡

### 2. 工具执行
- **动态发现**: 自动发现 MCP 服务器提供的工具
- **类型安全**: 使用 Spring AI 的 @Tool 注解
- **参数验证**: 自动参数验证和类型转换
- **错误处理**: 统一的错误处理机制

### 3. 资源管理  
- **资源访问**: 统一的资源访问接口
- **内容缓存**: 支持资源内容缓存
- **订阅机制**: 支持资源变更订阅
- **搜索功能**: 支持资源搜索和过滤

### 4. 提示模板
- **模板管理**: 支持参数化提示模板
- **动态生成**: 支持动态参数填充
- **版本控制**: 支持提示模板版本管理
- **搜索功能**: 支持提示模板搜索

## 快速开始

### 环境要求

- Java 17+
- Spring Boot 3.4.2+
- Nacos Server 2.3.0+
- Maven 3.9+

### 启动步骤

1. **启动 Nacos**
```bash
./start-nacos.sh
```

2. **构建项目**
```bash
mvn clean package -DskipTests
```

3. **启动应用**
```bash
./run.sh
```

4. **注册 MCP 服务器**
```bash
./demo-register-server.sh
```

### 配置说明

#### Nacos 配置
```yaml
nacos:
  server-addr: 127.0.0.1:8848
  username: nacos
  password: nacos
  ai:
    mcp:
      registry:
        enabled: true
```

#### MCP 传输配置
```yaml
spring:
  ai:
    mcp:
      server:
        transport:
          type: sse  # 支持 stdio, sse, websocket
          endpoint: /mcp/sse
```

## API 接口

### RESTful API

#### MCP 服务器管理
- `GET /api/mcp/servers` - 列出所有 MCP 服务器
- `POST /api/mcp/servers/register` - 注册新的 MCP 服务器
- `DELETE /api/mcp/servers/{serverName}` - 注销 MCP 服务器

#### 工具管理
- `GET /api/mcp/servers/{serverName}/tools` - 获取服务器工具
- `POST /api/mcp/servers/{serverName}/tools/{toolName}` - 执行工具

#### 资源管理
- `GET /api/mcp/resources` - 列出所有资源
- `GET /api/mcp/servers/{serverName}/resources/{resourceUri}` - 读取资源

#### 提示管理
- `GET /api/mcp/prompts` - 列出所有提示
- `POST /api/mcp/servers/{serverName}/prompts/{promptName}/execute` - 执行提示

### JSON-RPC 2.0 API

#### 核心方法
- `initialize` - 协议初始化
- `notifications/initialized` - 初始化完成通知

#### 工具方法
- `tools/list` - 列出可用工具
- `tools/call` - 执行工具

#### 资源方法
- `resources/list` - 列出可用资源
- `resources/read` - 读取资源内容

#### 提示方法
- `prompts/list` - 列出可用提示
- `prompts/get` - 获取提示内容

## 监控和管理

### 健康检查
访问 `http://localhost:8001/actuator/health` 查看应用健康状态

### 指标监控
访问 `http://localhost:8001/actuator/metrics` 查看应用指标

### MCP 专用端点
访问 `http://localhost:8001/actuator/mcp` 查看 MCP 特定信息

## 测试

### 单元测试
```bash
mvn test
```

### 集成测试
```bash
mvn verify
```

### 覆盖率测试
```bash
./test-coverage.sh
```

## 部署

### Docker 部署
```bash
docker build -t nacos-mcp-router .
docker run -p 8001:8001 nacos-mcp-router
```

### Kubernetes 部署
```bash
kubectl apply -f k8s/
```

## 性能优化

### 缓存配置
- 使用 Spring Cache 进行工具和资源缓存
- 支持多级缓存策略
- 可配置缓存过期时间

### 连接池
- WebFlux 连接池优化
- Nacos 连接池配置
- 数据库连接池调优

## 安全考虑

### 认证授权
- 支持 JWT 令牌认证
- RBAC 角色权限控制
- API 限流和防护

### 数据安全
- 敏感数据加密存储
- 传输层 TLS 加密
- 输入参数验证

## 故障排除

### 常见问题

1. **Nacos 连接失败**
   - 检查 Nacos 服务器状态
   - 验证网络连接
   - 确认认证信息

2. **MCP 服务器注册失败**
   - 检查服务器端口可达性
   - 验证传输类型配置
   - 查看服务器日志

3. **工具执行超时**
   - 调整超时配置
   - 检查目标服务性能
   - 优化网络连接

### 日志配置
```yaml
logging:
  level:
    com.nacos.mcp.router: DEBUG
    org.springframework.ai: DEBUG
    org.springframework.experimental.mcp: DEBUG
```

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送分支
5. 创建 Pull Request

## 许可证

本项目采用 Apache 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

## 联系方式

- 项目主页: [GitHub](https://github.com/your-org/nacos-mcp-router-spring)
- 问题反馈: [Issues](https://github.com/your-org/nacos-mcp-router-spring/issues)
- 文档: [Wiki](https://github.com/your-org/nacos-mcp-router-spring/wiki)

---

## 版本历史

### v1.0.0 (当前版本)
- ✅ 完成官方 Spring AI MCP SDK 迁移
- ✅ 支持 MCP 2024-11-05 协议规范
- ✅ 实现多传输类型支持
- ✅ 完善的工具、资源、提示管理
- ✅ JSON-RPC 2.0 协议支持
- ✅ Nacos 服务发现集成

### 后续计划
- 🔄 WebSocket 传输支持
- 🔄 分布式缓存集成
- 🔄 Kubernetes Operator
- 🔄 监控告警系统 