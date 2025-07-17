curl -X POST http://localhost:8052/mcp/servers/register -H "Content-Type: application/json" -d '{"name": "demo-server","version": "1.0.0","ip": "127.0.0.1","port": 9000,"protocol": "mcp-sse","description": "演示服务","enabled": true,"serviceGroup": "mcp-server","toolsMeta": {"enabled": true,"labels": ["gray", "beta"],"region": "cn-east","capabilities": ["TOOL", "AI"],"tags": ["test", "prod"],"gray": true,"env": "dev"}}'


curl -X POST "http://localhost:8052/mcp/servers/register" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "test-mcp-server",
       "version": "1.0.1",
       "ip": "127.0.0.1",
       "port": 8061,
       "sseEndpoint": "/sse",
       "status": "UP",
       "serviceGroup": "mcp-server",
       "healthy": true,
       "ephemeral": true,
       "weight": 1.0,
       "metadata": {
         "type": "test",
         "description": "Test MCP server for enhanced configuration validation"
       }
     }'

curl "http://localhost:8052/mcp/servers/healthy?serviceName=demo-server&serviceGroup=mcp-server"

curl "http://localhost:8052/mcp/servers/instances?serviceName=demo-server&serviceGroup=mcp-server"


curl "http://localhost:8052/mcp/servers/select?serviceName=demo-server&serviceGroup=mcp-server"

curl -X DELETE "http://localhost:8052/mcp/servers/deregister?serviceName=demo-server&serviceGroup=mcp-server"

curl "http://localhost:8052/mcp/servers/config/demo-server?version=1.0.0"

curl "http://localhost:8052/mcp/servers/config/full/demo-server?version=1.0.0"

curl "http://localhost:8052/mcp/servers/config/versions/demo-server"

curl -X POST http://localhost:8052/mcp/servers/config/publish -H "Content-Type: application/json" -d '{"name": "demo-server-v2","version": "1.0.0","ip": "127.0.0.1","port": 9000,"protocol": "mcp-sse","description": "演示服务","enabled": true,"serviceGroup": "mcp-server"}'


curl -X POST http://localhost:8052/mcp/servers/config/tools/publish -H "Content-Type: application/json" -d '{"name": "demo-server","version": "1.0.0","toolsMeta": {"enabled": true,"labels": ["gray", "beta"],"region": "cn-east"}}'


curl -X POST http://localhost:8052/mcp/servers/config/version/publish -H "Content-Type: application/json" -d '{"name": "demo-server","version": "1.0.0"}'


curl -s "http://localhost:8848/nacos/v1/cs/history?search=accurate&dataId=test-server-001-1.0.0-mcp-server.json&group=mcp-server&pageNo=1&pageSize=10" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:8848/nacos/v1/cs/history?search=accurate&dataId=test-server-001-1.0.0-mcp-server.json&group=mcp-server&pageNo=1&pageSize=10"


curl -s "http://localhost:8848/nacos/v1/cs/configs?pageNo=1&pageSize=10&search=accurate&dataId=test-server-001-1.0.0-mcp-server.json&group=mcp-server" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:8848/nacos/v1/cs/configs?pageNo=1&pageSize=10&search=accurate&dataId=test-server-001-1.0.0-mcp-server.json&group=mcp-server"

-----------------------------------
appName:

curl -X POST 'http://localhost:8848/nacos/v1/cs/configs' -d 'dataId=test-appname-curl&group=mcp-server&content=test-content&appName=mcp-router-v3&srcUser=mcp-router-v3&type=json' && echo

curl -s "http://localhost:8848/nacos/v1/cs/configs?pageNo=1&pageSize=10&search=accurate&dataId=test-appname-curl&group=mcp-server" | python3 -m json.tool 2>/dev/null

-----------------------------------
case 成功例子
curl -X POST "http://localhost:8052/mcp/servers/config/publish" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-success-appname",
    "name": "success-test-mcp-server",
    "version": "1.0.0",
    "description": "成功测试服务器，验证 appName 设置",
    "enabled": true,
    "host": "localhost",
    "port": 8080,
    "protocol": "http",
    "healthCheckPath": "/health",
    "metadata": {
      "tools.names": "success_test_tool_1,success_test_tool_2"
    }
  }'

curl -s "http://localhost:8848/nacos/v1/cs/configs?pageNo=1&pageSize=10&search=accurate&dataId=test-success-appname-1.0.0-mcp-server.json&group=mcp-server" | python3 -m json.tool 2>/dev/null