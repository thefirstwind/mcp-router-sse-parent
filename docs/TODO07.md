参考文档：
https://nacos.io/en/blog/nacos-gvr7dx_awbbpb_gg16sv97bgirkixe/?spm=5238cd80.7f2fc5d1.0.0.642e5f9aoZLhEW&source=blog
https://nacos.io/en/blog/nacos-gvr7dx_awbbpb_qdi918msnqbvonx2/?spm=5238cd80.7f2fc5d1.0.0.642e5f9aoZLhEW&source=blog
https://modelcontextprotocol.io/sdk/java/mcp-overview
https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html
https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html


从现在开始mcp-server不再修改，作为参考 从pom.xml 暂时注释掉，不参与parent模块的编译。
创建新模块mcp-server-v2 与 mcp-server有相同的功能，而mcp-server-v2实现时，避免手动实现MCP功能，使用spring-ai-alibaba的现成的能力

先分析 spring-ai-alibaba-mcp 的能力，
参考地址：https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-mcp/ 

需要满足以下建议：

目前项目的实现满足了 `TODO06.md` 中最核心的要求：
*   `mcp-server` 能够通过 `mcp-router` 自动注册。
*   `mcp-server` 在注册时能够声明自己提供的工具 (`Tool`s)。
*   `mcp-client` 能够通过 `mcp-router` 调用 `mcp-server` 上的工具。

这是一个非常好的基础。但是，在深入分析了代码并对照了 Spring AI 和 Nacos 的官方文档后，我发现当前项目在设计和实现上存在一些关键的不足之处。**最核心的问题是：项目几乎所有 MCP 相关的功能都是通过自定义代码手动实现的，而没有利用 Spring AI 官方提供的、更稳定、更简洁的 starter 组件。**

以下是我识别出的几个主要改进方向：

### 1. **核心短板：手动实现 MCP 功能，而非使用官方 Spring AI Starter**

这是当前项目最大的问题。我们花费了大量精力编写和调试了非常复杂的自定义代码来处理 MCP 的服务注册、工具发现和心跳机制。尤其是在 `mcp-server` 模块中的 `McpRouterRegistrationConfig` 和 `McpServerRegistrationInvoker` 这两个类，它们实际上是手动重新实现了一遍 `spring-ai-mcp-server-spring-boot-starter` 提供的核心功能。

**这带来了几个严重问题：**

*   **过高的复杂度和大量的样板代码**：`McpRouterRegistrationConfig` 这个类非常庞大和复杂，它手动处理了 Spring 的生命周期（`SmartLifecycle`）、通过反射机制扫描 `@Tool` 注解、通过 `@Scheduled` 发送心跳、以及手动构造 HTTP 请求。所有这些都属于样板代码，而官方的 starter 正是为了消除它们而设计的。
*   **脆弱且易于出错**：我们刚才修复的几乎所有 Bug，都源于这套自定义的实现。例如：
    *   启动时的服务注册竞争问题（我们为此实现了复杂的重试逻辑）。
    *   服务间的循环依赖问题。
    *   心跳机制的各种错误（日志记录不当、服务名错误、Nacos Group Name 错误等）。
*   **高昂的维护成本**：这部分自定义代码不仅难以维护，而且未来很可能与官方的 MCP 协议产生偏差，导致潜在的兼容性问题。

**我的建议：**

我强烈建议对 `mcp-server` 项目进行重构，全面采用官方的 `spring-ai-mcp-server-spring-boot-starter`。这样做会带来巨大的好处：

*   **删除大量冗余代码**，特别是 `McpRouterRegistrationConfig` 及其相关的所有复杂逻辑。
*   通过在 `application.yml` 中进行简单的配置，**自动化实现服务注册和工具声明**。
*   **自动处理标准的心跳机制**。
*   确保项目**严格遵循官方 MCP 协议**，提升其稳定性和互操作性。

### 2. **次要短板：硬编码与不灵活的配置**

在当前项目中，许多关键的配置值被直接硬编码在了 Java 代码里。

**具体例子：**

*   在 `McpServerServiceImpl` 中，Nacos 的组名 `MCP_SERVER_GROUP` 和 `TOOLS_CONFIG_GROUP` 是作为 `private static final String` 存在的。
*   在 `McpRouterRegistrationConfig` 中，服务注销的 URL 是通过字符串拼接手动构造的。

**我的建议：**

所有可配置的参数都应该被移到 `application.yml` 配置文件中。这使得应用在不同环境下部署和管理时变得更加灵活，无需修改任何代码。

### 3. **其他问题：代码和项目结构**

*   **重复代码**：`mcp-router` 项目的 `src/main/a` 目录下存在一个重复的 `McpRouterController.java` 文件。这种重复代码是潜在的 Bug 源头，应该被清理。
*   **设计缺陷**：我们之前修复的“循环依赖”问题是典型的设计缺陷信号。虽然我们通过 `ApplicationContext` 解决了它，但这本身就说明了自定义注册逻辑的脆弱性。采用官方 starter 能从根本上避免这类设计问题。

### 总结与后续步骤

总而言之，当前项目虽然**功能上可以运行**，但它背负着沉重的**技术债务**。

我的核心建议是：**全面拥抱 Spring AI 生态，通过官方的 MCP starter 来重构项目**。这完全符合您提供的官方文档指引，并将使整个项目变得更加健壮、易于维护且符合行业标准。

如果您同意，我们可以立即开始这项重构工作。第一步将是修改 `pom.xml` 文件，引入正确的 Spring AI 依赖，然后移除那些冗余、复杂的自定义代码。

您希望如何进行？


