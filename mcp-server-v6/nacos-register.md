# nacos配置要求
## 1 配置管理列表中：需要创建3条记录，

|  dataId  |  Group  |  格式  | 归属应用          |
|---------|---------|---------|---------------|
| {md5}-version-mcp-tools.json|mcp-tools|JSON| mcp-server-v6 |
| {md5}-mcp-versions.json|mcp-server-versions|JSON| mcp-server-v6 |
| {md5}-{version}-mcp-server.json|mcp-server|JSON| mcp-server-V6 |

### 02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-tools.json 的内容
{"tools":[{"name":"getPersonById","description":"Get a person by their ID","inputSchema":{"type":"object","properties":{"id":{"type":"integer","format":"int64","description":"Person's ID"}},"required":["id"],"additionalProperties":false}},{"name":"getAllPersons","description":"Get all persons from the database","inputSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false}},{"name":"get_system_info","description":"Get system information","inputSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false}},{"name":"list_servers","description":"List all registered servers","inputSchema":{"type":"object","properties":{},"required":[],"additionalProperties":false}},{"name":"addPerson","description":"Add a new person to the database","inputSchema":{"type":"object","properties":{"firstName":{"type":"string","description":"Person's first name"},"lastName":{"type":"string","description":"Person's last name"},"age":{"type":"integer","format":"int32","description":"Person's age"},"nationality":{"type":"string","description":"Person's nationality"},"gender":{"type":"string","description":"Person's gender (MALE, FEMALE, OTHER)"}},"required":["firstName","lastName","age","nationality","gender"],"additionalProperties":false}}],"toolsMeta":{}}
### 02bdea21-6b44-4432-9e8e-16514ebd8cb8-mcp-versions.json
{"id":"02bdea21-6b44-4432-9e8e-16514ebd8cb8","name":"mcp-server-v6","protocol":"mcp-sse","frontProtocol":"mcp-sse","description":"mcp-server-v6","enabled":true,"capabilities":["TOOL"],"latestPublishedVersion":"1.0.1","versionDetails":[{"version":"1.0.1","release_date":"2025-08-06T07:50:31Z"}]}
### 02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-server.json
{"id":"02bdea21-6b44-4432-9e8e-16514ebd8cb8","name":"mcp-server-v6","protocol":"mcp-sse","frontProtocol":"mcp-sse","description":"mcp-server-v6","versionDetail":{"version":"1.0.1","release_date":"2025-08-06T07:50:31Z"},"remoteServerConfig":{"serviceRef":{"namespaceId":"public","groupName":"mcp-server","serviceName":"mcp-server-v6"},"exportPath":"/sse"},"enabled":true,"capabilities":["TOOL"],"toolsDescriptionRef":"02bdea21-6b44-4432-9e8e-16514ebd8cb8-1.0.1-mcp-tools.json"}

## 2 服务列表中需要注册
服务名：mcp-server-v6
分组名称：mcp-server


## 3 MCP管理

| MCP Server | 支持能力 | 类型 | 版本 | 
|--------|--------|--------|--------|
|mcp-server-v5| TOOL| mcp-sse | {version} |

MCP Server详情
命名空间：public
名称：mcp-server-v6
类型：sse
描述：{description}
Server Config:
```
{
  "mcpServers": {
    "mcp-server-v5": {
      "url": "127.0.0.1:8066/sse"
    }
  }
}
```