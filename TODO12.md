
@nacos_register_flow.md 是官方的nacos注册流程，可以参考
当前项目结构如下
mcp-client: 调用mcp-router
mcp-router: 接收到mcp-client调用之后，在读取nacos的注册信息，找到匹配的mcp-server-v2（需要是活跃的），再通过sse协议调用mcp-server-v2的tool，
mcp-server-v2: 通过自动注册方法，上报到nacos，该项目无需修改，已经很完整了。

以下是mcp-router的改造流程：
1 当有mcp-server-v2住的到nacos时，mcp-router要能感知到，并且将注册信息，实例信息，mcp配置信息保存在本地，
2 通过nacos的listener检测到mcp-server-v2健康度，如果不可用清除相关的注册信息
3 mcp-router中提供外部可调用的所有mcp server的列表信息。
