# 基于 Spring AI Alibaba 的 Google Gemini 整合实施指南

> 利用现有的 Spring AI Alibaba 框架整合 Google Gemini API
> 
> 创建时间: 2026-01-28

## 📋 目录

1. [现状分析](#现状分析)
2. [整合方案](#整合方案)
3. [实施步骤](#实施步骤)
4. [配置示例](#配置示例)
5. [使用示例](#使用示例)
6. [最佳实践](#最佳实践)

---

## 🔍 现状分析

### 已有架构

您的项目已经包含了完整的 **Spring AI Alibaba** 框架，具备以下能力：

#### ✅ 现有组件

| 组件 | 路径 | 说明 |
|------|------|------|
| **Spring AI Alibaba Core** | `spring-ai-alibaba/spring-ai-alibaba-core` | 核心 Agent 接口和实现 |
| **Graph Framework** | `spring-ai-alibaba/spring-ai-alibaba-graph` | 基于 LangGraph 的工作流框架 |
| **JManus** | `spring-ai-alibaba/spring-ai-alibaba-jmanus` | Plan-Act 智能代理平台 |
| **DeepResearch** | `spring-ai-alibaba/spring-ai-alibaba-deepresearch` | 深度研究代理 |
| **MCP Support** | `spring-ai-alibaba/spring-ai-alibaba-mcp` | MCP 协议支持 |
| **NL2SQL** | `spring-ai-alibaba/spring-ai-alibaba-nl2sql` | 自然语言转 SQL |

#### ✅ 已有 Agent 类型

```
1. ReactAgent        - ReAct 模式代理（已实现）
2. ReflectAgent      - 反思模式代理（已实现）
3. SupervisorAgent   - 监督代理（已实现）
4. DashScopeAgent    - 阿里云灵积代理（已实现）
```

#### ✅ 已有工作流模式

```java
// 来自 spring-ai-alibaba-graph
- StateGraph           - 状态图（基础）
- LlmNode             - LLM 节点
- ToolNode            - 工具节点
- RouterNode          - 路由节点
- Human-in-the-Loop   - 人机协作
- Nested/Parallel     - 嵌套/并行执行
```

---

## 🎯 整合方案

### 方案选择：利用 Spring AI 的抽象层

**不需要重新造轮子！** Spring AI Alibaba 基于 **Spring AI** 构建，而 Spring AI 已经支持多种模型提供商的抽象。

### 整合策略

```
┌─────────────────────────────────────────────────────────────┐
│                   您的应用层                                  │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  JManus    │  │DeepResearch│  │ Custom App │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring AI Alibaba Agent Layer                   │
│  ┌─────────────────────────────────────────────────┐        │
│  │  ReactAgent / SupervisorAgent / CustomAgent     │        │
│  └─────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                Spring AI ChatClient (抽象层)                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │DashScope │  │ OpenAI   │  │ Gemini   │  │DeepSeek  │   │
│  │ ChatModel│  │ChatModel │  │ChatModel │  │ChatModel │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 关键点

1. **Spring AI Alibaba 的 Agent 与模型无关**：
   - `ReactAgent` 接受任何 `ChatClient`
   - `ChatClient` 可以配置为使用任何兼容的 `ChatModel`

2. **使用 Spring AI 的 OpenAI Adapter**：
   - Gemini API 可以通过 OpenAI 兼容接口访问
   - 或者通过 Vertex AI 访问
   - 或者自定义 `ChatModel` 实现

---

## 🛠️ 实施步骤

### 步骤 1: 添加 Gemini 依赖

根据您选择的集成方式，添加相应依赖：

#### 方式 A: 使用 Spring AI Vertex AI（推荐）

```xml
<!-- 在您的 pom.xml 中添加 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vertex-ai-gemini-spring-boot-starter</artifactId>
</dependency>
```

#### 方式 B: 使用 OpenAI 兼容 API

```xml
<!-- 使用现有的 OpenAI starter，配置 Base URL -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

#### 方式 C: 直接使用 Google Generative AI SDK

```xml
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-aiplatform</artifactId>
</dependency>
```

### 步骤 2: 配置 Gemini ChatModel

在 `mcp-client` 或新建的 `mcp-client-gemini` 模块中配置：

#### application.yml

```yaml
spring:
  application:
    name: mcp-client-gemini
  
  ai:
    # 方式 A: Vertex AI Gemini 配置
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID}
          location: us-central1
          chat:
            model: gemini-3-flash
            options:
              temperature: 0.7
              max-output-tokens: 2048
    
    # 方式 B: 作为 OpenAI 兼容 API（如果 Google 提供）
    openai:
      gemini:
        api-key: ${GEMINI_API_KEY}
        base-url: https://generativelanguage.googleapis.com/v1beta
        chat:
          model: gemini-3-flash
    
    # 现有的 DeepSeek 配置（保留）
    openai:
      deepseek:
        api-key: ${DEEPSEEK_API_KEY}
        base-url: https://api.deepseek.com
        chat:
          model: deepseek-chat
    
    # MCP 配置
    mcp:
      client:
        sse:
          connections:
            person-mcp-server:
              url: http://localhost:8060
            gemini-tools-server:
              url: http://localhost:8070  # 新增
```

### 步骤 3: 创建 Gemini ChatClient Bean

在您的配置类中：

```java
package com.example.mcp.client.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {
    
    /**
     * 创建 Gemini ChatClient
     */
    @Bean("geminiChatClient")
    public ChatClient geminiChatClient(
            @Qualifier("vertexAiGeminiChatModel") VertexAiGeminiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful AI assistant powered by Google Gemini.")
                .build();
    }
    
    /**
     * 创建多模型路由器（可选）
     */
    @Bean
    public MultiModelRouter multiModelRouter(
            @Qualifier("geminiChatClient") ChatClient geminiClient,
            @Qualifier("deepseekChatClient") ChatClient deepseekClient) {
        return MultiModelRouter.builder()
                .addModel("gemini-flash", geminiClient)  // 快速、低成本
                .addModel("deepseek", deepseekClient)    // 备用
                .defaultModel("gemini-flash")
                .build();
    }
}
```

### 步骤 4: 在现有 Agent 中使用 Gemini

利用 `spring-ai-alibaba-graph` 的 `ReactAgent`：

```java
package com.example.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.node.LlmNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiResearchAgent {
    
    private final ReactAgent agent;
    
    public GeminiResearchAgent(
            @Qualifier("geminiChatClient") ChatClient geminiChatClient,
            List<ToolCallback> tools) {
        
        // 使用 ReactAgent.Builder 构建代理
        this.agent = ReactAgent.builder()
                .name("gemini-research-agent")
                .chatClient(geminiChatClient)  // 使用 Gemini ChatClient
                .tools(tools)                  // 注入 MCP Tools
                .maxIterations(10)
                .build();
    }
    
    public String research(String topic) {
        var response = agent.getAndCompileGraph()
                .invoke(Map.of("input", "Research topic: " + topic));
        return response.get("output").toString();
    }
}
```

### 步骤 5: 创建 Gemini 专用工具 MCP Server

在 `mcp-server-gemini-tools` 模块中：

```java
package com.example.mcp.server.gemini.tools;

import com.alibaba.cloud.ai.tool.ToolParam;
import org.springframework.ai.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Gemini 文档理解工具
 */
@Component
public class DocumentUnderstandingTool {
    
    @Tool(description = "使用 Gemini 的 Long Context 能力分析长文档")
    public String analyzeLongDocument(
            @ToolParam(description = "文档 URL 或内容") String document,
            @ToolParam(description = "分析问题") String question) {
        
        // TODO: 调用 Gemini API 的 Document Understanding 功能
        // 使用 Long Context (支持数百万 tokens)
        
        return "文档分析结果...";
    }
    
    @Tool(description = "使用 Gemini Nano Banana 生成图像")
    public String generateImage(
            @ToolParam(description = "图像描述") String prompt) {
        
        // TODO: 调用 Gemini 的 Nano Banana API
        
        return "生成的图像 URL...";
    }
    
    @Tool(description = "使用 Gemini Veo 3.1 生成视频")
    public String generateVideo(
            @ToolParam(description = "视频描述") String prompt,
            @ToolParam(description = "视频时长（秒）") int duration) {
        
        // TODO: 调用 Veo 3.1 API
        
        return "生成的视频 URL...";
    }
}
```

### 步骤 6: 构建工作流（利用现有 Graph）

使用 `spring-ai-alibaba-graph` 构建复杂工作流：

```java
package com.example.workflow;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.node.LlmNode;
import com.alibaba.cloud.ai.graph.node.ToolNode;
import com.alibaba.cloud.ai.graph.state.OverAllState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@Component
public class MultiDocumentWorkflow {
    
    private final StateGraph graph;
    
    public MultiDocumentWorkflow(
            @Qualifier("geminiChatClient") ChatClient geminiClient,
            ToolNode toolNode) {
        
        // 创建状态图
        graph = new StateGraph();
        
        // 节点 1: 文档摘要（并行）
        LlmNode summaryNode = LlmNode.builder()
                .name("document-summary")
                .chatClient(geminiClient)
                .systemPrompt("为以下文档生成摘要")
                .build();
        
        // 节点 2: 对比分析
        LlmNode compareNode = LlmNode.builder()
                .name("document-compare")
                .chatClient(geminiClient)
                .systemPrompt("对比分析多个文档的差异")
                .build();
        
        // 节点 3: 工具调用（如需要）
        
        // 构建图
        graph.addNode("summary", summaryNode);
        graph.addNode("compare", compareNode);
        graph.addNode("tools", toolNode);
        
        // 路由逻辑
        graph.addEdge(START, "summary");
        graph.addConditionalEdges("summary", this::shouldUseTool, 
            Map.of(
                "yes", "tools",
                "no", "compare"
            ));
        graph.addEdge("tools", "compare");
        graph.addEdge("compare", END);
    }
    
    private String shouldUseTool(OverAllState state) {
        // 判断是否需要调用工具
        return state.getToolCalls().isEmpty() ? "no" : "yes";
    }
    
    public Map<String, Object> analyze(List<String> documentUrls) {
        var compiled = graph.compile();
        return compiled.invoke(Map.of("documents", documentUrls));
    }
}
```

---

## 📝 配置示例

### 完整的 application.yml

```yaml
spring:
  application:
    name: mcp-client-gemini
  
  # 数据源配置（如需要）
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  
  # AI 配置
  ai:
    # Gemini (Vertex AI)
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID:your-gcp-project}
          location: ${GCP_LOCATION:us-central1}
          credentials:
            # 方式 1: 使用服务账号 JSON
            location: file:${GCP_CREDENTIALS_PATH}
            # 方式 2: 使用应用默认凭证
            # use-application-default: true
          chat:
            model: ${GEMINI_MODEL:gemini-3-flash}
            options:
              temperature: 0.7
              max-output-tokens: 2048
              top-p: 0.95
              top-k: 40
    
    # OpenAI (DeepSeek) - 保留现有配置
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        model: deepseek-chat
    
    # MCP 客户端配置
    mcp:
      client:
        sse:
          connections:
            # 现有的 Person Server
            person-mcp-server:
              url: http://localhost:8060
              enabled: true
            
            # 新增的 Gemini Tools Server
            gemini-tools-server:
              url: http://localhost:8070
              enabled: true
            
            # 其他 MCP 服务器...
  
  # Nacos 配置（Spring Cloud Alibaba）
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER:localhost:8848}
        namespace: ${NACOS_NAMESPACE:public}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
      config:
        server-addr: ${NACOS_SERVER:localhost:8848}
        namespace: ${NACOS_NAMESPACE:public}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        file-extension: yaml

# Logging
logging:
  level:
    root: INFO
    com.alibaba.cloud.ai: DEBUG
    org.springframework.ai: DEBUG
    com.example: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# 服务端口
server:
  port: ${SERVER_PORT:8080}

# Actuator 监控
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### 环境变量配置 (.env)

```bash
# Google Cloud Platform
GCP_PROJECT_ID=your-gcp-project-id
GCP_LOCATION=us-central1
GCP_CREDENTIALS_PATH=/path/to/service-account-key.json

# Gemini Model Selection
GEMINI_MODEL=gemini-3-flash  # 或 gemini-3-pro
# GEMINI_API_KEY=your-api-key  # 如果不使用 Vertex AI

# DeepSeek (现有)
DEEPSEEK_API_KEY=your-deepseek-api-key

# Nacos
NACOS_SERVER=localhost:8848
NACOS_NAMESPACE=public
NACOS_GROUP=DEFAULT_GROUP

# Server
SERVER_PORT=8080
```

---

## 💡 使用示例

### 示例 1: 简单对话

```java
@RestController
@RequestMapping("/api/gemini")
public class GeminiController {
    
    @Autowired
    @Qualifier("geminiChatClient")
    private ChatClient geminiClient;
    
    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return geminiClient.prompt()
                .user(request.getMessage())
                .call()
                .content();
    }
}
```

### 示例 2: 使用 ReactAgent 进行智能问答

```java
@Service
public class GeminiQAService {
    
    private final ReactAgent qaAgent;
    
    public GeminiQAService(
            @Qualifier("geminiChatClient") ChatClient geminiClient,
            List<ToolCallback> tools) {
        
        this.qaAgent = ReactAgent.builder()
                .name("gemini-qa-agent")
                .chatClient(geminiClient)
                .tools(tools)  // 自动注入所有 @Tool 方法
                .maxIterations(5)
                .build();
    }
    
    public String answer(String question) {
        var result = qaAgent.getAndCompileGraph()
                .invoke(Map.of("input", question));
        return result.get("output").toString();
    }
}
```

### 示例 3: 参考 DeepResearch 实现自定义研究 Agent

查看现有的 `spring-ai-alibaba-deepresearch` 实现：

```bash
cd spring-ai-alibaba/spring-ai-alibaba-deepresearch
```

复制并修改为使用 Gemini：

```java
// 参考: spring-ai-alibaba-deepresearch/src/main/java/...
@Component
public class GeminiDeepResearch {
    
    private final StateGraph researchGraph;
    private final ChatClient geminiClient;
    
    public GeminiDeepResearch(
            @Qualifier("geminiChatClient") ChatClient geminiClient,
            WebSearchTool webSearchTool,
            WebCrawlerTool webCrawlerTool,
            PythonTool pythonTool) {
        
        this.geminiClient = geminiClient;
        this.researchGraph = buildResearchGraph();
    }
    
    private StateGraph buildResearchGraph() {
        var graph = new StateGraph();
        
        // 1. 规划节点
        var planNode = LlmNode.builder()
                .name("plan")
                .chatClient(geminiClient)
                .systemPrompt("为研究主题制定详细计划")
                .build();
        
        // 2. 执行节点（循环）
        var executeNode = LlmNode.builder()
                .name("execute")
                .chatClient(geminiClient)
                .systemPrompt("执行研究任务")
                .build();
        
        // 3. 工具节点
        var toolNode = new ToolNode("tools");
        
        // 4. 汇总节点
        var summaryNode = LlmNode.builder()
                .name("summary")
                .chatClient(geminiClient)
                .systemPrompt("汇总研究结果并生成报告")
                .build();
        
        // 构建图
        graph.addNode("plan", planNode);
        graph.addNode("execute", executeNode);
        graph.addNode("tools", toolNode);
        graph.addNode("summary", summaryNode);
        
        // 路由
        graph.addEdge(START, "plan");
        graph.addEdge("plan", "execute");
        graph.addConditionalEdges("execute",
                state -> needMoreResearch(state) ? "tools" : "summary",
                Map.of("tools", "tools", "summary", "summary"));
        graph.addEdge("tools", "execute");  // 循环
        graph.addEdge("summary", END);
        
        return graph;
    }
    
    public String research(String topic) {
        var compiled = researchGraph.compile();
        var result = compiled.invoke(Map.of("topic", topic));
        return result.get("report").toString();
    }
    
    private boolean needMoreResearch(OverAllState state) {
        // 判断逻辑...
        return state.getIterations() < 3;
    }
}
```

### 示例 4: 使用 JManus 风格的动态 Agent

参考 `spring-ai-alibaba-jmanus` 实现：

```java
// 参考: spring-ai-alibaba-jmanus/src/main/java/...
@Component
public class GeminiManus {
    
    private final StateGraph janusGraph;
    
    public GeminiManus(
            @Qualifier("geminiChatClient") ChatClient geminiClient,
            AgentService agentService) {
        
        // 构建 JManus 风格的图
        this.janusGraph = buildManusGraph(geminiClient, agentService);
    }
    
    private StateGraph buildManusGraph(
            ChatClient geminiClient, 
            AgentService agentService) {
        
        var graph = new StateGraph();
        
        // 主 Agent（使用 Gemini 3 Pro 进行规划）
        var mainAgent = ReactAgent.builder()
                .name("main-planner")
                .chatClient(geminiClient)
                .tools(agentService.getAllSubAgents())  // 动态加载子代理
                .build();
        
        // ... 构建图逻辑
        
        return graph;
    }
    
    public String execute(String task) {
        var compiled = janusGraph.compile();
        var result = compiled.invoke(Map.of("task", task));
        return result.get("result").toString();
    }
}
```

---

## 🎓 最佳实践

### 1. 模型选择策略

```java
@Component
public class ModelSelector {
    
    public ChatClient selectModel(TaskComplexity complexity) {
        return switch (complexity) {
            case SIMPLE -> geminiFlashClient;     // Gemini 3 Flash (快速、便宜)
            case MODERATE -> deepseekClient;       // DeepSeek (中等)
            case COMPLEX -> geminiProClient;       // Gemini 3 Pro (强大、准确)
            case MULTIMODAL -> geminiProClient;    // 多模态任务
        };
    }
}
```

### 2. 成本优化

- 使用 **Gemini 3 Flash** 处理简单任务
- 使用 **Gemini 3 Pro** 处理需要深度推理的任务
- 实现**缓存机制**减少重复调用
- 使用 **Structured Outputs** 减少后处理成本

### 3. 错误处理与重试

```java
@Component
public class GeminiService {
    
    @Retryable(
        value = {ApiException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String callGemini(String prompt) {
        try {
            return geminiClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (RateLimitException e) {
            // 降级到 DeepSeek
            return deepseekClient.prompt().user(prompt).call().content();
        }
    }
}
```

### 4. 监控与观测

利用 Spring AI 的原生监控支持：

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      # 添加自定义标签
      model: ${GEMINI_MODEL}
      service: gemini-integration
```

### 5. 安全最佳实践

```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public CredentialsProvider credentialsProvider() {
        // 使用 Google Cloud Secret Manager 或 Vault
        return GoogleCredentials.getApplicationDefault();
    }
    
    @Bean
    public ApiKeyManager apiKeyManager() {
        // API Key 轮换机制
        return new ApiKeyManager();
    }
}
```

---

## 📚 参考资源

### 现有项目文档

1. **Spring AI Alibaba README**: `spring-ai-alibaba/README.md`
2. **JManus 实现**: `spring-ai-alibaba/spring-ai-alibaba-jmanus/`
3. **DeepResearch 实现**: `spring-ai-alibaba/spring-ai-alibaba-deepresearch/`
4. **Graph 示例**: `spring-ai-alibaba/spring-ai-alibaba- graph/spring-ai-alibaba-graph-example/`

### 外部资源

1. **Spring AI 文档**: https://docs.spring.io/spring-ai/reference/
2. **Gemini API 文档**: https://ai.google.dev/gemini-api/docs
3. **Spring AI Alibaba GitHub**: https://github.com/alibaba/spring-ai-alibaba
4. **Spring AI Examples**: https://github.com/spring-projects/spring-ai-examples

---

## 🚀 快速开始清单

- [ ] 第 1 步: 申请 Google Cloud 账号和 Gemini API 访问权限
- [ ] 第 2 步: 在项目中添加 Vertex AI Gemini 或 OpenAI Adapter 依赖
- [ ] 第 3 步: 配置 `application.yml` 添加 Gemini 相关配置
- [ ] 第 4 步: 创建 `GeminiConfig` 配置类，定义 ChatClient Bean
- [ ] 第 5 步: 在现有的 ReactAgent 或自定义 Agent 中注入 Gemini ChatClient
- [ ] 第 6 步: （可选）创建 Gemini 专用工具 MCP Server
- [ ] 第 7 步: 测试 Gemini 集成
- [ ] 第 8 步: 参考 DeepResearch/JManus 实现自定义工作流
- [ ] 第 9 步: 添加监控和日志
- [ ] 第 10 步: 优化和部署

---

## ✅ 总结

### 核心要点

1. **不要重新造轮子**：利用现有的 Spring AI Alibaba 框架
2. **Agent 与模型无关**：ReactAgent、SupervisorAgent 等可以使用任何 ChatClient
3. **配置驱动**：通过 application.yml 切换不同模型
4. **参考现有实现**：JManus 和 DeepResearch 是很好的参考
5. **渐进式整合**：先整合基础对话，再添加高级功能

### 下一步

1. 选择一个现有的 Agent（如 ReactAgent）作为起点
2. 配置 Gemini ChatClient
3. 运行测试
4. 逐步添加 Gemini 特有功能（Document Understanding、Image Generation 等）

---

**📧 如有问题，请参考项目中的示例代码或查阅 Spring AI 文档。**
