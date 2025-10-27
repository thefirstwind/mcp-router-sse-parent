# 调用方法，getPersonById
curl -X POST http://localhost:8050/mcp/router/route/mcp-server-v2 \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-001",
    "method": "tools/call",
    "params": {
      "name": "getPersonById",
      "arguments": {
        "id": 1
      }
    }
  }'