curl -X POST http://localhost:8052/mcp/servers/register -H "Content-Type: application/json" -d '{"name": "demo-server","version": "1.0.0","ip": "127.0.0.1","port": 9000,"protocol": "mcp-sse","description": "演示服务","enabled": true,"serviceGroup": "mcp-server","toolsMeta": {"enabled": true,"labels": ["gray", "beta"],"region": "cn-east","capabilities": ["TOOL", "AI"],"tags": ["test", "prod"],"gray": true,"env": "dev"}}'

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

