# MCP Router V2 全量测试计划

## 📋 概述

本测试计划涵盖了 mcp-router-v2 项目的所有Controller和业务场景的全量验证，确保系统在各种情况下的稳定性和可靠性。

## 🎯 测试目标

- ✅ **功能完整性验证**：确保所有API接口功能正确
- ✅ **业务流程验证**：确保端到端业务流程正常
- ✅ **故障恢复验证**：确保系统在故障情况下能够恢复
- ✅ **性能稳定性验证**：确保系统在高并发下稳定运行
- ✅ **异常处理验证**：确保异常情况得到正确处理

## 📊 测试覆盖范围

### 1. Controller层测试 (31个测试场景)

#### HealthController (4个场景)
- ✅ 应用基础健康状态检查
- ✅ 应用就绪状态检查  
- ✅ 健康检查接口性能验证
- ✅ 错误路径测试

#### McpServerController (7个场景)
- ✅ 服务注册和发现完整流程
- ✅ 健康服务实例选择
- ✅ 服务配置管理完整流程
- ✅ 服务注销流程
- ✅ 异常情况处理
- ✅ 配置发布失败场景
- ✅ 参数验证测试

#### McpRouterController (11个场景)
- ✅ 单点路由完整流程
- ✅ 广播路由完整流程
- ✅ SSE消息发送流程
- ✅ SSE广播流程
- ✅ SSE路由流程
- ✅ 服务健康状态检查
- ✅ 路由统计信息获取
- ✅ 路由失败处理
- ✅ 不健康服务处理
- ✅ 并发路由测试
- ✅ 参数验证测试

#### HealthCheckController (8个场景)
- ✅ 全局健康状态监控
- ✅ 单个服务健康状态检查
- ✅ 熔断器管理完整流程
- ✅ 熔断器状态转换
- ✅ 健康检查统计信息
- ✅ 异常场景处理
- ✅ 性能压力测试
- ✅ 熔断器半开状态测试

### 2. 集成测试 (11个测试场景)

#### EndToEndRoutingTest (5个场景)
- ✅ 完整服务生命周期集成测试
- ✅ SSE消息传输集成测试
- ✅ 多服务并发路由集成测试
- ✅ 健康检查和熔断器集成测试
- ✅ 配置管理集成测试

#### FaultRecoveryTest (6个场景)
- ✅ 服务不可用故障恢复测试
- ✅ 熔断器故障恢复测试
- ✅ 网络分区故障恢复测试
- ✅ 负载过载故障恢复测试
- ✅ 配置中心故障恢复测试
- ✅ 多重故障恢复测试

## 🛠️ 技术栈

- **测试框架**: JUnit 5
- **Spring测试**: Spring Boot Test
- **WebFlux测试**: WebTestClient
- **Mock框架**: Mockito
- **断言库**: AssertJ
- **测试套件**: JUnit Platform Suite

## 🏗️ 项目结构

```
mcp-router-v2/src/test/java/
├── com/nacos/mcp/router/v2/
│   ├── controller/                    # Controller单元测试
│   │   ├── HealthControllerTest.java
│   │   ├── McpServerControllerTest.java
│   │   ├── McpRouterControllerTest.java
│   │   └── HealthCheckControllerTest.java
│   ├── integration/                   # 集成测试
│   │   ├── EndToEndRoutingTest.java
│   │   └── FaultRecoveryTest.java
│   └── McpRouterV2TestSuite.java     # 测试套件
└── TEST_PLAN.md                      # 测试计划文档
```

## 🚀 运行测试

### 运行完整测试套件
```bash
# 使用Maven运行完整测试套件
cd mcp-router-v2
mvn test -Dtest=McpRouterV2TestSuite

# 或运行所有测试
mvn test
```

### 运行特定测试类
```bash
# 运行健康检查测试
mvn test -Dtest=HealthControllerTest

# 运行服务管理测试
mvn test -Dtest=McpServerControllerTest

# 运行路由测试
mvn test -Dtest=McpRouterControllerTest

# 运行高级健康检查测试
mvn test -Dtest=HealthCheckControllerTest

# 运行集成测试
mvn test -Dtest=EndToEndRoutingTest

# 运行故障恢复测试
mvn test -Dtest=FaultRecoveryTest
```

### 运行特定测试方法
```bash
# 运行特定测试方法
mvn test -Dtest=HealthControllerTest#testApplicationHealthCheck
```

## 📈 测试执行策略

### 开发阶段
1. **单元测试优先**：开发新功能时先编写对应的单元测试
2. **增量测试**：每次代码变更后运行相关测试
3. **快速反馈**：使用IDE运行单个测试快速验证

### 集成阶段
1. **完整测试套件**：合并代码前运行完整测试套件
2. **端到端验证**：重点关注集成测试的执行结果
3. **性能验证**：确保性能测试通过

### 发布阶段
1. **全量测试**：发布前必须运行所有测试
2. **故障恢复验证**：重点验证故障恢复测试
3. **生产环境验证**：在类生产环境运行测试

## 🔍 测试验证点

### 功能验证
- ✅ API接口响应正确性
- ✅ 业务逻辑处理正确性
- ✅ 数据传输完整性
- ✅ 错误处理合理性

### 性能验证
- ✅ 响应时间在可接受范围内
- ✅ 并发处理能力
- ✅ 资源使用合理性
- ✅ 负载均衡效果

### 稳定性验证
- ✅ 长时间运行稳定性
- ✅ 异常情况恢复能力
- ✅ 边界条件处理
- ✅ 资源泄漏检查

## 📊 测试报告

### 执行统计
- **总测试数量**: 42个测试场景
- **覆盖Controller**: 4个主要Controller
- **覆盖业务流程**: 端到端完整流程
- **覆盖故障场景**: 6种主要故障类型

### 质量指标
- **功能覆盖率**: 100%
- **代码覆盖率**: 目标 >80%
- **API覆盖率**: 100%
- **业务场景覆盖率**: 100%

## 🚨 注意事项

### 测试环境要求
- Java 17+
- Spring Boot 3.2.5
- Maven 3.6+
- 足够的内存(建议4GB+)

### Mock配置
- 测试中使用Mock服务，避免依赖外部系统
- 禁用自动注册功能(`spring.ai.alibaba.mcp.nacos.registry.enabled=false`)
- 启用DEBUG日志便于问题排查

### 性能考虑
- 并发测试时注意资源限制
- 大量测试数据时注意内存使用
- CI/CD环境中考虑超时设置

## 🎯 持续改进

### 测试完善
1. **新功能测试**：新增功能时同步添加测试
2. **边界条件**：补充更多边界条件测试
3. **性能基准**：建立性能基准测试
4. **安全测试**：添加安全相关测试

### 测试工具
1. **测试覆盖率**：集成代码覆盖率工具
2. **性能测试**：集成性能测试工具
3. **自动化报告**：生成自动化测试报告
4. **CI/CD集成**：与CI/CD流水线深度集成

## 📞 支持

如有测试相关问题，请联系开发团队或参考：
- 项目文档
- 测试代码注释
- 错误日志分析

---

**版本**: v2.0.0  
**更新时间**: 2025年7月16日  
**维护团队**: MCP Router V2 开发团队 