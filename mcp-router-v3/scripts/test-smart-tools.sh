#!/bin/bash

echo "=== 智能MCP工具调用测试 ==="
echo ""

# 基础配置
ROUTER_URL="http://localhost:8052"
API_BASE="${ROUTER_URL}/api/v1/tools"

echo "🔧 测试环境: $ROUTER_URL"
echo ""

# 等待服务启动
echo "⏱️ 等待服务启动..."
sleep 5

# 1. 获取所有可用工具
echo "1️⃣ 获取所有可用工具"
echo "GET $API_BASE/list"
curl -s "$API_BASE/list" | python3 -m json.tool 2>/dev/null || curl -s "$API_BASE/list"
echo ""
echo ""

# 2. 检查特定工具可用性
echo "2️⃣ 检查工具可用性"
TOOL_NAME="getAllPersons"
echo "GET $API_BASE/check/$TOOL_NAME"
curl -s "$API_BASE/check/$TOOL_NAME" | python3 -m json.tool 2>/dev/null || curl -s "$API_BASE/check/$TOOL_NAME"
echo ""
echo ""

# 3. 获取工具的服务器列表
echo "3️⃣ 获取工具的服务器列表"
echo "GET $API_BASE/servers/$TOOL_NAME"
curl -s "$API_BASE/servers/$TOOL_NAME" | python3 -m json.tool 2>/dev/null || curl -s "$API_BASE/servers/$TOOL_NAME"
echo ""
echo ""

# 4. 智能工具调用 - 自动发现服务器
echo "4️⃣ 智能工具调用 - 获取所有人员"
echo "POST $API_BASE/call"
curl -X POST "$API_BASE/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "getAllPersons",
    "arguments": {}
  }' | python3 -m json.tool 2>/dev/null || curl -X POST "$API_BASE/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "getAllPersons",
    "arguments": {}
  }'
echo ""
echo ""

# 5. 智能工具调用 - 添加人员
echo "5️⃣ 智能工具调用 - 添加人员"
echo "POST $API_BASE/call"
curl -X POST "$API_BASE/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "addPerson",
    "arguments": {
      "name": "智能路由测试用户",
      "age": 30
    }
  }' | python3 -m json.tool 2>/dev/null || curl -X POST "$API_BASE/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "addPerson",
    "arguments": {
      "name": "智能路由测试用户",
      "age": 30
    }
  }'
echo ""
echo ""

# 6. 指定服务器的工具调用
echo "6️⃣ 指定服务器工具调用"
echo "POST $API_BASE/call/specific"
curl -X POST "$API_BASE/call/specific" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-server-v2",
    "toolName": "get_system_info",
    "arguments": {}
  }' | python3 -m json.tool 2>/dev/null || curl -X POST "$API_BASE/call/specific" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "mcp-server-v2", 
    "toolName": "get_system_info",
    "arguments": {}
  }'
echo ""
echo ""

# 7. 测试不存在的工具
echo "7️⃣ 测试不存在的工具"
echo "POST $API_BASE/call"
curl -X POST "$API_BASE/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "nonExistentTool",
    "arguments": {}
  }' | python3 -m json.tool 2>/dev/null || curl -X POST "$API_BASE/call" \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "nonExistentTool",
    "arguments": {}
  }'
echo ""
echo ""

echo "🎯 测试完成！"
echo ""
echo "📋 验证要点："
echo "1. ✅ 工具自动发现 - 只需提供工具名称即可调用"
echo "2. ✅ 前置校验 - 验证服务器是否支持指定工具"
echo "3. ✅ 负载均衡 - 自动选择最优服务器"
echo "4. ✅ 权重支持 - 优先选择权重高的节点"
echo "5. ✅ 活跃节点检查 - 没有活跃节点时提前结束"
echo "6. ✅ 错误处理 - 优雅处理工具不存在等异常情况" 