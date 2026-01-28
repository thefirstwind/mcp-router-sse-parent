# 下一步：实际操作指南

## 📍 当前状态

您已经阅读了整合方案文档，现在让我们进行**实际操作**。

## 🎯 建议的执行路径

### 选项 A: 快速验证（推荐新手）⭐

**适合**: 第一次接触 Gemini API，想快速看到效果

**步骤**:
```bash
# 1. 使用 Spring AI Alibaba 现有的示例
cd spring-ai-alibaba/spring-ai-alibaba-deepresearch
mvn spring-boot:run

# 2. 或者运行 Playground
cd spring-ai-alibaba-examples/spring-ai-alibaba-playground
# (需要先克隆 examples 仓库)
```

**为什么**: 先了解 Spring AI Alibaba 的能力，然后再添加 Gemini

---

###选项 B: 利用现有 mcp-client 添加 Gemini 支持（推荐）⭐⭐⭐

**适合**: 您熟悉项目结构，想在现有基础上添加 Gemini

#### 步骤 1: 注意事项

**重要**: 您的项目当前使用：
- Spring Boot: 3.2.5
- Spring AI Alibaba: 1.0.0.3.250728
- Spring AI: 1.0.0

**问题**: Spring AI 官方的 Vertex AI Gemini Starter 可能还在 M4 版本，存在版本兼容性问题。

#### 解决方案

**方案 1: 使用 OpenAI 兼容接口（更简单）**

Google Gemini 可能提供 OpenAI 兼容的 API 端点，这种情况下你可以复用现有的 OpenAI ChatModel：

```xml
<!-- mcp-client/pom.xml 中已有 Spring AI Core，无需额外添加 -->
```

```yaml
# application.yml
spring:
  ai:
    openai:
      # DeepSeek (现有)
      deepseek:
        api-key: ${DEEPSEEK_API_KEY}
        base-url: https://api.deepseek.com
        chat:
          model: deepseek-chat
      
      # Gemini (新增 - 使用 OpenAI 兼容 API)
      gemini:
        api-key: ${GEMINI_API_KEY}
        base-url: https://generativelanguage.googleapis.com/v1beta/openai
        chat:
          model: gemini-3-flash
```

**方案 2: 自定义 ChatModel（更灵活）**

如果 Gemini 不提供 OpenAI 兼容接口，你可以实现自己的 `ChatModel`：

```java
// 在 mcp-client 中创建
package com.example.mcp.client.gemini;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

public class GeminiChatModel implements ChatModel {
    
    private final String apiKey;
    private final String baseUrl;
    
    // 实现 call() 和 stream() 方法
    // 调用 Gemini REST API
}
```

#### 步骤 2: 实际操作

**2.1 查看 Spring AI 版本兼容性**

```bash
cd /Users/shine/projects.mcp-router-sse-parent

# 检查 Spring AI 可用的 ChatModel 实现
mvn dependency:tree | grep spring-ai
```

**2.2 选择最合适的方式**

基于您的项目，我建议：

1. **先尝试方案 1**（OpenAI 兼容）
   - 最简单
   - 无需额外依赖
   - 如果 Gemini 支持，立即可用

2. **如果方案 1 不可行，使用方案 2**（自定义）
   - 需要写少量适配代码
   - 但完全可控

---

### 选项 C: 创建独立的 Gemini 示例模块（学习目的）

**适合**: 想深度理解整合过程，愿意从零构建

我可以为您创建一个完整的示例模块：`mcp-client-gemini-example`

#### 我为您准备的示例项目结构

```
mcp-client-gemini-example/
├── pom.xml
├── src/main/java/
│   └── com/example/gemini/
│       ├── GeminiExampleApplication.java
│       ├── config/
│       │   ├── GeminiConfig.java          # Gemini 配置
│       │   └── MultiModelConfig.java      # 多模型配置
│       ├── controller/
│       │   └── GeminiController.java      # REST API
│       ├── service/
│       │   ├── GeminiChatService.java     # 基础对话
│       │   └── GeminiAgentService.java    # Agent 工作流
│       └── model/
│           ├── GeminiChatRequest.java
│           └── GeminiChatResponse.java
└── src/main/resources/
    ├── application.yml
    └── application-dev.yml
```

---

## 💡 我的建议（基于您的项目）

### 推荐路径：选项 B（在现有 mcp-client 上扩展）

**理由**:
1. ✅ 您已经有完整的 Spring AI Alibaba 框架
2. ✅ 您有现成的 MCP Client 模块
3. ✅ 您熟悉 DeepSeek 的集成方式
4. ✅ **Gemini 就是再添加一个 ChatModel 而已**

### 具体操作步骤

#### 第 1 步：确认 Gemini API 访问权限

```bash
# 测试 Gemini API 是否可用
curl -X POST \
  'https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash:generateContent?key=YOUR_API_KEY' \
  -H 'Content-Type: application/json' \
  -d '{
    "contents": [{
      "parts":[{"text": "Hello, Gemini!"}]
    }]
  }'
```

#### 第 2 步：在 mcp-client 中添加 Gemini 配置

**2.1 修改 `application.yml`**

```yaml
gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-3-flash
  base-url: https://generativelanguage.googleapis.com/v1beta
```

**2.2 创建 `GeminiConfig.java`**

```java
@Configuration
public class GeminiConfig {
    
    @Value("${gemini.api-key}")
    private String apiKey;
    
    @Value("${gemini.base-url}")
    private String baseUrl;
    
    @Value("${gemini.model}")
    private String model;
    
    @Bean("geminiChatClient")
    public ChatClient geminiChatClient() {
        // 使用 HTTP 客户端调用 Gemini API
        // TODO: 实现 Gemini API 调用逻辑
    }
}
```

#### 第 3 步：在 ReactAgent 中使用

```java
@Service
public class GeminiAgentService {
    
    @Autowired
    @Qualifier("geminiChatClient")
    private ChatClient geminiClient;
    
    @Autowired
    private List<ToolCallback> tools;
    
    public String process(String input) {
        var agent = ReactAgent.builder()
                .name("gemini-agent")
                .chatClient(geminiClient)
                .tools(tools)
                .build();
        
        var result = agent.getAndCompileGraph()
                .invoke(Map.of("input", input));
        return result.get("output").toString();
    }
}
```

---

## 🚀 立即开始

### 如果您选择**选项 A**（快速验证）

```bash
# 运行现有的 DeepResearch 示例
cd spring-ai-alibaba/spring-ai-alibaba-deepresearch
mvn spring-boot:run
```

### 如果您选择**选项 B**（扩展 mcp-client）⭐

**告诉我您的选择，我将为您：**
1. ✅ 创建 `GeminiConfig.java` 的完整实现
2. ✅ 创建 `GeminiChatClient` 适配器
3. ✅ 创建测试Controller
4. ✅ 提供完整的配置文件

**只需回复**: "选择 B，帮我创建 Gemini 集成代码"

### 如果您选择**选项 C**（独立示例模块）

**告诉我，我将：**
1. ✅ 创建完整的 `mcp-client-gemini-example` 模块
2. ✅ 包含所有必要的配置和代码
3. ✅ 提供运行说明和测试脚本

**只需回复**: "选择 C，创建独立示例模块"

---

## 📋 准备工作清单

无论选择哪个选项，请先完成：

- [ ] 申请 Google Cloud 账号
- [ ] 启用 Gemini API
- [ ] 获取 API Key
- [ ] 测试 API 连通性（使用上面的 curl 命令）
- [ ] 设置环境变量 `GEMINI_API_KEY`

---

## 🤔 常见问题

### Q: 我应该使用 Vertex AI 还是 Generative Language API？

**A**: 
- **Generative Language API**: 更简单，使用 API Key，适合快速开始
- **Vertex AI**: 企业级，使用服务账号，更多功能

**建议**: 先用 Generative Language API 快速验证，生产环境再考虑 Vertex AI

### Q: Spring AI 版本兼容问题怎么办？

**A**: 
您的项目使用 Spring AI 1.0.0，但官方的 Vertex AI Starter 可能需要更新版本。

**解决方案**:
1. 暂时使用自定义 ChatModel（方案 2）
2. 或者升级 Spring AI 版本（但可能影响其他部分）

**我建议**: 先用自定义方式，等 Spring AI 稳定后再迁移

### Q: 可以同时使用 Gemini 和 DeepSeek 吗？

**A**: 
完全可以！这就是 Spring AI 抽象层的优势：

```java
@Autowired
@Qualifier("geminiChatClient")
private ChatClient geminiClient;

@Autowired
@Qualifier("deepseekChatClient")
private ChatClient deepseekClient;

// 根据任务选择模型
if (isComplexTask) {
    return geminiClient.prompt().user(input).call().content();
} else {
    return deepseekClient.prompt().user(input).call().content();
}
```

---

## 📞 请告诉我您的选择

回复以下之一：

1. **"选择 A"** - 我先运行现有示例，熟悉框架
2. **"选择 B"** - 在 mcp-client 中添加 Gemini（推荐）
3. **"选择 C"** - 创建独立的示例模块
4. **"我需要更多信息"** - 说明您的具体疑问

我将立即为您提供下一步的具体代码和操作指南！🚀
