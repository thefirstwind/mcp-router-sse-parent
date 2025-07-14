项目开发原则遵循 @TODO10.md ，当前项目已经基本完善，但是注册到nacos上的信息和规范的格式不同，为了提高当先项目的通用性，需要参考 @/mcp-gateway-example-jdk17 中的实现方法注册到nacos上，请改造当前项目，优先修改 mcp-server-v2，mcp-router，mcp-client，用中文思考。

验证mcp-router 到 mcp-server-v2的连通性，暂时不考虑mcp-client项目@TODO10.md 


spring.ai.mcp.server.nacos.metadata.tools.names:

spring.ai.mcp.server.nacos.metadata.mcp.type: server
 spring.ai.mcp.server.nacos.metadata.mcp.version: 1.0.0
spring.ai.mcp.server.nacos.metadata.tools.names: getAllPersons,addPerson,deletePerson,get_system_info,list_servers
spring.ai.mcp.server.nacos.metadata.scheme: http


从日志中可以看到，mcp-server-v2 和 mcp-router 之间的连接存在一些问题，主要集中在以下几个方面：
服务发现正常：mcp-server-v2 成功注册到 Nacos，mcp-router 也能正确发现并列出其工具。
协议通信问题：McpServerServiceImpl 中的 callToolViaMcpProtocol 方法尝试使用 HTTP 调用 MCP 服务器，但根据项目要求，必须使用 SSE 协议。这导致了协议违规错误。
SSE 连接问题：CustomMcpClient 在尝试建立 SSE 连接时遇到了多次连接被拒绝的情况，尽管有时连接成功，但随后又被拒绝。
事件数据解析错误：在解析 SSE 事件数据时，出现了意外字符错误，可能是由于数据格式不正确导致的。
接下来的步骤建议：
确保 ApplicationContextHolder 正确注册并使用，以便在非 Spring 管理的类中访问 Spring Bean。
检查 SSE 连接的配置，确保服务器和客户端之间的通信符合 SSE 协议的要求。
检查和修复 SSE 事件数据的格式问题，确保数据格式符合预期。
如果需要进一步的帮助或有其他问题，请随时告知。



为了按照 MCP 标准协议和流程修复项目并确保连接的正确性，我们可以采取以下步骤：

1. **检查和更新配置文件**：
   - 确保 `application.yml` 中的配置符合 MCP 协议的要求，特别是关于 SSE 连接的配置。
   - 确保 Nacos 的配置正确，服务能够正确注册和发现。

2. **验证 SSE 连接**：
   - 确保 `McpServerServiceImpl` 中的 `callToolViaMcpProtocol` 方法使用的是 SSE 协议而不是 HTTP。
   - 检查 `CustomMcpClient` 的实现，确保 SSE 连接的建立和数据解析符合 MCP 协议。

3. **日志和错误处理**：
   - 增加日志记录，特别是在连接建立和数据传输的关键点，以便更好地调试和定位问题。
   - 处理和修复日志中提到的错误，例如数据解析错误和连接被拒绝的问题。

4. **测试和验证**：
   - 运行集成测试，确保所有工具调用和服务交互都能正常工作。
   - 使用 MCP 协议的标准测试用例来验证项目的正确性。

5. **参考 MCP 文档**：
   - 查阅 MCP 协议的官方文档，确保所有实现都符合最新的标准和最佳实践。

接下来，我将帮助你检查和更新项目中的配置文件。请确认是否需要我执行这些操作。

@/mcp-server-v2 使用 
@https://github.com/alibaba/spring-ai-alibaba  核心代码在
@/spring-ai-alibaba-mcp 目录下，将mcp相关信息注册到nacos上，深度理解




本项目的初期设计方案，已经完成目标。
@TODO10.md 

现在修改了 @/mcp-server-v2 ，使用 spring-ai-alibaba-starter-nacos-mcp-server 包，实现自动装载，将mcp-server的配置信息 上送到nacos中，

但是发现 mcp-client调用 mcp-router，再调用 mcp-server-v2这条链路上，如果考虑以上修改，nacos的配置信息的格式，以及 spring-ai-alibaba与nacos的规约有所修改，或者冲突。

现在需要分析 @/spring-ai-alibaba-mcp ，参考@mcp_nacos_analysis.md 分析报告。

主要需要改写 mcp-router

