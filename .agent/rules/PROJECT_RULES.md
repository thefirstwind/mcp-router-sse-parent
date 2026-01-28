
# 项目开发规范与约定 (Project Rules)

## 1. 核心技术栈 (Technology Stack)
- **Java 版本**: 17
- **基础框架**: Spring Boot 3.4.0
- **微服务架构**: Dubbo + Nacos
  - **Dubbo**: 3.x (Triple 协议为主)
  - **注册中心**: Nacos (核心), Zookeeper (遗留/兼容)
- **持久层**: Mybatis (XML Mapper) + MySQL

## 2. 模块职责定义 (Module Roles)

### 2.1 核心路由 (mcp-router-v3)
- **定位**: 系统的核心网关和路由器。
- **职责**:
  - SSE 连接管理
  - MCP 协议转发
  - AI Client 接入
- **关键路径**: `mcp-router-v3/src/main/resources` 下的配置必须谨慎修改。

### 2.2 服务提供层 (mcp-server-v6)
- **定位**: 标准 MCP 服务提供者示例。
- **职责**:
  - 提供被路由的标准微服务能力。
  - 演示如何将业务逻辑暴露给 Router。

### 2.3 泛化调用层 (zk-mcp-parent / zkInfo)
- **定位**: Dubbo 生态与 MCP 生态的连接器（Adapter）。
- **职责**:
  - **ZK 监听**: `zkWatcherSchedulerService` 负责监听 Zookeeper 中的 Dubbo 服务变化。
  - **自动注册**: `DubboToMcpAutoRegistrationService` 将发现的 Dubbo 接口自动注册为 MCP Tool。
  - **协议转换**: `McpExecutorService` 负责将 MCP JSON-RPC 调用转换为 Dubbo 泛化调用。
- **特殊约束**: 
  - `zkInfo` 模块承担了“元数据管理”和“协议适配”双重角色的重任。

### 2.4 测试支持 (demo-provider)
- **定位**: 纯粹的测试用例。
- **职责**: 提供简单的 Dubbo 接口，用于验证 `zkInfo` 的发现逻辑。

## 3. 编码标准 (Coding Standards)

### 3.1 通用规范
- **Lombok**: 强制使用 `@Data`, `@Slf4j`, `@Builder`。
- **日志**: 
  - 统一使用 `log.info/error`。
  - 异常捕获必须记录完整堆栈：`log.error("处理失败...", e)`。

### 3.2 架构分层
- **Controller**: 
  - 入口统一返回 `ResponseEntity<T>`。
  - **必须**包含全局异常处理逻辑（Try-Catch）。
- **Service**: 
  - `Mcp*Service` 类通常涉及协议转换，需注意 JSON 序列化性能。
  - `*RegistrationService` 类涉及 Nacos/ZK 交互，需注意异步处理和超时。

### 3.3 数据模型
- **DTO**: 必须使用 `@JsonProperty` 确保与 MCP 协议（Python/Node SDK）兼容。
- **Entity**: 对应数据库表，严禁直接通过 Controller 返回 Entity。

## 4. 目录结构规范
- **核心逻辑**: `com.pajk.mcpmetainfo.core`
  - `service/`: 业务逻辑
  - `model/`: MCP 协议对象 & DTO
  - `config/`: ZK/Nacos 配置
- **持久层**: `com.pajk.mcpmetainfo.persistence`
  - `mapper/`: Mybatis 接口
  - `entity/`: 数据库实体
