项目实现必须参考： https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-mcp/ 

参考项目 mcp-gateway-example-jdk17，参考的项目主要是如何使用mcp的标准创建连接，请求mcp server

其他参考文档：
  * nacos协议：
    * https://nacos.io/en/blog/nacos-gvr7dx_awbbpb_gg16sv97bgirkixe/?spm=5238cd80.7f2fc5d1.0.0.642e5f9aoZLhEW&source=blog
    * https://nacos.io/en/blog/nacos-gvr7dx_awbbpb_qdi918msnqbvonx2/?spm=5238cd80.7f2fc5d1.0.0.642e5f9aoZLhEW&source=blog
  * alibaba mcp源代码：
    * https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-mcp/ 
  * mcp sdk核心代码：
    * https://modelcontextprotocol.io/sdk/java/mcp-overview
  * spring ai mcp协议：
    * https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
    * https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html
    * https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html

项目结构：
mcp-client: 端口8070
mcp-router: 端口8050
mcp-server-v1: 端口8060
mcp-server-v2: 端口8061
mcp-server-v3: 端口8062
mcp-server：废弃

mcp-server-v2 中放开tool和toolParam注释，需要声明mcp server，这是核心逻辑
mcp-server-v2启动后，通过mcp-router注册到nacos中
mcp-client调用服务时，通过mcp-router查询nacos上的注册信息，寻找对应的mcp server服务
mcp-router端口 8050， mcp-server-v2端口 8061，mcp-client端口8070，不要修改。 
项目都使用响应式编程，依赖的jar包不要使用阻塞式
编译时，使用以下命令
    mvn -f mcp-server-v2/pom.xml clean install -nsu
    mvn -f mcp-router/pom.xml clean install -nsu
    mvn -f mcp-client/pom.xml clean install -nsu

mcp-router调用mcp-server-v2使用sse协议，遵从mcp的规约，不要使用http或者https协议调用
mcp-router除了调试用的接口，不提供对外的http服务
mcp-client调用mcp-router使用sse协议，遵从mcp的规约，不要使用http或者https协议调用
mcp-server-v2除了调试用的接口，不提供对外的http服务

项目执行时，通过 sh ./run-demo.sh 运行所有项目，分析输出日志分析问题。
spring-ai-alibaba 的官方文档或一个可运行的官方示例项目，来找出本项目配置上的差异。

mcp-client验证方法如下：
    # 获取工具列表
    curl "http://localhost:8070/mcp-client/api/v1/tools/list"

    # 调用系统信息工具
    curl -X POST http://localhost:8070/mcp-client/api/v1/tools/call \
    -H "Content-Type: application/json" \
    -d '{"toolName": "get_system_info", "arguments": {}}'

    # 列出已注册的服务
    curl -X POST http://localhost:8070/mcp-client/api/v1/tools/call \
    -H "Content-Type: application/json" \
    -d '{"toolName": "list_servers", "arguments": {}}'


mcp-server-v2 mcp-server-v1 和 mcp-servcer-v3,都要注册到 nacos上，可以通过mcp-client调用
mcp-server的三个项目，对外控制器
    /sse - GET方法，用于建立SSE连接
    /mcp/message - POST方法，用于发送MCP消息


mcp-client调用方法： getAllPersons, addPerson, deletePerson，验证是否能通过 mcp-router用sse方式调用mcp-server，操作数据库

mcp-client不需要nacos配置，只要连接mcp-router即可@TODO10.md 
要使mcp-client能够通过mcp-router用SSE方式调用mcp-server操作数据库，需要：
修复mcp-client的实现：使用McpAsyncClient替代当前的WebClient HTTP调用
修复mcp-router的路由实现：使用MCP协议的SSE通信替代HTTP POST
确保符合MCP规约：所有模块间通信必须遵循MCP标准协议
结论：虽然服务架构和发现机制正常工作，但核心的MCP协议通信实现需要重构以符合TODO10.md的要求。当前项目距离真正的MCP协议实现还有关键差距。

mcp-router中的ip不要使用localhost，使用nacos注册的ip地址

MCP SSE会话管理：
mcp server的session timeout设置60s以上
mcp-router使用的是http://192.168.0.102:8061 这个是nacos中注册的地址，没有问题。

mcp-client 不能直接和 mcp-server-v1 , mcp-server-v2, mcp-server-v3 连接，只能通过 mcp-router来调用
@TODO10.md 继续完善代码，遵循 MCP协议的标准，验证getAllPersons  addPerson getAllPersons调用需要通过mcp-router调用对应的 mcp server，（模块之间通信不要使用http协议）





