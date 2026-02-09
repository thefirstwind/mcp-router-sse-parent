# 项目深度分析报告

> 最后更新时间: 2026-02-09
> 本文档旨在从架构设计、工程化流程、一致性保障及可持续发展四个维度，对本项目进行深度剖析。

## 1. 核心能力与模块架构分析

### 核心能力
本项目是一个基于 **MCP (Model Context Protocol)** 标准的一站式解决方案，旨在实现传统微服务架构（特别是 Dubbo 和 Spring Cloud Alibaba 生态）与现代 AI Agent 生态的无缝连接。核心目标是打破业务系统与 AI 大模型之间的壁垒，让 AI 能够“看见”并“调用”企业现有的业务服务。

### 关键模块与交互流程

| 模块名称 | 角色定位 | 核心功能 | 交互关系 |
| :--- | :--- | :--- | :--- |
| **`mcp-router-v3`** | **核心枢纽 (MCP Gateway)** | 作为 MCP 网关，连接 Nacos 注册中心，动态发现所有 MCP Server，聚合工具列表并暴露给 AI 客户端。 | 监听 Nacos 服务变化 -> 聚合工具 -> 响应 AI Client 的 SSE 长连接请求。 |
| **`zk-mcp-parent / zkInfo`** | **遗留系统桥接器 (Bridge)** | 适配器模式的极致体现。读取 Zookeeper 中的 Dubbo 服务元数据，转换为 MCP Tool 定义，以“虚拟项目”形式注册到 Nacos。 | 读取 Zookeeper/MySQL -> 协议转换 -> 注册到 Nacos（伪装成标准 MCP Server）。 |
| **`mcp-server-v6`** | **标准参考实现 (Reference)** | 基于 Spring AI 的原生 MCP Server 实现。定义 `@Tool` 注解方法，通过 WebFlux/SSE 暴露服务。 | 启动时自动将自身注册到 Nacos，供 Router 发现。 |
| **`mcp-client / spring-ai-alibaba`** | **消费者 (Consumer)** | 集成 Spring AI ChatClient，连接到 Router 或 Server，接收用户 Prompt 并驱动 LLM 调用后端工具。 | 连接 Router 获取工具 -> 调用 LLM -> 执行工具调用。 |

## 2. CI/CD 持续集成与部署体系

项目已构建了基于 **GitHub Actions** 和 **Maven** 的标准化流水线，确保代码质量与交付效率。

### 本地构建与验证
*   **构建工具**：以 Maven 为核心，确保环境无关性 (`mvn clean install`)。
*   **脚本化管理**：根目录下提供 `start-all-projects.sh` 和 `stop-all-projects.sh`，以及 `testScript/` 目录，通过 Shell 脚本实现了本地环境的一键启动、停止和自动化测试验证。

### GitHub Workflows (`.github/workflows`)
*   **`maven-build.yml`**: 标准 Java 构建流程，每次 Push 触发，确保代码编译与单元测试通过。
*   **`multi-module-build.yml`**: 针对多模块项目的构建优化策略。
*   **`test-streamable-session.yml`**: **关键质量保障**。专门针对 SSE 长连接稳定性的集成测试，确保 MCP 协议交互的可靠性。
*   **`docs.yml`**: 文档自动化部署流水线，将 `docs/` 内容发布到 GitHub Pages。

### 分支策略
*   采用 **GitHub Flow**：以 `main` 为主干。
*   所有变更必须通过 `feature/*` 或 `bugfix/*` 分支进行开发。
*   必须通过 **PR (Pull Request)** 合并，强制执行 Code Review。

## 3. 文档、代码与 Git 提交的一致性保障

项目通过“强制约束”与“自动化辅助”相结合，治理“文档腐烂”问题，确保三者同步。

### Git 规范 (强制约束)
*   **Commit Message**：严格遵循 Conventional Commits (`feat`, `fix`, `docs`, `chore` 等)，便于自动生成 Changelog，追溯变更历史。
*   **分支命名**：在 `CONTRIBUTING.md` 中明确定义了命名规范，如 `feature/add-weather-server`，保持仓库整洁。

### Agent Workflows (自动化辅助)
*   **标准化流程**：`.agent/workflows/` 目录极其关键（如 `add-mcp-server.md`, `review.md`）。
*   **虚拟 Tech Lead**：这些工作流不仅是文档，更是 AI Agent 的“行动指南”。通过让 AI 读取这些文件，确保 AI 生成的代码、执行的操作步骤严格符合团队规范，消除人为随意性。

### 文档即代码 (Docs as Code)
*   **同库管理**：文档位于 `docs/` 目录，与代码在同一仓库维护，享受版本控制。
*   **同步机制**：PR Checklist 明确包含“文档已更新”检查项。任何功能变更代码提交时，必须包含对应的文档更新，否则 PR 不予合并。

## 4. 项目可持续性设计 (Anti-Degradation)

即使更换核心开发工具（IDE）或辅助编程的大模型（LLM），项目依然能持续演进，不会发生退化。

### IDE 无关性 (IDE Agnostic)
*   **去 IDE 化构建**：项目的构建、测试、运行完全依赖 **Maven** 和 **Shell 脚本**。
*   **通用性**：不依赖 IntelliJ .idea 或 VSCode .vscode 等特定配置文件。只要有 JDK 和 Terminal，任何开发者均可立即接手并运行项目。

### LLM 无关性 (LLM Agnostic)
*   **Spring AI 抽象层**：代码基于 `Spring AI` 框架，对底层模型（OpenAI, DeepSeek, Tongyi）进行了统一抽象。更换模型只需修改 `application.yml` 的 `base-url` 和 `api-key`，无需重构业务代码。
*   **MCP 标准协议**：项目核心是实现开放的 MCP 标准。这意味着任何支持 MCP 的客户端（如 Claude Desktop 或未来的 IDE 插件）都能直接调用后端服务，不锁定于某个特定的大模型厂商。

### 知识持久化 (Knowledge Persistence)
*   **Prompt 工程化**：`.agent` 目录下的 prompt instruction 和 workflow 文件，实际上是将团队的“开发知识”与“最佳实践”**代码化**和**持久化**了。
*   **一致性复用**：无论是 GPT-4O、Claude 3.5 Sonnet 还是 DeepSeek-V3，只要读取这些 Prompt，就能以相同的风格、标准和质量进行编码，确保项目风格的长期一致性。
