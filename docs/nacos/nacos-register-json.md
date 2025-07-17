
mcp server项目的名称为 webflux-mcp-server


nacos的 configurationManagement中添加
* webflux-mcp-server-mcp-server.json
* webflux-mcp-server-mcp-tools.json

webflux-mcp-server-mcp-server.json 的内容如下：
```
DataID:webflux-mcp-server-mcp-server.json
Group:mcp-server
MD5:dd15549b91f46c6f68f7d381ec5e0760
{
  "protocol" : "mcp-sse",
  "name" : "webflux-mcp-server",
  "description" : "webflux-mcp-server",
  "version" : "1.0.0",
  "enabled" : true,
  "remoteServerConfig" : {
    "serviceRef" : {
      "namespaceId" : "public",
      "groupName" : "mcp-server",
      "serviceName" : "webflux-mcp-server-mcp-service"
    },
    "exportPath" : "/sse"
  },
  "toolsDescriptionRef" : "webflux-mcp-server-mcp-tools.json"
}
```

webflux-mcp-server-mcp-tools.json 的内容如下：
```

DataID:webflux-mcp-server-mcp-tools.json
Group:mcp-tools
MD5:cfbb96a3e88b5f211470be5a88ca61df
{
  "tools" : [ {
    "name" : "getCityTimeMethod",
    "description" : "Get the time of a specified city.",
    "inputSchema" : {
      "type" : "object",
      "properties" : {
        "arg0" : {
          "type" : "string",
          "description" : "Time zone id, such as Asia/Shanghai"
        }
      },
      "required" : [ "arg0" ],
      "additionalProperties" : false
    }
  } ],
  "toolsMeta" : {
    "getCityTimeMethod" : {
      "enabled" : true
    }
  }
}
```

nacos的 serviceManagement 中配置如下：
Group: mcp-server
元数据:
```
server.md5=dd15549b91f46c6f68f7d381ec5e0760
tools.names=getCityTimeMethod
```


TODO: nacos的 mcpServerManagement为空，不知道配置方法



注册类参考：/Users/shine/projects.mcp-router-sse-parent/mcp-gateway-example-jdk17/mcp-server/src/main/java/com/alibaba/cloud/ai/mcp/nacos/registry/NacosMcpRegister.java
以上json信息参考
/Users/shine/projects.mcp-router-sse-parent/mcp-gateway-example-jdk17/mcp-server/src/main/java/com/alibaba/cloud/ai/mcp/nacos/registry/model/McpServerInfo.java
/Users/shine/projects.mcp-router-sse-parent/mcp-gateway-example-jdk17/mcp-server/src/main/java/com/alibaba/cloud/ai/mcp/nacos/registry/model/RemoteServerConfigInfo.java
/Users/shine/projects.mcp-router-sse-parent/mcp-gateway-example-jdk17/mcp-server/src/main/java/com/alibaba/cloud/ai/mcp/nacos/registry/model/ServiceRefInfo.java
/Users/shine/projects.mcp-router-sse-parent/mcp-gateway-example-jdk17/mcp-server/src/main/java/com/alibaba/cloud/ai/mcp/nacos/registry/model/ToolMetaInfo.java
/Users/shine/projects.mcp-router-sse-parent/mcp-gateway-example-jdk17/mcp-server/src/main/java/com/alibaba/cloud/ai/mcp/nacos/model/McpToolsInfo.java
