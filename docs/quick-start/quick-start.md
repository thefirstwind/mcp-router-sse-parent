# Gemini + Spring AI Alibaba 快速开始

> 5 分钟快速整合 Google Gemini 到您的项目

## 🎯 目标

在现有的 `mcp-router-sse-parent` 项目中，快速添加 Google Gemini 支持，**不需要编写大量代码**。

---

## ⚡ 快速开始（3 个步骤）

### 步骤 1: 添加依赖（2 分钟）

在 `mcp-client/pom.xml` 中添加：

```xml
<!-- Spring AI Vertex AI Gemini -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vertex-ai-gemini-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

### 步骤 2: 配置文件（2 分钟）

在 `mcp-client/src/main/resources/application.yml` 添加：

```yaml
spring:
  ai:
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID}
          location: us-central1
          chat:
            model: gemini-3-flash
            options:
              temperature: 0.7
```

创建 `.env` 文件：

```bash
GCP_PROJECT_ID=your-gcp-project-id
GCP_CREDENTIALS_PATH=/path/to/credentials.json
```

### 步骤 3: 使用 Gemini（1 分钟）

创建一个简单的 Controller：

```java
@RestController
@RequestMapping("/api/gemini")
public class GeminiController {
    
    @Autowired
    @Qualifier("vertexAiGeminiChatModel")
    private ChatModel chatModel;
    
    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return ChatClient.create(chatModel)
                .prompt()
                .user(message)
                .call()
                .content();
    }
}
```

**完成！** 🎉 现在您可以调用 Gemini API 了。

---

## 🚀 进阶：使用 ReactAgent（5 分钟）

利用现有的 Spring AI Alibaba `ReactAgent`：

```java
@Service
public class GeminiAgentService {
    
    private final ReactAgent agent;
    
    public GeminiAgentService(
            @Qualifier("vertexAiGeminiChatModel") ChatModel chatModel,
            List<ToolCallback> tools) {
        
        this.agent = ReactAgent.builder()
                .name("gemini-agent")
                .chatClient(ChatClient.create(chatModel))
                .tools(tools)  // 自动注入所有 @Tool
                .maxIterations(10)
                .build();
    }
    
    public String process(String input) {
        var result = agent.getAndCompileGraph()
                .invoke(Map.of("input", input));
        return result.get("output").toString();
    }
}
```

**就这么简单！** Agent 会自动：
- 调用 Gemini 模型
- 使用您定义的 @Tool 方法
- 进行多轮推理（最多 10 次）
- 返回最终结果

---

## 📊 模型选择指南

| 模型 | 用途 | 成本 | 速度 |
|------|------|------|------|
| `gemini-3-flash` | 日常任务、快速响应 | 💰 低 | ⚡ 快 |
| `gemini-3-pro` | 复杂推理、深度分析 | 💰💰 中 | 🐢 慢 |
| `gemini-2.5-pro-tts` | 语音生成 | 💰💰 中 | ⚡ 快 |

**建议**: 默认使用 `gemini-3-flash`，需要更强推理时切换到 `gemini-3-pro`。

---

## 🔧 多模型配置

如果您想同时使用 Gemini 和 DeepSeek：

```java
@Configuration
public class MultiModelConfig {
    
    @Bean
    public ChatClient geminiClient(
            @Qualifier("vertexAiGeminiChatModel") ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
    
    @Bean
    public ChatClient deepseekClient(
            @Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
    
    @Bean
    public ModelRouter modelRouter(
            @Qualifier("geminiClient") ChatClient gemini,
            @Qualifier("deepseekClient") ChatClient deepseek) {
        
        return new ModelRouter() {
            @Override
            public ChatClient select(String task) {
                if (task.contains("复杂") || task.contains("分析")) {
                    return gemini;  // 复杂任务用 Gemini
                }
                return deepseek;  // 简单任务用 DeepSeek
            }
        };
    }
}
```

---

## 🛠️ 常见问题

### Q1: 如何获取 GCP 凭证？

1. 访问 [Google Cloud Console](https://console.cloud.google.com/)
2. 创建或选择项目
3. 启用 Vertex AI API
4. 创建服务账号并下载 JSON 密钥

### Q2: 是否支持其他 Gemini 功能？

是的！包括：
- 文档理解（PDF、图片）
- 图像生成（Nano Banana）
- 视频生成（Veo 3.1）
- 语音生成（TTS）

查看 `GEMINI_INTEGRATION_GUIDE.md` 了解详情。

### Q3: 如何切换模型？

修改 `application.yml` 中的 `model` 字段，或在代码中动态指定：

```java
ChatClient.create(chatModel)
        .prompt()
        .options(ChatOptions.builder()
                .model("gemini-3-pro")  // 临时切换模型
                .build())
        .user(message)
        .call()
        .content();
```

---

## 📚 下一步

1. ✅ 完成快速开始
2. 📖 阅读 [Gemini 整合指南](./GEMINI_INTEGRATION_GUIDE.md)
3. 🔍 参考 [Spring AI Alibaba 文档](../spring-ai-alibaba/README.md)
4. 💡 查看 DeepResearch 和 JManus 示例
5. 🚀 构建您的自定义 Agent

---

## 🎁 完整示例

查看 `examples/gemini-quickstart/` 获取完整可运行示例。

**祝您使用愉快！** 🚀
