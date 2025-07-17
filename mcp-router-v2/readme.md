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

测试SSE连接
curl -N -H "Accept: text/event-stream" http://localhost:8050/sse/connect?clientId=test-client-001
id:3
event:connected
data:{"sessionId":"6d0df1d4-cd4c-4df0-b6c2-989de3c52d32","clientId":"test-client-001"}


# 路由统计信息
curl -s http://localhost:8050/mcp/router/stats
{"status":"active","version":"2.0.0","timestamp":1752644091786}