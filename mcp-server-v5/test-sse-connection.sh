#!/bin/bash

echo "🧪 Testing MCP Server V5 SSE Connection"
echo "======================================="

# 1. 测试MCP Server健康状态
echo "1. Testing MCP Server health..."
curl -s "http://127.0.0.1:8065/actuator/health" | jq '.' 2>/dev/null || echo "Failed to get health check from MCP Server"

echo ""
echo "2. Testing MCP message endpoint..."
curl -s "http://127.0.0.1:8065/mcp/message" | jq '.' 2>/dev/null || echo "Failed to get message endpoint"

echo ""
echo "3. Testing SSE endpoint..."
echo "Starting SSE connection test..."
curl -N -H "Accept: text/event-stream" "http://127.0.0.1:8065/sse" &
SSE_PID=$!

# 等待几秒钟看是否有响应
sleep 3

# 检查进程是否还在运行
if kill -0 $SSE_PID 2>/dev/null; then
    echo "✅ SSE connection is working"
    kill $SSE_PID
else
    echo "❌ SSE connection failed"
fi

echo ""
echo "4. Testing MCP Router connection..."
curl -s "http://localhost:8052/mcp/router/tools/mcp-server-v5" | jq '.' 2>/dev/null || echo "Failed to connect to MCP Router"

echo ""
echo "✅ SSE connection test completed!" 