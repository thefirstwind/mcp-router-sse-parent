

@/mcp-server-v5 现在只要修改这个模块的spring-boot版本改成 2.7.18 ，其他模块不变，依赖版本之间会有冲突，请调整，编译，并运行

@/mcp-server-v5 项目启动正常，但是 
1 注册到nacos时，mcp管理中 serverconfig 信息不对
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "null:8065/sse"
    }
  }
}
这里url的ip时null
2 以上问题导致外部系统通过mcp-router-v2调用 http://localhost:8052/mcp/router/tools/mcp-server-v5接口时，报错，无法找到对应的mcp server服务

3 可以参考 @/spring-cloud-alibaba 中 mcp nacos autoconfig的实现方式查找 mcp server得ip是如何注册上的。

4 mcp-server-v5项目中的 spring-boot，java，版本号不能修改

5 请修复以上问题