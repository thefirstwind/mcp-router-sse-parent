---
description: 添加新 MCP Server 的标准化工作流
---
# 添加新 MCP Server 工作流

本工作流用于在 `mcp-router-sse-parent` 项目中添加新的 MCP Server 模块。

## 前置条件

- 项目已正常运行
- 已了解 Spring AI 的 `@Tool` 注解机制
- 已了解 MCP 协议基础

## 工作流步骤

### 阶段 1: 需求分析 (Requirements Analysis)

#### 1.1 明确 MCP Server 用途
- **输入**: 用户描述的功能需求
- **输出**: 清晰的功能定义文档
- **检查点**:
  - [ ] Server 名称已定义（如 `weather-mcp-server`）
  - [ ] 主要功能已列举（至少 3 个 `@Tool` 方法）
  - [ ] 数据源已确认（API、数据库、文件等）

#### 1.2 参考现有实现
```bash
# 查看现有 MCP Server 示例
ls -la mcp-server-v*/
cat mcp-server-v6/src/main/java/com/example/mcp/server/tool/PersonTool.java
```
- **检查点**:
  - [ ] 已查看至少 1 个现有 MCP Server
  - [ ] 理解 `@Tool` 注解的使用方式

---

### 阶段 2: 架构设计 (Architecture Design)

#### 2.1 模块结构设计
基于项目规范，创建如下结构：
```
mcp-server-<name>/
├── pom.xml
├── src/main/java/com/example/mcp/server/
│   ├── <Name>McpServerApplication.java
│   ├── config/
│   │   └── McpServerConfig.java
│   ├── tool/
│   │   └── <Name>Tool.java
│   ├── service/
│   │   └── <Name>Service.java
│   └── model/
│       └── <Name>Data.java
└── src/main/resources/
    └── application.yml
```

#### 2.2 定义 Tool 接口
- **输出**: `@Tool` 方法签名列表
- **示例**:
```java
@Tool(description = "获取天气信息")
public WeatherInfo getWeather(@ToolParam(description = "城市名称") String city);

@Tool(description = "获取天气预报")
public List<WeatherForecast> getForecast(@ToolParam(description = "城市名称") String city);
```
- **检查点**:
  - [ ] 每个方法都有清晰的 `description`
  - [ ] 参数都有 `@ToolParam` 注解
  - [ ] 返回值类型明确

---

### 阶段 3: 实现 (Implementation)

#### 3.1 创建 Maven 模块
// turbo
```bash
# 复制现有模块作为模板
cp -r mcp-server-v6 mcp-server-<name>
```

#### 3.2 修改 pom.xml
- 更新 `artifactId`
- 更新 `name` 和 `description`
- 添加必要的依赖

#### 3.3 实现 Tool 类
**规范要点**:
1. 使用 `@Component` 注解
2. 使用 Lombok `@Slf4j` 进行日志记录
3. 每个方法都有完整的 Javadoc
4. 异常处理完整

**代码模板**:
```java
package com.example.mcp.server.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.Tool;
import org.springframework.ai.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * <Name> Tool - 提供 <功能描述>
 */
@Slf4j
@Component
public class <Name>Tool {
    
    @Tool(description = "方法功能描述")
    public <ReturnType> methodName(
            @ToolParam(description = "参数描述") String param) {
        
        log.info("调用 methodName, 参数: {}", param);
        
        try {
            // 实现逻辑
            return result;
        } catch (Exception e) {
            log.error("methodName 执行失败", e);
            throw new RuntimeException("执行失败: " + e.getMessage());
        }
    }
}
```

#### 3.4 配置 application.yml
```yaml
server:
  port: 80XX  # 选择未使用的端口

spring:
  application:
    name: <name>-mcp-server
  
  ai:
    mcp:
      server:
        name: <name>-mcp-server
        version: 1.0.0
```

- **检查点**:
  - [ ] 端口号不冲突
  - [ ] MCP server name 正确配置

---

### 阶段 4: 测试 (Testing)

#### 4.1 单元测试
创建 `src/test/java/com/example/mcp/server/tool/<Name>ToolTest.java`

```java
@SpringBootTest
class <Name>ToolTest {
    
    @Autowired
    private <Name>Tool tool;
    
    @Test
    void testMethodName() {
        var result = tool.methodName("test");
        assertNotNull(result);
    }
}
```

#### 4.2 启动测试
// turbo
```bash
cd mcp-server-<name>
mvn spring-boot:run
```

- **检查点**:
  - [ ] 应用成功启动
  - [ ] 日志中显示 MCP Server 已注册
  - [ ] 端口正常监听

#### 4.3 集成测试
使用 `mcp-client` 测试连接：

```bash
# 在 mcp-client 的 application.yml 中添加
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            <name>-mcp-server:
              url: http://localhost:80XX
```

// turbo
```bash
cd ../mcp-client
mvn spring-boot:run
```

测试工具调用：
```bash
curl -X POST http://localhost:8080/api/test \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "请调用 <name> 的功能"
  }'
```

- **检查点**:
  - [ ] MCP Client 成功连接到新 Server
  - [ ] Tool 方法被正确调用
  - [ ] 返回结果符合预期

---

### 阶段 5: 文档化 (Documentation)

#### 5.1 更新 README
在项目根目录的 `README.md` 中添加：

```markdown
### X. MCP Server (`mcp-server-<name>`)
- **Purpose**: <功能描述>
- **Port**: 80XX
- **Features**:
  - <功能1>
  - <功能2>
  - <功能3>

#### Tools
1. **methodName1**: <描述>
2. **methodName2**: <描述>
```

#### 5.2 创建模块 README
在 `mcp-server-<name>/README.md` 中详细说明：
- 功能介绍
- 使用示例
- 配置说明
- API 文档

---

### 阶段 6: 代码审查 (Code Review)

运行代码审查工作流：
```bash
# 触发工作流
/review mcp-server-<name>/src/main/java/com/example/mcp/server/tool/<Name>Tool.java
```

- **检查点**:
  - [ ] 无 Critical 级别问题
  - [ ] Major 级别问题已修复
  - [ ] 代码符合项目规范

---

### 阶段 7: 集成到主项目 (Integration)

#### 7.1 更新父 pom.xml
在 `mcp-router-parent/pom.xml` 中添加模块：
```xml
<modules>
    <module>mcp-router-v3</module>
    <module>mcp-server-v3</module>
    <module>mcp-server-v4</module>
    <module>mcp-server-v6</module>
    <module>mcp-server-<name></module>  <!-- 新增 -->
</modules>
```

#### 7.2 构建测试
// turbo
```bash
cd /Users/shine/projects.mcp-router-sse-parent
mvn clean install
```

- **检查点**:
  - [ ] 整体构建成功
  - [ ] 所有测试通过

---

## 输出物清单

完成本工作流后，应产出：

- [x] 新的 MCP Server 模块代码
- [x] 单元测试（覆盖率 > 80%）
- [x] 模块 README 文档
- [x] 主 README 更新
- [x] 代码审查报告
- [x] 集成测试通过证明

---

## 常见问题

### Q1: 如何选择端口号？
**A**: 查看现有端口分配：
```bash
grep -r "server.port" mcp-server-*/src/main/resources/
```
选择未使用的 80XX 端口。

### Q2: 如何处理外部 API 调用？
**A**: 
1. 创建 `@Service` 类封装 API 调用逻辑
2. 在 Tool 类中注入 Service
3. 使用 `RestTemplate` 或 `WebClient`

### Q3: 如何测试 Tool 是否被 MCP Client 发现？
**A**: 
启动 MCP Client 后，查看日志：
```
INFO: Discovered tools from <name>-mcp-server: [methodName1, methodName2]
```

---

## 参考资源

- 现有 MCP Server 实现: `mcp-server-v6`
- Spring AI 文档: https://docs.spring.io/spring-ai/reference/
- Spring AI Alibaba Graph: `spring-ai-alibaba/spring-ai-alibaba-graph`

---

**工作流版本**: 1.0  
**最后更新**: 2026-01-28  
**适用LLM**: Claude Sonnet 4.5 / Gemini / GPT-4 / DeepSeek
