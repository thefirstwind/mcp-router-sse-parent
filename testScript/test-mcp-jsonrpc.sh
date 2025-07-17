#!/bin/bash

# 🧪 快速测试 MCP JSON-RPC 功能
echo "🧪 测试 MCP JSON-RPC 功能"
echo "========================"

# 启动 MCP Router (后台)
echo "🚀 启动 MCP Router..."
cd mcp-router
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080" > ../logs/test-router.log 2>&1 &
ROUTER_PID=$!
cd ..

# 等待启动
echo "⏳ 等待服务启动 (30秒)..."
sleep 30

# 测试 JSON-RPC initialize
echo "📡 测试 MCP initialize..."
curl -s -X POST http://localhost:8080/mcp/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {}
    }
  }' | jq '.'

echo ""
echo "🔧 测试 tools/list..."
curl -s -X POST http://localhost:8080/mcp/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }' | jq '.'

echo ""
echo "🛠️  测试工具调用..."
curl -s -X POST http://localhost:8080/mcp/jsonrpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get_system_info",
      "arguments": {}
    }
  }' | jq '.'

echo ""
echo "🔧 停止服务..."
if kill -0 $ROUTER_PID 2>/dev/null; then
    kill $ROUTER_PID 2>/dev/null
    sleep 3
    if kill -0 $ROUTER_PID 2>/dev/null; then
        kill -9 $ROUTER_PID 2>/dev/null
    fi
    echo "服务已停止"
else
    echo "服务已经停止"
fi
echo "✅ 测试完成！" 